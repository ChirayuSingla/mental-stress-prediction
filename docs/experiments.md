# Real SWELL experiments

The unit of evaluation is one participant/block/condition observation: 75 observations, 25 participants, 3 labeled conditions. R is excluded. Five-fold GroupKFold holds out 5 participants (15 observations) and trains on 20 participants (60 observations) in each fold. Imputation and scaling are fold-local.

Experiment 1 compares six learned regressors plus a fold-mean baseline across 18/17 research features and 16/15 desktop-compatible features. Experiment 2 measures modality ablations. Experiment 3 compares distance inclusion. Experiment 4 provides descriptive full-data RF impurity/SHAP plots and held-out-participant permutation importance scored by MAE. Experiment 5 summarizes observed/predicted questionnaire scores by condition.

The selected desktop Random Forest has pooled OOF MAE 2.0866, RMSE 2.5148 and R² -0.2765. The mean baseline is stronger: 1.8772, 2.2850, -0.0539. These same-fold selection metrics are exploratory, not unbiased nested-CV estimates. No classification metrics, clinical thresholds or personalized-model improvements were measured. See ../ml/results and [README](../README.md).
