"""Experiment 1 — Candidate Regression Model Comparison on Real SWELL-KW Data.

Implements regression on the continuous questionnaire Stress score (0–10 scale).
Uses participant-aware cross-validation (GroupKFold) so no participant's data
appears in both training and testing folds, strictly preventing participant leakage.
Each model is a Pipeline with StandardScaler, ensuring no preprocessing leakage.
Systematically compares models with and without SnMouseDistance.

Usage:
  python training/train.py
  python training/train.py --data data/processed/swell_aggregated.csv --out models/ --results results/
"""

import argparse
import json
import hashlib
import sklearn
import os
import sys
import time
from pathlib import Path
import numpy as np
import pandas as pd
import joblib
import yaml
from sklearn.model_selection import GroupKFold, cross_val_predict
from sklearn.metrics import mean_absolute_error, root_mean_squared_error, r2_score

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from preprocessing.features import SWELL_ALL, SWELL_ALL_NO_MOUSE_DIST, DESKTOP_ALL, DESKTOP_NO_DIST, TARGET
from preprocessing.preprocess import build_regression_matrix
from training.models import candidate_regressors
from training.nested import evaluate_nested, select_candidate, deployment_candidates, investigate
from evaluation.plots import (
    plot_regression_comparison,
    plot_actual_vs_predicted,
    plot_mousedistance_comparison,
)


def evaluate_regression_cv(pipeline, X: np.ndarray, y: np.ndarray, groups: np.ndarray, folds: int):
    """Evaluate a regressor using GroupKFold to strictly prevent participant leakage."""
    cv = GroupKFold(n_splits=folds)
    preds = cross_val_predict(pipeline, X, y, groups=groups, cv=cv)
    mae = mean_absolute_error(y, preds)
    rmse = root_mean_squared_error(y, preds)
    r2 = r2_score(y, preds)
    return {
        "mae": round(float(mae), 4),
        "rmse": round(float(rmse), 4),
        "r2": round(float(r2), 4),
    }, preds


def resolve_data_path(data_arg: str) -> Path:
    """Resolve data path with helpful fallback and error messages."""
    p = Path(data_arg)
    if p.exists():
        return p

    # Resolve only the requested path; never silently substitute another dataset.
    raise FileNotFoundError(f"Processed dataset not found: {p}. Run preprocessing/preprocess_swell.py first.")


def main():
    # Resolve default paths relative to ml/ directory
    ml_dir = Path(__file__).resolve().parents[1]
    default_out = str(ml_dir / "models")
    default_results = str(ml_dir / "results")
    default_data = str(ml_dir / "data" / "processed" / "swell_aggregated.csv")

    ap = argparse.ArgumentParser(description="Train SWELL-KW regression models for stress prediction")
    ap.add_argument("--data", default=default_data, help="Path to processed SWELL CSV")
    ap.add_argument("--out", default=default_out, help="Output directory for serialized model and metadata")
    ap.add_argument("--results", default=default_results, help="Output directory for evaluation metrics and plots")
    ap.add_argument("--config", default=str(ml_dir / "config.yaml"), help="Configuration file")
    ap.add_argument("--no-mlp", action="store_true", help="Exclude MLPRegressor to speed up training")
    args = ap.parse_args()

    # Resolve config
    cfg_path = Path(args.config)
    if not cfg_path.exists():
        cfg_path = Path(__file__).resolve().parents[1] / args.config
    cfg = yaml.safe_load(open(cfg_path)) if cfg_path.exists() else {}

    seed = cfg.get("random_seed", 42)
    folds = cfg.get("cv_folds", 5)

    data_path = resolve_data_path(args.data)
    os.makedirs(args.out, exist_ok=True)
    os.makedirs(args.results, exist_ok=True)

    print(f"\n[1/4] Loading SWELL dataset: {data_path}")
    df = pd.read_csv(data_path)
    audit_path = data_path.parent / "preprocessing_audit.json"
    audit = json.loads(audit_path.read_text(encoding="utf-8"))
    if audit.get("processed_sha256") != hashlib.sha256(data_path.read_bytes()).hexdigest():
        raise ValueError("Preprocessing provenance does not match CSV; rerun real SWELL preprocessing")
    is_demo = "is_synthetic" in df and bool(df["is_synthetic"].max())
    data_mode = "SYNTHETIC_DEMO" if is_demo else "REAL_SWELL"

    target_col = "stress" if "stress" in df.columns else TARGET
    print(f"      Samples: {len(df)}, Target: '{target_col}' (range: [{df[target_col].min():.1f}, {df[target_col].max():.1f}])")
    print(f"      Data Mode: [{data_mode}]")

    # [2/4] Systematic comparison: With vs. Without SnMouseDistance
    print(f"\n[2/4] Evaluating candidate regressors using {folds}-fold GroupKFold CV...")
    all_rows = []
    configurations = [
        ("Full (18 features)", True, SWELL_ALL),
        ("Excl SnMouseDist (17 features)", False, SWELL_ALL_NO_MOUSE_DIST),
        ("Desktop_with_distance", True, DESKTOP_ALL),
        ("Desktop_without_distance", False, DESKTOP_NO_DIST),
    ]

    best_overall = None  # (model_name, feature_cols, with_dist, metrics, preds, y)

    for cfg_label, with_dist, feature_cols in configurations:
        print(f"\n--- Feature Configuration: {cfg_label} ---")
        X, y, groups = build_regression_matrix(df, feature_cols, target_col)

        for name, pipe in candidate_regressors(seed, include_mlp=not args.no_mlp).items():
            t0 = time.time()
            metrics, preds = evaluate_regression_cv(pipe, X, y, groups, folds)
            elapsed = round(time.time() - t0, 2)

            row = {
                "model": name,
                "feature_set": cfg_label,
                "features_count": len(feature_cols),
                "mae": metrics["mae"],
                "rmse": metrics["rmse"],
                "r2": metrics["r2"],
                "train_time_s": elapsed,
                "data_mode": data_mode,
            }
            all_rows.append(row)
            print(f"  {name:15s} | MAE: {metrics['mae']:.3f} | RMSE: {metrics['rmse']:.3f} | R²: {metrics['r2']:.3f} | {elapsed}s")

            # Selection criterion: lowest MAE across participant-grouped CV folds
            if cfg_label.startswith("Desktop") and name != "DummyMean" and (best_overall is None or metrics["mae"] < best_overall[3]["mae"]):
                best_overall = (name, feature_cols, with_dist, metrics, preds, y)

    res_df = pd.DataFrame(all_rows)

    # Save full comparison table
    comp_csv = os.path.join(args.results, "exp1_snmousedistance_comparison.csv")
    res_df.to_csv(comp_csv, index=False)
    print(f"\n[3/4] Saved feature-set comparison: {comp_csv}")
    plot_mousedistance_comparison(res_df, os.path.join(args.results, "exp1_snmousedistance_comparison.png"), is_demo)

    # Best feature set table
    best_name, best_features, best_dist, best_metrics, best_preds, best_y = best_overall
    selected_subset = res_df[res_df["feature_set"] == ("Desktop_with_distance" if best_dist else "Desktop_without_distance")]
    exp1_csv = os.path.join(args.results, "exp1_model_comparison.csv")
    selected_subset.to_csv(exp1_csv, index=False)
    plot_regression_comparison(selected_subset, os.path.join(args.results, "exp1_model_comparison.png"), is_demo)

    # Actual vs predicted scatter plot for best model
    plot_actual_vs_predicted(
        best_y,
        best_preds,
        os.path.join(args.results, "exp1_actual_vs_predicted.png"),
        best_name,
        is_demo,
    )

    # Deployment selection includes baselines. Outer held-out participants never
    # influence candidate selection, transforms or learned preprocessing.
    nested_report, nested_predictions = evaluate_nested(df, seed, folds, 4)
    nested_report["diagnosis"] = investigate(df, nested_report)
    (Path(args.results) / "nested_evaluation.json").write_text(json.dumps(nested_report, indent=2), encoding="utf-8")
    best_features = DESKTOP_NO_DIST
    best_dist = False
    X_dev = df[best_features]
    best_name, final_pipeline, ranking = select_candidate(
        X_dev, df[target_col].to_numpy(), df.participant_id.to_numpy(), deployment_candidates(seed), 4)
    pd.DataFrame(ranking).to_csv(Path(args.results) / "development_selection.csv", index=False)
    best_metrics = nested_report["metrics"]["NestedSelection"]
    best_preds = nested_predictions["NestedSelection"]
    best_y = df[target_col].to_numpy()
    pd.DataFrame(nested_report["metrics"]).T.to_csv(Path(args.results) / "nested_metrics.csv", index_label="procedure")

    # [4/4] Refit selected deployment procedure on all data after evaluation.
    print(f"\n[4/4] Best overall model: {best_name} ({'with' if best_dist else 'without'} SnMouseDistance)")
    print(f"      CV Metrics: MAE = {best_metrics['mae']:.3f}, RMSE = {best_metrics['rmse']:.3f}, R² = {best_metrics['r2']:.3f}")

    X_full, y_full, _ = build_regression_matrix(df, best_features, target_col)
    final_pipeline.fit(pd.DataFrame(X_full, columns=best_features), y_full)

    # Save model artifact
    model_path = os.path.join(args.out, "stress_model.joblib")
    joblib.dump(final_pipeline, model_path)
    print(f"      Saved model artifact: {model_path}")

    # Save comprehensive metadata
    meta = {
        "model": best_name,
        "task": "regression",
        "target": target_col,
        "stress_scale": "0-10",
        "feature_columns": best_features,
        "sn_mouse_distance_included": best_dist,
        "cv_metrics": best_metrics,
        "data_mode": data_mode,
        "n_samples": len(df),
        "n_participants": int(df["participant_id"].nunique()),
        "schema_version": 3,
        "feature_units": "mean per-minute counts/distance; mean minute ratios",
        "cv_method": "Nested GroupKFold", "cv_folds": folds, "inner_cv_folds": 4,
        "prediction_kind": "reference_baseline" if best_name.startswith("Dummy") else "behavioral_regression",
        "development_selection_metrics": next(row for row in ranking if row["model"] == best_name),
        "nested_comparators": nested_report["metrics"],
        "excluded_desktop_features": ["SnMouseAct", "SnTabfocusChange", "SnMouseDistance"],
        "evaluation_note": "CV metrics evaluate nested model selection, whose chosen candidate can differ by outer fold. Full-data refit selected by inner grouped MAE including baselines. No external or desktop validation.",
        "source_sha256": audit["source_sha256"],
        "processed_sha256": audit["processed_sha256"],
        "model_sha256": hashlib.sha256(Path(model_path).read_bytes()).hexdigest(),
        "sklearn_version": sklearn.__version__,
        "ground_truth_note": (
            "SWELL-KW Stress scores are continuous questionnaire ratings (0-10 scale) "
            "measured once per condition block and repeated across minute rows. "
            "Evaluation uses GroupKFold cross-validation grouped by participant to prevent leakage."
        ),
    }

    meta_path = os.path.join(args.out, "model_meta.json")
    with open(meta_path, "w") as f:
        json.dump(meta, f, indent=2)
    cv = GroupKFold(n_splits=folds)
    fold_assignment = np.zeros(len(df), dtype=int)
    fold_report = []
    for fold, (tr, te) in enumerate(cv.split(X_full, y_full, df.participant_id), 1):
        fold_assignment[te] = fold
        fold_report.append({"fold": fold, "train_participants": sorted(df.iloc[tr].participant_id.unique().tolist()),
                            "test_participants": sorted(df.iloc[te].participant_id.unique().tolist()),
                            "train_samples": len(tr), "test_samples": len(te)})
    pd.DataFrame({"participant_id": df.participant_id, "block": df.block, "condition": df.condition,
                  "fold": fold_assignment, "actual": best_y, "predicted": best_preds}).to_csv(Path(args.results) / "selected_oof_predictions.csv", index=False)
    (Path(args.results) / "cv_folds.json").write_text(json.dumps(fold_report, indent=2))
    print(f"      Saved model metadata: {meta_path}")

    print("\n[SUCCESS] Model training and evaluation complete.")


if __name__ == "__main__":
    main()
