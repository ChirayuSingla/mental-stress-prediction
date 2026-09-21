"""
Personal Baseline Engine (Python side, used for offline training).

Mirrors com.stressml.baseline.BaselineEngine in the Java app so that training-time and runtime
deviation features are computed identically.

For each participant the first `baseline_sessions` sessions are calibration. Baseline statistics
are computed on those windows only and applied to ALL of that participant's windows — future
sessions never leak into the baseline.
"""
import pandas as pd
from .features import BASELINE_FEATURES, deviation_name, zscore_name

EPS = 1e-6


def compute_baseline_profile(calib: pd.DataFrame) -> dict:
    profile = {}
    for f in BASELINE_FEATURES:
        col = calib[f].astype(float)
        q1, q3 = col.quantile(0.25), col.quantile(0.75)
        profile[f] = {"mean": float(col.mean()), "median": float(col.median()),
                      "std": float(col.std(ddof=0)), "low": float(q1 - 1.5 * (q3 - q1)),
                      "high": float(q3 + 1.5 * (q3 - q1)), "n": int(len(col))}
    return profile


def apply_profile(df: pd.DataFrame, profile: dict) -> pd.DataFrame:
    out = df.copy()
    for f in BASELINE_FEATURES:
        p = profile[f]
        out[deviation_name(f)] = (out[f] - p["mean"]) / (abs(p["mean"]) + EPS)   # relative deviation
        out[zscore_name(f)] = ((out[f] - p["mean"]) / (p["std"] + EPS)).clip(-10, 10)  # standardised deviation, capped
    return out


def add_personal_deviation_features(df: pd.DataFrame, baseline_sessions: int = 3,
                                    participant_col="participant_id", session_col="session_id"):
    """Returns (df_with_deviation_features, {participant: profile})."""
    profiles, parts = {}, []
    for pid, g in df.groupby(participant_col, sort=False):
        sessions = sorted(g[session_col].unique())
        calib = g[g[session_col].isin(sessions[:baseline_sessions])]
        prof = compute_baseline_profile(calib)
        profiles[pid] = prof
        parts.append(apply_profile(g, prof))
    return pd.concat(parts).sort_index(), profiles
