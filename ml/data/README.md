# Data provenance

The active experiment reads the locally licensed SWELL-KW workbook, sheet SWELLdata. It contains 3,139 minute rows, 172 columns and 25 participants. Preprocessing excludes 451 unlabeled relaxation rows and aggregates 2,688 labeled task minutes into 75 participant/block/condition observations. The target is the continuous Stress questionnaire score, renamed stress.

Only computer-interaction features enter training. Missing counters and ratios are handled as documented in the root README. Raw/processed data and the preprocessing audit JSON are ignored. Raw SWELL stays in the Downloads location; it is not copied into Git.

Run preprocessing/preprocess_swell.py with the existing virtual environment before training. Synthetic generators are demo/test-only and are rejected by the real training pipeline. Desktop LIVE_DESKTOP exports with ordinal-3 reports are not SWELL questionnaire observations. Cite Koldijk et al. (ICMI 2014) and comply with the noncommercial research/download agreement. See [README](../../README.md).
