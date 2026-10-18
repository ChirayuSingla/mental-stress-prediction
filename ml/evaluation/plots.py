"""Visualization utilities for regression model comparison, evaluation, and explainability."""

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

WATERMARK = "DEMO MODE — SYNTHETIC DATA, NOT EXPERIMENTAL RESULTS"
REAL_DATA_NOTE = "SWELL-KW Multimodal Dataset (Real Behavioral Telemetry)"


def _stamp(fig, is_demo: bool):
    if is_demo:
        fig.text(0.5, 0.01, WATERMARK, ha="center", fontsize=9, color="red", fontweight="bold")
    else:
        fig.text(0.5, 0.01, REAL_DATA_NOTE, ha="center", fontsize=8, color="#555555", style="italic")


def plot_regression_comparison(res: pd.DataFrame, path: str, is_demo: bool = False):
    """Plot MAE and RMSE error metrics across candidate regression models."""
    fig, ax = plt.subplots(figsize=(9, 5))
    df_plot = res.set_index("model")[["mae", "rmse"]]
    df_plot.plot(kind="bar", ax=ax, colormap="tab10", edgecolor="black", linewidth=0.5)
    ax.set_title("Experiment 1 — Model Comparison: Regression Error (Participant-Grouped CV)")
    ax.set_ylabel("Error (0–10 Questionnaire Stress Scale)")
    ax.set_xlabel("Candidate Regressor")
    ax.legend(["MAE (Mean Absolute Error)", "RMSE (Root Mean Squared Error)"], loc="upper right")
    plt.xticks(rotation=20)
    plt.grid(axis="y", linestyle="--", alpha=0.5)
    _stamp(fig, is_demo)
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)


def plot_mousedistance_comparison(res: pd.DataFrame, path: str, is_demo: bool = False):
    """Side-by-side comparison of error metrics with and without SnMouseDistance."""
    fig, ax = plt.subplots(figsize=(10, 5))
    pivot = res.pivot(index="model", columns="feature_set", values="mae")
    pivot.plot(kind="bar", ax=ax, edgecolor="black", linewidth=0.5)
    ax.set_title("SnMouseDistance Reliability Comparison — Mean Absolute Error (MAE)")
    ax.set_ylabel("MAE (lower is better)")
    ax.set_xlabel("Model")
    ax.legend(title="Feature Configuration", loc="upper right")
    plt.xticks(rotation=20)
    plt.grid(axis="y", linestyle="--", alpha=0.5)
    _stamp(fig, is_demo)
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)


def plot_actual_vs_predicted(actual, predicted, path: str, model_name: str, is_demo: bool = False):
    """Scatter plot of actual vs predicted continuous questionnaire stress score."""
    fig, ax = plt.subplots(figsize=(6, 6))
    ax.scatter(actual, predicted, alpha=0.7, edgecolors="k", c="#2b5c8f", s=45)
    ref = np.linspace(0, 10, 100)
    ax.plot(ref, ref, "r--", linewidth=1.5, label="Perfect agreement (y = x)")
    ax.set_xlabel("Actual Stress Score (0–10 Questionnaire)")
    ax.set_ylabel("Predicted Stress Score (0–10 Regressor)")
    ax.set_title(f"Actual vs. Predicted Stress — {model_name} (GroupKFold CV)")
    ax.set_xlim(-0.5, 10.5)
    ax.set_ylim(-0.5, 10.5)
    ax.legend(loc="upper left")
    plt.grid(True, linestyle="--", alpha=0.4)
    _stamp(fig, is_demo)
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)


def plot_importance(imp: pd.Series, path: str, title: str, is_demo: bool = False, top: int = 15):
    """Horizontal bar chart for feature importances."""
    fig, ax = plt.subplots(figsize=(9, 6))
    imp.sort_values().tail(top).plot(kind="barh", ax=ax, color="#3470a3", edgecolor="black", linewidth=0.5)
    ax.set_title(title)
    ax.set_xlabel("Relative Importance")
    plt.grid(axis="x", linestyle="--", alpha=0.4)
    _stamp(fig, is_demo)
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)


def plot_group_bars(res: pd.DataFrame, path: str, title: str, is_demo: bool = False, cols=("mae", "rmse"), label_col="group"):
    """Generic bar plot for ablation groups."""
    fig, ax = plt.subplots(figsize=(10, 5))
    res.set_index(label_col)[list(cols)].plot(kind="bar", ax=ax, edgecolor="black", linewidth=0.5)
    ax.set_title(title)
    ax.set_ylabel("Error Metric")
    plt.xticks(rotation=20)
    plt.grid(axis="y", linestyle="--", alpha=0.5)
    _stamp(fig, is_demo)
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)


def plot_condition_distribution(df: pd.DataFrame, path: str, is_demo: bool = False):
    """Boxplot of stress scores across working conditions (Neutral, Interruption, Time Pressure)."""
    fig, ax = plt.subplots(figsize=(7, 5))
    conds = [c for c in ["N", "I", "T"] if c in df["condition"].unique()]
    data = [df[df["condition"] == c]["stress"].values for c in conds]
    labels = [{"N": "Neutral (N)", "I": "Interruption (I)", "T": "Time Pressure (T)"}.get(c, c) for c in conds]
    ax.boxplot(data, tick_labels=labels, patch_artist=True,
               boxprops=dict(facecolor="#c7ddf5", color="#1c4870"),
               medianprops=dict(color="#b02a2a", linewidth=2))
    ax.set_title("SWELL-KW Ground Truth Stress by Experimental Condition")
    ax.set_ylabel("Questionnaire Stress Score (0–10)")
    ax.set_ylim(-0.5, 10.5)
    plt.grid(axis="y", linestyle="--", alpha=0.5)
    _stamp(fig, is_demo)
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    plt.close(fig)


# Legacy alias
def plot_model_comparison(res: pd.DataFrame, path: str, is_demo: bool = False):
    if "mae" in res.columns:
        plot_regression_comparison(res, path, is_demo)
    else:
        fig, ax = plt.subplots(figsize=(9, 5))
        cols = [c for c in ["accuracy", "precision_macro", "recall_macro", "f1_macro"] if c in res.columns]
        res.set_index("model")[cols].plot(kind="bar", ax=ax, ylim=(0, 1))
        ax.set_title("Experiment 1 — Model comparison (participant-aware CV)")
        ax.set_ylabel("Score")
        _stamp(fig, is_demo)
        fig.tight_layout()
        fig.savefig(path, dpi=150)
        plt.close(fig)
