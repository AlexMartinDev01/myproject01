import base64, math
from pathlib import Path
import cv2
import numpy as np
from PIL import Image, ImageDraw

SIZE=256
ANGLES=[0.0,22.5,45.0,67.5,90.0]
MID_TS=[0.25,0.5,0.75]

def decode_chunks(folder,out):
    parts=sorted(Path(folder).glob("*.txt"))
    s="".join(p.read_text().replace("\n","").replace("\r","").replace(" ","") for p in parts)
    Path(out).write_bytes(base64.b64decode(s))

def load_rgba(p):
    return np.array(Image.open(p).convert("RGBA"),dtype=np.uint8)

def f32(im):
    return im.astype(np.float32)/255.0

def bbox(im):
    a=im[...,3]
    ys,xs=np.where(a>8)
    if len(xs)==0:return (0,0,0,0)
    return int(xs.min()),int(ys.min()),int(xs.max())+1,int(ys.max())+1

def centroid(im):
    a=f32(im)[...,3]
    s=float(a.sum())
    if s<1e-9:return (0.0,0.0)
    y,x=np.indices(a.shape)
    return float((x*a).sum()/s),float((y*a).sum()/s)

def normalize(im,target_h=220,bottom=246):
    x0,y0,x1,y1=bbox(im)
    crop=im[y0:y1,x0:x1]
    h,w=crop.shape[:2]
    sc=target_h/max(h,1)
    nw,nh=max(1,round(w*sc)),max(1,round(h*sc))
    crop=np.array(Image.fromarray(crop).resize((nw,nh),Image.Resampling.LANCZOS))
    out=np.zeros((SIZE,SIZE,4),np.uint8)
    x=round(SIZE/2-nw/2); y=bottom-nh
    sx,sy=max(0,-x),max(0,-y); dx,dy=max(0,x),max(0,y)
    ww=min(nw-sx,SIZE-dx); hh=min(nh-sy,SIZE-dy)
    if ww>0 and hh>0: out[dy:dy+hh,dx:dx+ww]=crop[sy:sy+hh,sx:sx+ww]
    return out

def to_rgba8(premul,alpha):
    alpha=np.clip(alpha,0,1)
    rgb=np.where(alpha[...,None]>1e-5,premul/np.maximum(alpha[...,None],1e-5),0)
    return np.clip(np.dstack([np.clip(rgb,0,1),alpha])*255+0.5,0,255).astype(np.uint8)

def src_over(a8,b8,t):
    a,b=f32(a8),f32(b8)
    aa=a[...,3]*(1-t); ba=b[...,3]*t
    ap=a[...,:3]*a[...,3:4]*(1-t); bp=b[...,:3]*b[...,3:4]*t
    oa=ba+aa*(1-ba)
    op=bp+ap*(1-ba[...,None])
    return to_rgba8(op,oa)

def premul_blend(a8,b8,t):
    a,b=f32(a8),f32(b8)
    aa,ba=a[...,3],b[...,3]
    ap=a[...,:3]*aa[...,None]; bp=b[...,:3]*ba[...,None]
    return to_rgba8((1-t)*ap+t*bp,(1-t)*aa+t*ba)

def composite(im,bg=0.5):
    x=f32(im); a=x[...,3:4]
    if np.isscalar(bg): b=np.full(x.shape[:2]+(3,),bg,np.float32)
    else:b=bg
    return np.clip(x[...,:3]*a+b*(1-a),0,1)

def flow_mid(a8,b8,t):
    ca=(composite(a8)*255).astype(np.uint8); cb=(composite(b8)*255).astype(np.uint8)
    ga=cv2.cvtColor(ca,cv2.COLOR_RGB2GRAY); gb=cv2.cvtColor(cb,cv2.COLOR_RGB2GRAY)
    fab=cv2.calcOpticalFlowFarneback(ga,gb,None,.5,5,31,5,7,1.5,0)
    fba=cv2.calcOpticalFlowFarneback(gb,ga,None,.5,5,31,5,7,1.5,0)
    gx,gy=np.meshgrid(np.arange(SIZE,dtype=np.float32),np.arange(SIZE,dtype=np.float32))
    def warp(ch,flow,k):
        return cv2.remap(ch,gx-k*flow[...,0],gy-k*flow[...,1],cv2.INTER_LINEAR,borderMode=cv2.BORDER_CONSTANT,borderValue=0)
    a,b=f32(a8),f32(b8); aa,ba=a[...,3],b[...,3]
    ap,bp=a[...,:3]*aa[...,None],b[...,:3]*ba[...,None]
    wa,wb=warp(ap,fab,t),warp(bp,fba,1-t)
    xa,xb=warp(aa,fab,t),warp(ba,fba,1-t)
    return to_rgba8((1-t)*wa+t*wb,(1-t)*xa+t*xb)

def build_seq(keys,fn):
    seq=[keys[0]]; mids=[]
    for i in range(4):
        mm=[]
        for t in MID_TS:
            z=fn(keys[i],keys[i+1],t); seq.append(z); mm.append(z)
        seq.append(keys[i+1]); mids.append(mm)
    return seq,mids

def contour_chamfer(a,b):
    ea=cv2.Canny(((f32(a)[...,3]>.5)*255).astype(np.uint8),50,150)>0
    eb=cv2.Canny(((f32(b)[...,3]>.5)*255).astype(np.uint8),50,150)>0
    if not ea.any() or not eb.any():return None
    da=cv2.distanceTransform((~ea).astype(np.uint8),cv2.DIST_L2,3)
    db=cv2.distanceTransform((~eb).astype(np.uint8),cv2.DIST_L2,3)
    return float((db[ea].mean()+da[eb].mean())/2)

def key_metrics(imgs,label):
    frames=[]
    for ang,im in zip(ANGLES,imgs):
        x0,y0,x1,y1=bbox(im); cx,cy=centroid(im); a=f32(im)[...,3]; rgb=composite(im)
        lum=.2126*rgb[...,0]+.7152*rgb[...,1]+.0722*rgb[...,2]
        frames.append(dict(angle=ang,canvas=[im.shape[1],im.shape[0]],bbox=[x0,y0,x1,y1],bbox_w=x1-x0,bbox_h=y1-y0,bottom=y1,alpha_area=int((a>.5).sum()),centroid=[cx,cy],mean_luma=float(lum[a>.5].mean())))
    pairs=[]
    for i in range(4):
        a,b=imgs[i],imgs[i+1]; am=f32(a)[...,3]>.5; bm=f32(b)[...,3]>.5
        inter=np.logical_and(am,bm).sum(); union=np.logical_or(am,bm).sum()
        pairs.append(dict(pair=f"{ANGLES[i]}->{ANGLES[i+1]}",alpha_iou=float(inter/max(union,1)),centroid_shift_px=float(math.dist(centroid(a),centroid(b))),contour_chamfer_px=contour_chamfer(a,b),bbox_w_ratio=frames[i+1]["bbox_w"]/max(frames[i]["bbox_w"],1),bbox_h_ratio=frames[i+1]["bbox_h"]/max(frames[i]["bbox_h"],1),luma_delta=frames[i+1]["mean_luma"]-frames[i]["mean_luma"]))
    return dict(label=label,frames=frames,pairs=pairs)

def seq_metrics(seq,keys,mids,label):
    fs=[f32(x) for x in seq]
    pp=[np.dstack([x[...,:3]*x[...,3:4],x[...,3]]) for x in fs]
    dif=[float(np.abs(pp[i+1]-pp[i]).mean()) for i in range(len(pp)-1)]
    cs=[centroid(x) for x in seq]; cstep=[math.dist(cs[i],cs[i+1]) for i in range(len(cs)-1)]
    sharp=[]
    for im in seq:
        g=cv2.cvtColor((composite(im)*255).astype(np.uint8),cv2.COLOR_RGB2GRAY)
        sharp.append(float(cv2.Laplacian(g,cv2.CV_32F).var()))
    pm=[]
    for i,mm in enumerate(mids):
        mid=mm[1]; aa=f32(keys[i])[...,3]; ba=f32(keys[i+1])[...,3]; ma=f32(mid)[...,3]
        core=(aa>.95)&(ba>.95); union=(aa>.05)|(ba>.05)
        g0=cv2.cvtColor((composite(keys[i])*255).astype(np.uint8),cv2.COLOR_RGB2GRAY)
        g1=cv2.cvtColor((composite(keys[i+1])*255).astype(np.uint8),cv2.COLOR_RGB2GRAY)
        gm=cv2.cvtColor((composite(mid)*255).astype(np.uint8),cv2.COLOR_RGB2GRAY)
        e0=(cv2.Canny(g0,80,160)>0).sum(); e1=(cv2.Canny(g1,80,160)>0).sum(); em=(cv2.Canny(gm,80,160)>0).sum()
        pm.append(dict(pair=f"{ANGLES[i]}->{ANGLES[i+1]}",mid_core_alpha=float(ma[core].mean()) if core.any() else None,mid_translucent_fraction=float(((ma>.05)&(ma<.95)&union).sum()/max(union.sum(),1)),mid_edge_ratio=float(em/max((e0+e1)/2,1))))
    cv=lambda v:float(np.std(v)/(np.mean(v)+1e-9))
    return dict(label=label,frame_count=len(seq),mean_step_abs=float(np.mean(dif)),step_diff_cv=cv(dif),centroid_step_mean=float(np.mean(cstep)),centroid_step_cv=cv(cstep),mean_sharpness=float(np.mean(sharp)),pair_midpoints=pm)

def checker():
    y,x=np.indices((SIZE,SIZE)); c=((x//16+y//16)%2).astype(np.float32)
    return np.repeat((.75+.15*c)[...,None],3,2)

def save_gif(seq,path):
    bg=checker(); fr=[Image.fromarray((composite(x,bg)*255).astype(np.uint8)) for x in seq]
    fr[0].save(path,save_all=True,append_images=fr[1:],duration=42,loop=0,optimize=False)

def contact(methods,path):
    names=list(methods); cols=len(next(iter(methods.values()))); th=96; hh=22
    sh=Image.new("RGB",(cols*th,len(names)*(th+hh)),(238,238,238)); d=ImageDraw.Draw(sh); bg=checker()
    for r,n in enumerate(names):
        d.text((3,r*(th+hh)+3),n,fill=(0,0,0))
        for c,im in enumerate(methods[n]):
            p=Image.fromarray((composite(im,bg)*255).astype(np.uint8)).resize((th,th),Image.Resampling.LANCZOS)
            sh.paste(p,(c*th,r*(th+hh)+hh))
    sh.save(path)
