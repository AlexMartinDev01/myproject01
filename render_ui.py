"""Static 9:16 mobile design previews from the interactive prototype design system.
No secrets, network assets, or externally hosted images.
"""
from __future__ import annotations
from pathlib import Path
import io, math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import cairosvg
ROOT=Path(__file__).parent;OUT=ROOT/'previews';OUT.mkdir(exist_ok=True)
W,H=720,1280
CN='/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc'
SANS='/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
EN='/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf'
def f(size,serif=False):return ImageFont.truetype(CN if serif else SANS,size)
def fe(size):return ImageFont.truetype(EN,size)
GOLD='#e2c18d';TEXT='#f8efe5';MUTED='#b6a9c6';INK='#33273b'
def text(d,x,y,s,size=25,color=TEXT,serif=False,anchor=None):d.text((x,y),s,font=f(size,serif),fill=color,anchor=anchor)
def center(d,y,s,size=25,color=TEXT,serif=False):text(d,W/2,y,s,size,color,serif,anchor='ma')
def en(d,y,s,size=16,color=GOLD):d.text((W/2,y),s,font=fe(size),fill=color,anchor='ma')
def base():
 yy,xx=np.mgrid[:H,:W]
 g=np.clip(1-(((xx-360)/690)**2+((yy-520)/1060)**2),0,1)
 ar=np.zeros((H,W,3),dtype='uint8');ar[:,:,0]=11+31*g;ar[:,:,1]=11+24*g;ar[:,:,2]=27+43*g
 im=Image.fromarray(ar,'RGB').convert('RGBA')
 d=ImageDraw.Draw(im,'RGBA');random.seed(9)
 for _ in range(210):
  x=random.randrange(W);y=random.randrange(H);r=random.choice([1,1,2]);a=random.randrange(30,105)
  d.ellipse((x-r,y-r,x+r,y+r),fill=(233,204,146,a))
 return im

def top(im,title=None,back=False):
 d=ImageDraw.Draw(im,'RGBA')
 if not back:
  text(d,49,56,'星语',34,TEXT,True)
  d.text((140,68),'T A R O T   /   A I',font=fe(14),fill=GOLD)
  d.ellipse((637,55,679,98),outline=(223,181,130,130),width=2);centerx='⚙';text(d,648,57,'·',27,GOLD)
 else:
  text(d,48,54,'‹',49,TEXT)
  center(d,63,title,27,TEXT,True)

def nav(im,active='首页'):
 d=ImageDraw.Draw(im,'RGBA');d.rectangle((0,1172,W,H),fill=(15,14,31,250));d.line((0,1172,W,1172),fill=(225,184,131,48),width=2)
 for i,(symbol,name) in enumerate([('○','首页'),('◇','牌库'),('◯','记录'),('♡','我的')]):
  x=90+i*180;col=GOLD if name==active else '#ada3bd'
  text(d,x,1194,symbol,33,col,anchor='ma');text(d,x,1241,name,17,col,anchor='ma')

def button(im,y,label,light=True,width=620):
 d=ImageDraw.Draw(im,'RGBA');x=(W-width)/2
 d.rounded_rectangle((x,y,x+width,y+88),radius=44,fill='#e9cda4' if light else '#2f2943',outline='#e5be89',width=2)
 text(d,W/2,y+44,label,26,'#322538' if light else TEXT,anchor='mm')

def vector(which,w):
 data=cairosvg.svg2png(url=str(ROOT/'assets'/which),output_width=w)
 return Image.open(io.BytesIO(data)).convert('RGBA')
BACK=vector('tarot_back.svg',193)
FRONTS=[vector(n,150) for n in ['M02.svg','M17.svg','M19.svg']]

def putcard(im,card,cx,cy,angle=0,size=None):
 c=card.copy();
 if size:
  c=c.resize(size,Image.Resampling.LANCZOS)
 if angle:c=c.rotate(angle,Image.Resampling.BICUBIC,expand=True)
 x=int(cx-c.width/2);y=int(cy-c.height/2)
 sh=Image.new('RGBA',im.size,(0,0,0,0));sd=ImageDraw.Draw(sh,'RGBA')
 sd.rounded_rectangle((x+9,y+15,x+c.width+9,y+c.height+15),radius=10,fill=(0,0,0,55))
 im.alpha_composite(sh);im.alpha_composite(c,(x,y))

def ornament(im,cx,cy,r):
 d=ImageDraw.Draw(im,'RGBA')
 for rad,a in [(r,110),(r-22,60),(r-69,80)]:d.ellipse((cx-rad,cy-rad,cx+rad,cy+rad),outline=(220,177,107,a),width=2)
 for i in range(24):
  t=i*math.pi/12;x=cx+int(r*math.cos(t));y=cy+int(r*math.sin(t))
  d.ellipse((x-3,y-3,x+3,y+3),fill=(232,194,124,160))

def page_home():
 im=base();top(im);d=ImageDraw.Draw(im,'RGBA')
 en(d,173,'THE ART OF INNER LISTENING');center(d,230,'今晚，让心绪慢慢清晰',27,'#e8d4c9',True)
 ornament(im,360,514,260)
 putcard(im,BACK,301,532,-13,size=(186,310));putcard(im,BACK,420,524,10,size=(186,310));putcard(im,BACK,360,510,-2,size=(202,338))
 center(d,808,'答案也许不在远方',35,TEXT,True);center(d,863,'而在你此刻的心里',35,GOLD,True)
 center(d,933,'78 张塔罗牌   ·   3 种经典牌阵',21,MUTED)
 button(im,1004,'开启一次塔罗探索　 ↗');nav(im)
 return im

def page_question():
 im=base();top(im,'开始占卜',True);d=ImageDraw.Draw(im,'RGBA')
 text(d,53,157,'01 / DEFINE YOUR INTENTION',19,GOLD)
 text(d,53,203,'给心中的疑问',45,TEXT,True);text(d,53,266,'一个温柔的开端',45,TEXT,True)
 text(d,54,370,'你可以问感情、工作、成长中的困惑。',22,MUTED)
 text(d,54,408,'好的问题，不一定需要唯一答案。',22,MUTED)
 text(d,54,476,'选择探索主题',21,GOLD)
 for x,w,title in [(52,116,'综合'),(180,150,'感情关系'),(344,150,'学业工作'),(507,161,'自我成长')]:
  d.rounded_rectangle((x,520,x+w,570),radius=25,fill='#e5c99f' if title=='综合' else '#332a49',outline='#b6a075',width=2)
  text(d,x+w/2,545,title,18,INK if title=='综合' else TEXT,anchor='mm')
 d.rounded_rectangle((52,609,666,842),radius=28,fill='#231f39',outline='#9b826c',width=2)
 text(d,80,650,'面对最近的职业选择，我该',23,'#ded1df')
 text(d,80,692,'关注哪些更重要的因素？',23,'#ded1df')
 text(d,605,809,'24 / 500',17,MUTED,anchor='ra')
 text(d,53,889,'没有灵感？试试这样问',21,GOLD)
 d.rounded_rectangle((52,937,668,993),radius=15,fill='#2a243d',outline='#73637c')
 text(d,77,951,'✧ 我现在最需要关注的是什么？',22,'#ded0e2')
 button(im,1062,'继续 · 选择牌阵　 ↗')
 return im

def spreadBox(im,y,label,sub,num,selected):
 d=ImageDraw.Draw(im,'RGBA')
 d.rounded_rectangle((49,y,674,y+167),radius=22,fill='#382e47' if selected else '#211e35',outline='#e0bc8e' if selected else '#7e6a7a',width=2)
 if num==1:putcard(im,BACK,147,y+82,size=(66,109))
 else:
  for i in range(3):putcard(im,BACK,98+i*48,y+82,size=(57,96))
 text(d,281,y+28,label,25,TEXT,True)
 text(d,281,y+84,sub,19,MUTED)
 text(d,281,y+123,f'{num} 张牌 · '+('精选入门' if num==1 else '深入探索'),18,'#cebba4')

def page_spread():
 im=base();top(im,'选择牌阵',True);d=ImageDraw.Draw(im,'RGBA')
 text(d,53,156,'02 / CHOOSE A SPREAD',19,GOLD)
 text(d,53,203,'为这次探索',45,TEXT,True);text(d,53,266,'选择一幅牌阵',45,TEXT,True)
 text(d,54,361,'每张牌在牌阵中都有固定的位置和意义。',22,MUTED)
 spreadBox(im,420,'单牌指引','当下最值得关注的主题',1,False)
 spreadBox(im,603,'现状 · 障碍 · 建议','梳理困惑与下一步行动',3,True)
 spreadBox(im,786,'过去 · 现在 · 可能发展','观察变化与可能性',3,False)
 d.rounded_rectangle((51,967,670,1025),radius=16,fill='#29233f')
 text(d,72,980,'允许逆位牌',22,TEXT)
 d.rounded_rectangle((591,984,647,1011),radius=15,fill='#c8a36e')
 d.ellipse((621,986,645,1010),fill='#fff0dc')
 button(im,1070,'确认牌阵 · 开始洗牌　 ↗')
 return im

def page_draw():
 im=base();top(im,'抽取你的牌',True);d=ImageDraw.Draw(im,'RGBA')
 en(d,165,'05 / CHOOSE YOUR CARD');center(d,223,'跟随你的第一直觉',43,TEXT,True)
 center(d,300,'轻触其中一张牌，开启属于你的解读',21,MUTED)
 ornament(im,360,579,274)
 for i in range(9):
  j=i-4;card=BACK
  putcard(im,card,360+j*47,578+abs(j)*16,angle=-j*6.7,size=(126,210))
 center(d,844,'已选  1 / 3',22,'#e6cc97')
 for i,(x,s) in enumerate([(182,'现状'),(361,'障碍'),(540,'建议')]):
  d.rounded_rectangle((x-54,884,x+54,1040),radius=9,fill='#241c3d',outline='#d8b585',width=2)
  if i==0:putcard(im,BACK,x,961,size=(101,148))
  else:centerText=str(i+1);text(d,x,949,centerText,29,GOLD,anchor='mm')
  text(d,x,1050,s,21,MUTED,anchor='ma')
 button(im,1113,'抽取一张　 ↗')
 return im

def page_reveal():
 im=base();top(im,'揭示牌面',True);d=ImageDraw.Draw(im,'RGBA')
 en(d,172,'06 / REVEAL THE CARDS');center(d,232,'让答案缓缓显现',43,TEXT,True)
 center(d,305,'轻触每一张牌，亲自翻开它',22,MUTED)
 ornament(im,360,596,289)
 for i,x in enumerate((148,360,572)):
  putcard(im,FRONTS[i],x,598,size=(176,292))
  text(d,x,789,['现状','障碍','建议'][i],23,TEXT,anchor='ma')
  text(d,x,831,['女祭司','星星','太阳'][i],18,GOLD,anchor='ma')
 center(d,925,'三张牌 · 已全部揭示',24,'#dfc79d')
 button(im,1085,'查看本次解读　 ↗')
 return im

def page_result():
 im=Image.new('RGBA',(W,H),'#f7f0e6')
 d=ImageDraw.Draw(im,'RGBA');d.rectangle((0,0,W,132),fill='#201a32')
 text(d,46,58,'‹',45,TEXT);center(d,67,'本次解读',27,TEXT,True)
 text(d,55,161,'YOUR TAROT READING',18,'#9b7546')
 text(d,53,218,'在不确定中',40,INK,True);text(d,53,274,'找到自己的方向',40,INK,True)
 for i,x in enumerate((149,361,573)):
  putcard(im,FRONTS[i],x,511,size=(162,266))
  text(d,x,666,['现状 · 女祭司','障碍 · 星星','建议 · 太阳'][i],17,'#765e76',anchor='ma')
 d.line((53,721,667,721),fill='#dac7ad',width=2)
 text(d,56,747,'整体解读',31,INK,True)
 d.rounded_rectangle((52,814,670,1050),radius=19,fill='#efe3d0')
 for i,s in enumerate(['这组牌邀请你把注意力放在当下，','用更清晰的眼光审视现有选择。','暂时不需要追求唯一的答案，','先从你能够决定的小事开始。']):
  text(d,84,844+i*47,s,22,'#615363')
 text(d,54,1101,'逐牌解析  →  牌面联系  →  行动建议',22,'#8b735d')
 d.rounded_rectangle((54,1163,665,1238),radius=38,fill='#e4c294')
 text(d,360,1201,'继续追问 AI　 ↗',26,INK,anchor='mm')
 return im

for i,(name,fun) in enumerate([('01_home',page_home),('02_question',page_question),('03_spread',page_spread),('04_draw',page_draw),('05_reveal',page_reveal),('06_result',page_result)],1):
 img=fun().convert('RGB');file=OUT/f'UI_{name}.png';img.save(file,optimize=True)
 print(file.name,img.size,file.stat().st_size)
