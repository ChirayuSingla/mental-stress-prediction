import os
import sys
import subprocess
import numpy as np
import pandas as pd
import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from preprocessing.baseline import compute_baseline_profile, apply_profile, add_personal_deviation_features
from preprocessing.features import BASELINE_FEATURES, deviation_name, SWELL_ALL, SWELL_ALL_NO_MOUSE_DIST
from preprocessing.preprocess import build_regression_matrix
from training.models import candidate_regressors
from api.server import app, fatigue_score, burnout_indicator
from api.recommendation import RuleBasedRecommender


def _df():
    rows = []
    for p in ("P1", "P2"):
        for s in range(1, 6):
            for w in range(5):
                r = {f: float(10 + w) for f in BASELINE_FEATURES}
                r.update(participant_id=p, session_id=f"{p}_S{s}", stress_label=w % 3)
                rows.append(r)
    return pd.DataFrame(rows)


def test_baseline_uses_only_first_sessions():
    df = _df()
    out, prof = add_personal_deviation_features(df, baseline_sessions=3)
    assert prof["P1"]["typing_speed_wpm"]["n"] == 15
    assert deviation_name("typing_speed_wpm") in out.columns
    assert np.isclose(out[deviation_name("typing_speed_wpm")].mean(), 0, atol=1e-9)


def test_deviation_sign():
    df = _df()
    prof = compute_baseline_profile(df)
    row = df.iloc[[0]].copy()
    row["typing_speed_wpm"] = 100
    assert apply_profile(row, prof)[deviation_name("typing_speed_wpm")].iloc[0] > 0


def test_fatigue_bounds():
    assert 0 <= fatigue_score({"ErrorKeyRatio": 0.5, "SnAppChange": 5, "SnKeyStrokes": 100}) <= 100
    assert fatigue_score({}) == 0


def test_burnout_requires_history():
    assert burnout_indicator([5.0], [], [])[1] == 0
    # This is a descriptive history average, without a clinical threshold.
    label, score = burnout_indicator([8.0] * 6, [300] * 6, [0] * 6)
    assert score == 80 and "heuristic" in label


def test_recommender_never_empty():
    assert RuleBasedRecommender().recommend(0, 1.0, 0, 0, {}, True)


def test_synthetic_generator_labeled_demo(tmp_path):
    out = tmp_path / "d.csv"
    subprocess.check_call([
        sys.executable,
        os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "synthetic", "generate_synthetic.py"),
        "--participants", "2", "--sessions", "4", "--windows", "3", "--out", str(out),
    ])
    df = pd.read_csv(out)
    assert df["is_synthetic"].all()


def test_swell_aggregated_data():
    csv_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data", "processed", "swell_aggregated.csv")
    if not os.path.exists(csv_path):
        pytest.skip("Run real SWELL preprocessing for dataset validation")
    df = pd.read_csv(csv_path)
    assert len(df) == 75
    assert df["participant_id"].nunique() == 25
    assert not df[SWELL_ALL].isna().any().any()
    assert 0.0 <= df["stress"].min() and df["stress"].max() <= 10.0


def test_candidate_regressors_fit():
    X = np.random.RandomState(42).randn(20, len(SWELL_ALL_NO_MOUSE_DIST))
    y = np.random.RandomState(42).uniform(0, 10, size=20)
    for name, model in candidate_regressors(seed=42, include_mlp=False).items():
        model.fit(X, y)
        preds = model.predict(X)
        assert len(preds) == 20
        assert np.isfinite(preds).all()


def test_api_health_and_predict(trained_artifacts):
    from fastapi.testclient import TestClient
    X, _, _ = trained_artifacts
    with TestClient(app) as client:
        assert client.get("/health").json()["model_loaded"]
        result = client.post("/predict", json={"features": X.iloc[0].to_dict()})
        assert result.status_code == 200
        assert result.json()["data_mode"] == "REAL_SWELL"
