# Research paper — Sections 3 to 6 (draft aligned with the implementation)

> Placeholders marked **[TBP]** must be populated from `ml/results/*.csv` after training on real data.
> Do not paste synthetic-demo numbers. Verify every citation against the original source.

## 3. Proposed Methodology

### 3.1 Overall methodology
The proposed system, *Personalised Multimodal Behavioural Stress Prediction*, passively observes a user's keyboard, mouse and
application-interaction behaviour on a desktop computer, expresses each 60-second window both as absolute behavioural features
and as deviations from the user's own calibrated baseline, and classifies the window into Low, Moderate or High stress with an
interpretable machine-learning model. Unlike the base study, which modelled population-level relationships between behaviour and
perceived stress in a simulated office, our approach explicitly models *intra-individual change* and adds application-usage and
work/idle context, while remaining fully unobtrusive (no wearable) and privacy-preserving (no content capture).

### 3.2 System architecture
See `docs/architecture.md` (Figure 1: data flow; Figure 2: component diagram). Layers: monitoring (JNativeHook/JNA), feature
extraction (Java), storage (MySQL), personal baseline engine (Java, mirrored in Python), ML service (FastAPI, scikit-learn/XGBoost,
SHAP), recommendation engine, JavaFX dashboard.

### 3.3 Data collection
Behavioural metadata only, aggregated per 60-s window (Table 1 feature list). Session labels are obtained from a short self-report
at the end of each session **[instrument: TBP, e.g. single-item VAS or PSS-4 — confirm with ethics reviewer]**. A DEMO mode with
clearly-labelled synthetic data is used exclusively for software demonstration.

### 3.4 Feature extraction
26 absolute features (10 keyboard, 10 mouse, 6 application/work) plus one-hot application category; definitions in
`docs/architecture.md` §1.5. Error rate is a correction-based proxy because text is never captured.

### 3.5 Personal baseline engine
Calibration on the first three sessions; profile = mean, median, SD, IQR fences per feature; runtime features
`dev_X = (X − μ)/|μ|`, `z_X = (X − μ)/σ` for nine baseline features (Equation 1–2). The same procedure is applied per participant
during offline training to avoid train/serve skew.

### 3.6 Machine learning model
Six classifiers compared (LR, DT, RF, SVM, XGBoost, MLP) under participant-aware 5-fold GroupKFold cross-validation with scaling
fitted inside each fold. Selection criterion: macro-F1. The deployed model produces class probabilities from which a 0–100 stress
score is derived as the expected class ordinal.

### 3.7 Explainable AI
SHAP TreeExplainer attributions for the predicted class are converted into natural-language factors relative to the personal
baseline and displayed in the dashboard; global importance is reported via impurity, permutation and mean |SHAP| (Experiment 4).

### 3.8 Recommendation engine
Rule-based, triggered by predicted class, continuous-work duration and baseline deviations (rules in `docs/methodology.md` §2.7).
Optionally re-phrased by an LLM (Gemini free tier) without transmitting behavioural data.

### 3.9 Workflow
Consent → calibration → monitoring windows → predict/explain/recommend → self-report → history/report → export → retrain.

## 4. Implementation / Experimental Setup

### 4.1 Hardware requirements
Any laptop with ≥ 8 GB RAM, dual-core CPU; no GPU or wearable required. **[Record the actual machine used: TBP]**

### 4.2 Software requirements
Java 17, JavaFX 21, Maven 3.9, Python 3.10+, MySQL 8 (or H2), libraries listed in `docs/architecture.md` §1.4.

### 4.3 Dataset
(a) Public data for pipeline validation: **[TBP — e.g. SWELL-KW if access is granted; cite Koldijk et al. 2014]**.
(b) Custom dataset: **[N = TBP]** consenting volunteers, **[TBP]** sessions each, **[TBP]** 60-s windows, label distribution **[TBP]**.
Ethics approval reference **[TBP]**.

### 4.4 Tools and technologies
As in 4.2 plus JNativeHook, JNA, HikariCP, Gson, scikit-learn, XGBoost, SHAP, FastAPI, matplotlib.

### 4.5 Implementation procedure
Steps 1–12 in `docs/setup.md`.

## 5. Results and Discussion

### 5.1 Model performance
**[TBP from exp1_model_comparison.csv]** Best model **[TBP]** achieved accuracy **[TBP]**, macro-F1 **[TBP]**, ROC-AUC **[TBP]**;
confusion matrix in Figure **[TBP]** (`exp1_confusion_matrix.png`).

### 5.2 Algorithm comparison
Table **[TBP]**. Discuss tree ensembles vs linear/SVM; note fold-wise variance.

### 5.3 Feature importance
Figure **[TBP]** (`exp4_shap_summary_high.png`). Report which modality dominates; compare with the base paper's finding that
mouse and keyboard features outperformed HRV.

### 5.4 Personalised vs generic prediction
Table **[TBP]** (`exp3_personalised_vs_generic.csv`) and feature-group ablation (`exp2_feature_groups.csv`). State the Δ macro-F1
and whether it is consistent across models; report honestly if the gain is small or absent.

### 5.5 Discussion
Compare with the base paper's macro-F1 of 0.625 for perceived stress (different data, so not directly comparable);
limitations: small N, self-report noise, correction-based error proxy, laptop-only, no physiological ground truth;
threats to validity: label imbalance, participant heterogeneity, calibration during already-stressed periods.

## 6. Conclusion
Summarise the research contribution (personalised multimodal behavioural stress prediction with application-usage context and
interpretable outputs), the research gap addressed (person-specific normalisation absent from population-level models;
application-usage rarely modelled; lack of deployable privacy-preserving desktop tools), limitations, and future scope
(adaptive baselines, on-device learning, longitudinal validation, physiological fusion when consented, sequence models over windows).
Patentability is not claimed; it depends on prior-art search and legal examination.

## References (verify and complete)
1. Naegelin M. et al. An interpretable machine learning approach to multimodal stress detection in a simulated office environment. J Biomed Inform. 2023;139:104299.
2. Koldijk S. et al. The SWELL knowledge work dataset for stress and user modeling research. ACM ICMI 2014.
3. Naegelin M. et al. Computer mouse movements as an indicator of work stress: longitudinal observational field study. JMIR 2021;23(4):e27121.
4. Lundberg S.M., Lee S.-I. A unified approach to interpreting model predictions. NeurIPS 2017.
5. Chen T., Guestrin C. XGBoost: a scalable tree boosting system. KDD 2016.
6. Cohen S. et al. A global measure of perceived stress. J Health Soc Behav 1983 (PSS).
