"""Nested participant-grouped selection; outer labels never select models.

Candidates are fixed before this run. No unavailable/uLog-only or questioned
mouse-distance feature enters deployment. Baselines may win: a trained reference
baseline must be identified as such, rather than disguised as behavioral inference.
"""
from collections import OrderedDict
import numpy as np
import pandas as pd
from sklearn.base import clone
from sklearn.dummy import DummyRegressor
from sklearn.ensemble import RandomForestRegressor
from sklearn.impute import SimpleImputer
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error, root_mean_squared_error, r2_score
from sklearn.model_selection import GroupKFold, cross_val_predict
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import FunctionTransformer, StandardScaler
from sklearn.svm import SVR
from preprocessing.features import DESKTOP_NO_DIST
from preprocessing.preprocess import build_regression_matrix


def metrics(y, pred):
    return {"mae": float(mean_absolute_error(y, pred)),
            "rmse": float(root_mean_squared_error(y, pred)), "r2": float(r2_score(y, pred))}


def deployment_candidates(seed=42):
    estimators = OrderedDict([
        ("DummyMean", (DummyRegressor(strategy="mean"), False)),
        ("DummyMedian", (DummyRegressor(strategy="median"), False)),
    ])
    for alpha in (10., 100., 1000.):
        estimators[f"Ridge_{alpha:g}"] = (Ridge(alpha=alpha), False)
    for alpha in (10., 100.):
        estimators[f"LogRidge_{alpha:g}"] = (Ridge(alpha=alpha), True)
    for c in (.1, 1., 2.):
        estimators[f"LogSVR_{c:g}"] = (SVR(C=c, epsilon=.2), True)
    estimators["RFRegularized"] = (RandomForestRegressor(
        n_estimators=200, min_samples_leaf=5, max_depth=3, n_jobs=1, random_state=seed), False)
    estimators["RFPrevious"] = (RandomForestRegressor(
        n_estimators=200, min_samples_leaf=2, max_depth=6, n_jobs=1, random_state=seed), False)
    result = OrderedDict()
    for name, (regressor, log) in estimators.items():
        steps = [("imputer", SimpleImputer(strategy="median", keep_empty_features=True))]
        if log:
            steps.append(("log", FunctionTransformer(np.log1p, feature_names_out="one-to-one")))
        steps.extend([("scaler", StandardScaler()), ("reg", regressor)])
        result[name] = Pipeline(steps)
    return result


def select_candidate(X, y, groups, candidates, inner_folds=4):
    """Only this supplied training subset is visible during model selection."""
    cv = GroupKFold(inner_folds)
    ranks = []
    for name, pipe in candidates.items():
        pred = cross_val_predict(clone(pipe), X, y, groups=groups, cv=cv)
        fold_mae = [mean_absolute_error(y[te], pred[te]) for _, te in cv.split(X, y, groups)]
        ranks.append({"model": name, **metrics(y, pred),
                      "mae_standard_error": float(np.std(fold_mae, ddof=1) / np.sqrt(inner_folds))})
    # Conservative one-standard-error rule: prefer a reference baseline when a
    # learned model's apparent gain is smaller than fold-to-fold uncertainty.
    ranks.sort(key=lambda row: row["mae"])
    cutoff = ranks[0]["mae"] + ranks[0]["mae_standard_error"]
    eligible = {row["model"] for row in ranks if row["mae"] <= cutoff}
    winner = next((name for name in ("DummyMean", "DummyMedian") if name in eligible), ranks[0]["model"])
    for row in ranks:
        row["selected"] = row["model"] == winner
    return winner, clone(candidates[winner]), ranks


def evaluate_nested(df, seed=42, outer_folds=5, inner_folds=4, candidates=None):
    array, y, groups = build_regression_matrix(df, DESKTOP_NO_DIST)
    X = pd.DataFrame(array, columns=DESKTOP_NO_DIST)
    candidates = deployment_candidates(seed) if candidates is None else candidates
    if len(np.unique(groups)) < max(outer_folds, inner_folds + 1):
        raise ValueError("Insufficient independent participants for nested folds")
    predictions = {name: np.empty(len(y)) for name in ["NestedSelection", "DummyMean", "DummyMedian", "RFPrevious"]}
    predictions = {name: values for name, values in predictions.items()
                   if name == "NestedSelection" or name in candidates}
    folds = []
    for number, (tr, te) in enumerate(GroupKFold(outer_folds).split(X, y, groups), 1):
        winner, pipe, ranking = select_candidate(X.iloc[tr], y[tr], groups[tr], candidates, inner_folds)
        pipe.fit(X.iloc[tr], y[tr])
        predictions["NestedSelection"][te] = pipe.predict(X.iloc[te])
        for name in predictions:
            if name != "NestedSelection":
                fixed = clone(candidates[name]).fit(X.iloc[tr], y[tr])
                predictions[name][te] = fixed.predict(X.iloc[te])
        folds.append({"fold": number, "selected_model": winner,
                      "train_participants": sorted(set(groups[tr])), "test_participants": sorted(set(groups[te])),
                      "train_samples": len(tr), "test_samples": len(te),
                      "inner_ranking": ranking, "outer_metrics": metrics(y[te], predictions["NestedSelection"][te])})
        print(f"Nested fold {number}: selected={winner}, outer MAE={folds[-1]['outer_metrics']['mae']:.4f}", flush=True)
    report = {"method": "Nested GroupKFold", "outer_folds": outer_folds, "inner_folds": inner_folds,
              "n_samples": len(df), "n_participants": len(set(groups)), "features": DESKTOP_NO_DIST,
              "selection_metric": "inner pooled OOF MAE, one-standard-error baseline preference",
              "metrics": {name: metrics(y, pred) for name, pred in predictions.items()}, "folds": folds,
              "limitations": "Internal nested estimates on a previously studied dataset; no untouched external cohort. Desktop validity is unmeasured."}
    return report, predictions


def investigate(df, report):
    """Descriptive diagnosis; not a feature-selection step or causal claim."""
    X = pd.DataFrame(df[DESKTOP_NO_DIST], columns=DESKTOP_NO_DIST)
    y = df.stress.to_numpy()
    rf = deployment_candidates()["RFPrevious"].fit(X, y)
    participant_means = df.groupby("participant_id").stress.mean()
    within = df.stress - df.groupby("participant_id").stress.transform("mean")
    return {"observations_per_feature": len(df) / len(DESKTOP_NO_DIST),
            "target_sd": float(df.stress.std(ddof=0)),
            "participant_mean_sd": float(participant_means.std(ddof=0)),
            "within_participant_residual_sd": float(within.std(ddof=0)),
            "rf_training_metrics": metrics(y, rf.predict(X)),
            "rf_grouped_outer_metrics": report["metrics"]["RFPrevious"],
            "interpretation": "Compare fitting error with held-out-participant error to assess overfitting. Participant heterogeneity, only 75 labels and collector differences limit inference; these summaries do not establish causes."}
