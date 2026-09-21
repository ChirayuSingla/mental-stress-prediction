"""
DEMO-ONLY synthetic behavioural data generator.

Generates clearly-labelled synthetic feature windows with per-participant baselines so that the
ML pipeline, dashboard and experiments can be demonstrated without real participants.
The output must NEVER be reported as real experimental results.
"""
import argparse
import numpy as np
import pandas as pd
from datetime import datetime, timedelta

APP_CATEGORIES = ["IDE", "BROWSER", "DOCUMENT", "COMMUNICATION", "ENTERTAINMENT", "OTHER"]


def sample_participant_baseline(rng):
    """Each synthetic participant has their own 'normal' behaviour (why personal baselines matter)."""
    return {
        "typing_speed_wpm": rng.uniform(30, 75),
        "avg_key_hold_ms": rng.uniform(70, 130),
        "avg_key_latency_ms": rng.uniform(120, 260),
        "error_rate": rng.uniform(0.02, 0.08),
        "mouse_speed_px_s": rng.uniform(250, 700),
        "click_rate": rng.uniform(5, 25),
        "app_switch_rate": rng.uniform(1, 6),
        "idle_fraction": rng.uniform(0.05, 0.25),
    }


def generate_window(rng, base, stress, cont_work_s):
    """stress: 0 low, 1 moderate, 2 high. Directional effects are literature-informed assumptions
    (slower/more erratic typing, more errors, more erratic mouse, more switching) plus noise."""
    s = stress
    fatigue = min(cont_work_s / (3 * 3600), 1.0)
    noise = lambda sd: rng.normal(0, sd)

    typing_speed = base["typing_speed_wpm"] * (1 - 0.10 * s - 0.08 * fatigue) * (1 + noise(0.08))
    kpm = typing_speed * 5 * (1 + noise(0.05))
    hold = base["avg_key_hold_ms"] * (1 + 0.06 * s + noise(0.06))
    latency = base["avg_key_latency_ms"] * (1 + 0.12 * s + 0.05 * fatigue + noise(0.08))
    err = base["error_rate"] * (1 + 0.35 * s + 0.2 * fatigue) * (1 + noise(0.15))
    backspace = err * rng.uniform(0.8, 1.3)
    pause_count = max(0, int(rng.poisson(4 + 3 * s)))
    avg_pause = rng.uniform(1500, 4000) * (1 + 0.1 * s)
    burst = max(0, int(rng.poisson(6 - s)))
    rhythm_cv = 0.25 + 0.08 * s + noise(0.03)

    mouse_speed = base["mouse_speed_px_s"] * (1 + 0.15 * s + noise(0.1))
    distance = mouse_speed * 60 * rng.uniform(0.3, 0.7)
    accel = mouse_speed * (1.5 + 0.5 * s) * (1 + noise(0.1))
    click_rate = base["click_rate"] * (1 + 0.2 * s + noise(0.12))
    clicks = max(0, int(click_rate * (1 + noise(0.1))))
    dbl = click_rate * rng.uniform(0.05, 0.2)
    click_dist = rng.uniform(80, 400) * (1 + 0.1 * s)
    mouse_idle = max(0, 60 * base["idle_fraction"] * (1 - 0.2 * s) + noise(4))
    dir_changes = max(0, int(rng.poisson(40 + 25 * s)))
    mouse_cv = 0.4 + 0.12 * s + noise(0.04)

    switch_rate = base["app_switch_rate"] * (1 + 0.4 * s + noise(0.15))
    switches = max(0, int(switch_rate * (1 + noise(0.1))))
    cat_p = np.array([0.35, 0.25, 0.15, 0.1 + 0.08 * s, 0.05, 0.1]); cat_p = cat_p / cat_p.sum()
    category = rng.choice(APP_CATEGORIES, p=cat_p)
    time_in_app = max(5, 60 / max(switches, 1) * (1 + noise(0.2)))
    idle_s = min(60, max(0, 60 * base["idle_fraction"] * (1 - 0.3 * s) + noise(5)))
    work_s = 60 - idle_s

    return {
        "typing_speed_wpm": max(typing_speed, 5), "keystrokes_per_min": max(kpm, 20),
        "avg_key_hold_ms": hold, "avg_key_latency_ms": latency,
        "backspace_rate": max(backspace, 0), "error_rate": max(err, 0),
        "pause_count": pause_count, "avg_pause_ms": avg_pause, "burst_count": burst,
        "typing_rhythm_cv": max(rhythm_cv, 0.05),
        "mouse_distance_px": distance, "mouse_speed_px_s": mouse_speed, "mouse_accel_px_s2": accel,
        "click_count": clicks, "click_rate": click_rate, "double_click_rate": dbl,
        "avg_click_distance_px": click_dist, "mouse_idle_s": mouse_idle,
        "direction_changes": dir_changes, "mouse_speed_cv": max(mouse_cv, 0.05),
        "app_switch_count": switches, "app_switch_rate": switch_rate,
        "active_app_category": category, "time_in_app_s": time_in_app,
        "work_duration_s": work_s, "idle_duration_s": idle_s, "continuous_work_s": cont_work_s,
        "stress_label": s,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--participants", type=int, default=20)
    ap.add_argument("--sessions", type=int, default=8)
    ap.add_argument("--windows", type=int, default=30, help="1-minute windows per session")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--label-noise", type=float, default=0.12, help="fraction of windows whose label is randomly flipped (self-report noise)")
    ap.add_argument("--out", default="data/demo_synthetic.csv")
    args = ap.parse_args()

    rng = np.random.default_rng(args.seed)
    rows = []
    start = datetime(2026, 1, 5, 9, 0, 0)
    for p in range(1, args.participants + 1):
        base = sample_participant_baseline(rng)
        for sess in range(1, args.sessions + 1):
            p_stress = [0.7, 0.2, 0.1] if sess <= 3 else [0.35, 0.35, 0.30]
            session_stress = rng.choice([0, 1, 2], p=p_stress)
            t0 = start + timedelta(days=sess, hours=int(rng.integers(0, 6)))
            cont = 0
            for w in range(args.windows):
                s = session_stress
                if rng.random() < 0.15:
                    s = int(np.clip(session_stress + rng.choice([-1, 1]), 0, 2))
                row = generate_window(rng, base, s, cont)
                if rng.random() < args.label_noise:  # simulate noisy self-reports
                    row["stress_label"] = int(rng.integers(0, 3))
                cont = 0 if row["idle_duration_s"] > 20 else cont + 60
                row.update({"participant_id": f"P{p:03d}", "session_id": f"P{p:03d}_S{sess:02d}",
                            "timestamp": (t0 + timedelta(minutes=w)).isoformat(), "window_index": w,
                            "is_synthetic": 1})
                rows.append(row)
    df = pd.DataFrame(rows)
    df.to_csv(args.out, index=False)
    print(f"[DEMO] wrote {len(df)} synthetic windows for {args.participants} participants -> {args.out}")
    print("       This file is SYNTHETIC and must not be reported as experimental results.")


if __name__ == "__main__":
    main()
