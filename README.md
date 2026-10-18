# Mental Stress Prediction Using Typing, Mouse and Application Interaction

Java 17/JavaFX desktop collector, local H2 storage, and a Python/FastAPI regression service using the real SWELL-KW workbook. The output estimates a 0–10 questionnaire score. It does not clinically diagnose stress.

**Current evidence:** all evaluated learned models performed worse than the training-fold mean-score baseline on participant-grouped cross-validation. This is an exploratory research application, not a validated stress detector. Desktop sessions differ from SWELL condition blocks; transport/integration success is not evidence of predictive validity.

## Reproduce on this Windows computer

Project root:

    C:\Users\chirayu singla\OneDrive\Desktop\MentalStressPrediction\mental-stress-prediction\mental-stress-prediction

Use the existing ml/.venv. Requirements include openpyxl for Excel and httpx for HTTP contract tests.

    cd "C:\Users\chirayu singla\OneDrive\Desktop\MentalStressPrediction\mental-stress-prediction\mental-stress-prediction"
    .\ml\.venv\Scripts\python.exe -m pip install -r .\ml\requirements.txt
    cd ml
    .\.venv\Scripts\python.exe preprocessing/preprocess_swell.py --input "C:\Users\chirayu singla\Downloads\0_SWELL\Behavioral-features - per minute.xlsx" --save-minutes
    .\.venv\Scripts\python.exe training/train.py
    .\.venv\Scripts\python.exe evaluation/run_experiments.py
    .\.venv\Scripts\python.exe -m pytest tests -q
    .\.venv\Scripts\uvicorn.exe api.server:app --host 127.0.0.1 --port 8000

Preprocess --out is a DIRECTORY, not a CSV filename. Default output is ml/data/processed/swell_aggregated.csv. --save-minutes writes only identifiers, interaction features and target to swell_minutes_clean.csv. No physiological, facial or Kinect features enter supervised training.

Training writes ml/models/stress_model.joblib and model_meta.json, the exact paths loaded by the API. A preprocessing audit links the processed CSV to the raw workbook using SHA-256. Training refuses a provenance mismatch, duplicate participant/block/condition observations, synthetic markers, relaxation conditions, missing targets or invalid values. Explicit invalid data paths fail instead of selecting another dataset.

In a second PowerShell terminal:

    $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
    $env:Path = "$env:JAVA_HOME\bin;C:\Users\chirayu singla\Downloads\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin;$env:Path"
    cd "C:\Users\chirayu singla\OneDrive\Desktop\MentalStressPrediction\mental-stress-prediction\mental-stress-prediction\java-app"
    mvn test
    mvn clean javafx:run

Accept consent yourself to start real collection. The first prediction follows a completed 60-second window. Closing the window normally hides it in the tray when supported; Settings → Quit & Exit Application stops monitoring and closes the database.

Real monitoring is the default. APP_MODE=demo explicitly enables synthetic UI demonstration windows with a visible banner. Synthetic data is never used to train or evaluate the real model or as an offline prediction fallback.

## Windows desktop socket workaround

In this desktop execution environment, Java's default socket directory caused “Unable to establish loopback connection” / “Invalid argument: connect”. The installed JDK source and [Oracle networking documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/doc-files/net-properties.html) describe the temporary directory used for Unix-domain sockets.

The verified workaround uses a short, writable directory:

    $socketDir = "C:\Users\chirayu singla\OneDrive\Desktop\MentalStressPrediction\.sockets"
    New-Item -ItemType Directory -Force $socketDir | Out-Null
    $env:JAVA_TOOL_OPTIONS = '"-Djdk.net.unixdomain.tmpdir=' + $socketDir + '"'
    mvn clean javafx:run

This sets a process environment option; it does not change Windows security settings. Sandboxed processes also denied loopback networking; run the local integration tests/server with the execution permission requested by Codex, or from a normal PowerShell terminal.

## Data and evaluation

Verified workbook sheet SWELLdata: 3,139 rows, 172 columns, 25 participants. N=1,028, I=996, T=664, R=451 minutes. R has no Stress labels and is excluded. The 2,688 active minutes aggregate to 75 observations grouped by PP, Blok and Condition, with 25 observations per active condition.

Stress is constant within each labeled block. Minute records are not independent labeled samples. Participant ID, block, condition, questionnaire covariates and demographic information are not model features.

All candidate models use a Pipeline containing median imputation, StandardScaler and a regressor. Learned preprocessing fits only in each training fold. Evaluation uses five-fold GroupKFold on participant ID: 60 observations/20 participants train and 15 observations/5 participants test in each fold. Reported MAE, RMSE and R² are pooled out-of-fold metrics.

The same CV is used to compare/select models and feature configurations. These are exploratory selection metrics, not an unbiased nested-CV estimate or an external validation result.

| Configuration / model | Features | MAE | RMSE | R² |
|---|---:|---:|---:|---:|
| Training-fold mean baseline | any | 1.8772 | 2.2850 | -0.0539 |
| Full interaction / Random Forest, with distance | 18 | 2.0944 | 2.5210 | -0.2827 |
| Full interaction / Random Forest, without distance | 17 | 2.1038 | 2.5420 | -0.3043 |
| Desktop / Random Forest, with distance (deployed) | 16 | 2.0866 | 2.5148 | -0.2765 |
| Desktop / Random Forest, without distance | 15 | 2.1123 | 2.5614 | -0.3242 |

The full table compares Ridge, DecisionTree, RandomForest, SVR, XGBoost, MLP and DummyMean in four configurations. The deployed model is the learned desktop-compatible candidate with lowest observed CV MAE; DummyMean is a comparator and is never a production fallback. Negative R² and the stronger baseline prevent claims of demonstrated useful generalization.

Outputs under ml/results include exp1_snmousedistance_comparison.csv, selected_oof_predictions.csv, cv_folds.json, modality ablations, held-out-participant permutation importance and descriptive full-data RF/SHAP plots. The descriptive full-data SHAP/impurity plots are not validation metrics.

### Missingness and suspicious values

31 active minute records have missing interaction counters: 21 for PP11/block 3/I and 10 for PP16/block 2/I. Workbook ratio columns contain zeros even when their source counts are missing; preprocessing marks those ratios missing too. Group means use observed minutes. Any entirely missing aggregated feature remains missing for fold-local median imputation; there is no global imputation.

61 active minutes have ErrorKeyRatio above 1 (maximum 61). This is consistent with corrections divided by a small number of characters plus spaces. Counts reproduce the workbook ratios within its six-decimal precision. Ratios above 1 are retained without clipping; this does not prove the absence of tracker artifacts. A zero denominator maps to 0, matching the supplied workbook. Physiological and other sensor anomalies do not affect this computer-interaction experiment.

The workbook labels SnMouseDistance “unreliable?”. Both feature sets are measured and reported. This run slightly favors keeping it for Random Forest; exclusion does not universally improve performance. Resolution, event semantics and polling differences remain deployment limitations.

## Collector → model → display contract

The desktop sends these 16 numeric measurements in a named JSON feature map:

- Keyboard: SnKeyStrokes, SnChars, SnSpecialKeys, SnDirectionKeys, SnErrorKeys, SnShortcutKeys, SnSpaces, CharactersRatio, ErrorKeyRatio.
- Mouse: SnLeftClicked, SnRightClicked, SnDoubleClicked, SnWheel, SnDragged, SnMouseDistance.
- Application: SnAppChange.

SnMouseAct and SnTabfocusChange remain in offline research comparisons but are excluded from deployment. Native mouse-move event frequency is not a reliable equivalent of uLog mouse actions. Foreground process polling does not measure application-tab focus, so those values are not fabricated.

Letter recognition uses explicit native key codes (the codes are not alphabetically contiguous); digits are non-letter keys. Special keys overlap with the specific non-letter categories, matching the workbook definition. Error keys include backspace, delete and Ctrl+Z. Shortcut counts follow the documented Ctrl+C/X/V/Z/S/A and Shift+Tab definitions. Drags count operations instead of every drag-motion callback. Counts and distance are normalized to per-minute rates.

Completed windows are averaged within the current desktop session before prediction, matching SWELL's mean-within-observation aggregation form. Baseline deviations, participant identity, application categories and history do not enter the trained regressor. A desktop session is still not a controlled SWELL condition and its performance remains unmeasured.

Raw measured windows and exact session-mean model inputs are saved in additive swell_feature_windows and swell_prediction_inputs tables. Old rows without measurements are not backfilled with invented features.

GET /health reports readiness, schema version, ordered feature columns, model and data mode. POST /predict requires every trained feature; omitted, negative or nonfinite measurements fail with HTTP 422. The model/metadata checksum and feature count are validated at startup. An absent or inconsistent real model gives HTTP 503 for predictions.

For a real HTTP smoke test, run from ml while the backend is running:

    .\.venv\Scripts\python.exe -c 'import pandas as pd,requests; h=requests.get("http://127.0.0.1:8000/health",timeout=5).json(); d=pd.read_csv("data/processed/swell_aggregated.csv"); print(requests.post("http://127.0.0.1:8000/predict",json={"features":d.iloc[0][h["feature_columns"]].to_dict()},timeout=10).json())'

The response is a continuous score. stress_class=-1 and probabilities={} explicitly mean regression, without invented class thresholds. Raw prediction is also returned; the displayed score is bounded to the questionnaire range. Model sensitivities replace one feature with its training median; they are not SHAP values, causal effects or calibrated probabilities. The optional Java alert threshold is a user preference, not a validated category.

The local activity/history heuristics are unvalidated and labeled accordingly. The production API does not transmit scores to Gemini. Personal baseline displays are descriptive only. Low/Moderate/High self-report buttons store an ordinal-3 label, not SWELL's continuous questionnaire target; desktop exports are labeled LIVE_DESKTOP and cannot be silently retrained as SWELL.

HTTP calls run on the window-cycle worker, not the JavaFX thread. Timeouts, invalid responses and connection failures display an unavailable state and useful error text; no numeric replacement prediction is saved.

## Tests and verification

    cd ml
    .\.venv\Scripts\python.exe -m compileall -q api preprocessing training evaluation synthetic tests
    .\.venv\Scripts\python.exe -m pip check
    .\.venv\Scripts\python.exe -m pytest tests -q

Python: 15 tests passed on the audit run. Tests cover participant separation, duplicate minute/synthetic rejection, fold-local imputation, model checksum mismatch, actual HTTP serialization including NaN errors, missing model readiness and regression contracts.

Java unit tests always use an in-memory H2 database. With the backend running and the real workbook preprocessed, run the optional live tests:

    cd java-app
    mvn test "-Dstressml.integration=true" "-Djavafx.ui.tests=true"

These flags enable the real Java HTTP request and the displayed consent lifecycle test. The latter requires a desktop session. The socket workaround above may also be needed. The audit's final full Java suite passed 18 tests, including collector categories, session means and database round trips.

The actual JavaFX application was launched with mvn clean javafx:run. Consent, real hooks, backend predictions, database persistence, continuous-score display and the explanation view were verified. See [AUDIT_REPORT.md](AUDIT_REPORT.md) for command results, limitations and preservation evidence.

## Database and Git safety

Do not delete java-app/data/stressdb.mv.db or recreate it to troubleshoot. H2 uses the directory relative to java-app; launch there consistently. Additive migrations create only new tables. Legacy predictions remain stored; current 0–10 reports select schema-3 prediction records so old synthetic/0–100 scores do not contaminate history or model context.

A byte-identical pre-audit backup is retained locally under .audit/backups/stressdb.before.mv.db. All original rows in seven tables were compared by deterministic hashes and preserved. Real verification sessions append records.

Raw and processed datasets, model binaries/metadata, results, databases, audit logs/backups, local overrides and credentials are ignored. The initial commit tracked two H2 files. This audit removed only their Git index entries using git rm --cached; both local files remain intact. Those staged deletions concern tracking only. No commit or publication was made. Historical database objects remain in the initial commit; removing them requires a separate history-rewrite decision.

## Attribution and use

Use SWELL only under the dataset agreement supplied with your download and this project's noncommercial research scope. Keep raw/derived participant data and trained artifacts local. The source-code license grants no rights to redistribute the dataset.

Cite Koldijk, Sappelli, Verberne, Neerincx and Kraaij (2014), *The SWELL Knowledge Work Dataset for Stress and User Modeling Research*, ACM ICMI. [Author's paper](https://cs.ru.nl/~skoldijk/Papers/ICMI%202014%20paper_final_cr.pdf), [DOI](https://doi.org/10.1145/2663204.2663257). The local workbook's “Features and interest” and “Notes on the data” sheets define the counters and repeated questionnaire scores.

Other historical proposal/paper/viva notes may describe the former synthetic classification design. They are not evidence for the active real-data regression pipeline; use this README, actual result files and the audit report.
