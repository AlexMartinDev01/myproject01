from pathlib import Path
import html, math
root=Path(__file__).parent
assets=root/'assets'
assets.mkdir(exist_ok=True)
major=['愚者','魔术师','女祭司','皇后','皇帝','教皇','恋人','战车','力量','隐者','命运之轮','正义','倒吊人','死神','节制','恶魔','高塔','星星','月亮','太阳','审判','世界']
major_en=['The Fool','The Magician','The High Priestess','The Empress','The Emperor','The Hierophant','The Lovers','The Chariot','Strength','The Hermit','Wheel of Fortune','Justice','The Hanged Man','Death','Temperance','The Devil','The Tower','The Star','The Moon','The Sun','Judgement','The World']
suits={'W':('权杖','Wands','⚜'),'C':('圣杯','Cups','♜'),'S':('宝剑','Swords','✧'),'P':('星币','Pentacles','✥')}
ranks=['王牌','二','三','四','五','六','七','八','九','十','侍从','骑士','皇后','国王']
ranks_en=['Ace','Two','Three','Four','Five','Six','Seven','Eight','Nine','Ten','Page','Knight','Queen','King']
cards=[]
for k,(n,e) in enumerate(zip(major,major_en)):
 cards.append({'id':f'M{k:02}','name':n,'english':e,'major':True,'number':k,'suit':None})
for s,(name,en,_) in suits.items():
 for k,(rank,rene) in enumerate(zip(ranks,ranks_en),1):
  cards.append({'id':f'{s}{k:02}','name':name+rank,'english':f'{rene} of {en}','major':False,'number':k,'suit':s})
import json
(assets/'cards.json').write_text(json.dumps(cards,ensure_ascii=False,indent=2),encoding='utf8')
back='''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 600" width="360" height="600"><defs>
<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#24204b"/><stop offset=".52" stop-color="#0d102b"/><stop offset="1" stop-color="#37315e"/></linearGradient>
<linearGradient id="gold" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#fbe3a3"/><stop offset=".45" stop-color="#b8874b"/><stop offset=".77" stop-color="#f2dba1"/><stop offset="1" stop-color="#9c6b4e"/></linearGradient>
<radialGradient id="glow"><stop stop-color="#706097" stop-opacity=".55"/><stop offset="1" stop-color="#33204f" stop-opacity="0"/></radialGradient>
<filter id="shine"><feGaussianBlur stdDeviation="2"/></filter></defs>
<rect width="360" height="600" rx="19" fill="url(#bg)"/><rect width="354" height="594" x="3" y="3" rx="18" fill="none" stroke="url(#gold)" stroke-width="5"/>
<rect x="18" y="18" width="324" height="564" rx="11" fill="none" stroke="#cba86b" stroke-width="1.5"/>
<rect x="29" y="29" width="302" height="542" rx="7" fill="none" stroke="#957558" stroke-width=".7"/>
<ellipse cx="180" cy="300" rx="170" ry="240" fill="url(#glow)"/>
<g stroke="url(#gold)" fill="none" stroke-width="1.5" opacity=".75"><circle cx="180" cy="300" r="100"/><circle cx="180" cy="300" r="119" stroke-dasharray="2 7"/><circle cx="180" cy="300" r="73"/><path d="M180 118V482M0 300H360M51 171L309 429M309 171L51 429" opacity=".3"/></g>
<g fill="url(#gold)"><path d="M180 173L190 270L241 300L190 330L180 427L170 330L119 300L170 270Z" opacity=".75"/><path d="M180 224L192 285L252 300L192 315L180 376L168 315L108 300L168 285Z"/><circle cx="180" cy="300" r="30" fill="#171632" stroke="#e4c488" stroke-width="4"/><circle cx="180" cy="300" r="19" fill="#a88d74"/><circle cx="188" cy="295" r="16" fill="#171632"/><circle cx="180" cy="107" r="5"/><circle cx="180" cy="493" r="5"/></g>
<g stroke="#f2d59b" stroke-width="1.5" opacity=".88"><path d="M52 60l0 26 M39 73h26 M308 514v26 M295 527h26"/><path d="M308 60v26M295 73h26M52 514v26M39 527h26"/></g>
<g fill="#d8b87b"><circle cx="91" cy="180" r="2"/><circle cx="272" cy="192" r="2.5"/><circle cx="95" cy="422" r="2"/><circle cx="271" cy="432" r="2"/><circle cx="180" cy="65" r="2"/><circle cx="180" cy="536" r="2"/></g></svg>'''
(assets/'tarot_back.svg').write_text(back,encoding='utf8')
# Distinct vector art, one graphic per card. These serve as design placeholders, NOT traditional illustrated RWS art.
icons={'M00':'☄','M01':'✦','M02':'☾','M03':'❀','M04':'♜','M05':'✣','M06':'♡','M07':'✺','M08':'♌','M09':'✧','M10':'☼','M11':'⚖','M12':'◇','M13':'❦','M14':'♒','M15':'⛧','M16':'⚡','M17':'✷','M18':'☽','M19':'☼','M20':'❂','M21':'◎'}
def graphic(id):
    gold='#f3d89a';pale='#fce8b7';mid='#c89d5e';dark='#292344'
    if id=='M19':
        rays=''.join(f'<line x1="{180+65*math.cos(i*math.pi/9):.1f}" y1="{275+65*math.sin(i*math.pi/9):.1f}" x2="{180+102*math.cos(i*math.pi/9):.1f}" y2="{275+102*math.sin(i*math.pi/9):.1f}" stroke="{gold}" stroke-width="4" stroke-linecap="round"/>' for i in range(18))
        return rays+f'<circle cx="180" cy="275" r="60" fill="{gold}"/><circle cx="162" cy="268" r="4" fill="{dark}"/><circle cx="198" cy="268" r="4" fill="{dark}"/><path d="M160 289Q180 306 200 289" stroke="{dark}" fill="none" stroke-width="3"/>'
    if id=='M17':
        points=' '.join(f'{180+(92 if i%2==0 else 28)*math.cos(i*math.pi/8-math.pi/2):.1f},{269+(92 if i%2==0 else 28)*math.sin(i*math.pi/8-math.pi/2):.1f}' for i in range(16))
        return f'<polygon points="{points}" fill="{gold}" stroke="{pale}" stroke-width="2"/><circle cx="180" cy="269" r="13" fill="{dark}"/>'+''.join(f'<circle cx="{180+118*math.cos(i*math.pi/4):.1f}" cy="{269+118*math.sin(i*math.pi/4):.1f}" r="5" fill="{pale}"/>' for i in range(8))
    if id=='M02':
        return f'<rect x="108" y="173" width="23" height="204" rx="3" fill="{gold}"/><rect x="230" y="173" width="23" height="204" rx="3" fill="{gold}"/><path d="M124 185Q180 124 236 185" fill="none" stroke="{gold}" stroke-width="13"/><path d="M140 315Q180 352 220 315" fill="none" stroke="{mid}" stroke-width="3"/><circle cx="180" cy="260" r="41" fill="{pale}"/><circle cx="195" cy="248" r="35" fill="{dark}"/>'
    if id=='M18':
        return f'<circle cx="180" cy="265" r="88" fill="{gold}"/><circle cx="215" cy="244" r="76" fill="{dark}"/><circle cx="148" cy="377" r="8" fill="{pale}"/><circle cx="218" cy="388" r="6" fill="{pale}"/>'
    if id.startswith('C'):
        return f'<path d="M114 200H246Q244 298 197 327V361H238V376H122V361H164V327Q116 301 114 200Z" fill="none" stroke="{gold}" stroke-width="11"/><path d="M114 221Q81 208 95 261Q108 291 147 296M246 221Q280 208 265 261Q252 291 211 296" fill="none" stroke="{pale}" stroke-width="7"/>'
    if id.startswith('W'):
        return f'<g transform="rotate(-22 180 280)"><rect x="162" y="167" width="36" height="228" rx="9" fill="{mid}" stroke="{gold}" stroke-width="4"/><path d="M182 186Q224 208 197 241M181 277Q145 251 168 223M183 308Q215 293 210 277" stroke="{pale}" fill="none" stroke-width="7"/></g>'
    if id.startswith('S'):
        return f'<path d="M180 153L207 296L180 327L153 296Z" fill="{gold}"/><path d="M126 327H234" stroke="{gold}" stroke-width="12" stroke-linecap="round"/><path d="M180 330V391" stroke="{mid}" stroke-width="13"/><circle cx="180" cy="402" r="12" fill="none" stroke="{gold}" stroke-width="6"/>'
    if id.startswith('P'):
        pts=[(180+73*math.cos(i*2*math.pi/5-math.pi/2),270+73*math.sin(i*2*math.pi/5-math.pi/2)) for i in range(5)]
        order=[0,2,4,1,3,0];path=' '.join(('M' if j==0 else 'L')+f'{pts[i][0]:.1f},{pts[i][1]:.1f}' for j,i in enumerate(order))
        return f'<circle cx="180" cy="270" r="98" fill="none" stroke="{gold}" stroke-width="9"/><path d="{path}" fill="none" stroke="{pale}" stroke-width="7"/>'
    rays=''.join(f'<path d="M180 {265+(-1)**i*(56+i%4*9)}" stroke="{mid}" stroke-width="2" transform="rotate({i*30} 180 265)"/>' for i in range(12))
    return rays+f'<path d="M180 151L203 242L281 265L203 288L180 379L157 288L79 265L157 242Z" fill="{gold}"/><circle cx="180" cy="265" r="27" fill="{dark}" stroke="{pale}" stroke-width="5"/>'

for card in cards:
 id=card['id'];majorflag=card['major'];k=card['number']
 symbol=icons[id] if id in icons else suits[id[0]][2];name=html.escape(card['name']);english=html.escape(card['english'])
 groupstars=''.join(f'<circle cx="{180+math.cos(i*2.399+k)*int(92+(i%3)*28)}" cy="{267+math.sin(i*2.399+k)*int(92+(i%3)*28)}" r="{1+i%3}" fill="#d3b37f" opacity=".7"/>' for i in range(24))
 art=f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 600" width="360" height="600"><defs><linearGradient id="paper" x1="0" x2="1" y1="0" y2="1"><stop stop-color="#fff7e6"/><stop offset=".54" stop-color="#eddfc7"/><stop offset="1" stop-color="#d8c2a1"/></linearGradient><linearGradient id="sky" x1=".2" y1="0" x2=".8" y2="1"><stop stop-color="#111831"/><stop offset=".55" stop-color="#302a56"/><stop offset="1" stop-color="#706290"/></linearGradient><radialGradient id="halo"><stop stop-color="#f6d6a4" stop-opacity=".6"/><stop offset="1" stop-color="#f6d6a4" stop-opacity="0"/></radialGradient></defs>
<rect width="360" height="600" rx="18" fill="url(#paper)"/><rect x="9" y="9" width="342" height="582" rx="14" fill="none" stroke="#9d754b" stroke-width="3"/>
<rect x="23" y="58" width="314" height="430" rx="7" fill="url(#sky)"/>
<circle cx="180" cy="270" r="135" fill="url(#halo)"/>
<g opacity=".6" fill="none" stroke="#c6a879"><circle cx="180" cy="266" r="118"/><circle cx="180" cy="266" r="97" stroke-dasharray="4 9"/><circle cx="180" cy="266" r="65"/></g>
{groupstars}<text x="180" y="50" text-anchor="middle" font-size="24" font-family="serif" fill="#74502e">{int(k) if majorflag else ('Ⅶ' if k==7 else str(k))}</text>
{graphic(id)}
<path d="M40 408Q90 365 140 405T240 405T320 405" fill="none" stroke="#e6c795" stroke-width="2" opacity=".7"/>
<text x="180" y="529" text-anchor="middle" font-size="26" font-family="Noto Serif CJK SC,serif" fill="#5a3f31">{name}</text>
<text x="180" y="557" text-anchor="middle" font-size="14" font-family="serif" fill="#7c6045">{english}</text>
<circle cx="34" cy="34" r="4" fill="#98744a"/><circle cx="326" cy="34" r="4" fill="#98744a"/><circle cx="34" cy="566" r="4" fill="#98744a"/><circle cx="326" cy="566" r="4" fill="#98744a"/></svg>'''
 (assets/f'{id}.svg').write_text(art,encoding='utf8')
print('Generated',len(cards),'card assets plus back')
