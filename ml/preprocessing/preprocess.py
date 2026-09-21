"""Missing-value handling, outlier clipping and feature-matrix construction."""
import pandas as pd
from .features import ABSOLUTE, APP_CATEGORY_COL, APP_CATEGORIES


def clean(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    for c in ABSOLUTE:
        if c not in df:
            raise ValueError(f"Missing required column: {c}")
        df[c] = pd.to_numeric(df[c], errors="coerce")
    for c in ABSOLUTE:  # missing values: per-participant median, then global median
        df[c] = df.groupby("participant_id")[c].transform(lambda s: s.fillna(s.median()))
        df[c] = df[c].fillna(df[c].median())
    for c in ABSOLUTE:  # outliers: winsorise to 1st/99th percentile
        lo, hi = df[c].quantile(0.01), df[c].quantile(0.99)
        df[c] = df[c].clip(lo, hi)
    df[APP_CATEGORY_COL] = df[APP_CATEGORY_COL].fillna("OTHER").astype(str).str.upper()
    df.loc[~df[APP_CATEGORY_COL].isin(APP_CATEGORIES), APP_CATEGORY_COL] = "OTHER"
    return df


def one_hot_app(df: pd.DataFrame) -> pd.DataFrame:
    for cat in APP_CATEGORIES:
        df[f"app_{cat.lower()}"] = (df[APP_CATEGORY_COL] == cat).astype(int)
    return df


def app_onehot_columns():
    return [f"app_{c.lower()}" for c in APP_CATEGORIES]


def build_matrix(df: pd.DataFrame, feature_cols):
    """Scaling lives inside each model Pipeline (StandardScaler) so CV folds never leak statistics."""
    X = df[feature_cols].astype(float).values
    y = df["stress_label"].astype(int).values
    groups = df["participant_id"].values
    return X, y, groups
