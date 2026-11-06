import importlib.util
import sys
import json
from pathlib import Path
import pytest
from pydantic import ValidationError

ROOT=Path(__file__).resolve().parents[1]


def load():
    spec=importlib.util.spec_from_file_location('tarot_backend',ROOT/'backend'/'main.py')
    mod=importlib.util.module_from_spec(spec)
    sys.modules[spec.name]=mod
    spec.loader.exec_module(mod)
    return mod


def test_standard_deck():
    cards=json.loads((ROOT/'assets'/'cards.json').read_text(encoding='utf8'))
    assert len(cards)==78
    assert len({c['id'] for c in cards})==78
    assert len([x for x in cards if x['major']])==22
    for suit in 'WCSP':assert len([x for x in cards if x['suit']==suit])==14
    assert all((ROOT/'assets'/f"{c['id']}.svg").exists() for c in cards)


def test_positions_and_duplicate():
    mod=load()
    mod.check_cards('analysis',[
        mod.SelectedCard(card_id='M19',position_key='present',reversed=False),
        mod.SelectedCard(card_id='M17',position_key='obstacle',reversed=True),
        mod.SelectedCard(card_id='C09',position_key='advice',reversed=False)])
    with pytest.raises(ValueError,match='DUPLICATE_CARD'):
        mod.check_cards('analysis',[
            mod.SelectedCard(card_id='M19',position_key='present',reversed=False),
            mod.SelectedCard(card_id='M19',position_key='obstacle',reversed=True),
            mod.SelectedCard(card_id='C09',position_key='advice',reversed=False)])
    with pytest.raises(ValueError,match='INVALID_SPREAD_POSITIONS'):
        mod.check_cards('one',[mod.SelectedCard(card_id='M19',position_key='past',reversed=False)])


def test_consent_required():
    mod=load()
    with pytest.raises(ValidationError):
        mod.ReadingRequest(reading_id='abcd1234',question='工作规划？',spread_id='one',cards=[{'card_id':'M19','position_key':'focus','reversed':False}],consented=False)


def test_no_api_key_fails_closed(monkeypatch):
    mod=load()
    from fastapi import HTTPException
    import asyncio
    monkeypatch.delenv('DEEPSEEK_API_KEY',raising=False)
    with pytest.raises(HTTPException) as exc:
        asyncio.run(mod.deepseek_completion([{'role':'user','content':'hi'}]))
    assert exc.value.status_code==503
