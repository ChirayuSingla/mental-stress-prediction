"""Small explicit test fixtures; no licensed files needed for API unit tests."""
import hashlib
import json
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import joblib
import numpy as np
import pandas as pd
import pytest
from preprocessing.features import DESKTOP_NO_DIST
from training.nested import deployment_candidates

@pytest.fixture
def trained_artifacts(tmp_path, monkeypatch):
    import api.server as server
    X = pd.DataFrame([{f: (i + 1) * .1 for f in DESKTOP_NO_DIST} for i in range(6)])
    model = deployment_candidates()["Ridge_100"].fit(X, np.arange(6, dtype=float))
    path = tmp_path / "stress_model.joblib"
    joblib.dump(model, path)
    meta = {"task": "regression", "model": "Ridge_test_fixture", "data_mode": "REAL_SWELL",
            "schema_version": 3, "feature_columns": DESKTOP_NO_DIST, "prediction_kind": "behavioral_regression",
            "model_sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    (tmp_path / "model_meta.json").write_text(json.dumps(meta))
    monkeypatch.setattr(server, "MODEL_DIR", tmp_path)
    return X, tmp_path, meta
