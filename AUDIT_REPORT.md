# Audit, repair and execution report — 18 October 2026

## Verified outcome

The real SWELL preprocessing, grouped regression evaluation, local API, Java client, consent dialog and JavaFX live collection/prediction/display workflow work in this Windows environment. The backend and final JavaFX build were left running after the user's consent.

Python: 15 tests passed. Java: 18 tests passed, no failures, errors or skips with integration/UI flags enabled. The application was launched with mvn clean javafx:run. This is software integration success, not validation of stress-detection accuracy.

The saved Random Forest performs worse than a mean-score baseline. Desktop-session predictive validity is unmeasured. No clinical or classification performance is claimed.

## Initial state and prioritized root causes

Existing user edits were present throughout the Java collector, API, training, evaluation, configuration and README. They were inspected and retained. Before edits, Git status was saved to .audit/before-status.txt, the tracked working diff to .audit/before.patch and the database to .audit/backups/stressdb.before.mv.db. The backup and original had identical SHA-256 95a164b0726f10c40feb1a552e57f6efd257cebc6a1207fc3fe0fecaf7a650a6.

| Priority | Root cause | Repair |
|---|---|---|
| P0 | ConsentDialog called initOwner on a stage without a Scene; JavaFX copies scene styles while binding the dialog | Attach an owner Scene before consent; ask before opening DB sessions/hooks; retain Yes/No workflow and add lifecycle test |
| P0 | SWELL preprocessing/training/evaluation import an undefined TARGET | Define the raw target name and run actual imports/preprocessing |
| P0 | API silently supplies absent features as zero and applies invalid legacy unit aliases | Require every trained feature; reject negative/nonfinite inputs; no fabricated legacy mappings |
| P0 | Collector duplicates application switches as tab-focus measurements and counts native mouse motion as uLog mouse actions | Exclude those two features from the deployed schema; keep explicit research comparisons |
| P0 | Offline fallback invents numeric stress scores on a different 0–100 scale | Show unavailable/error state; save no numeric fallback prediction |
| P1 | Keyboard native codes are not alphabetically contiguous; special/error/shortcut definitions differ from workbook | Explicit letter code set and documented overlapping SWELL categories, Ctrl+Z and shortcut definitions |
| P1 | Counters are interval totals, drags count movement callbacks, and zero-denominator ratio differs from workbook | Per-minute normalization, drag-operation counts, workbook-compatible zero ratio |
| P1 | Per-minute app inputs do not share the training aggregation form | Average completed windows within a current desktop session; persist exact model inputs and source window count |
| P1 | Missing source counters leave apparently valid zero ratios; input validation permits repeated minute observations | Mark those ratios missing, aggregate within blocks, validate unique observations, impute inside folds |
| P1 | No naïve benchmark; README overstates negative-R² results and uses inconsistent metrics | Add fold-mean comparator, save OOF/fold records, report actual negative R² and selection bias |
| P1 | API creates one-hot “probabilities” and calls standardized feature magnitude an explanation of model effects | Explicit regression contract (-1/empty probabilities); real one-feature model sensitivity, clearly not SHAP/causality |
| P1 | DB test uses live file; SWELL measurements are not persisted; export headers and history scales disagree | In-memory test DB, additive measurement/input tables, aligned CSV, schema-3 history filter |
| P1 | Self-report submission ends a live session and later shutdown erases the stored response | Separate self-report update; retain existing report on shutdown; label ordinal-3 correctly |
| P1 | Java default Windows socket temp path causes loopback/Invalid argument failures in this desktop environment | Verified short writable socket-directory JVM option, documented without changing OS security |
| P2 | Stale server serves old SVR/schema despite source changes; missing HTTP test dependency | Identify exact stale project process, restart actual backend, install httpx; declare openpyxl/httpx |
| P2 | Optional external rephrasing can transmit aggregate context without an explicit task request | Production API keeps Gemini disabled and UI/docs say so |
| P2 | Initial commit tracks personal H2 data despite ignore rules | Remove only its two Git index entries using git rm --cached; retain both local files and all records |

## Data flow now verified

Consent → native keyboard/mouse hooks and foreground-process poller → a completed measurement window → per-minute features → original measurements saved → current-session feature means → Java HTTP/1.1 POST /predict → metadata-ordered real SWELL regressor → continuous questionnaire score and model sensitivities → prediction plus exact inputs saved → JavaFX updates on the FX thread.

HTTP runs on the window-cycle worker. Participant ID and baseline do not enter the trained model. Baseline comparisons remain descriptive. A desktop session does not reproduce the experimental SWELL condition protocol.

A real workbook observation returned 4.20/10 through both HTTP and MlClient. The final live session was independently checked after four completed windows: all 16 saved feature means matched the stored measured windows; saved score 3.85/10 exactly matched an API replay. Subsequent live scores continued to be saved. The explanation view displayed real sensitivities; history displayed the corrected 0–10 scale.

## Measured evaluation

Raw SWELLdata: 3,139 rows × 172 columns; 25 participants. Active minutes: 2,688. Unlabeled R rows excluded: 451. Aggregated labeled observations: 75, with 25 each for N/I/T.

Five-fold GroupKFold on participant: each fold trains on 20 participants/60 observations and tests on 5 participants/15 observations. Imputation and scaling are fitted within each training fold. Metrics below pool all held-out predictions. No physiological, facial-expression, Kinect, condition, identity or other questionnaire feature enters these models.

| Model/configuration | MAE | RMSE | R² |
|---|---:|---:|---:|
| Fold-mean benchmark | 1.8772 | 2.2850 | -0.0539 |
| Full 18-feature RF with SnMouseDistance | 2.0944 | 2.5210 | -0.2827 |
| Research 17-feature RF without distance | 2.1038 | 2.5420 | -0.3043 |
| Deployed desktop 16-feature RF with distance | 2.0866 | 2.5148 | -0.2765 |
| Desktop 15-feature RF without distance | 2.1123 | 2.5614 | -0.3242 |

OOF metrics were recomputed independently from selected_oof_predictions.csv: MAE 2.086595013383535, RMSE 2.5148130648553777, R² -0.2764683874690179. Full candidate/model tables, fold membership, ablation, reliability and plots are under ml/results.

The deployed pipeline is selected among learned desktop-compatible models; DummyMean is only a benchmark. Comparing/selecting on these same folds introduces model-selection optimism. This is not nested CV or external validation. Keeping distance performed slightly better for this Random Forest; its documented reliability concern remains.

31 task minutes have missing interaction counters (PP11/I: 21; PP16/I: 10). Their derived ratios are made missing too. Within-observation means use observed minutes, and fully missing aggregated features would be imputed within training folds. The 61 ErrorKeyRatio values above 1, maximum 61, are retained. Nonzero-denominator calculations match within the workbook's six-decimal precision. No arbitrary clipping is applied to input observations.

## Commands actually executed and results

Commands use the configured project directories; full logs are local under .audit.

| Command/check | Actual result |
|---|---|
| git status --short, git diff, source/config/test/metadata inspection | Existing edits identified; preserved |
| python -m compileall -q api preprocessing training evaluation synthetic tests | Passed |
| Imports of api.server, preprocessing.preprocess_swell, training.train, evaluation.run_experiments | Passed after TARGET repair |
| Existing .venv python -m pip check | No broken requirements |
| Existing .venv python -m pip install httpx>=0.27 openpyxl>=3.1 | Initial sandbox DNS denial; approved retry installed httpx 0.28.1/httpcore 1.0.9; openpyxl 3.1.5 already present |
| python preprocessing/preprocess_swell.py --save-minutes | Passed; 75 aggregated rows, 25 participants; audit and interaction-only minute output saved |
| python training/train.py | Passed; 28 model/configuration results; RandomForest desktop model and matching metadata saved |
| python evaluation/run_experiments.py | Passed; experiments 2–5, including held-out-participant permutation importance and descriptive SHAP plots |
| api.server.load_artifacts("ml/models") | Passed; regression/REAL_SWELL/schema 3/hash/feature count consistent |
| python -m pytest tests -q | Final run: 15 passed in 6.26 s; two dependency deprecation warnings |
| uvicorn api.server:app --host 127.0.0.1 --port 8000 | Running after restart of verified stale project backend |
| GET /health | HTTP 200; model_loaded=true, RandomForest, REAL_SWELL, schema 3, 16 ordered features |
| POST /predict with a real workbook observation | HTTP 200; continuous score 4.20/10; no classification probabilities |
| mvn test -Dstressml.integration=true -Djavafx.ui.tests=true -Djdk.net.unixdomain.tmpdir=SHORT_DIRECTORY | Final run: 18 tests passed, zero errors/failures/skips; actual Java request returned 4.20 |
| mvn clean javafx:run with JAVA_TOOL_OPTIONS short socket directory | Launched; consent dialog and final application opened; real monitoring/prediction saved and displayed |
| Standalone Java VerifyLive read-only DB comparison and HTTP replay | Four window means matched all 16 stored input values; saved/replayed score both 3.85 |
| Standalone Java VerifyDatabase against preserved DB copy | Every original row in seven tables has identical deterministic content hash |
| git rm --cached -- java-app/data/stressdb.mv.db java-app/data/stressdb.trace.db | Tracking removed; both local files still exist; no commit or publication |

Failures were investigated rather than treated as successes. Sandbox Python asyncio socket creation hung and Java loopback creation was denied; approved runs were used. The Java JVM additionally required a short Unix-domain socket directory even outside the sandbox. An invalid NaN request exposed error-body JSON serialization and was repaired and retested. A new DB-test fixture initially lacked its required timestamp and was corrected. A standalone replay probe initially used Java's default HTTP protocol negotiation; using the production client's HTTP/1.1 setting reproduced predictions.

The first full app run shut down normally through Settings. The final build was launched again, the user accepted consent, and its live session-mean workflow was verified.

## Changed files

Paths below are repository-relative. Existing independent user edits in SyntheticDataGenerator, MonitoringService, plots.py and inspect_swell.py were left in place.

| File | Audit change |
|---|---|
| .gitignore | Ignore audit backups/logs, data audits, result JSON, local configuration and key files |
| README.md | Verified commands, measured results, feature contract, limitations, safety and troubleshooting |
| AUDIT_REPORT.md | This execution report |
| docs/setup.md | Remove obsolete synthetic production setup; use real-data commands |
| docs/experiments.md | Current regression experiments, grouped metrics and benchmark limitation |
| docs/methodology.md | Current aggregation, schema, baseline separation and honest explanations |
| docs/ethics_privacy.md | Consent, local-only API, restricted data and research scope |
| ml/data/README.md | Actual SWELL provenance and exclusion of demo/desktop exports from SWELL experiments |
| database/schema.sql | Add raw-feature and exact-prediction-input tables without altering old records |
| java-app/pom.xml | Isolate tests in in-memory H2 |
| java-app/src/main/resources/schema.sql | Matching additive H2 bootstrap tables |
| java-app/src/main/resources/application.properties | Clarify alert threshold is an unvalidated user preference |
| java-app/src/main/java/com/stressml/App.java | Scene before consent; consent before DB/hooks; resource shutdown override |
| java-app/src/main/java/com/stressml/config/AppConfig.java | JVM property overrides for test isolation; real-mode fallback default |
| java-app/src/main/java/com/stressml/ui/ConsentDialog.java | Safe owner binding and testable dialog construction |
| java-app/src/main/java/com/stressml/feature/FeatureAggregator.java | Per-minute normalization, valid ratio, no fabricated tab focus |
| java-app/src/main/java/com/stressml/feature/SwellObservation.java | Current-session means over completed windows |
| java-app/src/main/java/com/stressml/monitor/KeyboardMonitor.java | Native letter set and workbook key definitions |
| java-app/src/main/java/com/stressml/monitor/KeyboardStats.java | Correct overlapping special-key count; no invented legacy key proportions |
| java-app/src/main/java/com/stressml/monitor/MouseMonitor.java | Count a drag operation once |
| java-app/src/main/java/com/stressml/monitor/MouseStats.java | Separate drag starts; don't relabel middle clicks as left clicks |
| java-app/src/main/java/com/stressml/model/FeatureWindow.java | Measured deployment map, preserved input map, honest CSV columns |
| java-app/src/main/java/com/stressml/model/Prediction.java | Regression/no-score defaults and exact input provenance fields |
| java-app/src/main/java/com/stressml/ml/MlClient.java | Strict real regression parsing, errors/timeouts/interruption, measured-only features |
| java-app/src/main/java/com/stressml/util/AppContext.java | Session means, no offline numeric fallback, FX-safe updates, exact input persistence, report/shutdown/deletion lifecycle |
| java-app/src/main/java/com/stressml/db/DatabaseManager.java | Fail clearly on schema bootstrap failure instead of continuing |
| java-app/src/main/java/com/stressml/db/FeatureRepository.java | Transactional raw SWELL feature persistence and reload |
| java-app/src/main/java/com/stressml/db/PredictionRepository.java | Exact prediction inputs; isolate schema-3 continuous history from legacy records |
| java-app/src/main/java/com/stressml/db/SessionRepository.java | Self-report update separate from session close; retain existing report |
| java-app/src/main/java/com/stressml/report/ReportGenerator.java | Align exported values/headers and correct report score scale |
| java-app/src/main/java/com/stressml/ui/DashboardView.java | Continuous session-mean score, unavailable state, evidence limitation and heuristic labels |
| java-app/src/main/java/com/stressml/ui/ExplanationView.java | Real model sensitivities and descriptive-only baseline language |
| java-app/src/main/java/com/stressml/ui/HistoryView.java | 0–10 stress scale, explicitly rescaled heuristic line |
| java-app/src/main/java/com/stressml/ui/MainLayout.java | Useful backend/error status; remove heuristic-fallback banner |
| java-app/src/main/java/com/stressml/ui/HighStressPopup.java | Clarify configured alert preference and no baseline-model claim |
| java-app/src/main/java/com/stressml/ui/NotificationService.java | Correct tray score denominator to 10 |
| java-app/src/main/java/com/stressml/ui/ReportsView.java | Label CSV as measured features, not SWELL retraining input |
| java-app/src/main/java/com/stressml/ui/SettingsView.java | Accurate local-only/no-Gemini privacy description |
| java-app/src/main/java/com/stressml/ui/UiKit.java | Neutral styling for regression rather than automatic high-class styling |
| java-app/src/test/java/com/stressml/db/DatabaseManagerTest.java | Measurement/input persistence and scale round trip using isolated DB |
| java-app/src/test/java/com/stressml/feature/FeatureAggregatorTest.java | Deployment schema, normalization and ratio regression coverage |
| java-app/src/test/java/com/stressml/feature/SwellObservationTest.java | Current-session averaging without previous-participant history |
| java-app/src/test/java/com/stressml/ml/MlClientTest.java | Real Python request, malformed/503 responses, timeout, continuous score |
| java-app/src/test/java/com/stressml/monitor/KeyboardMonitorTest.java | Noncontiguous key codes and overlapping categories |
| java-app/src/test/java/com/stressml/ui/ConsentDialogTest.java | Real JavaFX owner-scene/decline lifecycle |
| ml/preprocessing/features.py | TARGET and explicit desktop-compatible subsets |
| ml/preprocessing/preprocess_swell.py | Real workbook validation, aggregation, missingness, audit/provenance |
| ml/preprocessing/preprocess.py | Reject repeated minutes/synthetic/unlabeled/invalid observations |
| ml/training/models.py | Fold-local imputation and mean baseline |
| ml/training/train.py | Four configurations, desktop selection, provenance, OOF/fold outputs |
| ml/evaluation/run_experiments.py | Explicit path handling, genuine held-out permutation importance, imputer-aware SHAP input |
| ml/api/server.py | Validated model load, strict regression HTTP contract, genuine sensitivity, no fallback or external rephrasing |
| ml/config.yaml | Remove model classification-threshold claim |
| ml/requirements.txt | Declare Excel and HTTP test dependencies |
| ml/tests/test_pipeline.py | Correct heuristic expectations and honest missing-resource skip |
| ml/tests/test_contract.py | Leakage/shape/provenance/model/HTTP validation regression tests |

Generated data, models, metrics, plots and local evidence are ignored artifacts; they are not staged. Only the two H2 tracking removals are staged. No source commit or publication was made.

## Preservation and remaining limitations

Original rows were compared by deterministic hashes with the preserved backup:

| Table | Original rows | Result |
|---|---:|---|
| users | 1 | All unchanged |
| sessions | 9 | All unchanged |
| behavior_features | 18,270 | All unchanged |
| predictions | 18,268 | All unchanged |
| baseline_profiles | 9 | All unchanged |
| recommendations | 68,535 | All unchanged |
| application_usage | 0 | Unchanged |

New consenting live verification sessions append rows. Raw and derived SWELL data, models and logs were not published. The historic initial Git commit still contains its formerly tracked database; a history rewrite was not authorized or performed.

| Remaining limitation/check | Exact next action |
|---|---|
| Learned regressor is worse than mean baseline; same folds used for selection | Treat it as exploratory; predefine model/feature selection and perform nested participant-grouped or independent participant validation before performance claims |
| Desktop session protocol and uLog collector differ | Collect a consented desktop dataset with matched continuous questionnaires and evaluate held-out participants; do not use ordinal-3 buttons as SWELL targets |
| Foreground application polling can miss subsecond switches; distance depends on devices | Measure collector agreement against documented events and retain/report sensor ablations |
| Destructive data deletion was not exercised on personal data | Exercise confirmation/delete/restart on a separate disposable test DB only |
| 30-window calibration and high-score tray/popups not exercised through long live UI sessions | Run controlled tests in an isolated DB; never manufacture user questionnaire responses |
| Packaged shaded JAR and optional MySQL/non-Windows paths not run | Validate separately if those deployment targets are required; verified launch is Maven/JavaFX on Windows |
| Test dependencies emit deprecation warnings | Migrate the test-client transport when updating FastAPI/Starlette; current tests pass |
| Initial commit still contains historic H2 files | Before sharing history, decide on a separate reviewed history-cleanup process; local DB files must remain preserved |

No known crash or failing automated check remains in the verified Maven/FastAPI/main JavaFX workflow. No GUI deletion, external volunteer validation, clinical validation, classification accuracy or calibrated probabilities is claimed.
