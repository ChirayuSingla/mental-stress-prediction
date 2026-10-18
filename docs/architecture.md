# Current system architecture

Consent → native counts/geometry and foreground process category polling → completed per-minute windows → local H2/JDBC persistence → current-session mean feature map → Java HTTP/1.1 client → FastAPI named-column validation → fitted SWELL regression pipeline → score/reference + explicitly experimental heuristics → persisted exact inputs → FX-thread UI updates.

The deployment map has 15 keyboard, mouse and application counters/ratios. SnMouseAct, SnTabfocusChange and questioned SnMouseDistance are excluded. Baseline deviations, categories, participant ID and condition are not regressor inputs. Named training columns, metadata order, artifact hash and inference DataFrame order are checked.

The current model is a training-target mean population reference selected by conservative nested participant-grouped validation. It does not individualize scores from behavior, generate probabilities or claim clinical validity. Learned-model median-replacement sensitivities are genuine prediction differences, not SHAP or causality; the reference has no feature contributions.

AppContext serializes measurement/storage transitions under a short lock and performs HTTP outside that lock. Generation tokens discard late responses after pause/resume/deletion. A scheduler restarts a complete interval on resume. Empty windows persist measurements without a current prediction. Demo records are isolated from real history.

The database retains original legacy tables plus swell_feature_windows and swell_prediction_inputs. All deletion tests use in-memory databases. H2 is the verified target; MySQL remains optional/unverified. The shaded JAR merges JDBC service descriptors. No external Gemini call runs.

Offline: licensed Excel → validated participant/block/condition means → fold-local imputation/transforms/scaling → inner grouped candidate selection → outer grouped performance estimates → full-data refit and paired artifact/metadata. See [README](../README.md) and [validation report](../VALIDATION_REPORT.md).
