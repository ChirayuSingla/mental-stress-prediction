# 6. Base-paper comparison

Base paper (verified from the publisher abstract, Sept 2026 search): Naegelin et al., *J. Biomed. Inform.* 139:104299 (2023).
N = 90, simulated group-office experiment; mouse, keyboard and heart-rate-variability features; three levels of perceived stress,
valence and arousal; SVM, random forest and gradient boosting with 10-fold CV; SHAP value plots; best macro-F1 for stress 0.625
(mouse + keyboard). **Re-verify every cell against the full text before submission.**

| Feature | Base paper | Proposed system |
|---|---|---|
| Keyboard data | Yes | Yes |
| Mouse data | Yes | Yes |
| Physiological data (HRV) | Yes | No (deliberately unobtrusive, no wearable) |
| Application usage / switching | Not among the modelled feature groups (verify in full text) | Yes (category, switch rate, dwell time) |
| Work / idle duration | Not modelled as features (verify) | Yes |
| Personal baseline deviation features | Population-level models with 10-fold CV (verify whether any per-subject normalisation was applied) | Yes — core contribution |
| Real-time desktop monitoring | Lab experiment logging | Yes (JavaFX background monitor) |
| Stress prediction (3 levels) | Yes (perceived stress, valence, arousal) | Yes (stress only as primary target) |
| Fatigue indicator | No | Yes (transparent composite indicator) |
| Burnout-risk indicator | No | Yes (longitudinal indicator, not diagnosis) |
| Explainability | SHAP | SHAP + per-window human-readable factors in the UI |
| Recommendations | Discussed as future just-in-time interventions | Rule-based, personalised engine |
| Privacy-preserving collection | Lab logging | Metadata-only, consent, deletion |
| Java implementation | No (research pipeline) | Yes |
| Participant-aware evaluation | 10-fold CV | GroupKFold by participant |

## Related prior art to cite (verify each)
* Koldijk et al., 2014 — SWELL knowledge-work dataset (computer interaction incl. application switching).
* Naegelin et al., 2021, *JMIR* — mouse movements as an indicator of work stress (field study).
* Follow-up field study by the same group (preprint 2025, medRxiv) contrasting one-fits-all vs personalised models — directly relevant to the personal-baseline claim; check publication status.
* Vizer, Zhou & Sears, 2009 — keystroke/linguistic features for stress detection.
* Sun et al., 2014 (MouStress) — mouse dynamics under stress.

Patentability of the personalised-baseline approach depends on a prior-art search and legal examination; do not claim it.
