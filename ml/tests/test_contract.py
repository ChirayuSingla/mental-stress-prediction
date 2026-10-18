"""Regression tests for data leakage safeguards and the actual HTTP contract."""
import json
from pathlib import Path
import numpy as np
import pandas as pd
import pytest
from fastapi.testclient import TestClient
from sklearn.model_selection import GroupKFold
from api.server import app, load_artifacts
from preprocessing.features import SWELL_ALL
from preprocessing.preprocess import build_regression_matrix
from training.models import candidate_regressors

ROOT = Path(__file__).resolve().parents[1]

@pytest.fixture
def real_data():
    path = ROOT / "data/processed/swell_aggregated.csv"
    if not path.exists():
        pytest.skip("Licensed workbook preprocessing required")
    return pd.read_csv(path)

def test_no_repeated_minutes_or_synthetic_training(real_data):
    with pytest.raises(ValueError, match="Repeated minute"):
        build_regression_matrix(pd.concat([real_data, real_data.iloc[:1]]))
    with pytest.raises(ValueError, match="Synthetic"):
        build_regression_matrix(real_data.assign(is_synthetic=1))
    with pytest.raises(ValueError, match="Only labeled"):
        build_regression_matrix(real_data.assign(condition="R"))

def test_participant_folds_are_disjoint(real_data):
    X, y, groups = build_regression_matrix(real_data)
    for tr, te in GroupKFold(5).split(X, y, groups):
        assert not set(groups[tr]) & set(groups[te])
        assert len(set(groups[tr])) == 20 and len(set(groups[te])) == 5
        assert len(tr) == 60 and len(te) == 15

def test_imputer_is_fit_only_on_training_values():
    pipe = candidate_regressors(include_mlp=False)["Ridge"]
    pipe.fit(np.array([[1., np.nan], [3., 5.], [5., 7.]]), [1, 2, 3])
    assert pipe.named_steps["imputer"].statistics_.tolist() == [3., 6.]
    pipe.predict([[10000., 10000.]])
    assert pipe.named_steps["imputer"].statistics_.tolist() == [3., 6.]

def test_model_checksum_mismatch_is_rejected(tmp_path):
    (tmp_path / "stress_model.joblib").write_bytes(b"invalid model")
    meta = {"task": "regression", "data_mode": "REAL_SWELL", "schema_version": 3,
            "feature_columns": SWELL_ALL, "model_sha256": "wrong"}
    (tmp_path / "model_meta.json").write_text(json.dumps(meta))
    with pytest.raises(ValueError, match="checksum"):
        load_artifacts(tmp_path)

def test_http_prediction_and_invalid_requests(trained_artifacts):
    real_data, _, _ = trained_artifacts  # explicit isolated API fixture
    with TestClient(app) as client:
        health = client.get("/health").json()
        assert health["model_loaded"] and health["schema_version"] == 3
        features = real_data.iloc[0][health["feature_columns"]].to_dict()
        body = {"participant_id": "independent-test", "features": features}
        response = client.post("/predict", json=body)
        assert response.status_code == 200, response.text
        result = response.json()
        assert result["stress_class"] == -1 and result["probabilities"] == {}
        assert result["data_mode"] == "REAL_SWELL" and 0 <= result["stress_score"] <= 10
        assert result["top_factors"] and "not SHAP" in result["explanation_method"]
        changed = client.post("/predict", json={**body, "participant_id": "another-participant",
                   "baseline": {"SnChars": {"mean": 9999, "std": 1}}}).json()
        assert changed["raw_stress_score"] == result["raw_stress_score"]
        assert client.post("/predict", json={"features": {}}).status_code == 422
        assert client.post("/predict", json={**body, "features": {**features, "unknown": 1}}).status_code == 422
        assert client.post("/predict", json={**body, "features": {**features, "CharactersRatio": 1.1}}).status_code == 422
        for invalid in (True, "1", None):
            invalid_features = {**features, next(iter(features)): invalid}
            assert client.post("/predict", json={**body, "features": invalid_features}).status_code == 422
        features[next(iter(features))] = -1
        assert client.post("/predict", json=body).status_code == 422
        assert client.post("/predict", content='{"features":{"SnChars":NaN}}',
                           headers={"Content-Type": "application/json"}).status_code == 422

def test_missing_model_returns_503(tmp_path, monkeypatch):
    import api.server as server
    monkeypatch.setattr(server, "MODEL_DIR", tmp_path)
    with TestClient(app) as client:
        assert not client.get("/health").json()["model_loaded"]
        assert client.post("/predict", json={"features": {}}).status_code == 503

def test_reordered_feature_metadata_is_rejected(trained_artifacts):
    _, folder, meta = trained_artifacts
    meta["feature_columns"] = list(reversed(meta["feature_columns"]))
    (folder / "model_meta.json").write_text(json.dumps(meta))
    with pytest.raises(ValueError, match="order"):
        load_artifacts(folder)

def test_corrupt_model_with_matching_hash_is_unavailable(trained_artifacts):
    import hashlib
    import api.server as server
    _, folder, meta = trained_artifacts
    path = folder / "stress_model.joblib"
    path.write_bytes(b"not a serialized model")
    meta["model_sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
    (folder / "model_meta.json").write_text(json.dumps(meta))
    with TestClient(app) as client:
        assert client.get("/health").json()["status"] == "degraded"
        assert client.post("/predict", json={"features": {}}).status_code == 503

def test_trained_reference_is_explicit_and_has_no_behavioral_factors(trained_artifacts):
    import hashlib, joblib
    from training.nested import deployment_candidates
    X, folder, meta = trained_artifacts
    model = deployment_candidates()["DummyMean"].fit(X, [1., 2., 3., 4., 5., 6.])
    path = folder / "stress_model.joblib"
    joblib.dump(model, path)
    meta.update(model="DummyMean", prediction_kind="reference_baseline",
                model_sha256=hashlib.sha256(path.read_bytes()).hexdigest())
    (folder / "model_meta.json").write_text(json.dumps(meta))
    with TestClient(app) as client:
        response = client.post("/predict", json={"features": X.iloc[0].to_dict()})
        assert response.status_code == 200
        result = response.json()
        assert result["stress_score"] == 3.5
        assert result["prediction_kind"] == "reference_baseline"
        assert result["explanation_method"] == "population training-target reference; no behavioral feature attribution"
        assert "not individualized" in result["stress_label"]
        assert result["top_factors"] == []
        assert result["burnout_risk"] == "Insufficient history"
