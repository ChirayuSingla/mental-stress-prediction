# Current methodology

SWELL's Stress target is a repeated condition-block questionnaire score. Aggregate PP/Blok/Condition first: 75 independent labeled observations from 25 participants; exclude unlabeled R. The supervised experiment uses only interaction features.

Five outer GroupKFold folds hold out 5 participants/15 observations each. Four inner grouped folds choose among mean/median references, regularized raw/log Ridge, log-SVR and two Random Forest complexities. Imputation, transforms and scaling fit within training folds. A conservative one-standard-error rule prefers a reference baseline when learned-model gains are within inner-fold uncertainty.

The procedure chose the mean reference in all outer folds and full-data refit. Pooled outer MAE 1.8772, RMSE 2.2850, R² -0.0539. The median comparator is 1.8693/2.3811/-0.1443; prior RF configuration without distance is 2.1123/2.5614/-0.3242. Internal nested estimates on a previously explored dataset are not external validation.

RF training MAE 0.8747 compared with grouped MAE 2.1123 indicates a fitting/generalization gap. Only 75 labels, 15 predictors and participant heterogeneity limit conclusions. No individual stress detector is validated.

Desktop means share the aggregation form, but the collection protocol and event semantics differ. Baseline comparisons remain descriptive. Ordinal-3 self-reports are not continuous SWELL ratings. Feature-replacement differences are not SHAP/causality; the current mean reference has no behavioral attributions. See the root README for exact fields, missingness, provenance and reproducibility commands.
