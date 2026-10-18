"""Experiments 2–5: Scientific Ablation, Reliability & Explainability on SWELL-KW.

Run after training/train.py.
  2. Feature-group ablation (Keyboard, Mouse, App, Keyboard+Mouse, Multimodal)
  3. SnMouseDistance reliability ablation across all candidate regressors
  4. Feature importance (Gini impurity, Permutation importance) and SHAP summary
  5. Condition-level stress response comparison (Neutral vs. Interruption vs. Time Pressure)

Usage:
  python evaluation/run_experiments.py
  python evaluation/run_experiments.py --data data/processed/swell_aggregated.csv --results results/
"""

import argparse
import os
import sys
from pathlib import Path
import numpy as np
import pandas as pd
import yaml
from sklearn.model_selection import GroupKFold, cross_val_predict
from sklearn.metrics import mean_absolute_error, root_mean_squared_error, r2_score
from sklearn.inspection import permutation_importance

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from preprocessing.features import (
    SWELL_ALL,
    SWELL_ALL_NO_MOUSE_DIST,
    FEATURE_GROUPS,
    FEATURE_DISPLAY_NAMES,
    TARGET,
)
from preprocessing.preprocess import build_regression_matrix
from training.models import candidate_regressors
from evaluation.plots import (
    plot_group_bars,
    plot_importance,
    plot_condition_distribution,
    plot_mousedistance_comparison,
)


def cv_regression(model, X: np.ndarray, y: np.ndarray, groups: np.ndarray, folds: int):
    """Run GroupKFold cross-validation and compute regression metrics."""
    cv = GroupKFold(n_splits=folds)
    preds = cross_val_predict(model, X, y, groups=groups, cv=cv)
    mae = mean_absolute_error(y, preds)
    rmse = root_mean_squared_error(y, preds)
    r2 = r2_score(y, preds)
    return {
        "mae": round(float(mae), 4),
        "rmse": round(float(rmse), 4),
        "r2": round(float(r2), 4),
    }, preds


def resolve_data_path(data_arg: str) -> Path:
    p = Path(data_arg)
    if p.exists():
        return p
    raise FileNotFoundError(f"SWELL dataset not found: {p}; run preprocessing first.")


def main():
    ml_dir = Path(__file__).resolve().parents[1]
    default_data = str(ml_dir / "data" / "processed" / "swell_aggregated.csv")
    default_results = str(ml_dir / "results")

    ap = argparse.ArgumentParser(description="Run SWELL-KW experiments 2-5")
    ap.add_argument("--data", default=default_data, help="Path to processed SWELL CSV")
    ap.add_argument("--results", default=default_results, help="Output directory for results")
    ap.add_argument("--config", default=str(ml_dir / "config.yaml"), help="Config file")
    ap.add_argument("--model", default="RandomForest", help="Model to use for ablation and SHAP")
    args = ap.parse_args()

    cfg_path = Path(args.config)
    cfg = yaml.safe_load(open(cfg_path)) if cfg_path.exists() else {}
    os.makedirs(args.results, exist_ok=True)

    seed = cfg.get("random_seed", 42)
    folds = cfg.get("cv_folds", 5)

    data_path = resolve_data_path(args.data)
    df = pd.read_csv(data_path)
    is_demo = "is_synthetic" in df and bool(df["is_synthetic"].max())
    tag = "SYNTHETIC_DEMO" if is_demo else "REAL_SWELL"
    target_col = "stress" if "stress" in df.columns else TARGET

    print(f"\n=================================================================")
    print(f"Running Experiments 2–5 on SWELL-KW dataset [{tag}]")
    print(f"Samples: {len(df)}, Target: '{target_col}', Model: {args.model}")
    print(f"=================================================================\n")

    # ---- Experiment 2: Feature-group ablation ----
    print("[Exp 2] Feature-Group Ablation (GroupKFold CV)...")
    exp2_rows = []
    reg_models = candidate_regressors(seed, include_mlp=False)
    eval_model = reg_models.get(args.model, reg_models["RandomForest"])

    for gname, cols in FEATURE_GROUPS.items():
        X, y, groups = build_regression_matrix(df, cols, target_col)
        m, _ = cv_regression(eval_model, X, y, groups, folds)
        m.update({
            "group": gname,
            "n_features": len(cols),
            "features": ";".join(cols),
            "model": args.model,
            "data_mode": tag,
        })
        exp2_rows.append(m)
        print(f"  {gname:26s} | {len(cols):2d} feats | MAE: {m['mae']:.3f} | RMSE: {m['rmse']:.3f} | R²: {m['r2']:.3f}")

    r2_df = pd.DataFrame(exp2_rows)
    exp2_csv = os.path.join(args.results, "exp2_feature_groups.csv")
    r2_df.to_csv(exp2_csv, index=False)
    plot_group_bars(
        r2_df,
        os.path.join(args.results, "exp2_feature_groups.png"),
        f"Experiment 2 — Feature-Group Ablation ({args.model})",
        is_demo,
        cols=("mae", "rmse"),
    )

    # ---- Experiment 3: SnMouseDistance reliability ablation ----
    print("\n[Exp 3] SnMouseDistance Reliability Comparison across all regressors...")
    exp3_rows = []
    for with_dist, label, cols in [(True, "With_SnMouseDistance", SWELL_ALL), (False, "Without_SnMouseDistance", SWELL_ALL_NO_MOUSE_DIST)]:
        X, y, groups = build_regression_matrix(df, cols, target_col)
        for mname, pipe in candidate_regressors(seed, include_mlp=False).items():
            m, _ = cv_regression(pipe, X, y, groups, folds)
            m.update({
                "model": mname,
                "feature_set": label,
                "n_features": len(cols),
                "data_mode": tag,
            })
            exp3_rows.append(m)
            print(f"  {mname:15s} | {label:26s} | MAE: {m['mae']:.3f} | RMSE: {m['rmse']:.3f} | R²: {m['r2']:.3f}")

    r3_df = pd.DataFrame(exp3_rows)
    exp3_csv = os.path.join(args.results, "exp3_snmousedistance_reliability.csv")
    r3_df.to_csv(exp3_csv, index=False)
    plot_mousedistance_comparison(
        r3_df,
        os.path.join(args.results, "exp3_snmousedistance_reliability.png"),
        is_demo,
    )

    # ---- Experiment 4: Feature importance & SHAP explainability ----
    print("\n[Exp 4] Feature Importance (Impurity & Permutation) + SHAP Analysis...")
    features_to_explain = SWELL_ALL_NO_MOUSE_DIST
    X, y, groups = build_regression_matrix(df, features_to_explain, target_col)

    # Train Random Forest on full data for feature importance analysis
    rf_pipe = candidate_regressors(seed, include_mlp=False)["RandomForest"]
    rf_pipe.fit(X, y)
    rf_reg = rf_pipe.named_steps["reg"]

    # Gini / variance-reduction impurity importance
    imp_series = pd.Series(rf_reg.feature_importances_, index=features_to_explain)
    imp_csv = os.path.join(args.results, "exp4_feature_importance_rf.csv")
    imp_series.sort_values(ascending=False).to_csv(imp_csv, header=["importance"])
    plot_importance(
        imp_series,
        os.path.join(args.results, "exp4_feature_importance_rf.png"),
        "Experiment 4 — Random Forest Feature Importance (Impurity Reduction)",
        is_demo,
    )

    # Permutation importance
    from sklearn.base import clone
    fold_importances = []
    for tr, te in GroupKFold(n_splits=folds).split(X, y, groups):
        fitted = clone(rf_pipe).fit(X[tr], y[tr])
        perm = permutation_importance(fitted, X[te], y[te], n_repeats=10,
                                     scoring="neg_mean_absolute_error", random_state=seed, n_jobs=1)
        fold_importances.append(perm.importances_mean)
    perm_values = np.mean(fold_importances, axis=0)
    perm_series = pd.Series(perm_values, index=features_to_explain)
    perm_csv = os.path.join(args.results, "exp4_permutation_importance.csv")
    perm_series.sort_values(ascending=False).to_csv(perm_csv, header=["importance_mean"])
    plot_importance(
        perm_series,
        os.path.join(args.results, "exp4_permutation_importance.png"),
        "Experiment 4 — Held-out participant permutation importance (MAE increase)",
        is_demo,
    )

    # SHAP summary plot for continuous regression
    try:
        import shap
        import matplotlib.pyplot as plt

        scaler = rf_pipe.named_steps["scaler"]
        X_scaled = scaler.transform(rf_pipe.named_steps["imputer"].transform(X))
        explainer = shap.TreeExplainer(rf_reg)
        shap_values = explainer.shap_values(X_scaled)

        # Plot SHAP summary
        plt.figure(figsize=(9, 6))
        shap.summary_plot(
            shap_values,
            X_scaled,
            feature_names=[FEATURE_DISPLAY_NAMES.get(f, f) for f in features_to_explain],
            show=False,
            max_display=15,
        )
        plt.title("Experiment 4 — SHAP Feature Attribution for Continuous Stress Prediction")
        plt.tight_layout()
        shap_png = os.path.join(args.results, "exp4_shap_summary.png")
        plt.savefig(shap_png, dpi=150)
        plt.close()

        # Mean absolute SHAP values
        mean_abs_shap = pd.Series(np.abs(shap_values).mean(axis=0), index=features_to_explain)
        mean_abs_shap.sort_values(ascending=False).to_csv(
            os.path.join(args.results, "exp4_shap_mean_abs.csv"), header=["mean_abs_shap"]
        )
        print("  SHAP analysis successfully generated.")
    except Exception as exc:
        print(f"  [Exp 4] SHAP analysis skipped due to: {exc}")

    # ---- Experiment 5: Condition-level stress response ----
    print("\n[Exp 5] Condition-Level Stress Analysis (Neutral vs. Interruption vs. Time Pressure)...")
    # Ground truth stress distribution across experimental conditions
    cond_plot = os.path.join(args.results, "exp5_condition_stress_distribution.png")
    plot_condition_distribution(df, cond_plot, is_demo)

    # Cross-validated predicted vs actual stress across conditions
    _, cv_preds = cv_regression(eval_model, X, y, groups, folds)
    df_pred = df.copy()
    df_pred["predicted_stress"] = cv_preds
    df_pred["prediction_error"] = np.abs(df_pred["predicted_stress"] - df_pred[target_col])

    cond_summary = df_pred.groupby("condition").agg(
        actual_stress_mean=("stress", "mean"),
        actual_stress_std=("stress", "std"),
        predicted_stress_mean=("predicted_stress", "mean"),
        mae=("prediction_error", "mean"),
        count=("stress", "count"),
    ).reset_index()
    cond_summary["data_mode"] = tag
    cond_summary_csv = os.path.join(args.results, "exp5_condition_stress_summary.csv")
    cond_summary.to_csv(cond_summary_csv, index=False)
    print("  Condition-Level Summary:")
    print(cond_summary.to_string(index=False))

    print(f"\n[SUCCESS] Experiments 2–5 complete. All artifacts saved to: {args.results}")


if __name__ == "__main__":
    main()
