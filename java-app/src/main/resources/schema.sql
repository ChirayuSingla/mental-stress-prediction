-- Mental Stress Prediction — MySQL 8 schema (normalised, 3NF).
-- No raw keystroke content, application titles or screenshots are ever stored.
CREATE DATABASE IF NOT EXISTS stress_prediction CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE stress_prediction;

CREATE TABLE IF NOT EXISTS users (
  user_id          INT AUTO_INCREMENT PRIMARY KEY,
  participant_code VARCHAR(32)  NOT NULL UNIQUE,          -- pseudonymous code, e.g. P001 (no real name required)
  password_hash    VARCHAR(128) NULL,                     -- PBKDF2 hash, only if local login enabled
  consent_given    TINYINT(1)   NOT NULL DEFAULT 0,
  consent_at       DATETIME     NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sessions (
  session_id     INT AUTO_INCREMENT PRIMARY KEY,
  user_id        INT NOT NULL,
  started_at     DATETIME NOT NULL,
  ended_at       DATETIME NULL,
  is_calibration TINYINT(1) NOT NULL DEFAULT 0,
  is_demo        TINYINT(1) NOT NULL DEFAULT 0,           -- DEMO MODE flag: synthetic session
  break_count    INT NOT NULL DEFAULT 0,
  self_report_stress TINYINT NULL,                        -- 0 low / 1 moderate / 2 high (questionnaire)
  self_report_instrument VARCHAR(32) NULL,                -- e.g. PSS-4, VAS
  CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  INDEX idx_sessions_user_started (user_id, started_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS behavior_features (
  feature_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id        INT NOT NULL,
  window_start      DATETIME NOT NULL,
  window_seconds    INT NOT NULL DEFAULT 60,
  -- keyboard
  typing_speed_wpm   DOUBLE, keystrokes_per_min DOUBLE, avg_key_hold_ms DOUBLE, avg_key_latency_ms DOUBLE,
  backspace_rate     DOUBLE, error_rate DOUBLE, pause_count INT, avg_pause_ms DOUBLE, burst_count INT,
  typing_rhythm_cv   DOUBLE,
  -- mouse
  mouse_distance_px  DOUBLE, mouse_speed_px_s DOUBLE, mouse_accel_px_s2 DOUBLE, click_count INT, click_rate DOUBLE,
  double_click_rate  DOUBLE, avg_click_distance_px DOUBLE, mouse_idle_s DOUBLE, direction_changes INT,
  mouse_speed_cv     DOUBLE,
  -- application / work
  app_switch_count   INT, app_switch_rate DOUBLE, active_app_category VARCHAR(20), time_in_app_s DOUBLE,
  work_duration_s    DOUBLE, idle_duration_s DOUBLE, continuous_work_s DOUBLE,
  stress_label       TINYINT NULL,                       -- self-reported label for this window (if collected)
  CONSTRAINT fk_features_session FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
  INDEX idx_features_session_time (session_id, window_start)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS application_usage (
  usage_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id   INT NOT NULL,
  category     VARCHAR(20) NOT NULL,                      -- IDE / BROWSER / DOCUMENT / COMMUNICATION / ENTERTAINMENT / OTHER
  window_start DATETIME NOT NULL,
  seconds      DOUBLE NOT NULL,
  CONSTRAINT fk_usage_session FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
  INDEX idx_usage_session (session_id, window_start)
) ENGINE=InnoDB;

-- Additive storage: retain exact measured model inputs without changing old rows.
CREATE TABLE IF NOT EXISTS swell_feature_windows (
  feature_id BIGINT PRIMARY KEY,
  feature_json TEXT NOT NULL,
  CONSTRAINT fk_swell_feature FOREIGN KEY (feature_id) REFERENCES behavior_features(feature_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS baseline_profiles (
  profile_id   INT AUTO_INCREMENT PRIMARY KEY,
  user_id      INT NOT NULL,
  feature_name VARCHAR(40) NOT NULL,
  mean_value   DOUBLE NOT NULL,
  median_value DOUBLE NOT NULL,
  std_value    DOUBLE NOT NULL,
  low_value    DOUBLE NOT NULL,
  high_value   DOUBLE NOT NULL,
  sample_count INT NOT NULL,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_baseline_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  UNIQUE KEY uq_baseline_user_feature (user_id, feature_name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS predictions (
  prediction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  feature_id    BIGINT NOT NULL,
  predicted_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  stress_class  TINYINT NOT NULL,
  prob_low      DOUBLE NOT NULL, prob_moderate DOUBLE NOT NULL, prob_high DOUBLE NOT NULL,
  stress_score  DOUBLE NOT NULL,
  fatigue_score DOUBLE NOT NULL,
  burnout_score DOUBLE NOT NULL,
  burnout_label VARCHAR(32) NOT NULL,
  model_name    VARCHAR(40) NOT NULL,
  explanation_json TEXT,                                  -- top factors (feature, contribution, text)
  CONSTRAINT fk_pred_feature FOREIGN KEY (feature_id) REFERENCES behavior_features(feature_id) ON DELETE CASCADE,
  INDEX idx_pred_time (predicted_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS recommendations (
  recommendation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  prediction_id     BIGINT NOT NULL,
  rule_code         VARCHAR(40) NOT NULL,
  message           VARCHAR(400) NOT NULL,
  acknowledged      TINYINT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_rec_prediction FOREIGN KEY (prediction_id) REFERENCES predictions(prediction_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS swell_prediction_inputs (
  prediction_id BIGINT PRIMARY KEY,
  observation_windows INT NOT NULL,
  feature_json TEXT NOT NULL,
  CONSTRAINT fk_swell_prediction FOREIGN KEY (prediction_id) REFERENCES predictions(prediction_id) ON DELETE CASCADE
) ENGINE=InnoDB;
