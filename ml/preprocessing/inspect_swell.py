
"""Inspect SWELL-KW files without changing the original data."""
from pathlib import Path
import sys
import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / "data" / "raw" / "SWELL-KW"

SUPPORTED = {".csv", ".tab", ".tsv", ".xlsx", ".xls"}


def inspect_file(path: Path) -> None:
    print(f"\n{'=' * 80}\nFILE: {path.name}")
    print(f"SIZE: {path.stat().st_size:,} bytes")

    try:
        if path.suffix.lower() in {".xlsx", ".xls"}:
            df = pd.read_excel(path, nrows=5)
            print("Format: Excel; previewing first 5 rows")
        else:
            # SWELL feature tables may be tab-separated.
            try:
                df = pd.read_csv(path, sep="\t", nrows=5, low_memory=False)
                if len(df.columns) == 1:
                    df = pd.read_csv(path, sep=",", nrows=5, low_memory=False)
            except Exception:
                df = pd.read_csv(path, sep=",", nrows=5, low_memory=False)

        print("Columns:")
        for column in df.columns:
            print(f"  - {column}")

        print("\nPreview:")
        print(df.head(3).to_string(index=False))
    except Exception as exc:
        print(f"Could not inspect file: {exc}")


def main() -> None:
    if not DATA_DIR.exists():
        print(f"Dataset folder not found: {DATA_DIR}")
        sys.exit(1)

    files = [
        path for path in DATA_DIR.rglob("*")
        if path.is_file() and path.suffix.lower() in SUPPORTED
    ]

    if not files:
        print(f"No supported data files found under: {DATA_DIR}")
        print("Wait until the dataset download and extraction are complete.")
        return

    print(f"Found {len(files)} supported data files.")
    for path in files:
        inspect_file(path)


if __name__ == "__main__":
    main()
