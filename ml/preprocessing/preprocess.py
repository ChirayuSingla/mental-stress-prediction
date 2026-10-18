"""Validate condition-block observations; learned preprocessing belongs in folds."""
import numpy as np
from .features import SWELL_ALL

def validate_swell_df(df, feature_cols, target_col="stress"):
    required = list(feature_cols) + [target_col, "participant_id", "block", "condition"]
    missing = [c for c in required if c not in df]
    if missing:
        raise ValueError(f"Missing required columns: {missing}")
    if "is_synthetic" in df and df.is_synthetic.astype(str).str.lower().isin(["1", "1.0", "true"]).any():
        raise ValueError("Synthetic data is demo-only and cannot enter SWELL experiments")
    if df[["participant_id", "block", "condition", target_col]].isna().any().any():
        raise ValueError("Missing identifiers or target")
    if not df.condition.isin(["N", "I", "T"]).all():
        raise ValueError("Only labeled N/I/T conditions are supervised observations")
    if df.duplicated(["participant_id", "block", "condition"]).any():
        raise ValueError("Repeated minute rows detected: aggregate participant/block/condition first")
    y = df[target_col].to_numpy(dtype=float)
    X = df[list(feature_cols)].to_numpy(dtype=float)
    if not np.isfinite(y).all() or ((y < 0) | (y > 10)).any():
        raise ValueError("Target must be finite questionnaire ratings in [0, 10]")
    if np.isinf(X).any() or (X < 0).any():
        raise ValueError("Infinite or negative interaction values require investigation")

def build_regression_matrix(df, feature_cols=None, target_col="stress"):
    cols = SWELL_ALL if feature_cols is None else feature_cols
    validate_swell_df(df, cols, target_col)
    return (df[cols].to_numpy(dtype=float), df[target_col].to_numpy(dtype=float),
            df.participant_id.astype(str).to_numpy())

build_matrix = build_regression_matrix
