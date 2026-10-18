# Mental Stress Prediction — SWELL research application

Java 17/JavaFX collector + local H2 database + Python/FastAPI. The target is SWELL's continuous 0–10 questionnaire score. It is not a clinical diagnosis or a probability of being stressed.

**Current result:** nested participant-grouped validation does not establish useful individualized prediction. Conservative model selection chooses a trained population mean reference. The API and dashboard explicitly identify this as a reference independent of current behavior; it has no behavioral feature contributions and does not trigger individual score alerts. No fixed score is hardcoded: the reference is fitted from the real aggregated SWELL training targets.

See [VALIDATION_REPORT.md](VALIDATION_REPORT.md) for this pass's actual results and remaining limitations. [AUDIT_REPORT.md](AUDIT_REPORT.md) records the earlier repair pass and its superseded deployment metrics.

## Setup and launch

Verified platform: Windows x64, Python 3.10.11, Java 17, Maven 3.9, JavaFX 21. The existing ml/.venv is preserved. requirements-lock.txt pins the 59 installed distributions used by the tested fresh environment. requirements.txt states compatible minimum dependencies; use the lock file to reproduce this run.

From the repository root:

    cd "C:\Users\chirayu singla\OneDrive\Desktop\MentalStressPrediction\mental-stress-prediction\mental-stress-prediction"
    # Only if ml/.venv does not exist: python -m venv ml/.venv
    .\ml\.venv\Scripts\python.exe -m pip install -r ml/requirements-lock.txt
    .\ml\.venv\Scripts\python.exe ml/preprocessing/preprocess_swell.py --input "C:\Users\chirayu singla\Downloads\0_SWELL\Behavioral-features - per minute.xlsx" --save-minutes
    .\ml\.venv\Scripts\python.exe ml/training/train.py
    .\ml\.venv\Scripts\python.exe ml/evaluation/run_experiments.py
    .\ml\.venv\Scripts\python.exe -m pytest ml/tests -q
    .\scripts\run-backend.ps1

The backend binds only to 127.0.0.1:8000. In a second terminal, from the same root:

    $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
    $env:Path = "$env:JAVA_HOME\bin;C:\Users\chirayu singla\Downloads\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin;$env:Path"
    .\scripts\run-java.ps1

Accept the dialog yourself to start real collection. The first completed real interval takes 60 seconds. Empty intervals save the measurements but show no prediction. Pause cancels/discards a partial interval; resume waits for a complete new interval.

The scripts select the correct working directories. run-java.ps1 sets a short workspace socket path for the installed JDK's Windows/MSIX socket failure and restores its process environment on exit. It accepts -SocketDirectory for an explicit short writable path. It does not change Windows security settings.

Equivalent direct commands, from the component directories:

    # ml/
    .\.venv\Scripts\uvicorn.exe api.server:app --host 127.0.0.1 --port 8000
    # java-app/
    mvn clean javafx:run

When running directly in Codex's Windows desktop environment, set the JVM workaround first:

    $socketDir = "C:\Users\chirayu singla\OneDrive\Desktop\MentalStressPrediction\.sockets"
    New-Item -ItemType Directory -Force $socketDir | Out-Null
    $env:JAVA_TOOL_OPTIONS = '"-Djdk.net.unixdomain.tmpdir=' + $socketDir + '"'

## Build and test

From java-app with backend running and SWELL already preprocessed:

    mvn package "-Dstressml.integration=true" "-Djavafx.ui.tests=true"

Set the JVM socket option above when needed. Tests use an in-memory H2 database, including deletion tests; they never reset the personal database. The flags enable a real Java HTTP prediction and JavaFX dialog/lifecycle tests. Without them those optional integration/UI tests are explicitly skipped.

From the repository root after packaging:

    .\scripts\run-java.ps1 -Packaged

The shaded JAR merges JDBC service providers so H2 and MySQL are both discoverable. Packaging no longer rewrites the tracked dependency-reduced-pom.xml.

Explicit demonstrations only:

    .\scripts\run-java.ps1 -Demo
    .\scripts\run-java.ps1 -Demo -Packaged

These show a synthetic-data banner, do not install real collection hooks and mark generated sessions/predictions as demo. They are never used in SWELL evaluation or real participant history.

The audit verified fresh installation in a separate virtual environment/source copy, real Excel preprocessing, training and API tests. Nested metrics, feature order and all 75 refit predictions reproduced exactly. A licensed workbook is still an external prerequisite, intentionally absent from Git.

## Real SWELL preprocessing

The workbook's SWELLdata sheet contains 3,139 rows, 172 columns and 25 participants. N=1,028, I=996, T=664, R=451 minute records. R has no Stress labels and is excluded from supervised fitting. The 2,688 labeled task minutes aggregate by PP/Blok/Condition to 75 observations: 25 each for N/I/T.

Questionnaire scores repeat within each block; minute rows are not independent labeled samples. A block must have one consistent target. The target is renamed stress. Identity, block, experimental condition, demographics, physiological signals, facial features, Kinect and other questionnaire scores are not predictors.

31 task minutes have missing interaction counters. Derived ratios on those minutes are made missing rather than treating cached zeros as measurements. Means use observed minutes within each block; any all-missing aggregated feature is imputed inside each training fold. 61 ErrorKeyRatio values exceed 1, maximum 61. Small denominators explain how these can occur; supplied outliers are retained without arbitrary clipping. Zero-denominator ratios follow the workbook's zero convention.

Preprocessing saves data/processed/swell_aggregated.csv and preprocessing_audit.json with raw/processed SHA-256 hashes. --out names a directory. --save-minutes saves interaction-only minute data. Training rejects duplicate block observations, synthetic markers, relaxation conditions, invalid labels, invalid feature values and provenance mismatches. Missing paths fail rather than silently substituting data.

## Validation and baseline gap

Final internal estimates use five outer GroupKFold splits by participant and four inner GroupKFold splits on each outer training set. Each outer fold contains 60 observations/20 participants for training and 15 observations/5 participants for testing.

Inside each inner fold, the pipeline fits median imputation, optional log1p transformation, scaling and the regressor. Candidates are mean/median baselines, regularized Ridge, log-Ridge, log-SVR and two Random Forest complexities. The inner rule prefers a baseline when it lies within one standard error of the best inner MAE. Neither outer targets nor outer preprocessing distributions select the model. A test perturbs outer targets and verifies unchanged inner selection and held-out predictions.

| Procedure / fixed comparator | MAE | RMSE | R² |
|---|---:|---:|---:|
| Nested conservative selection | 1.8772 | 2.2850 | -0.0539 |
| Training-fold mean | 1.8772 | 2.2850 | -0.0539 |
| Training-fold median | 1.8693 | 2.3811 | -0.1443 |
| Previous RF configuration, reliable desktop subset | 2.1123 | 2.5614 | -0.3242 |

The mean reference was selected in all five outer folds and for the full-data refit. The median has slightly lower MAE in this comparison; the prespecified conservative preference favors the mean when inner differences are within uncertainty. This is not evidence of a useful behavioral predictor.

RF training MAE is 0.8747 versus held-out-participant MAE 2.1123. There are only 75 independent questionnaire observations for 15 deployment features. Participant-mean target SD is 1.6758, versus within-participant residual SD 1.4650. These support concern about overfitting and heterogeneity; they do not establish a causal explanation.

The earlier 28 model/feature comparisons remain exploratory. SnMouseDistance is included/excluded in those comparisons because the workbook marks it “unreliable?”. Production now consistently excludes it, SnMouseAct and SnTabfocusChange. The full research RF comparison with/without distance is MAE 2.0944/2.1038; that small selection result does not validate the sensor or justify individual deployment.

This public dataset was already studied in the previous pass. Nested evaluation is an internal estimate of the specified procedure, not an untouched external validation or a claim that repeated development has no optimism. An independent consented participant cohort remains necessary.

Relevant ignored outputs in ml/results:

- nested_evaluation.json: outer/inner participant membership, rankings, fold metrics and fitting-gap diagnosis.
- nested_metrics.csv: pooled outer metrics for selection and fixed comparators.
- development_selection.csv: full-data inner ranking used for the final refit.
- selected_oof_predictions.csv and cv_folds.json: held-out predictions and fold assignment.
- exp1_* and experiments 2–5: exploratory comparison/ablation/reliability and descriptive plots.

## Feature and API contract

The deployment feature order contains 15 finite nonnegative numeric measurements:

- Keyboard: SnKeyStrokes, SnChars, SnSpecialKeys, SnDirectionKeys, SnErrorKeys, SnShortcutKeys, SnSpaces, CharactersRatio, ErrorKeyRatio.
- Mouse: SnLeftClicked, SnRightClicked, SnDoubleClicked, SnWheel, SnDragged.
- Application: SnAppChange.

Counts are per-minute rates; ratios are minute ratios. Completed windows are averaged over the current desktop session. Inactive windows remain in these means but do not produce a new current prediction. Desktop sessions still differ from SWELL's controlled condition blocks.

Native letter codes are explicitly enumerated. Special keys overlap with other non-letter categories. Error keys include backspace/delete/Ctrl+Z; documented shortcuts are counted separately. Drags count operations. Unmeasured tab focus and non-equivalent uLog mouse actions are not fabricated. Distance remains available as a descriptive local geometry metric but is not a model input.

GET /health returns model readiness and ordered columns. POST /predict rejects missing/extra features, nonfinite/negative measurements and CharactersRatio outside [0,1]. Metadata order must exactly equal the fitted pipeline's named columns, and the model checksum/count must match. Missing/corrupt models leave health degraded and predictions return 503.

A minimal request uses participant_id, features and optionally demo_mode. The score response is continuous; stress_class=-1 and probabilities={} explicitly mean regression. prediction_kind distinguishes behavioral_regression from reference_baseline. The latter is the current fitted model and makes no individual behavioral claim. raw_stress_score is also returned; display bounds are 0–10.

Feature-replacement sensitivities for learned models compare real predictions with one feature replaced by its training median. They are not SHAP or causality. A population reference has no behavioral sensitivities. Activity and score-history fields are experimental heuristics, not validated fatigue or burnout assessments.

Java HTTP calls occur off the FX thread. Timeouts, corrupt responses and unavailable networking/backend produce an unavailable state. No heuristic, random or hardcoded score replaces a failed real model. Late responses are invalidated after pause, deletion or shutdown. Logs avoid request/response bodies and personal scores.

## Consent, storage, exports and safety

Consent precedes real hooks and database sessions. The app does not persist typed content, passwords, clipboard contents, screenshots, application titles or executable names. Names are used transiently for category/switch detection; the Linux fallback no longer requests window titles. Gemini transmission is disabled.

H2 defaults to java-app/data/stressdb. Never delete/recreate this database to fix a run. Raw windows and exact session-mean inputs are stored in additive tables. Schema-3 real history excludes demo sessions, including historical demo records with an old real-model prefix. Legacy records remain stored.

Low/Moderate/High self-reports are ordinal-3, separate from the regression target. They are saved without ending the session, survive shutdown, and export as self_report_ordinal3. Export headers match measured values and source is LIVE_DESKTOP or SYNTHETIC_DEMO; desktop exports cannot be passed off as SWELL training observations. The daily report filters its requested day.

Data deletion requires confirmation and applies only to the selected participant. Tests verify cascades, preservation of another participant and rejection of late in-flight predictions. Restart after deletion to create a new consented session. No deletion test uses the personal database.

The unused break-reminder spinner was removed because it never scheduled reminders. The “I took a break” control records a break and resets the descriptive work streak. Closing the window normally hides it to the tray when supported; Settings → Quit & Exit Application stops hooks and closes the database.

Raw/derived datasets, personal databases, models, result artifacts, credentials, local overrides and audit copies are ignored. Both Git index and working tree were inspected; no commit or push was made by this pass. Git history contains the database from the initial commit and requires a separate reviewed history-cleanup decision before publication.

## Troubleshooting and attribution

- Missing workbook: supply --input with your licensed local path; no synthetic fallback exists.
- Degraded health / feature-order error: rerun preprocessing and training using the pinned environment.
- HTTP 422: match /health feature_columns exactly; never invent missing measurements.
- Port 8000 occupied: identify the existing process before stopping it; do not kill unrelated services.
- Java “Unable to establish loopback connection” / “Invalid argument”: use run-java.ps1 or a short workspace -SocketDirectory. A short LocalAppData path can still be MSIX-virtualized in this environment.
- Database lock: close another app instance normally; do not remove the DB file.
- GUI tests require an unlocked desktop. Optional MySQL and non-Windows deployment have not been validated.

Use SWELL under the download agreement and this project's noncommercial research scope. Keep raw/derived participant data and trained artifacts local. The source-code license grants no dataset redistribution rights.

Cite Koldijk, Sappelli, Verberne, Neerincx and Kraaij (2014), “The SWELL Knowledge Work Dataset for Stress and User Modeling Research”, ACM ICMI: [author's paper](https://cs.ru.nl/~skoldijk/Papers/ICMI%202014%20paper_final_cr.pdf), [DOI](https://doi.org/10.1145/2663204.2663257). The workbook's Features and interest / Notes on the data sheets define the interaction counters and repeated condition ratings.
