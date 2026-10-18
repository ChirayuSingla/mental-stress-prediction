import numpy as np
import pandas as pd
from training.nested import evaluate_nested, deployment_candidates, select_candidate
from preprocessing.features import DESKTOP_NO_DIST

def observations():
    rng = np.random.RandomState(7)
    rows = []
    for p in range(6):
        for block, condition in enumerate(["N", "I", "T"]):
            row = dict(zip(DESKTOP_NO_DIST, rng.uniform(.01, .8, len(DESKTOP_NO_DIST))))
            row.update(participant_id=f"P{p}", block=block, condition=condition, stress=float(p))
            rows.append(row)
    return pd.DataFrame(rows)

def test_nested_outer_labels_do_not_select_or_fit_the_first_fold():
    df = observations()
    candidates = {name: model for name, model in deployment_candidates().items()
                  if name in ["DummyMean", "DummyMedian", "Ridge_100"]}
    report, pred = evaluate_nested(df, outer_folds=3, inner_folds=2, candidates=candidates)
    heldout = report["folds"][0]["test_participants"]
    changed = df.copy()
    changed.loc[changed.participant_id.isin(heldout), "stress"] = 10.
    report2, pred2 = evaluate_nested(changed, outer_folds=3, inner_folds=2, candidates=candidates)
    assert report["folds"][0]["inner_ranking"] == report2["folds"][0]["inner_ranking"]
    assert report["folds"][0]["selected_model"] == report2["folds"][0]["selected_model"]
    mask = df.participant_id.isin(heldout)
    assert np.array_equal(pred["NestedSelection"][mask], pred2["NestedSelection"][mask])
    for fold in report["folds"]:
        assert not set(fold["train_participants"]) & set(fold["test_participants"])

def test_baseline_is_eligible_instead_of_forcing_a_learned_model():
    df = observations()
    winner, pipe, ranking = select_candidate(df[DESKTOP_NO_DIST], df.stress.to_numpy(),
                                             df.participant_id.to_numpy(), deployment_candidates(), 3)
    assert winner in ["DummyMean", "DummyMedian"]
    assert sum(row["selected"] for row in ranking) == 1
