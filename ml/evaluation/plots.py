import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd

WATERMARK = "DEMO MODE — SYNTHETIC DATA, NOT EXPERIMENTAL RESULTS"


def _stamp(fig, is_demo):
    if is_demo:
        fig.text(0.5, 0.01, WATERMARK, ha="center", fontsize=9, color="red")


def plot_model_comparison(res: pd.DataFrame, path, is_demo):
    fig, ax = plt.subplots(figsize=(9, 5))
    res.set_index("model")[["accuracy", "precision_macro", "recall_macro", "f1_macro"]].plot(kind="bar", ax=ax, ylim=(0, 1))
    ax.set_title("Experiment 1 — Model comparison (participant-aware CV)"); ax.set_ylabel("Score")
    ax.legend(loc="lower right"); plt.xticks(rotation=20)
    _stamp(fig, is_demo); fig.tight_layout(); fig.savefig(path, dpi=150); plt.close(fig)


def plot_confusion(cm, path, model_name, is_demo):
    labels = ["Low", "Moderate", "High"]
    fig, ax = plt.subplots(figsize=(5, 4.5))
    im = ax.imshow(cm, cmap="Blues")
    ax.set_xticks(range(3)); ax.set_yticks(range(3)); ax.set_xticklabels(labels); ax.set_yticklabels(labels)
    ax.set_xlabel("Predicted"); ax.set_ylabel("Actual"); ax.set_title(f"Confusion matrix — {model_name}")
    for i in range(3):
        for j in range(3):
            ax.text(j, i, cm[i, j], ha="center", va="center", color="white" if cm[i, j] > cm.max() / 2 else "black")
    fig.colorbar(im); _stamp(fig, is_demo); fig.tight_layout(); fig.savefig(path, dpi=150); plt.close(fig)


def plot_group_bars(res: pd.DataFrame, path, title, is_demo, cols=("accuracy", "f1_macro"), label_col="group"):
    fig, ax = plt.subplots(figsize=(9, 5))
    res.set_index(label_col)[list(cols)].plot(kind="bar", ax=ax, ylim=(0, 1))
    ax.set_title(title); ax.set_ylabel("Score"); plt.xticks(rotation=15)
    _stamp(fig, is_demo); fig.tight_layout(); fig.savefig(path, dpi=150); plt.close(fig)


def plot_importance(imp: pd.Series, path, title, is_demo, top=15):
    fig, ax = plt.subplots(figsize=(8, 6))
    imp.sort_values().tail(top).plot(kind="barh", ax=ax)
    ax.set_title(title); _stamp(fig, is_demo); fig.tight_layout(); fig.savefig(path, dpi=150); plt.close(fig)


def plot_trend(trend: pd.DataFrame, path, is_demo):
    fig, ax = plt.subplots(figsize=(10, 4))
    for pid, g in trend.groupby("participant_id"):
        ax.plot(range(len(g)), g["stress_score"], marker="o", label=pid)
    ax.set_xlabel("Session"); ax.set_ylabel("Stress score (0–100)"); ax.set_ylim(0, 100)
    ax.set_title("Experiment 5 — Stress trend across sessions"); ax.legend(ncol=4, fontsize=7)
    _stamp(fig, is_demo); fig.tight_layout(); fig.savefig(path, dpi=150); plt.close(fig)
