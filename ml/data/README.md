# Dataset strategy

No dataset is invented in this project. Three sources are supported:

## 1. Public datasets for initial model development (verify licence/access before use)
| Dataset | Content | Why relevant | Access |
|---|---|---|---|
| **SWELL-KW** (Koldijk et al., 2014, ACM ICMI) | Computer interaction (keyboard/mouse logs, application switching), facial expression, posture, physiology; conditions: neutral / time pressure / interruptions; NASA-TLX & self-report | Closest public analogue to our feature set (includes application-switching) | Radboud University data repository — request access |
| **WESAD** (Schmidt et al., 2018) | Wearable physiology under stress/amusement | Physiological comparison only (no keyboard/mouse) | Public download |
| **CMU Keystroke Dynamics Benchmark** (Killourhy & Maxion, 2009) | Key hold / latency timings | Keystroke-timing feature validation (no stress label) | Public download |
| **Balabit Mouse Dynamics Challenge** (2016) | Mouse trajectories | Mouse-feature extraction validation (no stress label) | GitHub |

> Verify each dataset's current URL, licence and citation before including it in the paper.

## 2. Custom research dataset (real-data mode)
Collected by the Java collector from **consenting volunteer participants**. Schema = `behavior_features`
table in `database/schema.sql` plus a self-reported label captured via the in-app questionnaire
(recommended: PSS-4 or a single-item stress VAS; confirm with your ethics reviewer which instrument is
acceptable). Export with Reports → CSV export.

Required columns (identical to the synthetic file so the same pipeline runs on both):
```
participant_id, session_id, timestamp, window_index,
typing_speed_wpm, keystrokes_per_min, avg_key_hold_ms, avg_key_latency_ms, backspace_rate,
error_rate, pause_count, avg_pause_ms, burst_count, typing_rhythm_cv,
mouse_distance_px, mouse_speed_px_s, mouse_accel_px_s2, click_count, click_rate,
double_click_rate, avg_click_distance_px, mouse_idle_s, direction_changes, mouse_speed_cv,
app_switch_count, app_switch_rate, active_app_category, time_in_app_s,
work_duration_s, idle_duration_s, continuous_work_s,
stress_label
```

## 3. Synthetic DEMO data (`synthetic/generate_synthetic.py`)
Only for demonstrating that the pipeline runs end-to-end. Output files carry an `is_synthetic=1`
column and every plot is watermarked. **Never report metrics from synthetic data as experimental results.**
