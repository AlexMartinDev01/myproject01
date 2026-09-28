import json, os, sys, time, traceback
from pathlib import Path
import numpy as np
from PIL import Image
import torch

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
sys.path.insert(0,str(HERE))
from common import *

OUT=HERE/"results"/"animeinterp"
OUT.mkdir(parents=True,exist_ok=True)
ANIME=Path(os.environ["ANIME_HOME"]).resolve()
sys.path.insert(0,str(ANIME))

from models.AnimeInterp_no_cupy import AnimeInterpNoCupy

def load_keys():
    kdir=HERE/"results"/"keyframes"
    return [load_rgba(kdir/f"{a:g}.png") for a in ANGLES]

def tensor3(arr):
    return torch.from_numpy(arr.transpose(2,0,1)).float().unsqueeze(0)

def load_model():
    ckpt=torch.load(os.environ["ANIME_CKPT"],map_location="cpu")
    model=AnimeInterpNoCupy(path=None)
    sd=ckpt.get("model_state_dict",ckpt)
    clean={}
    for k,v in sd.items():
        clean[k[7:] if k.startswith("module.") else k]=v
    missing,unexpected=model.load_state_dict(clean,strict=False)
    model.eval()
    return model,missing,unexpected

def run_one(model,a8,b8,t=0.5):
    a,b=f32(a8),f32(b8)
    aa,ba=a[...,3],b[...,3]
    ap=a[...,:3]*aa[...,None]
    bp=b[...,:3]*ba[...,None]
    h,w=aa.shape
    z=torch.zeros(1,2,h,w)
    with torch.no_grad():
        rgb=model(tensor3(ap),tensor3(bp),z.clone(),z.clone(),t)[0]
        a3=np.repeat(aa[...,None],3,axis=2)
        b3=np.repeat(ba[...,None],3,axis=2)
        al=model(tensor3(a3),tensor3(b3),z.clone(),z.clone(),t)[0]
    prem=rgb[0].cpu().numpy().transpose(1,2,0)
    alpha=al[0].cpu().numpy().transpose(1,2,0).mean(2)
    return to_rgba8(np.clip(prem,0,1),np.clip(alpha,0,1))

keys=load_keys()
model,missing,unexpected=load_model()
rows=[]
mids=[]
for i in range(4):
    t0=time.time()
    im=run_one(model,keys[i],keys[i+1],0.5)
    dt=time.time()-t0
    Image.fromarray(im).save(OUT/f"mid_{ANGLES[i]:g}_{ANGLES[i+1]:g}.png")
    mids.append(im)
    aa=f32(keys[i])[...,3]; ba=f32(keys[i+1])[...,3]; ma=f32(im)[...,3]
    core=(aa>.95)&(ba>.95); union=(aa>.05)|(ba>.05)
    g0=cv2.cvtColor((composite(keys[i])*255).astype(np.uint8),cv2.COLOR_RGB2GRAY)
    g1=cv2.cvtColor((composite(keys[i+1])*255).astype(np.uint8),cv2.COLOR_RGB2GRAY)
    gm=cv2.cvtColor((composite(im)*255).astype(np.uint8),cv2.COLOR_RGB2GRAY)
    e0=(cv2.Canny(g0,80,160)>0).sum(); e1=(cv2.Canny(g1,80,160)>0).sum(); em=(cv2.Canny(gm,80,160)>0).sum()
    rows.append({
      "pair":f"{ANGLES[i]}->{ANGLES[i+1]}",
      "seconds":dt,
      "core_alpha":float(ma[core].mean()) if core.any() else None,
      "translucent_fraction":float(((ma>.05)&(ma<.95)&union).sum()/max(union.sum(),1)),
      "edge_ratio":float(em/max((e0+e1)/2,1)),
      "sharpness":float(cv2.Laplacian(gm,cv2.CV_32F).var())
    })

# contact sheet: endpoints and AnimeInterp midpoint for each pair
bg=checker()
canvas=Image.new("RGB",(4*3*160,180),(238,238,238))
for i in range(4):
    trip=[keys[i],mids[i],keys[i+1]]
    for j,im in enumerate(trip):
        p=Image.fromarray((composite(im,bg)*255).astype(np.uint8)).resize((160,160),Image.Resampling.LANCZOS)
        canvas.paste(p,((i*3+j)*160,20))
canvas.save(OUT/"animeinterp_midpoints.png")

res={
 "model":"AnimeInterpNoCupy using official anime_interp_full.ckpt",
 "checkpoint":os.environ["ANIME_CKPT"],
 "missing_keys":list(missing),
 "unexpected_keys":list(unexpected),
 "pairs":rows,
 "mean_seconds":float(np.mean([x["seconds"] for x in rows])),
 "mean_core_alpha":float(np.mean([x["core_alpha"] for x in rows if x["core_alpha"] is not None])),
 "mean_translucent_fraction":float(np.mean([x["translucent_fraction"] for x in rows])),
 "mean_edge_ratio":float(np.mean([x["edge_ratio"] for x in rows])),
 "mean_sharpness":float(np.mean([x["sharpness"] for x in rows]))
}
(OUT/"metrics.json").write_text(json.dumps(res,ensure_ascii=False,indent=2),encoding="utf-8")
print(json.dumps(res,ensure_ascii=False,indent=2))
