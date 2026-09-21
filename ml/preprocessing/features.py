"""Feature definitions shared across training, experiments and the API."""

KEYBOARD = ["typing_speed_wpm", "keystrokes_per_min", "avg_key_hold_ms", "avg_key_latency_ms",
            "backspace_rate", "error_rate", "pause_count", "avg_pause_ms", "burst_count", "typing_rhythm_cv"]
MOUSE = ["mouse_distance_px", "mouse_speed_px_s", "mouse_accel_px_s2", "click_count", "click_rate",
         "double_click_rate", "avg_click_distance_px", "mouse_idle_s", "direction_changes", "mouse_speed_cv"]
APP = ["app_switch_count", "app_switch_rate", "time_in_app_s", "work_duration_s", "idle_duration_s",
       "continuous_work_s"]
APP_CATEGORY_COL = "active_app_category"
APP_CATEGORIES = ["IDE", "BROWSER", "DOCUMENT", "COMMUNICATION", "ENTERTAINMENT", "OTHER"]

# Features for which personal deviation is computed (the personal-baseline contribution)
BASELINE_FEATURES = ["typing_speed_wpm", "error_rate", "avg_key_latency_ms", "mouse_speed_px_s",
                     "click_rate", "app_switch_rate", "idle_duration_s", "continuous_work_s", "mouse_speed_cv"]

ABSOLUTE = KEYBOARD + MOUSE + APP
LABELS = {0: "Low Stress", 1: "Moderate Stress", 2: "High Stress"}


def deviation_name(f: str) -> str:
    return f"dev_{f}"


def zscore_name(f: str) -> str:
    return f"z_{f}"


DEVIATION = [deviation_name(f) for f in BASELINE_FEATURES] + [zscore_name(f) for f in BASELINE_FEATURES]

FEATURE_GROUPS = {
    "A_keyboard": KEYBOARD,
    "B_mouse": MOUSE,
    "C_app": APP,
    "D_keyboard_mouse": KEYBOARD + MOUSE,
    "E_keyboard_mouse_app": ABSOLUTE,
    "F_all_plus_baseline": ABSOLUTE + DEVIATION,
}
