"""
Experiment 1 — algorithm comparison with participant-aware cross-validation, then fit the best model on
all data and persist it (with feature order and per-participant baseline profiles) for the API.

Usage:  python training/train.py --data data/demo_synthetic.csv --out models/ --results results/
"""
import argparse, json, os, sys, time
import numpy as np, pandas as pd, joblib, yaml
from sklearn.model_selection import GroupKFold, cross_val_predict
from sklearn.metrics import (accuracy_score, precision_score, recall_score, f1_score,
                             roc_auc_score, confusion_matrix)

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from preprocessing.preprocess import clean, one_hot_app, app_onehot_columns, build_matrix
from preprocessing.baseline import add_personal_deviation_features
from preprocessing.features import ABSOLUTE, DEVIATION, LABELS
from training.models import candidate_models
from evaluation.plots import plot_model_comparison, plot_confusion


def evaluate_cv(model, X, y, groups, folds):
    """Participant-aware CV: GroupKFold guarantees no participant appears in both train and test."""
    proba = cross_val_predict(model, X, y, groups=groups, cv=GroupKFold(n_splits=folds), method="predict_proba")
    pred = proba.argmax(axis=1)
    return {
        "accuracy": accuracy_score(y, pred),
        "precision_macro": precision_score(y, pred, average="macro", zero_division=0),
        "recall_macro": recall_score(y, pred, average="macro", zero_division=0),
        "f1_macro": f1_score(y, pred, average="macro", zero_division=0),
        "roc_auc_ovr": roc_auc_score(y, proba, multi_class="ovr") if len(np.unique(y)) > 2 else float("nan"),
    }, pred


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", default="models/")
    ap.add_argument("--results", default="results/")
    ap.add_argument("--config", default="config.yaml")
    ap.add_argument("--no-mlp", action="store_true")
    args = ap.parse_args()
    cfg = yaml.safe_load(open(args.config))
    os.makedirs(args.out, exist_ok=True); os.makedirs(args.results, exist_ok=True)

    df = pd.read_csv(args.data)
    is_demo = "is_synthetic" in df and bool(df["is_synthetic"].max())
    df = one_hot_app(clean(df))
    df, profiles = add_personal_deviation_features(df, cfg["baseline_sessions"])
    feature_cols = ABSOLUTE + app_onehot_columns() + DEVIATION
    X, y, groups = build_matrix(df, feature_cols)
    print(f"Data: {X.shape[0]} windows, {X.shape[1]} features, {len(np.unique(groups))} participants, "
          f"labels {np.bincount(y).tolist()}  {'[DEMO / SYNTHETIC]' if is_demo else '[REAL]'}")

    rows, best = [], None
    for name, model in candidate_models(cfg["random_seed"], include_mlp=not args.no_mlp).items():
        t = time.time()
        metrics, pred = evaluate_cv(model, X, y, groups, cfg["cv_folds"])
        metrics.update({"model": name, "train_time_s": round(time.time() - t, 1)})
        rows.append(metrics)
        print(f"{name:20s} acc={metrics['accuracy']:.3f} f1={metrics['f1_macro']:.3f} auc={metrics['roc_auc_ovr']:.3f}")
        if best is None or metrics["f1_macro"] > best[1]["f1_macro"]:
            best = (name, metrics, pred)
    res = pd.DataFrame(rows)[["model", "accuracy", "precision_macro", "recall_macro", "f1_macro",
                              "roc_auc_ovr", "train_time_s"]]
    res["data_mode"] = "SYNTHETIC_DEMO" if is_demo else "REAL"
    res.to_csv(os.path.join(args.results, "exp1_model_comparison.csv"), index=False)
    plot_model_comparison(res, os.path.join(args.results, "exp1_model_comparison.png"), is_demo)

    best_name, best_metrics, best_pred = best
    cm = confusion_matrix(y, best_pred)
    pd.DataFrame(cm, index=list(LABELS.values()), columns=list(LABELS.values())).to_csv(
        os.path.join(args.results, "exp1_confusion_matrix.csv"))
    plot_confusion(cm, os.path.join(args.results, "exp1_confusion_matrix.png"), best_name, is_demo)

    final = candidate_models(cfg["random_seed"], include_mlp=not args.no_mlp)[best_name]
    final.fit(X, y)
    joblib.dump(final, os.path.join(args.out, "stress_model.joblib"))
    with open(os.path.join(args.out, "model_meta.json"), "w") as f:
        json.dump({"model": best_name, "feature_columns": feature_cols, "labels": LABELS,
                   "cv_metrics": best_metrics, "data_mode": res["data_mode"][0],
                   "baseline_sessions": cfg["baseline_sessions"],
                   "stress_score_weights": cfg["stress_score_weights"]}, f, indent=2)
    with open(os.path.join(args.out, "baseline_profiles.json"), "w") as f:
        json.dump(profiles, f, indent=2)
    print(f"Best model: {best_name}. Saved to {args.out}")


if __name__ == "__main__":
    main()
