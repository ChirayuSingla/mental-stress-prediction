# Evaluation artifacts

Run ml/training/train.py with the real preprocessed workbook. The initial 28 model/feature comparisons remain exploratory and include SnMouseDistance ablations. Deployment excludes unavailable or questioned sensors and is evaluated with nested participant grouping.

ml/results/nested_evaluation.json contains all outer participants, inner rankings and fold results; nested_metrics.csv compares nested selection, mean/median references and the previous RF configuration. selected_oof_predictions.csv contains pooled held-out predictions. development_selection.csv is the later full-data inner selection for refitting.

Current nested selection: MAE 1.8772, RMSE 2.2850, R² -0.0539. No behavioral performance gain is established. The selected mean reference is not an individualized prediction.

evaluation/run_experiments.py also produces exploratory modality/distance ablations, held-out permutation importance and descriptive full-data RF/SHAP plots. Those full-data explanation plots are not final validation metrics. No classification accuracy, probabilities, clinical thresholds or personalized-model advantage were measured.
