"""Feature definitions for the SWELL-KW multimodal dataset and runtime API.

SWELL-KW Computer Interaction Features:
- Keyboard: 9 features
- Mouse: 7 features (including SnMouseDistance)
- Application: 2 features
Total: 18 multimodal behavioral features.
"""

SWELL_KEYBOARD = [
    "SnKeyStrokes",
    "SnChars",
    "SnSpecialKeys",
    "SnDirectionKeys",
    "SnErrorKeys",
    "SnShortcutKeys",
    "SnSpaces",
    "CharactersRatio",
    "ErrorKeyRatio",
]

SWELL_MOUSE = [
    "SnMouseAct",
    "SnLeftClicked",
    "SnRightClicked",
    "SnDoubleClicked",
    "SnWheel",
    "SnDragged",
    "SnMouseDistance",
]

SWELL_MOUSE_NO_DIST = [f for f in SWELL_MOUSE if f != "SnMouseDistance"]

SWELL_APP = [
    "SnAppChange",
    "SnTabfocusChange",
]

# Full 18 SWELL Computer Interaction features
SWELL_ALL = SWELL_KEYBOARD + SWELL_MOUSE + SWELL_APP

# 17 features without SnMouseDistance (which is flagged as potentially unreliable)
SWELL_ALL_NO_MOUSE_DIST = SWELL_KEYBOARD + SWELL_MOUSE_NO_DIST + SWELL_APP

TARGET = "Stress"  # raw workbook name; processed target is `stress`
# Foreground process polling cannot observe in-app tab focus. Native mouse motion
# events also have a different frequency from uLog's aggregate mouse actions.
# Exclude both from deployment instead of inventing measurements.
DESKTOP_ALL = [f for f in SWELL_ALL if f not in {"SnTabfocusChange", "SnMouseAct"}]
DESKTOP_NO_DIST = [f for f in DESKTOP_ALL if f != "SnMouseDistance"]

# Baseline calculation functions and features
def deviation_name(f: str) -> str:
    return f"dev_{f}"


def zscore_name(f: str) -> str:
    return f"z_{f}"


BASELINE_FEATURES = [
    "typing_speed_wpm",
    "error_rate",
    "avg_key_latency_ms",
    "mouse_speed_px_s",
    "click_rate",
    "app_switch_rate",
    "idle_duration_s",
    "continuous_work_s",
    "mouse_speed_cv",
]

# ABSOLUTE alias mapping to SWELL_ALL
ABSOLUTE = SWELL_ALL
DEVIATION = [deviation_name(f) for f in BASELINE_FEATURES] + [zscore_name(f) for f in BASELINE_FEATURES]
LABELS = {0: "Low Stress", 1: "Moderate Stress", 2: "High Stress"}
APP_CATEGORY_COL = "active_app_category"
APP_CATEGORIES = ["IDE", "BROWSER", "DOCUMENT", "COMMUNICATION", "ENTERTAINMENT", "OTHER"]

# Feature groups for ablation studies
FEATURE_GROUPS = {
    "A_keyboard": SWELL_KEYBOARD,
    "B_mouse_full": SWELL_MOUSE,
    "B_mouse_no_dist": SWELL_MOUSE_NO_DIST,
    "C_app": SWELL_APP,
    "D_keyboard_mouse": SWELL_KEYBOARD + SWELL_MOUSE,
    "D_keyboard_mouse_no_dist": SWELL_KEYBOARD + SWELL_MOUSE_NO_DIST,
    "E_multimodal_full": SWELL_ALL,
    "E_multimodal_no_dist": SWELL_ALL_NO_MOUSE_DIST,
}

# Human-readable labels for SHAP explanations and UI display
FEATURE_DISPLAY_NAMES = {
    "SnMouseAct": "Mouse activity count",
    "SnLeftClicked": "Left mouse clicks",
    "SnRightClicked": "Right mouse clicks",
    "SnDoubleClicked": "Double clicks",
    "SnWheel": "Scroll wheel events",
    "SnDragged": "Mouse drag operations",
    "SnMouseDistance": "Mouse travel distance (px)",
    "SnKeyStrokes": "Total keystrokes",
    "SnChars": "Character keys typed (a-z)",
    "SnSpecialKeys": "Special keys typed",
    "SnDirectionKeys": "Arrow / navigation keys",
    "SnErrorKeys": "Correction keys (backspace/delete)",
    "SnShortcutKeys": "Shortcut key combinations",
    "SnSpaces": "Spacebar key presses",
    "CharactersRatio": "Character-to-keystroke ratio",
    "ErrorKeyRatio": "Typing error ratio",
    "SnAppChange": "Application window switches",
    "SnTabfocusChange": "Focus / tab changes",
}

# Legacy alias mappings for backwards compatibility if needed
LEGACY_MAPPINGS = {
    "typing_speed_wpm": "SnKeyStrokes",
    "keystrokes_per_min": "SnKeyStrokes",
    "backspace_rate": "ErrorKeyRatio",
    "error_rate": "ErrorKeyRatio",
    "mouse_distance_px": "SnMouseDistance",
    "click_count": "SnLeftClicked",
    "click_rate": "SnLeftClicked",
    "double_click_rate": "SnDoubleClicked",
    "app_switch_count": "SnAppChange",
    "app_switch_rate": "SnAppChange",
}
