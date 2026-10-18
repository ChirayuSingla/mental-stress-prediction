# Validation and repair report — 18 October 2026

This pass began from clean commit `6d130d0` after the earlier repair pass. No commit, push, destructive Git operation or personal-data deletion was performed. The previous AUDIT_REPORT.md is historical; its deployed RF result is superseded here.

The software workflow works on this Windows installation. The scientific result does **not** establish useful individualized behavioral stress prediction. Nested selection chooses a mean reference fitted from real SWELL labels. UI/API identify it as a population reference independent of current behavior, with no feature contributions or individual alerts. This outcome is preferable to claiming an unmeasured improvement, but it does not fulfill a claim of accurate personalized prediction.

## Prioritized findings and fixes

1. **Scientific selection:** the previous process forced deployment of a learned model even though it underperformed the mean. Added mean/median candidates, regularized/log-transformed alternatives and nested participant-grouped selection. Learned preprocessing is inside every inner training fold. A conservative one-standard-error preference permits a reference baseline to win.
2. **Measurement contract:** mouse distance is questioned by the workbook and desktop mouse-action/tab-focus counters are not equivalent to SWELL. Deployment consistently excludes SnMouseDistance, SnMouseAct and SnTabfocusChange. Its 15 named columns match Java, fitted pipeline and metadata exactly. Desktop session means approximate condition aggregates; their external validity remains unmeasured.
3. **Monitoring lifecycle:** a fixed timer could submit partial post-resume windows, retained timing/mouse/app state could bridge pauses, and a delayed API response could repopulate data after deletion. Resume now resets transient state and waits a whole interval; generation checks invalidate stale responses. HTTP runs outside the state lock. Empty intervals are retained in session means but invent no prediction.
4. **Persistence/reporting:** self-report could be overwritten at shutdown; real history could include old demo predictions; calibration could stop without enough data; baseline replacement was not atomic; CSV header/value and daily-date filtering were inconsistent. Fixed each without resetting the database. Ordinal self-reports remain separate from the regression target.
5. **Startup/packaging:** preserved and retested the earlier consent Scene-before-owner fix. Eager HttpClient construction could crash startup when local networking was unavailable; construction is now lazy and failures become unavailable status. The shaded JAR originally retained only MySQL's JDBC service descriptor, hiding H2; service descriptors now merge. Added launch scripts that handle working directories and this installation's short workspace socket path.
6. **Honesty/privacy:** baseline results have no behavioral explanation. UI labels distinguish questionnaire score, reference baseline and unvalidated activity/history heuristics. Generic user score alerts replace diagnostic language. Removed Linux's window-title fallback and request/response-body logging. Removed the unused reminder control. Real hooks still require human consent.
7. **Integration regression found during this pass:** a new population-reference label was accidentally reused as burnout_risk, exceeding its existing database VARCHAR(32). The actual packaged app exposed this. Separated score/history labels and added an API-to-H2 regression test. No schema widening or exception suppression was used.
8. **Reproducibility/invalid inputs:** added an exact Windows/Python dependency lock, isolated API fixtures, preprocessing/nested/Java lifecycle tests, model integrity checks and strict numeric input validation. Missing or corrupt models produce degraded health and HTTP 503, never synthetic predictions.

## Actual ML results

Source workbook: 3,139 rows × 172 columns in SWELLdata, 25 participants. Excluded 451 unlabeled R minutes. Aggregated 2,688 N/I/T minutes by participant/block/condition into **75 questionnaire observations**, three per participant. Minute rows are not independent labels. Physiological, facial and Kinect features are excluded.

Missing source counters propagate to dependent ratios before aggregation; training-fold median imputation handles residual missingness. Investigated 31 missing-counter rows and 61 ErrorKeyRatio values above one (maximum 61); retained documented measured values rather than blindly clipping them. Workbook notes question SnMouseDistance; research ablations retain both cases, deployment excludes it. Exact data diagnostics/provenance remain in ignored preprocessing audit artifacts.

Evaluation: **5 outer GroupKFold folds** (20 training participants/60 observations; 5 held-out participants/15 observations), **4 inner participant-grouped folds** on each outer training partition. Twelve fixed candidate configurations include mean/median, Ridge, log1p Ridge/SVR and regularized/previous RF. Candidate selection uses inner pooled OOF MAE and a one-standard-error baseline preference. Imputation, transformations and scaling are fit only on training partitions. Full-data inner selection/refit occurs after outer evaluation.

| Procedure / fixed comparator | Outer pooled MAE | RMSE | R² |
|---|---:|---:|---:|
| Nested conservative selection | 1.8772 | 2.2850 | -0.0539 |
| Training-fold mean | 1.8772 | 2.2850 | -0.0539 |
| Training-fold median | 1.8693 | 2.3811 | -0.1443 |
| Previous RF configuration, reliable desktop subset | 2.1123 | 2.5614 | -0.3242 |

The mean was selected in all five outer folds and the final refit. The median's slightly lower MAE does not change the specified preference within inner uncertainty. The full-data reference is approximately **3.30/10**, learned from the real targets rather than hardcoded. Negative R² and baseline selection provide no evidence of useful individualized prediction.

RF full-data training MAE is 0.8747 versus grouped held-out-participant MAE 2.1123. There are five independent observations per deployment feature. Target SD is 2.2259; participant-mean SD 1.6758 and within-participant residual SD 1.4650. These support concern about overfitting/heterogeneity, without proving a cause. Descriptive research RF with/without distance yields MAE 2.0944/2.1038 on the larger research feature sets; these exploratory comparisons do not justify deploying distance.

This dataset had already been explored. The selection rule was developed during this audit, including examination of an earlier less-conservative nested run. Final nested metrics are an internal estimate of the specified final procedure, **not an untouched external-cohort estimate or a cure for repeated development**. An independent participant cohort remains required. No fabricated labels, clinical threshold, classification accuracy or personalized improvement is reported.

## Files changed in this pass

Paths below are repository-relative; grouped rows list each affected file.

| Files | Change |
|---|---|
| ml/training/nested.py (new), ml/training/train.py | Nested grouped selection, baselines, conservative refit, OOF/fold/ranking/diagnosis artifacts and metadata. |
| ml/preprocessing/preprocess_swell.py | Reject an empty labeled active dataset; preserve existing real aggregation and provenance checks. |
| ml/api/server.py | Named DataFrame inference, exact feature-order integrity checks, strict numeric/missing/extra validation, honest reference/explanation fields and separate history label. |
| ml/requirements-lock.txt (new) | Exact 59-distribution Windows/Python 3.10 lock used for fresh installation. |
| ml/tests/conftest.py, test_nested.py, test_preprocess_swell.py (new); test_contract.py, test_pipeline.py | Isolated model fixtures; leakage, aggregation, missing/corrupt/order, baseline and invalid HTTP tests. |
| java-app/pom.xml | Merge JDBC service providers; stop rewriting dependency-reduced-pom.xml. |
| config/AppConfig.java | Validate mode, timing, pseudonym and alert settings. |
| util/AppContext.java | Complete-window scheduling, asynchronous inference, stale-response invalidation, empty intervals, safe self-report/delete/shutdown and honest alerts. |
| feature/FeatureAggregator.java, feature/SwellObservation.java | Reset partial intervals, correct start timestamp, validate stable finite schema. |
| monitor/KeyboardStats.java, MouseStats.java, KeyboardMonitor.java, MouseMonitor.java, ApplicationMonitor.java, MonitoringService.java | Clear transient state across monitoring pauses. |
| monitor/ActiveWindowProvider.java | Remove sensitive-title fallback on Linux. |
| model/FeatureWindow.java, model/Prediction.java; ml/MlClient.java | Canonical 15 features, prediction kind, lazy HTTP construction and sanitized failure logging. |
| db/BaselineRepository.java, FeatureRepository.java, PredictionRepository.java | Atomic profile replacement, persisted ordinal reports, demo-separated real history. |
| report/ReportGenerator.java | Correct CSV columns/quoting, ordinal semantics, daily filtering and configured alert preference. |
| ui/DashboardView.java, ExplanationView.java, HighStressPopup.java, NotificationService.java, ReportsView.java, SettingsView.java | Honest baseline/heuristic language, working monitoring-state display and removal of dead reminder option. |
| src/test/java/com/stressml/FxTestSupport.java; config/AppConfigTest.java, report/ReportGeneratorTest.java, util/AppContextTest.java (new) | Shared FX runtime, validation/report tests, pause/deletion/in-flight/empty-window tests. |
| db/DatabaseManagerTest.java, feature/FeatureAggregatorTest.java, feature/SwellObservationTest.java, ml/MlClientTest.java, ui/ConsentDialogTest.java under Java tests | Actual API-to-H2 save regression, participant isolation, canonical schema and retained consent coverage. |
| scripts/run-backend.ps1, scripts/run-java.ps1 (new) | Correct component directories, explicit demo/packaged options, short workspace socket support. |
| .gitignore | Ignore local .sockets directory. Existing data/model/database/credential exclusions retained. |
| README.md, docs/architecture.md, docs/methodology.md, docs/experiments.md, docs/setup.md, docs/ethics_privacy.md, AUDIT_REPORT.md, VALIDATION_REPORT.md | Actual architecture, setup, nested results, limitations, privacy and this evidence report. |

Java production/test paths in the table are relative to java-app/src/main/java/com/stressml and java-app/src/test/java/com/stressml respectively. No model, workbook, generated dataset or personal database is tracked. The incidental dependency-reduced-pom.xml generation was restored; it has no substantive diff.

## Executed checks and evidence

Commands were run with the existing environment; scripts/explicit paths selected the correct component directories. Logs and temporary fixtures are ignored in `.audit/round2/`.

| Executed command/check | Actual result |
|---|---|
| git status --short; repository/source/config/test inspection; data-flow trace | Started clean at 6d130d0; inspected existing implementation before changes. |
| ml/.venv/Scripts/python.exe -m compileall -q api preprocessing training evaluation tests (from ml) | Passed syntax compilation. |
| ml/.venv/Scripts/python.exe -m pip check | No broken requirements. |
| ml/.venv/Scripts/python.exe training/train.py (from ml) | Completed real-data exploratory and nested training, saved model/metadata in ml/models; 75 observations, 25 participants. |
| ml/.venv/Scripts/python.exe evaluation/run_experiments.py (from ml) | Completed experiments 2–5; modality/distance ablations, held-out permutation and descriptive SHAP artifacts generated. These SHAP plots are research RF analyses, not the API's explanation method. |
| ml/.venv/Scripts/python.exe -m pytest tests -q (from ml) | **23 passed**, zero skipped; 2 dependency deprecation warnings. Latest log python-tests-verified.log. |
| Separate Python venv; pip install -r ml/requirements-lock.txt; pip check | Fresh 59-package install succeeded, no broken requirements. Existing venv preserved. |
| Fresh-source preprocessing/preprocess_swell.py --input [licensed workbook] --save-minutes; training/train.py | Both completed. Same nested metrics, ordered columns and all 75 refit predictions exactly reproduced. |
| Fresh-source pytest after copying final API/tests | **23 passed**, zero skipped; same 2 warnings (fresh-tests-verified.log). |
| mvn package -Dstressml.integration=true -Djavafx.ui.tests=true -Djdk.net.unixdomain.tmpdir=[short workspace .sockets] | **BUILD SUCCESS; 27 tests, 0 failures/errors/skips**. Includes actual local API HTTP prediction, API-to-H2 insertion, dialog, lifecycle and deletion tests. |
| Inspect shaded JAR META-INF/services/java.sql.Driver | Both H2 and MySQL service providers retained after fix. |
| scripts/run-backend.ps1; GET http://127.0.0.1:8000/health | Started; HTTP 200, model_loaded=true, REAL_SWELL, DummyMean, reference_baseline, 15 features, Nested GroupKFold. |
| scripts/run-java.ps1 -Demo -Packaged with explicit in-memory DB_URL | Packaged JAR opened; visible demo banner/population reference, actual backend result saved after history-label fix. No personal DB touched by this packaging smoke test. |
| scripts/run-java.ps1 in real mode | App opened; human accepted consent; native collectors started. Final run recorded real windows/predictions without the consent Scene crash. |
| Read-only JDBC live input verification + actual HTTP replay | Latest replay: 14 completed windows, 15 inputs; every stored input equaled its session-window mean; saved 3.3 and replayed 3.3 matched exactly. Evidence live-verification-final.log. |
| Read-only row digest comparison against backup after live collection | All original rows identical in users, sessions, behavior_features, predictions, baseline_profiles, recommendations and application_usage. New session/measurements/predictions only appended. Evidence db-preservation-final.log. |
| git diff --check; index/ignore inspection | No whitespace errors; no staged files; local data/models/results remain ignored; no commit or push. |

Initial restricted Python test processes stalled; rerunning with local socket access produced the results above. A first attempt to reuse a locked log file did not launch tests; it is not counted as a pass. Short LocalAppData socket paths also failed in this MSIX environment; the workspace socket path was then implemented and actually verified. Maven emits JavaFX effective-model/module and duplicate-resource warnings; packaging and tests pass. Python warnings concern Starlette/httpx and the anyio BlockingPortal alias; none were hidden.

## Verified workflow and limits

Backend starts: **yes**. JavaFX opens with safe consent lifecycle: **yes**, human accepted the real-mode prompt. Native collection → completed window → session mean → Java HTTP → real trained artifact → continuous/reference response → H2 persistence: **yes**, checked against stored measurements and replay. Packaged dashboard visibly showed the reference label. A final screenshot of the real-mode dashboard could not be recaptured because the window remained minimized in the automation interface; its opening, consent, collection and persistence were verified independently. Do not equate that screenshot limitation with an observed application failure.

Automated pause/resume/deletion and unavailable-backend tests use isolated monitors/databases. No personal deletion, privacy-control automation or fabricated live event was used. Real hooks were started only after human consent. Fresh Python setup was verified in a separate environment; a second physical Windows machine, optional MySQL server, Linux/macOS runtime and independently consented desktop validation cohort were unavailable and were not tested.

The app and loopback backend are left running after verification. Empty real intervals correctly add raw measurements without a new prediction. Close through Settings → Quit & Exit Application to stop hooks and close H2; normal window close may hide to the tray.

## Remaining issues and exact next actions

- **Individual prediction validity is unresolved.** Keep the explicit population-reference deployment. Before claiming an individualized model, define a study/protocol, collect independently consented participant-level questionnaire observations with the same desktop feature definitions, freeze selection, and evaluate on untouched participants. There is no software fix that creates missing external evidence.
- **Domain/feature equivalence:** session averages and SWELL controlled blocks differ. Validate units/event definitions and observation duration in that independent study; do not silently restore unavailable or unreliable features.
- **Platform scope:** run the documented test suite against an isolated configured MySQL instance if MySQL is required, and on each supported OS before claiming support. This audit verified Windows/H2 only.
- **Dependency warnings:** upgrade FastAPI/Starlette's supported test-client transport and anyio integration together in a future tested environment revision. The exact working environment is pinned; warnings do not currently fail the suite.
- **Historical repository privacy:** current tracked files exclude data/artifacts; initial Git history contains an old database. Do not publish that history until a separately reviewed backup/history-cleanup procedure is authorized. No history rewrite was attempted.

## Launch commands

From the repository root, backend terminal:

```powershell
.\scripts\run-backend.ps1
```

Second terminal, same root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;C:\Users\chirayu singla\Downloads\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin;$env:Path"
.\scripts\run-java.ps1
```

Use the current running instances instead of launching duplicates. See README.md for preprocessing, retraining, installation, direct commands, explicit demos and packaged launch. Keep the licensed workbook local and follow the SWELL noncommercial research/download agreement and Koldijk et al. (ICMI 2014) attribution.
