"""星语 Tarot: local-first prototype with a server-side DeepSeek proxy.
Do NOT place the API key in a browser or Android application.
Before deployment, add authentication, durable rate limits, abuse prevention, HTTPS and logging controls.
"""
from __future__ import annotations
import json
import os
from datetime import datetime
from pathlib import Path
from typing import Literal
from collections import defaultdict
from time import monotonic

import httpx
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, model_validator

ROOT = Path(__file__).resolve().parent.parent
load_dotenv(ROOT / '.env')
CARDS = {c['id']: c for c in json.loads((ROOT / 'assets/cards.json').read_text(encoding='utf8'))}
SPREADS = {'one':['focus'],'analysis':['present','obstacle','advice'],'timeline':['past','present','potential']}
POSITION_LABELS = {'focus':'核心提示','present':'现状','obstacle':'障碍','advice':'建议','past':'过去','potential':'可能发展'}
SUIT_CONTEXT={'W':'权杖：创造、动力、主动推进','C':'圣杯：情感、关系、共情','S':'宝剑：思维、沟通、判断','P':'星币：资源、工作、现实投入','M':'大阿尔卡那：重要主题、阶段变化和象征议题'}
MODEL=os.getenv('DEEPSEEK_MODEL','deepseek-flash')
BASE=os.getenv('DEEPSEEK_API_BASE','https://api.deepseek.com').rstrip('/')
app = FastAPI(title='Starweave Tarot API', version='0.1.0', docs_url='/api/docs')

class SelectedCard(BaseModel):
    card_id: str = Field(min_length=3,max_length=3)
    position_key: str = Field(min_length=2,max_length=32)
    reversed: bool

class ReadingRequest(BaseModel):
    reading_id: str = Field(min_length=6,max_length=128)
    question: str = Field(min_length=1,max_length=500)
    theme: str = Field(default='综合',max_length=25)
    spread_id: Literal['one','analysis','timeline']
    consented: Literal[True]
    cards: list[SelectedCard] = Field(min_length=1,max_length=3)
    @model_validator(mode='after')
    def validate_cards(self):
        check_cards(self.spread_id,self.cards)
        return self

class Message(BaseModel):
    role: Literal['user','assistant']
    content: str = Field(min_length=1,max_length=1200)

class FollowupRequest(BaseModel):
    reading_id: str = Field(min_length=6,max_length=128)
    question: str = Field(min_length=1,max_length=500)
    spread_id: Literal['one','analysis','timeline']
    consented: Literal[True]
    cards: list[SelectedCard] = Field(min_length=1,max_length=3)
    messages: list[Message] = Field(min_length=1,max_length=10)
    @model_validator(mode='after')
    def validate_cards(self):
        check_cards(self.spread_id,self.cards)
        return self

def check_cards(spread_id,items):
    expected=SPREADS[spread_id]
    if len(items)!=len(expected):
        raise ValueError('CARD_COUNT_MISMATCH')
    ids=[c.card_id for c in items]
    if len(ids)!=len(set(ids)):
        raise ValueError('DUPLICATE_CARD')
    if any(i not in CARDS for i in ids):
        raise ValueError('UNKNOWN_CARD')
    if [c.position_key for c in items]!=expected:
        raise ValueError('INVALID_SPREAD_POSITIONS')

def card_context(items):
    return [{'card_id':c.card_id,'card_name':CARDS[c.card_id]['name'],
             'position_key':c.position_key,'position_name':POSITION_LABELS[c.position_key],
             'orientation':'逆位' if c.reversed else '正位',
             'suit_context':SUIT_CONTEXT[c.card_id[0]]} for c in items]

SYS='''你是专业、温柔、清楚、尊重用户自主性的塔罗解读助手。采用标准RWS 78张牌及固定牌位。
塔罗是一种象征性自我探索与娱乐，不具有预测未来的客观确定性。
重要规则：绝对不添加、替换、重新抽取卡牌；不得改变正逆位和牌位；不得断言第三人真实心理；不得虚构具体日期、百分比或结果。
逆位不等于坏牌；死神、高塔不意味着确定灾难；重大健康、法律、财务或安全话题不可提供决定性专业指导。
避免恐吓、鼓励反复占卜、制造依赖。建议应当现实可执行，不以 AI 代替用户自主决定。
输出必须为且仅为一个合法 JSON 对象。必须完全按照请求给定的卡牌集合和顺序生成 cards 数组。
JSON结构: {"reading_id":"原ID","headline":"短标题","overview":"80-140字","key_takeaways":["要点一","要点二","要点三"],"cards":[{"card_id":"原ID","position_key":"原牌位","keyword":"关键词","interpretation":"逐牌解释"}],"connections":"跨牌联系，单牌不虚构","actions":["现实行动1","现实行动2"],"reflection_question":"反思问题","disclaimer":"仅供娱乐与自我探索"}。
只输出 JSON，不要Markdown。'''

async def deepseek_completion(messages,structured=True,max_tokens=1700):
    key=os.getenv('DEEPSEEK_API_KEY','').strip()
    if not key or key.startswith('REPLACE_'):
        raise HTTPException(status_code=503,detail={'code':'AI_NOT_CONFIGURED','message':'尚未配置服务端 DeepSeek Key，请先更换并配置新密钥'})
    payload={'model':MODEL,'messages':messages,'max_tokens':max_tokens,'thinking':{'type':'disabled'},'stream':False}
    if structured:
        payload['response_format']={'type':'json_object'}
    try:
        async with httpx.AsyncClient(timeout=48) as client:
            r=await client.post(BASE+'/chat/completions',headers={'Authorization':f'Bearer {key}','Content-Type':'application/json'},json=payload)
        r.raise_for_status()
        body=r.json()
        content=(body['choices'][0]['message']['content'] or '').strip()
        if not content:
            raise ValueError('empty AI output')
        return content
    except Exception:
        # Don't log request body, prompt or key.
        raise HTTPException(status_code=502,detail={'code':'AI_PROVIDER_UNAVAILABLE','message':'DeepSeek 服务暂不可用，请稍后重试'})

# Single-process demonstration limiter. Replace with durable per-account quota in production.
RATE=defaultdict(list)
def rate_limit(request: Request):
    now=monotonic(); addr=request.client.host if request.client else 'unknown'
    RATE[addr]=[t for t in RATE[addr] if now-t<3600]
    if len(RATE[addr])>=20:
        raise HTTPException(status_code=429,detail={'code':'AI_RATE_LIMITED','message':'本小时请求额度已达上限'})
    RATE[addr].append(now)

@app.get('/api/health')
def health():
    key=os.getenv('DEEPSEEK_API_KEY','').strip()
    return {'status':'ok','model':MODEL,'api_configured':bool(key) and not key.startswith('REPLACE_')}

@app.post('/api/interpret')
async def interpret(body:ReadingRequest,request:Request):
    rate_limit(request)
    user={'reading_id':body.reading_id,'question':body.question,'theme':body.theme,
          'spread_id':body.spread_id,'cards':card_context(body.cards)}
    result=await deepseek_completion([{'role':'system','content':SYS},{'role':'user','content':json.dumps(user,ensure_ascii=False)}],structured=True)
    try:
        parsed=json.loads(result)
        assert isinstance(parsed,dict)
        assert parsed.get('reading_id')==body.reading_id
        pc=parsed['cards']
        assert isinstance(pc,list) and len(pc)==len(body.cards)
        assert [(x['card_id'],x['position_key']) for x in pc]==[(x.card_id,x.position_key) for x in body.cards]
        for key in ['headline','overview','connections','reflection_question']:
            assert isinstance(parsed[key],str) and parsed[key].strip()
        assert isinstance(parsed['key_takeaways'],list) and 1<=len(parsed['key_takeaways'])<=5
        assert isinstance(parsed['actions'],list) and 1<=len(parsed['actions'])<=4
        for item in pc:
            assert isinstance(item['keyword'],str) and isinstance(item['interpretation'],str)
        parsed['disclaimer']='仅供娱乐与自我探索，不替代医疗、法律、投资等专业建议。'
        return parsed
    except Exception:
        raise HTTPException(status_code=502,detail={'code':'AI_INVALID_SCHEMA','message':'模型解读格式异常，牌面不变，请重试'})

@app.post('/api/followup')
async def followup(body:FollowupRequest,request:Request):
    rate_limit(request)
    context=json.dumps({'original_question':body.question,'spread_id':body.spread_id,'cards':card_context(body.cards)},ensure_ascii=False)
    msgs=[{'role':'system','content':SYS.split('输出必须')[0]+'在追问时使用自然中文文本，不输出JSON。只能解释原卡牌，不得补抽。'}, {'role':'user','content':'固定牌局事实：'+context}]
    msgs.extend({'role':m.role,'content':m.content} for m in body.messages[-10:])
    reply=await deepseek_completion(msgs,structured=False,max_tokens=850)
    return {'reading_id':body.reading_id,'reply':reply}

app.mount('/assets',StaticFiles(directory=ROOT/'assets'),name='assets')

# Critical: NEVER mount the project root as a public directory; doing so would
# accidentally expose .env and source files containing deployment configuration.
@app.get('/')
def index():
    return FileResponse(ROOT/'index.html')

@app.get('/{filename}')
def frontend_asset(filename: str):
    allowed={'index.html','style.css','app.js','rules.js','manifest.webmanifest','motion_lab.html','service-worker.js'}
    if filename not in allowed:
        raise HTTPException(status_code=404,detail='Not found')
    return FileResponse(ROOT/filename)

