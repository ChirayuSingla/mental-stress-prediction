"""Rule-based recommendation engine (+ optional Gemini free-tier re-phrasing, never sending raw data)."""
import json
from typing import Dict, List, Optional


class RuleBasedRecommender:
    def recommend(self, stress_class: int, stress_score: float, fatigue: float, burnout: float,
                  f: Dict[str, float], has_baseline: bool) -> List[str]:
        recs: List[str] = []
        cont_min = f.get("continuous_work_s", 0) / 60
        if stress_class == 2 and cont_min >= 45:
            recs.append("High stress with a long continuous session — take a 5–10 minute break away from the screen.")
        elif stress_class == 2:
            recs.append("Stress indicators are high — pause for a few slow breaths or a short walk before continuing.")
        if f.get("dev_error_rate", 0) > 0.30:
            recs.append("Typing errors are well above your usual level — a short rest usually restores accuracy.")
        if cont_min >= 90:
            recs.append("Over 90 minutes of continuous work — stand up, stretch and move for a couple of minutes.")
        if f.get("dev_app_switch_rate", 0) > 0.40 or f.get("app_switch_rate", 0) > 8:
            recs.append("Application switching is unusually frequent — consider single-tasking on one window for the next block.")
        if f.get("dev_typing_speed_wpm", 0) < -0.25:
            recs.append("Typing speed has dropped noticeably from your baseline — this often signals fatigue; consider a break.")
        if fatigue >= 60:
            recs.append("Fatigue indicators are elevated — schedule a longer break or finish the current task and stop.")
        if burnout >= 65:
            recs.append("Stress has been persistently high across recent sessions — plan a longer recovery period and consider talking to someone you trust.")
        if stress_class == 0 and not recs:
            recs.append("Behaviour is within your normal range — keep the current pace and remember regular micro-breaks.")
        if not has_baseline:
            recs.append("Calibration incomplete — recommendations become personalised after the baseline sessions.")
        return recs[:4]


class GeminiRephraser:
    """Optionally re-phrases rule outputs with the Gemini API free tier. Sends ONLY the rule text and a few
    aggregate numbers — never keystrokes, application names or raw behavioural logs. Falls back silently."""
    URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    def __init__(self, api_key: Optional[str]):
        self.enabled = bool(api_key); self.key = api_key

    def rephrase(self, recs: List[str], context: Dict) -> List[str]:
        if not self.enabled or not recs:
            return recs
        try:
            import requests
            prompt = ("You are a workplace-wellness assistant. Rewrite the following recommendations as 2-4 short, warm, "
                      "non-medical sentences. Do not diagnose. Return a JSON list of strings only.\n"
                      f"Context: {json.dumps(context)}\nRecommendations: {json.dumps(recs)}")
            r = requests.post(f"{self.URL}?key={self.key}", json={"contents": [{"parts": [{"text": prompt}]}]}, timeout=8)
            text = r.json()["candidates"][0]["content"]["parts"][0]["text"].strip().strip("`")
            text = text[text.find("["):text.rfind("]") + 1]
            out = json.loads(text)
            return [str(x) for x in out][:4] if isinstance(out, list) and out else recs
        except Exception:
            return recs
