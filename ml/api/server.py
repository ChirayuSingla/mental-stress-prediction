"""
Prediction service consumed by the Java desktop app.

Why REST (FastAPI) instead of ONNX/PMML: SHAP explanations, per-participant baseline profiles and the
recommendation engine all live naturally in Python; ONNX/PMML would only export the classifier and force
re-implementing explainability in Java. Localhost HTTP on a student laptop adds ~5 ms latency.

Endpoints
  GET  /health
  POST /predict      -> stress class/probabilities/score, fatigue & burnout indicators, SHAP explanation, recommendations
  POST /recommend    -> recommendations only (rule-based, optionally re-phrased by Gemini free tier)

Run:  uvicorn api.server:app --port 8000   (from the ml/ directory)
"""
import json, os, sys
from typing import Dict, List, Optional
import numpy as np, joblib
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from preprocessing.features import ABSOLUTE, APP_CATEGORIES, BASELINE_FEATURES, LABELS, deviation_name, zscore_name
from api.recommendation import RuleBasedRecommender, GeminiRephraser

MODEL_DIR = os.environ.get("MODEL_DIR", os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "models"))
app = FastAPI(title="Stress Prediction Service", version="1.0")
_state: Dict = {}


class BaselineStat(BaseModel):
    mean: float
    std: float


class PredictRequest(BaseModel):
    participant_id: str
    features: Dict[str, float] = Field(description="absolute feature values for one window (ABSOLUTE list)")
    active_app_category: str = "OTHER"
    baseline: Optional[Dict[str, BaselineStat]] = Field(default=None, description="personal baseline from the Java BaselineEngine; "
                                                                                     "if absent, the training-time profile (if any) or zero deviation is used")
    recent_stress_scores: List[float] = Field(default_factory=list, description="mean stress score of recent sessions (for burnout indicator)")
    recent_session_durations_min: List[float] = Field(default_factory=list)
    recent_break_counts: List[int] = Field(default_factory=list)
    demo_mode: bool = False


class Factor(BaseModel):
    feature: str
    contribution: float
    direction: str
    human_readable: str


class PredictResponse(BaseModel):
    stress_class: int
    stress_label: str
    probabilities: Dict[str, float]
    stress_score: float
    fatigue_score: float
    burnout_risk: str
    burnout_score: float
    top_factors: List[Factor]
    recommendations: List[str]
    model_name: str
    data_mode: str
    demo_mode: bool
    disclaimer: str = "Behavioural research prediction only — not a medical diagnosis."


@app.on_event("startup")
def load():
    try:
        _state["model"] = joblib.load(os.path.join(MODEL_DIR, "stress_model.joblib"))
        _state["meta"] = json.load(open(os.path.join(MODEL_DIR, "model_meta.json")))
        p = os.path.join(MODEL_DIR, "baseline_profiles.json")
        _state["profiles"] = json.load(open(p)) if os.path.exists(p) else {}
        clf = _state["model"].named_steps["clf"]
        try:
            import shap
            if hasattr(clf, "estimators_") or clf.__class__.__name__.startswith("XGB") or hasattr(clf, "tree_"):
                _state["explainer"] = shap.TreeExplainer(clf)
        except Exception as e:
            print("SHAP unavailable, falling back to permutation-free linear attribution:", e)
    except FileNotFoundError:
        print("No trained model found in", MODEL_DIR, "- run training/train.py first. /predict will return 503.")
    _state["recommender"] = RuleBasedRecommender()
    _state["rephraser"] = GeminiRephraser(os.environ.get("GEMINI_API_KEY"))


@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": "model" in _state,
            "model": _state.get("meta", {}).get("model"), "data_mode": _state.get("meta", {}).get("data_mode"),
            "gemini_enabled": _state["rephraser"].enabled}


def _build_vector(req: PredictRequest):
    meta = _state["meta"]; cols = meta["feature_columns"]
    f = dict(req.features)
    missing = [c for c in ABSOLUTE if c not in f]
    if missing:
        raise HTTPException(400, f"missing features: {missing}")
    for cat in APP_CATEGORIES:
        f[f"app_{cat.lower()}"] = 1.0 if req.active_app_category.upper() == cat else 0.0
    baseline = {k: {"mean": v.mean, "std": v.std} for k, v in (req.baseline or {}).items()} \
        or _state["profiles"].get(req.participant_id, {})
    for bf in BASELINE_FEATURES:
        b = baseline.get(bf)
        if b:
            f[deviation_name(bf)] = (f[bf] - b["mean"]) / (abs(b["mean"]) + 1e-6)
            f[zscore_name(bf)] = float(np.clip((f[bf] - b["mean"]) / (b["std"] + 1e-6), -10, 10))
        else:
            f[deviation_name(bf)] = 0.0; f[zscore_name(bf)] = 0.0
    return np.array([[f[c] for c in cols]], dtype=float), f, cols, bool(baseline)


HUMAN = {
    "typing_speed_wpm": "typing speed", "error_rate": "typing error rate", "avg_key_latency_ms": "key-to-key latency",
    "mouse_speed_px_s": "mouse speed", "click_rate": "click rate", "app_switch_rate": "application switching",
    "idle_duration_s": "idle time", "continuous_work_s": "continuous working time", "mouse_speed_cv": "mouse movement irregularity",
    "backspace_rate": "backspace frequency", "typing_rhythm_cv": "typing rhythm variability", "direction_changes": "cursor direction changes",
}


def _explain(X, f, cols, target_class) -> List[Factor]:
    model = _state["model"]; Xs = model.named_steps["scaler"].transform(X)
    if "explainer" in _state:
        sv = _state["explainer"].shap_values(Xs)
        contrib = (sv[target_class][0] if isinstance(sv, list) else sv[0, :, target_class])
    else:  # linear/other models: coefficient * standardised value as a first-order attribution
        clf = model.named_steps["clf"]
        coef = clf.coef_[target_class] if hasattr(clf, "coef_") else np.zeros(Xs.shape[1])
        contrib = coef * Xs[0]
    order = np.argsort(-np.abs(contrib))[:6]
    out = []
    for i in order:
        name = cols[i]; base = name.replace("dev_", "").replace("z_", "")
        pretty = HUMAN.get(base, base.replace("_", " "))
        if name.startswith("dev_"):
            pct = f[name] * 100
            text = f"{pretty} {'increased' if pct > 0 else 'decreased'} {abs(pct):.0f}% from your baseline"
        elif name.startswith("z_"):
            text = f"{pretty} is {abs(f[name]):.1f} SD {'above' if f[name] > 0 else 'below'} your baseline"
        else:
            text = f"{pretty} = {f[name]:.2f}"
        out.append(Factor(feature=name, contribution=float(contrib[i]),
                          direction="increases" if contrib[i] > 0 else "decreases", human_readable=text))
    return out


def fatigue_score(f: Dict[str, float]) -> float:
    """Transparent 0–100 composite (not a diagnosis): continuous work, idle collapse, performance degradation."""
    cont = min(f.get("continuous_work_s", 0) / (2 * 3600), 1.0)              # 2 h continuous work -> saturates
    slow = max(0.0, -f.get("dev_typing_speed_wpm", 0))                           # typing slower than baseline
    err = max(0.0, f.get("dev_error_rate", 0))                                    # more errors than baseline
    lat = max(0.0, f.get("dev_avg_key_latency_ms", 0))
    idle_drop = max(0.0, -f.get("dev_idle_duration_s", 0)) * 0.5                 # no micro-breaks
    raw = 0.40 * cont + 0.20 * min(slow, 1) + 0.15 * min(err, 1) + 0.15 * min(lat, 1) + 0.10 * min(idle_drop, 1)
    return round(100 * min(raw, 1.0), 1)


def burnout_indicator(scores: List[float], durations: List[float], breaks: List[int]):
    """Longer-term trend indicator (0–100) — 'burnout risk indicator', NOT clinical burnout."""
    if len(scores) < 3:
        return "Insufficient history", 0.0
    s = np.array(scores[-10:])
    persistent_high = float((s >= 60).mean())
    slope = float(np.polyfit(np.arange(len(s)), s, 1)[0]) if len(s) > 1 else 0.0
    trend = float(np.clip(slope / 5.0, 0, 1))
    d = np.array(durations[-10:]) if durations else np.array([0.0])
    long_days = float((d >= 240).mean())
    b = np.array(breaks[-10:]) if breaks else np.array([1])
    few_breaks = float((b <= 1).mean())
    score = 100 * (0.45 * persistent_high + 0.20 * trend + 0.20 * long_days + 0.15 * few_breaks)
    label = "Low" if score < 35 else "Elevated" if score < 65 else "High"
    return f"{label} (indicator)", round(score, 1)


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    if "model" not in _state:
        raise HTTPException(503, "model not trained yet")
    X, f, cols, has_baseline = _build_vector(req)
    meta = _state["meta"]
    proba = _state["model"].predict_proba(X)[0]
    cls = int(np.argmax(proba))
    score = float(100 * np.dot(proba, np.array(meta["stress_score_weights"])))
    fatigue = fatigue_score(f)
    b_label, b_score = burnout_indicator(req.recent_stress_scores, req.recent_session_durations_min, req.recent_break_counts)
    factors = _explain(X, f, cols, cls)
    recs = _state["recommender"].recommend(cls, score, fatigue, b_score, f, has_baseline)
    recs = _state["rephraser"].rephrase(recs, {"stress_label": LABELS[cls], "stress_score": round(score),
                                               "fatigue_score": fatigue, "factors": [x.human_readable for x in factors[:3]]})
    return PredictResponse(stress_class=cls, stress_label=LABELS[cls],
                           probabilities={LABELS[i]: float(p) for i, p in enumerate(proba)},
                           stress_score=round(score, 1), fatigue_score=fatigue, burnout_risk=b_label, burnout_score=b_score,
                           top_factors=factors, recommendations=recs, model_name=meta["model"],
                           data_mode=meta["data_mode"], demo_mode=req.demo_mode)


class RecommendRequest(BaseModel):
    stress_class: int
    stress_score: float
    fatigue_score: float
    burnout_score: float = 0.0
    features: Dict[str, float] = Field(default_factory=dict)


@app.post("/recommend")
def recommend(req: RecommendRequest):
    recs = _state["recommender"].recommend(req.stress_class, req.stress_score, req.fatigue_score, req.burnout_score, req.features, True)
    return {"recommendations": _state["rephraser"].rephrase(recs, {"stress_score": req.stress_score})}
