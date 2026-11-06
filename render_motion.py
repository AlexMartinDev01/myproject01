"""High resolution, deterministic frame renderer for premium Tarot GIF motion assets.
No browser, no network, no secrets. Uses vector RWS-inspired ORIGINAL prototype art.
Usage: python render_motion.py [shuffle cut draw flip]
"""
from __future__ import annotations
import math
import random
import sys
import tempfile
import subprocess
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import numpy as np
import cairosvg

ROOT=Path(__file__).resolve().parent
OUT=ROOT/'assets/gifs';OUT.mkdir(exist_ok=True)
W,H=720,960
N=48
FPS=24
COLORS={'shuffle':('SHUFFLE THE CARDS','星辰洗牌','让纷乱的思绪慢慢沉淀'),
        'cut':('CUT THE DECK','静心切牌','跟随自己的节奏'),
        'draw':('DRAW YOUR CARD','心动抽牌','选择一张属于此刻的牌'),
        'flip':('REVEAL THE CARD','命运揭牌','新的视角，正缓缓展开')}
FONTCH='/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc'
FONTE='/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf'
CN=ImageFont.truetype(FONTCH,37)
EN=ImageFont.truetype(FONTE,19)
SM=ImageFont.truetype(FONTCH,21)

def ease(x):
 x=max(0,min(1,x));return x*x*(3-2*x)

def vector(path,w):
 b=cairosvg.svg2png(url=str(ROOT/'assets'/path),output_width=w)
 import io
 return Image.open(io.BytesIO(b)).convert('RGBA')
BACK=vector('tarot_back.svg',185)
FRONT=vector('M17.svg',185)
STAR=vector('M19.svg',185)

def background():
 yy,xx=np.mgrid[:H,:W]
 rad=((xx-W*.5)/(W*.86))**2+((yy-H*.47)/(H*.78))**2
 g=np.clip(1-rad,0,1)
 light=np.exp(-(((xx-W*.49)/240)**2+((yy-H*.48)/260)**2))
 rgb=np.zeros((H,W,4),dtype=np.uint8)
 rgb[:,:,0]=np.clip(11+32*g+13*light,0,255)
 rgb[:,:,1]=np.clip(11+21*g+8*light,0,255)
 rgb[:,:,2]=np.clip(29+47*g+9*light,0,255)
 rgb[:,:,3]=255
 im=Image.fromarray(rgb,'RGBA')
 d=ImageDraw.Draw(im,'RGBA');random.seed(1032)
 for k in range(154):
  x=random.randrange(W);y=random.randrange(H);r=random.choice([1,1,1,2,2,3]);o=random.randint(35,115)
  d.ellipse((x-r,y-r,x+r,y+r),fill=(255,221,181,o))
 cx,cy=W//2,440
 for r,a in [(265,80),(246,46),(220,66),(193,33),(120,34)]:
  d.ellipse((cx-r,cy-r,cx+r,cy+r),outline=(205,166,112,a),width=2)
 for i in range(24):
  t=i*math.pi/12
  x=cx+245*math.cos(t);y=cy+245*math.sin(t)
  d.ellipse((x-2,y-2,x+2,y+2),fill=(228,188,125,140))
 for i in range(16):
  t=i*math.pi/8
  x1=cx+225*math.cos(t);y1=cy+225*math.sin(t)
  x2=cx+241*math.cos(t);y2=cy+241*math.sin(t)
  d.line([(x1,y1),(x2,y2)],fill=(208,160,118,75),width=2)
 d.line((45,795,W-45,795),fill=(217,182,120,40),width=1)
 return im
BASE=background()

def text_center(d,xy,t,font,color):
 box=d.textbbox((0,0),t,font=font)
 d.text((xy[0]-(box[2]-box[0])/2,xy[1]),t,font=font,fill=color)

def compose_card(canvas,card,cx,cy,wscale=1,angle=0,brightness=1,scale=1,flip=False):
 c=card
 if scale!=1:
  c=c.resize((max(2,int(c.width*scale)),max(3,int(c.height*scale))),Image.Resampling.LANCZOS)
 if wscale!=1:
  w=max(3,int(c.width*wscale))
  c=c.resize((w,c.height),Image.Resampling.BICUBIC)
 if brightness!=1:
  import PIL.ImageEnhance
  c=PIL.ImageEnhance.Brightness(c).enhance(brightness)
 if angle:c=c.rotate(angle,Image.Resampling.BICUBIC,expand=True)
 x=int(cx-c.width/2);y=int(cy-c.height/2)
 # Use a local soft shadow to keep per-frame rendering fast and memory-safe.
 sd=ImageDraw.Draw(canvas,'RGBA')
 sd.rounded_rectangle((x+7,y+12,x+c.width+8,y+c.height+14),radius=13,fill=(3,2,14,58))
 canvas.alpha_composite(c,(x,y))

def render(kind,t):
 im=BASE.copy();d=ImageDraw.Draw(im,'RGBA')
 eng,zh,foot=COLORS[kind]
 text_center(d,(W/2,84),eng,EN,(219,181,125,222))
 text_center(d,(W/2,129),zh,CN,(249,232,206,250))
 text_center(d,(W/2,838),foot,SM,(223,198,218,210))
 text_center(d,(W/2,888),'✧  TAROT  ×  AI  ✧',EN,(195,165,127,160))
 cx,cy=W/2,475
 a=math.sin(math.pi*t)**2
 if kind=='shuffle':
  for i in range(11):
   side=-1 if i%2==0 else 1;layer=i//2
   x=cx+side*(54+layer*13)*a+(i-5)*2*(1-a)
   y=cy+(layer-2)*20*a+(i-5)*1.6*(1-a)
   deg=side*(4+layer*4)*a
   compose_card(im,BACK,x,y,angle=deg,scale=.86,brightness=1-(i%3)*.035)
 elif kind=='cut':
  for i in range(12):
   side=-1 if i<6 else 1
   x=cx+side*135*a
   y=cy+(i%6-3)*2 + (17*side*a)
   compose_card(im,BACK,x,y,angle=side*7*a,scale=.84)
 elif kind=='draw':
  appear=ease(t*3)*ease((1-t)*3)
  lift=ease((t-.28)/.25)*(1-ease((t-.7)/.26))
  for i in range(9):
   j=i-4
   x=cx+j*48*appear
   y=cy+abs(j)*17*appear
   scale=.82
   if i==4:
    y-=145*lift
    scale+=.2*lift
   compose_card(im,BACK,x,y,angle=j*6.5*appear,scale=scale)
 else:
  u=ease(2*t) if t<.5 else 1-ease(2*(t-.5))
  yaw=math.pi*u
  front=u>.5
  bw=abs(math.cos(yaw))
  card=FRONT if front else BACK
  glow=Image.new('RGBA',im.size,(0,0,0,0));dg=ImageDraw.Draw(glow,'RGBA')
  dg.ellipse((cx-135,cy-225,cx+135,cy+225),fill=(251,191,121,int(110*math.sin(math.pi*u))))
  glow=glow.filter(ImageFilter.GaussianBlur(35));im.alpha_composite(glow)
  compose_card(im,card,cx,cy-18*math.sin(math.pi*u),wscale=bw,scale=1.26,brightness=1)
 return im.convert('RGB')

def main(kinds):
 for kind in kinds:
  if kind not in COLORS:raise ValueError(kind)
  with tempfile.TemporaryDirectory() as tmp:
   tpath=Path(tmp)
   for i in range(N):
    # Excludes duplicate closing frame for seamless GIF loops.
    render(kind,i/N).save(tpath/f'f{i:03}.png',optimize=False)
   palette=tpath/'palette.png'
   subprocess.run(['ffmpeg','-v','error','-y','-framerate',str(FPS),'-i',str(tpath/'f%03d.png'),'-vf','palettegen=stats_mode=diff:max_colors=128',str(palette)],check=True)
   out=OUT/f'{kind}_hq.gif'
   subprocess.run(['ffmpeg','-v','error','-y','-framerate',str(FPS),'-i',str(tpath/'f%03d.png'),'-i',str(palette),'-filter_complex','paletteuse=dither=bayer:bayer_scale=3','-loop','0',str(out)],check=True)
   # H.264 is smoother on mobile and smaller than GIF; no audio.
   mp4=OUT/f'{kind}_hq.mp4'
   subprocess.run(['ffmpeg','-v','error','-y','-framerate',str(FPS),'-i',str(tpath/'f%03d.png'),'-c:v','libx264','-crf','21','-preset','medium','-pix_fmt','yuv420p',str(mp4)],check=True)
   print(f'{kind}: GIF {out.stat().st_size/1e6:.2f} MB, MP4 {mp4.stat().st_size/1e6:.2f} MB')
if __name__=='__main__':main(sys.argv[1:] or list(COLORS))
