"""
Experiments 2–5 (run after training/train.py).
  2. Feature-group ablation (A–F)
  3. Personalised (absolute + deviation) vs generic (absolute only), every model
  4. Feature importance (impurity + permutation) and SHAP summary
  5. Stress trend over sessions (from cross-validated probabilities)
Usage: python evaluation/run_experiments.py --data data/demo_synthetic.csv --results results/
"""
import argparse, os, sys
import numpy as np, pandas as pd, yaml
from sklearn.model_selection import GroupKFold, cross_val_predict
from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score
from sklearn.inspection import permutation_importance

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from preprocessing.preprocess import clean, one_hot_app, app_onehot_columns, build_matrix
from preprocessing.baseline import add_personal_deviation_features
from preprocessing.features import ABSOLUTE, DEVIATION, FEATURE_GROUPS
from training.models import candidate_models
from evaluation.plots import plot_group_bars, plot_importance, plot_trend


def cv_metrics(model, X, y, groups, folds):
    proba = cross_val_predict(model, X, y, groups=groups, cv=GroupKFold(folds), method="predict_proba")
    pred = proba.argmax(1)
    return {"accuracy": accuracy_score(y, pred),
            "precision_macro": precision_score(y, pred, average="macro", zero_division=0),
            "recall_macro": recall_score(y, pred, average="macro", zero_division=0),
            "f1_macro": f1_score(y, pred, average="macro", zero_division=0)}, proba


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True); ap.add_argument("--results", default="results/")
    ap.add_argument("--config", default="config.yaml"); ap.add_argument("--model", default="RandomForest")
    args = ap.parse_args()
    cfg = yaml.safe_load(open(args.config)); os.makedirs(args.results, exist_ok=True)
    seed, folds = cfg["random_seed"], cfg["cv_folds"]

    df = pd.read_csv(args.data)
    is_demo = "is_synthetic" in df and bool(df["is_synthetic"].max())
    df = one_hot_app(clean(df)); df, _ = add_personal_deviation_features(df, cfg["baseline_sessions"])
    app_cols = app_onehot_columns(); tag = "SYNTHETIC_DEMO" if is_demo else "REAL"

    # ---- Experiment 2: feature groups ----
    rows = []
    for gname, cols in FEATURE_GROUPS.items():
        cols = cols + (app_cols if gname in ("C_app", "E_keyboard_mouse_app", "F_all_plus_baseline") else [])
        X, y, groups = build_matrix(df, cols)
        m, _ = cv_metrics(candidate_models(seed, False)[args.model], X, y, groups, folds)
        m.update({"group": gname, "n_features": len(cols), "data_mode": tag}); rows.append(m)
        print(f"[Exp2] {gname:24s} acc={m['accuracy']:.3f} f1={m['f1_macro']:.3f}")
    r2 = pd.DataFrame(rows); r2.to_csv(os.path.join(args.results, "exp2_feature_groups.csv"), index=False)
    plot_group_bars(r2, os.path.join(args.results, "exp2_feature_groups.png"),
                    f"Experiment 2 — Feature-group ablation ({args.model})", is_demo)

    # ---- Experiment 3: personalised vs generic ----
    rows = []
    generic_cols, personal_cols = ABSOLUTE + app_cols, ABSOLUTE + app_cols + DEVIATION
    for name, model in candidate_models(seed, True).items():
        for label, cols in (("generic", generic_cols), ("personalised", personal_cols)):
            X, y, groups = build_matrix(df, cols)
            m, _ = cv_metrics(model, X, y, groups, folds)
            m.update({"model": name, "variant": label, "data_mode": tag}); rows.append(m)
            print(f"[Exp3] {name:18s} {label:12s} acc={m['accuracy']:.3f} f1={m['f1_macro']:.3f}")
    r3 = pd.DataFrame(rows); r3.to_csv(os.path.join(args.results, "exp3_personalised_vs_generic.csv"), index=False)
    piv = r3.pivot(index="model", columns="variant", values="f1_macro").reset_index().rename(columns={"model": "group"})
    piv.columns.name = None
    plot_group_bars(piv, os.path.join(args.results, "exp3_personalised_vs_generic.png"),
                    "Experiment 3 — Macro-F1: generic vs personalised", is_demo, cols=("generic", "personalised"))

    # ---- Experiment 4: importance + SHAP ----
    X, y, groups = build_matrix(df, personal_cols)
    rf = candidate_models(seed, False)["RandomForest"].fit(X, y)
    imp = pd.Series(rf.named_steps["clf"].feature_importances_, index=personal_cols)
    imp.sort_values(ascending=False).to_csv(os.path.join(args.results, "exp4_feature_importance_rf.csv"))
    plot_importance(imp, os.path.join(args.results, "exp4_feature_importance_rf.png"), "Experiment 4 — RF impurity importance", is_demo)
    perm = permutation_importance(rf, X, y, n_repeats=5, random_state=seed, n_jobs=-1)
    pimp = pd.Series(perm.importances_mean, index=personal_cols)
    pimp.sort_values(ascending=False).to_csv(os.path.join(args.results, "exp4_permutation_importance.csv"))
    plot_importance(pimp, os.path.join(args.results, "exp4_permutation_importance.png"), "Experiment 4 — Permutation importance", is_demo)
    try:
        import shap, matplotlib.pyplot as plt
        Xs = rf.named_steps["scaler"].transform(X[: min(500, len(X))])
        sv = shap.TreeExplainer(rf.named_steps["clf"]).shap_values(Xs)
        high = sv[2] if isinstance(sv, list) else sv[:, :, 2]
        shap.summary_plot(high, Xs, feature_names=personal_cols, show=False, max_display=15)
        plt.title("Experiment 4 — SHAP summary (High Stress)" + ("  [DEMO]" if is_demo else ""))
        plt.tight_layout(); plt.savefig(os.path.join(args.results, "exp4_shap_summary_high.png"), dpi=150); plt.close()
        pd.Series(np.abs(high).mean(0), index=personal_cols).sort_values(ascending=False).to_csv(
            os.path.join(args.results, "exp4_shap_mean_abs_high.csv"))
    except Exception as e:
        print("[Exp4] SHAP skipped:", e)

    # ---- Experiment 5: stress trend ----
    _, proba = cv_metrics(candidate_models(seed, False)[args.model], X, y, groups, folds)
    df["stress_score"] = 100 * (proba @ np.array(cfg["stress_score_weights"]))
    trend = df.groupby(["participant_id", "session_id"], sort=True)["stress_score"].mean().reset_index()
    trend["data_mode"] = tag; trend.to_csv(os.path.join(args.results, "exp5_stress_trend.csv"), index=False)
    plot_trend(trend[trend["participant_id"].isin(trend["participant_id"].unique()[:6])],
               os.path.join(args.results, "exp5_stress_trend.png"), is_demo)
    print("Experiments complete ->", args.results, "| data mode:", tag)


if __name__ == "__main__":
    main()
