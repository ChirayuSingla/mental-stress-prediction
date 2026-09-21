-- Minimal sample rows (clearly marked demo) to verify the schema and dashboard queries.
USE stress_prediction;
INSERT INTO users (participant_code, consent_given, consent_at) VALUES ('DEMO001', 1, NOW());
INSERT INTO sessions (user_id, started_at, ended_at, is_calibration, is_demo) VALUES
 (1, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY + INTERVAL 2 HOUR, 1, 1),
 (1, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY + INTERVAL 2 HOUR, 1, 1),
 (1, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY + INTERVAL 2 HOUR, 1, 1);
INSERT INTO behavior_features (session_id, window_start, typing_speed_wpm, keystrokes_per_min, avg_key_hold_ms,
  avg_key_latency_ms, backspace_rate, error_rate, pause_count, avg_pause_ms, burst_count, typing_rhythm_cv,
  mouse_distance_px, mouse_speed_px_s, mouse_accel_px_s2, click_count, click_rate, double_click_rate,
  avg_click_distance_px, mouse_idle_s, direction_changes, mouse_speed_cv, app_switch_count, app_switch_rate,
  active_app_category, time_in_app_s, work_duration_s, idle_duration_s, continuous_work_s, stress_label)
VALUES (1, NOW() - INTERVAL 3 DAY, 55, 275, 95, 180, 0.04, 0.04, 4, 2500, 6, 0.25, 15000, 400, 700, 15, 15, 1.5,
        200, 10, 45, 0.4, 3, 3, 'IDE', 20, 50, 10, 600, 0);
