"""SWELL-KW Dataset Preprocessing and Feature Aggregation.

Reads the licensed SWELL-KW behavioral dataset directly from the local download path,
validates columns and questionnaire labels, investigates data nuances (e.g. ErrorKeyRatio
outliers and SnMouseDistance reliability), aggregates minute-level features by participant,
block, and condition, and persists the processed dataset for regression training and evaluation.

Usage:
  python preprocessing/preprocess_swell.py
  python preprocessing/preprocess_swell.py --input "path/to/Behavioral-features - per minute.xlsx"
"""

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path
import numpy as np
import pandas as pd

# Add parent directory to path so relative imports work
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from preprocessing.features import (
    SWELL_ALL,
    SWELL_KEYBOARD,
    SWELL_MOUSE,
    SWELL_APP,
    TARGET,
)

DEFAULT_DATASET_PATH = r"C:\Users\chirayu singla\Downloads\0_SWELL\Behavioral-features - per minute.xlsx"
DEFAULT_OUTPUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data")


def validate_file(path: Path) -> None:
    """Validate that the input dataset file exists and is accessible."""
    if not path.exists():
        raise FileNotFoundError(
            f"\n[ERROR] SWELL dataset file not found at: '{path}'\n"
            "Please check the path. The raw dataset should be located at:\n"
            r"C:\Users\chirayu singla\Downloads\0_SWELL\Behavioral-features - per minute.xlsx"
        )
    if not os.access(path, os.R_OK):
        raise PermissionError(f"[ERROR] Cannot read file at '{path}'. Check read permissions.")


def validate_schema(df: pd.DataFrame) -> None:
    """Validate that all required metadata, condition, target, and interaction columns are present."""
    required_id_cols = ["PP", "Blok", "Condition", TARGET]
    missing_ids = [col for col in required_id_cols if col not in df.columns]
    if missing_ids:
        raise ValueError(f"[ERROR] Missing required metadata/target columns in SWELLdata: {missing_ids}")

    missing_features = [col for col in SWELL_ALL if col not in df.columns]
    if missing_features:
        raise ValueError(f"[ERROR] Missing required computer interaction feature columns: {missing_features}")


def preprocess_swell(
    excel_path: str = DEFAULT_DATASET_PATH,
    out_dir: str = DEFAULT_OUTPUT_DIR,
    save_minutes: bool = False,
) -> pd.DataFrame:
    """Load, validate, investigate, aggregate, and save SWELL-KW behavioral data."""
    path = Path(excel_path)
    validate_file(path)

    print(f"[1/5] Loading SWELL dataset from: {path} (Sheet: 'SWELLdata')...")
    df = pd.read_excel(path, sheet_name="SWELLdata")
    print(f"      Loaded {len(df):,} raw rows and {len(df.columns)} columns.")

    # Validate columns
    validate_schema(df)
    if df[["PP", "Blok", "Condition"]].isna().any().any():
        raise ValueError("Missing observation identifiers")
    if not df["Condition"].isin(["N", "I", "T", "R"]).all():
        raise ValueError("Unexpected experimental condition")
    for c in SWELL_ALL + [TARGET]:
        df[c] = pd.to_numeric(df[c], errors="raise")
    if np.isinf(df[SWELL_ALL + [TARGET]].to_numpy()).any():
        raise ValueError("Infinite feature or target values")
    if (df[SWELL_ALL] < 0).any().any():
        raise ValueError("Negative interaction counts/ratios need investigation")

    # 1. Inspect conditions and handle Relaxation ('R')
    condition_counts = df["Condition"].value_counts(dropna=False).to_dict()
    print(f"[2/5] Raw condition distribution: {condition_counts}")

    # In SWELL-KW, 'R' represents relaxation where participants watched a video.
    # Computer interaction is 0 and no questionnaire stress ratings were recorded (Stress is NaN).
    # Active task conditions are N (Neutral), I (Interruption), and T (Time pressure).
    df_valid = df[df["Condition"].isin(["N", "I", "T"])].copy()
    print(f"      Filtered {len(df_valid):,} task rows across active conditions: 'N', 'I', 'T'.")
    print(f"      Excluded {condition_counts.get('R', 0):,} 'R' (relaxation) rows lacking questionnaire ground truth.")

    # Validate target variable
    if df_valid[TARGET].isna().any():
        n_missing = df_valid[TARGET].isna().sum()
        print(f"      [WARNING] Found {n_missing} missing Stress values in active task rows; dropping.")
        df_valid = df_valid.dropna(subset=[TARGET])

    if df_valid.empty:
        raise ValueError("No labeled active-task observations")
    stress_min, stress_max = df_valid[TARGET].min(), df_valid[TARGET].max()
    print(f"      Stress score range: [{stress_min:.1f}, {stress_max:.1f}] (mean: {df_valid[TARGET].mean():.2f})")
    if stress_min < 0.0 or stress_max > 10.0:
        raise ValueError(f"[ERROR] Stress score out of expected [0, 10] bounds: [{stress_min}, {stress_max}]")

    # 2. Investigate ErrorKeyRatio outliers and SnMouseDistance
    print("[3/5] Investigating feature nuances:")
    err_outliers = (df_valid["ErrorKeyRatio"] > 1.0).sum()
    print(f"      ErrorKeyRatio > 1.0 occurs in {err_outliers} minute rows (max: {df_valid['ErrorKeyRatio'].max():.1f}).")
    print(
        "      Investigation: In SWELL, ErrorKeyRatio = SnErrorKeys / (SnChars + SnSpaces). "
        "High single-minute ratios can occur during editing with few new characters. "
        "The supplied values are preserved, not clipped or deleted; this does not prove every sensor value is error-free."
    )

    zero_mouse_dist = (df_valid["SnMouseDistance"] == 0).sum()
    print(
        f"      SnMouseDistance is 0 in {zero_mouse_dist} minute rows. "
        "The workbook documentation marks this feature as 'unreliable?'. "
        "Both full and distance-excluded models will be evaluated."
    )

    # 3. Aggregate minute-level features by participant, block, and condition
    print("[4/5] Aggregating minute-level features by Participant (PP), Block (Blok), and Condition...")
    # Questionnaire Stress score is recorded once per condition and repeated across minutes.
    # Computing the mean over the condition block produces the condition-level mean interaction rates.
    agg_cols = SWELL_ALL + [TARGET]
    label_counts = df_valid.groupby(["PP", "Blok", "Condition"])[TARGET].nunique()
    if not label_counts.eq(1).all():
        raise ValueError("Conflicting questionnaire scores within an observation")

    # Check for missing values in interaction features prior to aggregation
    # Workbook ratios are zero even when their source counters were not recorded.
    df_valid.loc[df_valid[["SnChars", "SnKeyStrokes"]].isna().any(axis=1), "CharactersRatio"] = np.nan
    df_valid.loc[df_valid[["SnErrorKeys", "SnChars", "SnSpaces"]].isna().any(axis=1), "ErrorKeyRatio"] = np.nan
    missing_feats = df_valid[SWELL_ALL].isna().sum()
    if missing_feats.any():
        cols_with_na = missing_feats[missing_feats > 0].to_dict()
        print(f"      Handling missing feature values in minute rows: {cols_with_na}")
        print("      Missing rows occur in PP11/PP16 Condition I; group mean handles them automatically.")

    agg = df_valid.groupby(["PP", "Blok", "Condition"], as_index=False)[agg_cols].mean()

    # Standardize column naming
    rename_map = {
        "PP": "participant_id",
        "Blok": "block",
        "Condition": "condition",
        TARGET: "stress",
    }
    agg = agg.rename(columns=rename_map)

    # Verify group counts
    n_groups = len(agg)
    n_participants = agg["participant_id"].nunique()
    print(f"      Aggregated dataset has {n_groups} participant-condition blocks across {n_participants} participants.")
    print(f"      Conditions per block: {agg['condition'].value_counts().to_dict()}")

    if n_groups != 75:
        print(f"      [NOTE] Expected 75 groups (25 participants x 3 conditions), got {n_groups}.")

    # Check for any remaining NaN values
    # All-missing feature groups remain missing for fold-local imputation.

    # 4. Save processed dataset files
    print(f"[5/5] Saving processed SWELL dataset to '{out_dir}'...")
    processed_dir = Path(out_dir) / "processed"
    processed_dir.mkdir(parents=True, exist_ok=True)

    agg_file = processed_dir / "swell_aggregated.csv"
    agg.to_csv(agg_file, index=False)
    audit = {
        "source": path.name, "source_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "sheet": "SWELLdata", "raw_rows": len(df), "raw_columns": len(df.columns),
        "labeled_minutes": len(df_valid), "excluded_R": int((df.Condition == "R").sum()),
        "observations": n_groups, "participants": n_participants,
        "aggregation": ["PP", "Blok", "Condition"], "feature_aggregation": "mean of observed minutes",
        "minute_missing": {c: int(n) for c, n in missing_feats.items() if n},
        "error_ratio_above_one": int(err_outliers),
        "error_ratio_max": float(df_valid.ErrorKeyRatio.max()),
        "policy": "Preserve nonnegative outliers; no global imputation or clipping. Ratios on minutes with missing source counts are set missing before aggregation.",
        "data_mode": "REAL_SWELL",
        "processed_sha256": hashlib.sha256(agg_file.read_bytes()).hexdigest(),
    }
    (processed_dir / "preprocessing_audit.json").write_text(json.dumps(audit, indent=2), encoding="utf-8")
    print(f"      Saved aggregated dataset: {agg_file} ({len(agg)} rows, {len(agg.columns)} columns)")

    # Also save direct convenience copy at ml/data/swell_features.csv
    features_file = Path(out_dir) / "swell_features.csv"
    agg.to_csv(features_file, index=False)
    print(f"      Saved direct reference: {features_file}")

    if save_minutes:
        min_file = processed_dir / "swell_minutes_clean.csv"
        df_clean = df_valid[["PP", "Blok", "Condition", "timestamp"] + SWELL_ALL + [TARGET]].rename(columns=rename_map)
        df_clean.to_csv(min_file, index=False)
        print(f"      Saved minute-level dataset: {min_file} ({len(df_clean)} rows)")

    print("\n[SUCCESS] SWELL-KW preprocessing complete.")
    print("---------------------------------------------------------------")
    print(f"Total Participants : {n_participants}")
    print(f"Total Samples (CV) : {n_groups}")
    print(f"Stress Score Mean  : {agg['stress'].mean():.2f} (std: {agg['stress'].std():.2f})")
    print(f"Stress Score Range : [{agg['stress'].min():.1f}, {agg['stress'].max():.1f}]")
    print(f"Feature Count      : {len(SWELL_ALL)} multimodal features")
    print("---------------------------------------------------------------")

    return agg


def main():
    parser = argparse.ArgumentParser(description="Preprocess SWELL-KW dataset for Mental Stress Prediction")
    parser.add_argument("--input", default=DEFAULT_DATASET_PATH, help="Path to Behavioral-features - per minute.xlsx")
    parser.add_argument("--out", default=DEFAULT_OUTPUT_DIR, help="Output directory for processed CSV files")
    parser.add_argument("--save-minutes", action="store_true", help="Also save cleaned minute-level CSV")
    args = parser.parse_args()

    preprocess_swell(args.input, args.out, args.save_minutes)


if __name__ == "__main__":
    main()
