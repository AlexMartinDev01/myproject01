#!/usr/bin/env python3
import base64, json, math, os, subprocess, sys, zipfile
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw

HERE=Path(__file__).resolve().parent
OUT=HERE/"results"
KEYS=OUT/"keyframes"
FRAMES=OUT/"rife4x_frames"
PREVIEW=OUT/"preview_frames"
COMPARE=OUT/"compare_frames"
for p in (OUT,KEYS,FRAMES,PREVIEW,COMPARE): p.mkdir(parents=True,exist_ok=True)

def reconstruct_input():
    parts=sorted((HERE/"input_b64").glob("part*.txt"), key=lambda p:int(p.stem.replace("part","")))
    if len(parts)!=7:
        raise RuntimeError(f"expected 7 input parts, got {len(parts)}")
    encoded="".join(p.read_text().strip() for p in parts)
    raw=base64.b64decode(encoded,validate=True)
    z=OUT/"aligned_64.zip"; z.write_bytes(raw)
    d=OUT/"input"; d.mkdir(exist_ok=True)
    with zipfile.ZipFile(z) as f:
        f.extractall(d)
    files=sorted(d.glob("*.webp"))
    if len(files)!=17:
        raise RuntimeError(f"expected 17 keyframes, got {len(files)}")
    return files

def load_rgba(path):
    return np.array(Image.open(path).convert("RGBA"),dtype=np.uint8)

def f32(im): return im.astype(np.float32)/255.0

def to_rgba8(premul,alpha):
    alpha=np.clip(alpha,0,1)
    rgb=np.where(alpha[...,None]>1e-5,premul/np.maximum(alpha[...,None],1e-5),0)
    return np.clip(np.dstack([np.clip(rgb,0,1),alpha])*255+0.5,0,255).astype(np.uint8)

def load_rife():
    home=Path(os.environ["RIFE_HOME"]).resolve()
    sys.path.insert(0,str(home)); sys.path.insert(0,str(home/"train_log"))
    import torch
    from train_log.RIFE_HDv3 import Model
    model=Model()
    model.load_model(str(home/"train_log"),-1)
    model.eval(); model.device()
    return torch,model

def tensor3(torch,arr):
    return torch.from_numpy(arr.transpose(2,0,1)).float().unsqueeze(0)

def rife_rgba(torch,model,a8,b8,t):
    a,b=f32(a8),f32(b8)
    aa,ba=a[...,3],b[...,3]
    ap,bp=a[...,:3]*aa[...,None],b[...,:3]*ba[...,None]
    a3=np.repeat(aa[...,None],3,2); b3=np.repeat(ba[...,None],3,2)
    with torch.no_grad():
        p=model.inference(tensor3(torch,ap),tensor3(torch,bp),float(t))
        q=model.inference(tensor3(torch,a3),tensor3(torch,b3),float(t))
    prem=p[0].cpu().numpy().transpose(1,2,0)
    alpha=q[0].cpu().numpy().transpose(1,2,0).mean(2)
    return to_rgba8(np.clip(prem,0,1),np.clip(alpha,0,1))

def checker(size=64,cell=8):
    yy,xx=np.indices((size,size))
    c=((xx//cell+yy//cell)%2).astype(np.float32)
    return np.repeat((0.78+0.14*c)[...,None],3,2)

def composite(im,bg):
    x=f32(im); a=x[...,3:4]
    return np.clip(x[...,:3]*a+bg*(1-a),0,1)

def bbox_centroid_area(im):
    a=f32(im)[...,3]
    ys,xs=np.where(a>0.08)
    if len(xs)==0:return [0,0,0,0],[0,0],0
    weights=a
    yy,xx=np.indices(a.shape); s=weights.sum()
    return [int(xs.min()),int(ys.min()),int(xs.max()+1),int(ys.max()+1)], [float((xx*weights).sum()/s),float((yy*weights).sum()/s)], int((a>.5).sum())

def premul_diff(a,b):
    af,bf=f32(a),f32(b)
    ap=np.dstack([af[...,:3]*af[...,3:4],af[...,3]])
    bp=np.dstack([bf[...,:3]*bf[...,3:4],bf[...,3]])
    return float(np.abs(ap-bp).mean())

def render_rgb(im,scale=8):
    bg=checker()
    rgb=(composite(im,bg)*255).astype(np.uint8)
    return Image.fromarray(rgb).resize((64*scale,64*scale),Image.Resampling.LANCZOS)

def make_gif(frames,path,fps):
    ims=[render_rgb(x) for x in frames]
    dur=round(1000/fps)
    ims[0].save(path,save_all=True,append_images=ims[1:],duration=dur,loop=0,optimize=False)

def encode_mp4(frame_dir,out_path,fps):
    subprocess.run([
        "ffmpeg","-y","-hide_banner","-loglevel","error",
        "-framerate",str(fps),"-i",str(frame_dir/"%04d.png"),
        "-c:v","libx264","-preset","medium","-crf","18",
        "-pix_fmt","yuv420p","-movflags","+faststart",str(out_path)
    ],check=True)

def save_video_frames(seq,dst):
    for i,im in enumerate(seq):
        render_rgb(im).save(dst/f"{i:04d}.png")

def make_contact(seq,path):
    picks=list(range(0,65,4))  # all 17 true keyframe positions
    thumb=160; cols=6; rows=math.ceil(len(picks)/cols)
    sheet=Image.new("RGB",(cols*thumb,rows*(thumb+24)),(238,238,238))
    draw=ImageDraw.Draw(sheet)
    for j,idx in enumerate(picks):
        x=(j%cols)*thumb; y=(j//cols)*(thumb+24)
        p=render_rgb(seq[idx],scale=3).resize((thumb,thumb),Image.Resampling.LANCZOS)
        sheet.paste(p,(x,y+24))
        draw.text((x+5,y+5),f"frame {idx:02d}",fill=(0,0,0))
    sheet.save(path)

files=reconstruct_input()
keys=[load_rgba(p) for p in files]
for i,im in enumerate(keys):
    Image.fromarray(im).save(KEYS/f"key_{i:02d}.png")

torch,model=load_rife()

seq=[keys[0]]
source_index=[0.0]
for i in range(16):
    for t in (0.25,0.5,0.75):
        seq.append(rife_rgba(torch,model,keys[i],keys[i+1],t))
        source_index.append(i+t)
    seq.append(keys[i+1]); source_index.append(float(i+1))

assert len(seq)==65
for i,im in enumerate(seq):
    Image.fromarray(im).save(FRAMES/f"frame_{i:02d}.png")

# Baseline comparator: identical duration, but hold each real key until next key.
nearest=[]
for i in range(16):
    nearest.extend([keys[i]]*4)
nearest.append(keys[-1])
assert len(nearest)==65

# Metrics and anomaly ranking.
frame_metrics=[]
step_metrics=[]
for i,im in enumerate(seq):
    bb,c,a=bbox_centroid_area(im)
    g=cv2.cvtColor((composite(im,np.full((64,64,3),.5,np.float32))*255).astype(np.uint8),cv2.COLOR_RGB2GRAY)
    frame_metrics.append({
        "frame":i,"source_position":source_index[i],"bbox":bb,"centroid":c,"alpha_area":a,
        "sharpness":float(cv2.Laplacian(g,cv2.CV_32F).var())
    })
for i in range(64):
    c1=frame_metrics[i]["centroid"]; c2=frame_metrics[i+1]["centroid"]
    step_metrics.append({
        "from":i,"to":i+1,"source_from":source_index[i],"source_to":source_index[i+1],
        "premul_rgba_abs_diff":premul_diff(seq[i],seq[i+1]),
        "centroid_shift_px":float(math.dist(c1,c2)),
        "alpha_area_delta":abs(frame_metrics[i+1]["alpha_area"]-frame_metrics[i]["alpha_area"])
    })
diffs=[x["premul_rgba_abs_diff"] for x in step_metrics]
csh=[x["centroid_shift_px"] for x in step_metrics]
areas=[x["alpha_area_delta"] for x in step_metrics]
metrics={
    "method":"Practical-RIFE 4.25 arbitrary timestep 4x",
    "input_keyframes":17,"output_frames":65,"input_size":[64,64],
    "alpha_method":"premultiplied RGB interpolation + separately interpolated alpha",
    "preview_note":"64x64 technical motion proof; previews are only upscaled for viewing",
    "step_diff_mean":float(np.mean(diffs)),"step_diff_cv":float(np.std(diffs)/(np.mean(diffs)+1e-9)),
    "centroid_shift_mean_px":float(np.mean(csh)),"centroid_shift_max_px":float(np.max(csh)),
    "alpha_area_delta_mean":float(np.mean(areas)),
    "top_diff_steps":sorted(step_metrics,key=lambda x:x["premul_rgba_abs_diff"],reverse=True)[:10],
    "top_centroid_steps":sorted(step_metrics,key=lambda x:x["centroid_shift_px"],reverse=True)[:10],
    "frames":frame_metrics
}
(OUT/"metrics.json").write_text(json.dumps(metrics,ensure_ascii=False,indent=2),encoding="utf-8")

# Primary previews.
make_gif(seq,OUT/"yutuan_RIFE4x_48fps.gif",48)
make_gif(seq,OUT/"yutuan_RIFE4x_60fps.gif",60)
save_video_frames(seq,PREVIEW)
encode_mp4(PREVIEW,OUT/"yutuan_RIFE4x_48fps.mp4",48)
encode_mp4(PREVIEW,OUT/"yutuan_RIFE4x_60fps.mp4",60)

# Side-by-side baseline vs RIFE.
bg=checker()
compare_images=[]
for i,(left,right) in enumerate(zip(nearest,seq)):
    li=render_rgb(left); ri=render_rgb(right)
    pair=Image.new("RGB",(1024,560),(245,245,245))
    pair.paste(li,(0,48)); pair.paste(ri,(512,48))
    d=ImageDraw.Draw(pair)
    d.text((16,14),"Original 17 keyframes (held)",fill=(0,0,0))
    d.text((528,14),"Practical-RIFE 4.25 / 65 frames",fill=(0,0,0))
    d.text((16,540),f"output frame {i:02d}/64",fill=(0,0,0))
    pair.save(COMPARE/f"{i:04d}.png")
    compare_images.append(pair)
compare_images[0].save(OUT/"original17_vs_RIFE65_48fps.gif",save_all=True,append_images=compare_images[1:],duration=round(1000/48),loop=0,optimize=False)
encode_mp4(COMPARE,OUT/"original17_vs_RIFE65_48fps.mp4",48)
make_contact(seq,OUT/"rife_key_positions_contact_sheet.png")

report=[
"# 雨团 17→65 帧 RIFE 转身技术样片","",
"## 流水线",
"17 张关键帧 → 离线统一画布/脚底/角色高度/中心 → Premultiplied Alpha → Practical-RIFE 4.25 → 每段 t=0.25/0.5/0.75 → 65 帧。",
"",
"## 本次验证口径",
"- 输入为 64×64 技术验证版，仅用于判断运动连续性和 RIFE 是否产生结构伪影；预览放大到 512px。",
"- 正式进 App 前必须用 256px 或更高分辨率重跑，不能直接用这批低清验证帧。",
"- Android 工程没有修改。",
"",
"## 自动指标",
f"- 输出帧数：65",
f"- 帧间 premultiplied RGBA 平均变化：{metrics['step_diff_mean']:.6f}",
f"- 帧间变化 CV：{metrics['step_diff_cv']:.3f}",
f"- 重心平均位移：{metrics['centroid_shift_mean_px']:.3f}px（64px 输入）",
f"- 重心最大单步位移：{metrics['centroid_shift_max_px']:.3f}px",
"",
"## 最大变化步骤",
]
for x in metrics["top_diff_steps"][:8]:
    report.append(f"- {x['from']:02d}→{x['to']:02d}（源姿态 {x['source_from']:.2f}→{x['source_to']:.2f}）：diff={x['premul_rgba_abs_diff']:.6f}, centroid={x['centroid_shift_px']:.3f}px")
(OUT/"REPORT.md").write_text("\n".join(report),encoding="utf-8")
print("\n".join(report))
