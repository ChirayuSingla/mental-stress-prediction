# Viva questions and model answers

**Q1. How does your work extend the base paper rather than reproduce it?**
The base paper models population-level relationships between mouse/keyboard/HRV features and perceived stress in a simulated office.
We drop the physiological channel, add application-usage and work/idle context, introduce a personal baseline that converts every
feature into a deviation from the user's own normal behaviour, and deliver it as a privacy-preserving desktop tool with per-window
explanations and recommendations.

**Q2. Why is the personal baseline important?**
Typing 40 WPM is normal for one person and a 30 % slowdown for another. Population thresholds confound trait (who you are) with state
(how stressed you are now). Deviation features isolate the state component.

**Q3. How do you prevent data leakage?**
Baseline statistics use only calibration sessions; scaling is fitted inside each CV fold; GroupKFold ensures a participant's windows
never appear in both training and test folds.

**Q4. Why GroupKFold instead of random K-fold?**
Windows from the same person are strongly correlated; random splits let the model recognise the person, inflating accuracy that would
not transfer to new users.

**Q5. How is the stress score computed?**
Expected value of the class ordinal: 100 × (0·P_low + 0.5·P_mod + 1·P_high). It is a convenience number, not a measurement.

**Q6. Is this a medical diagnosis?**
No. It is a behavioural prediction/research tool; labels are self-reported perceived stress; fatigue and burnout are indicators.

**Q7. What is stored about what I type?**
Only timestamps, counts and whether a key was a correction key. Key codes are used transiently and discarded; no characters, no window titles.

**Q8. Why REST instead of ONNX for Java–Python integration?**
SHAP explanations, baseline profiles and the recommender live in Python; ONNX would export only the classifier.

**Q9. What if the ML service is down?**
The Java app falls back to a clearly-labelled heuristic based on baseline deviations so the demo does not crash.

**Q10. How do you measure typing errors without the text?**
Correction-key rate (Backspace/Delete ÷ keystrokes) is a proxy; we state this limitation explicitly.

**Q11. Which public datasets did you consider?**
SWELL-KW (closest, has application switching), WESAD (physiology only), CMU keystroke benchmark and Balabit mouse data (no stress labels).
No dataset was invented.

**Q12. What are the main limitations?**
Small N, noisy self-reports, error-rate proxy, calibration may occur during stressful periods, no physiological ground truth.

**Q13. Can the personalised model be patented?**
Patentability depends on a prior-art search and legal examination; related work already explores personalised stress models, so we
do not claim it.

**Q14. How does the burnout indicator work?**
A longitudinal score combining the fraction of high-stress sessions, the trend slope, long sessions and few breaks over the last
ten sessions; labelled Low/Elevated/High indicator.

**Q15. How would you validate the system further?**
Longitudinal field study with more participants, comparison of instruments (VAS vs PSS), adaptive baselines, sequence models across windows.
