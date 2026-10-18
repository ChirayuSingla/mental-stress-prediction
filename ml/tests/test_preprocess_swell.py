import numpy as np
import pandas as pd
import pytest
from preprocessing.features import SWELL_ALL
from preprocessing.preprocess_swell import preprocess_swell

def workbook(tmp_path, conflict=False):
    rows=[]
    for stress, chars in [(2., 10.), (3. if conflict else 2., np.nan)]:
        row={f: 0. for f in SWELL_ALL}
        row.update(PP="TEST", Blok=1, Condition="N", Stress=stress, timestamp="12:00", SnChars=chars,
                   SnKeyStrokes=20., CharactersRatio=.5)
        rows.append(row)
    r=rows[0].copy();r.update(Condition="R",Stress=np.nan);rows.append(r)
    path=tmp_path/"fixture.xlsx"
    pd.DataFrame(rows).to_excel(path,sheet_name="SWELLdata",index=False)
    return path

def test_aggregation_excludes_relaxation_and_missing_ratio_sources(tmp_path):
    out=preprocess_swell(str(workbook(tmp_path)),str(tmp_path/"out"),True)
    assert len(out)==1 and out.stress.iloc[0]==2.
    assert out.SnChars.iloc[0]==10. and out.CharactersRatio.iloc[0]==.5
    minutes=pd.read_csv(tmp_path/"out/processed/swell_minutes_clean.csv")
    assert len(minutes)==2 and minutes.CharactersRatio.isna().sum()==1
    assert set(minutes.columns).issubset(set(SWELL_ALL+["participant_id","block","condition","timestamp","stress"]))

def test_conflicting_group_questionnaires_are_rejected(tmp_path):
    with pytest.raises(ValueError,match="Conflicting"):
        preprocess_swell(str(workbook(tmp_path,True)),str(tmp_path/"out"))

def test_missing_workbook_fails_without_synthetic_fallback(tmp_path):
    with pytest.raises(FileNotFoundError):
        preprocess_swell(str(tmp_path/"missing.xlsx"),str(tmp_path/"out"))
