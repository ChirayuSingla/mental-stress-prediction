import os, sys, subprocess, numpy as np, pandas as pd
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from preprocessing.baseline import compute_baseline_profile, apply_profile, add_personal_deviation_features
from preprocessing.features import BASELINE_FEATURES, deviation_name
from api.server import fatigue_score, burnout_indicator
from api.recommendation import RuleBasedRecommender


def _df():
    rows = []
    for p in ("P1", "P2"):
        for s in range(1, 6):
            for w in range(5):
                r = {f: float(10 + w) for f in BASELINE_FEATURES}
                r.update(participant_id=p, session_id=f"{p}_S{s}", stress_label=w % 3)
                rows.append(r)
    return pd.DataFrame(rows)


def test_baseline_uses_only_first_sessions():
    df = _df(); out, prof = add_personal_deviation_features(df, baseline_sessions=3)
    assert prof["P1"]["typing_speed_wpm"]["n"] == 15
    assert deviation_name("typing_speed_wpm") in out.columns
    assert np.isclose(out[deviation_name("typing_speed_wpm")].mean(), 0, atol=1e-9)


def test_deviation_sign():
    df = _df(); prof = compute_baseline_profile(df)
    row = df.iloc[[0]].copy(); row["typing_speed_wpm"] = 100
    assert apply_profile(row, prof)[deviation_name("typing_speed_wpm")].iloc[0] > 0


def test_fatigue_bounds():
    assert 0 <= fatigue_score({"continuous_work_s": 99999, "dev_typing_speed_wpm": -1, "dev_error_rate": 2}) <= 100
    assert fatigue_score({}) == 0


def test_burnout_requires_history():
    assert burnout_indicator([50], [], [])[1] == 0
    label, score = burnout_indicator([80] * 6, [300] * 6, [0] * 6)
    assert score > 65 and "High" in label


def test_recommender_never_empty():
    assert RuleBasedRecommender().recommend(0, 10, 0, 0, {}, True)


def test_synthetic_generator_runs(tmp_path):
    out = tmp_path / "d.csv"
    subprocess.check_call([sys.executable, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                                                       "synthetic", "generate_synthetic.py"),
                           "--participants", "2", "--sessions", "4", "--windows", "3", "--out", str(out)])
    assert pd.read_csv(out)["is_synthetic"].all()
