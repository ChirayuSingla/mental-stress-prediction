"""Local SWELL questionnaire-score regression API. No synthetic/offline fallback."""
import hashlib
import json
import os
import sys
from pathlib import Path
from contextlib import asynccontextmanager
from typing import Dict, List, Optional
import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from preprocessing.features import SWELL_ALL, FEATURE_DISPLAY_NAMES
from api.recommendation import RuleBasedRecommender, GeminiRephraser

MODEL_DIR = Path(os.environ.get("MODEL_DIR", Path(__file__).resolve().parents[1] / "models"))
_state: Dict = {}

def load_artifacts(directory):
    directory = Path(directory)
    model_file = directory / "stress_model.joblib"
    meta = json.loads((directory / "model_meta.json").read_text(encoding="utf-8"))
    cols = meta.get("feature_columns", [])
    if (meta.get("task") != "regression" or meta.get("data_mode") != "REAL_SWELL"
            or meta.get("schema_version") != 3 or not cols
            or len(set(cols)) != len(cols) or not set(cols).issubset(SWELL_ALL)):
        raise ValueError("Unsupported or inconsistent real SWELL regression metadata; retrain")
    if hashlib.sha256(model_file.read_bytes()).hexdigest() != meta.get("model_sha256"):
        raise ValueError("Model checksum differs from metadata; retrain")
    model = joblib.load(model_file)
    if model.n_features_in_ != len(cols):
        raise ValueError("Model feature count differs from metadata")
    if not np.isfinite(model.predict(np.zeros((1, len(cols))))).all():
        raise ValueError("Model produced a nonfinite validation prediction")
    return model, meta

@asynccontextmanager
async def lifespan(app):
    _state.clear()
    try:
        model, meta = load_artifacts(MODEL_DIR)
        _state.update(model=model, meta=meta)
        print(f"Loaded trained model: {meta['model']} (Task: regression)")
        print(f"Dataset mode: {meta['data_mode']}")
    except Exception as exc:
        _state["load_error"] = str(exc)
        print(f"Model unavailable: {exc}")
    _state["recommender"] = RuleBasedRecommender()
    # No unsolicited external transmission of aggregate scores.
    _state["rephraser"] = GeminiRephraser(None)
    yield
    _state.clear()

app = FastAPI(title="SWELL Questionnaire Score Research Service", version="3.0", lifespan=lifespan)

@app.exception_handler(RequestValidationError)
async def validation_error(request, exc):
    # Invalid NaN/Infinity inputs cannot themselves be serialized into an error body.
    return JSONResponse(status_code=422, content={"detail": [
        {"loc": list(e["loc"]), "msg": e["msg"], "type": e["type"]} for e in exc.errors()
    ]})

class BaselineStat(BaseModel):
    model_config = ConfigDict(allow_inf_nan=False)
    mean: float
    std: float

class PredictRequest(BaseModel):
    model_config = ConfigDict(allow_inf_nan=False, extra="forbid")
    participant_id: str = "P001"
    features: Dict[str, float]
    active_app_category: str = "OTHER"
    baseline: Optional[Dict[str, BaselineStat]] = None
    recent_stress_scores: List[float] = Field(default_factory=list)
    recent_session_durations_min: List[float] = Field(default_factory=list)
    recent_break_counts: List[int] = Field(default_factory=list)
    demo_mode: bool = False

class Factor(BaseModel):
    feature: str
    contribution: float
    direction: str
    human_readable: str

class PredictResponse(BaseModel):
    stress_class: int = -1  # regression: no measured classes/probabilities
    stress_label: str
    probabilities: Dict[str, float] = Field(default_factory=dict)
    stress_score: float
    raw_stress_score: float
    fatigue_score: float
    burnout_risk: str
    burnout_score: float
    top_factors: List[Factor]
    recommendations: List[str]
    model_name: str
    data_mode: str
    demo_mode: bool
    explanation_method: str = "one-feature replacement with training median; score difference, not SHAP or causality"
    disclaimer: str = "Exploratory SWELL questionnaire-score estimate; minute-level desktop validity unmeasured. Fatigue/history indicators are unvalidated heuristics. No clinical diagnosis."

@app.get("/health")
def health():
    meta = _state.get("meta", {})
    return {"status": "ok" if "model" in _state else "degraded",
            "model_loaded": "model" in _state, "model": meta.get("model", "n/a"),
            "task": meta.get("task"), "target": meta.get("target"),
            "data_mode": meta.get("data_mode", "n/a"), "stress_scale": "0-10",
            "features_count": len(meta.get("feature_columns", [])),
            "feature_columns": meta.get("feature_columns", []),
            "schema_version": meta.get("schema_version"), "gemini_enabled": False,
            "load_error": _state.get("load_error")}

def _build_feature_vector(req):
    cols = _state["meta"]["feature_columns"]
    missing = [c for c in cols if c not in req.features]
    if missing:
        raise HTTPException(422, detail={"missing_features": missing})
    values = np.array([[req.features[c] for c in cols]], dtype=float)
    if not np.isfinite(values).all() or (values < 0).any():
        raise HTTPException(422, detail="Features must be finite nonnegative measurements")
    return values, {c: req.features[c] for c in cols}, cols

def _explain_regression(X, f, cols, score):
    # Actual prediction sensitivity in questionnaire-score units; no invented signs.
    model = _state["model"]
    medians = model.named_steps["imputer"].statistics_
    variants = np.repeat(X, len(cols), axis=0)
    for i in range(len(cols)):
        variants[i, i] = medians[i]
    contributions = score - model.predict(variants)
    order = np.argsort(-np.abs(contributions))[:5]
    return [Factor(feature=cols[i], contribution=round(float(contributions[i]), 4),
                   direction="raises" if contributions[i] > 0 else "lowers" if contributions[i] < 0 else "unchanged",
                   human_readable=f"{FEATURE_DISPLAY_NAMES.get(cols[i], cols[i])}: {f[cols[i]]:.2f}; sensitivity to median replacement")
            for i in order]

def fatigue_score(f, session_duration_s=0):
    return round(100 * (.4 * min(1, f.get("ErrorKeyRatio", 0) * 2)
                        + .3 * min(1, f.get("SnAppChange", 0) / 10)
                        + .3 * min(1, f.get("SnKeyStrokes", 0) / 150)), 1)

def burnout_indicator(scores, durations, breaks):
    # Keep legacy function contract, but avoid assigning diagnostic categories.
    if len(scores) < 3:
        return "Insufficient history", 0.0
    value = float(np.mean(np.clip(scores[-10:], 0, 10))) * 10
    return "Score history (heuristic)", round(value, 1)

@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    if "model" not in _state:
        raise HTTPException(503, detail="Real model unavailable; run SWELL preprocessing and training. " + _state.get("load_error", ""))
    X, features, cols = _build_feature_vector(req)
    raw = float(_state["model"].predict(X)[0])
    if not np.isfinite(raw):
        raise HTTPException(500, detail="Model produced a nonfinite score")
    score = round(float(np.clip(raw, 0, 10)), 2)
    label, history = burnout_indicator(req.recent_stress_scores, req.recent_session_durations_min, req.recent_break_counts)
    return PredictResponse(stress_label=f"Estimated questionnaire score: {score:.1f} / 10",
                           stress_score=score, raw_stress_score=raw,
                           fatigue_score=fatigue_score(features), burnout_risk=label, burnout_score=history,
                           top_factors=_explain_regression(X, features, cols, raw),
                           recommendations=["This is an exploratory questionnaire-score estimate. Take regular breaks and use your own assessment of how you feel."],
                           model_name=_state["meta"]["model"], data_mode="REAL_SWELL", demo_mode=req.demo_mode)

class RecommendRequest(BaseModel):
    stress_class: int = -1
    stress_score: float
    fatigue_score: float
    burnout_score: float = 0
    features: Dict[str, float] = Field(default_factory=dict)

@app.post("/recommend")
def recommend(req: RecommendRequest):
    return {"recommendations": ["Take regular breaks and use your own assessment of how you feel."]}
