# Current methodology

The supervised target is SWELL Stress, a continuous questionnaire rating. Repeated minute labels are aggregated by PP/Blok/Condition before evaluation. Only keyboard, mouse and application counters are predictors. GroupKFold separates participants. Each model pipeline fits its median imputer and scaler in the training fold.

The desktop averages completed per-minute measurement windows within the current session. This shares the training aggregation form, but desktop sessions remain outside the controlled SWELL protocol. Their predictive validity has not been established. SnMouseAct and SnTabfocusChange are excluded from deployment because equivalent measurements are unavailable. No participant baseline, categorical app encoding, condition or participant ID enters the regressor.

Personal baseline comparisons are local descriptive UI features, not a tested contribution to SWELL prediction. Self-report Low/Moderate/High is ordinal-3, not SWELL's continuous target. The active regressor returns no class probabilities. Sensitivities compare the actual model output with one feature replaced by its training median; they are not causal effects or SHAP values. Activity and score-history indicators are unvalidated heuristics. See [README](../README.md) for the feature mapping and missingness decisions.
