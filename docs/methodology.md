# 2. Methodology

## 2.1 Personal baseline engine
1. **Calibration**: the first *N* sessions (default 3) or first 30 windows are calibration. Users are asked to work normally.
2. **Profile**: for each of 9 baseline features compute mean, median, standard deviation and IQR fences (Q1 − 1.5·IQR, Q3 + 1.5·IQR).
3. **Deviation features** for every later window: relative deviation `dev_X` and standardised deviation `z_X`.
4. **Training-time equivalence**: `ml/preprocessing/baseline.py` applies exactly the same procedure per participant on the dataset
   (first 3 session IDs per participant = calibration), so the model is trained on the same feature semantics the app produces at runtime.
5. **Leakage control**: baseline statistics are computed only from calibration sessions and never updated with the windows they are
   applied to; cross-validation is `GroupKFold` on participant, so a participant's windows are never split across train and test.

**Why participant-aware splitting matters.** Windows from one person are highly correlated (same keyboard, same habits). Random
splits would let the model memorise the person rather than the stress signal and report inflated accuracy that collapses on new
users — the base paper's community warns about exactly this (see also the follow-up field study by the same group, which contrasts
"one-fits-all" and personalised modelling; verify citation).

## 2.2 Models compared
Logistic Regression, Decision Tree, Random Forest, SVM (RBF), XGBoost, MLP — each in a `Pipeline(StandardScaler, model)`.
Metrics: accuracy, macro precision/recall/F1, one-vs-rest ROC-AUC, confusion matrix. Best model by macro-F1 is persisted.

## 2.3 Stress score
`score = 100 × (0·P(Low) + 0.5·P(Moderate) + 1·P(High))` — the expected value of the class ordinal. It is a convenience
number for the dashboard, not a measurement.

## 2.4 Fatigue indicator (0–100, transparent)
`0.40·min(continuous_work/2h,1) + 0.20·max(0,−dev_typing_speed) + 0.15·max(0,dev_error_rate) + 0.15·max(0,dev_latency) + 0.10·idle-collapse`
(each term capped at 1).

## 2.5 Burnout-risk indicator (0–100, longitudinal)
Over the last ≤10 sessions: `0.45·fraction of sessions with mean stress ≥ 60 + 0.20·positive trend slope + 0.20·fraction of sessions ≥ 4 h + 0.15·fraction with ≤ 1 break`.
Labelled Low / Elevated / High **indicator** — never "burnout diagnosis".

## 2.6 Explainability
SHAP `TreeExplainer` on the deployed tree model; the six largest |SHAP| values for the predicted class are returned with
human-readable text (e.g. "typing speed decreased 25% from your baseline"). For non-tree models, coefficient × standardised value
is used as a first-order attribution. Global importance (impurity, permutation, mean |SHAP|) is produced by Experiment 4.

## 2.7 Recommendation engine
Rule table (see `ml/api/recommendation.py` and `java-app/recommendation/RecommendationEngine.java`): high stress + long session → short break;
error-rate deviation > 30 % → rest; continuous work ≥ 90 min → movement break; switching deviation > 40 % → reduce multitasking;
typing-speed deviation < −25 % → fatigue notice; fatigue ≥ 60 → longer break; burnout indicator ≥ 65 → recovery period.
Optional Gemini free-tier re-phrasing receives only the rule text and three aggregate numbers.

## 2.8 Workflow
Consent → calibration sessions → baseline profile → monitoring windows → prediction/explanation/recommendation every window →
optional self-report per session (label for the custom dataset) → history & reports → CSV export → retraining.
