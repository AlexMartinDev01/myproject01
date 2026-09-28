import json, os, sys, traceback
from pathlib import Path
import numpy as np
from PIL import Image

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
sys.path.insert(0,str(HERE))
from common import *

OUT=HERE/"results"
OUT.mkdir(exist_ok=True)
APP=ROOT/"ShiguangBox"/"app"/"src"/"main"

def assets():
    dec=OUT/"_decoded"; dec.mkdir(exist_ok=True)
    p0=dec/"turn_0_runtime.webp"; p22=dec/"turn_22_5.webp"; p67=dec/"turn_67_5.webp"
    decode_chunks(APP/"pet_assets"/"orange_idle",p0)
    decode_chunks(APP/"pet_assets"/"orange_turn_22_5",p22)
    decode_chunks(APP/"pet_assets"/"orange_turn_67_5",p67)
    return [
        p0,
        p22,
        APP/"res"/"drawable-nodpi"/"pet_orange_3q_right.webp",
        p67,
        APP/"res"/"drawable-nodpi"/"pet_orange_side_right.webp",
    ]

def load_rife():
    home=Path(os.environ["RIFE_HOME"]).resolve()
    sys.path.insert(0,str(home))
    sys.path.insert(0,str(home/"train_log"))
    import torch
    from train_log.RIFE_HDv3 import Model
    model=Model()
    model.load_model(str(home/"train_log"),-1)
    model.eval(); model.device()
    return torch,model

def rife_t(torch,arr):
    return torch.from_numpy(arr.transpose(2,0,1)).float().unsqueeze(0)

def rife_rgba(torch,model,a8,b8,t):
    a,b=f32(a8),f32(b8); aa,ba=a[...,3],b[...,3]
    ap,bp=a[...,:3]*aa[...,None],b[...,:3]*ba[...,None]
    a3=np.repeat(aa[...,None],3,2); b3=np.repeat(ba[...,None],3,2)
    with torch.no_grad():
        p=model.inference(rife_t(torch,ap),rife_t(torch,bp),float(t))
        q=model.inference(rife_t(torch,a3),rife_t(torch,b3),float(t))
    prem=p[0].cpu().numpy().transpose(1,2,0)
    alpha=q[0].cpu().numpy().transpose(1,2,0).mean(2)
    return to_rgba8(np.clip(prem,0,1),np.clip(alpha,0,1))

raw=[load_rgba(p) for p in assets()]
aligned=[normalize(x) for x in raw]
kdir=OUT/"keyframes"; kdir.mkdir(exist_ok=True)
for ang,im in zip(ANGLES,aligned): Image.fromarray(im).save(kdir/f"{ang:g}.png")

metrics={
    "raw":key_metrics(raw,"raw"),
    "aligned":key_metrics(aligned,"anchor_normalized"),
    "theory":{
        "src_over_overlap_alpha_formula":"1-t+t^2",
        "midpoint_alpha":0.75,
        "opacity_drop_percent":25.0
    },
    "methods":{},
    "rife_error":None
}
methods={}

for name,fn in [
    ("current_SRC_OVER",src_over),
    ("true_premul_blend",premul_blend),
    ("farneback_flow",flow_mid),
]:
    seq,mids=build_seq(aligned,fn)
    methods[name]=seq
    metrics["methods"][name]=seq_metrics(seq,aligned,mids,name)
    save_gif(seq,OUT/f"{name}.gif")

try:
    torch,model=load_rife()
    rife_fn=lambda a,b,t:rife_rgba(torch,model,a,b,t)

    # 4x is the direct A/B comparator used in comparison.png.
    seq,mids=build_seq(aligned,rife_fn)
    name="RIFE_4.25_premul_alpha"
    methods[name]=seq
    metrics["methods"][name]=seq_metrics(seq,aligned,mids,name)
    save_gif(seq,OUT/f"{name}.gif")

    # Preserve the exact 17-frame RIFE right-turn sequence for Android.
    frame_dir=OUT/"rife4x_frames"
    frame_dir.mkdir(exist_ok=True)
    for idx,frame in enumerate(seq):
        Image.fromarray(frame).save(frame_dir/f"frame_{idx:02d}.png")

    # Build a true left-turn sequence that starts from the SAME unmirrored idle frame.
    # Only directional key poses 22.5/45/67.5/90 are mirrored.
    # This removes the old experiment's frame-0 mirror flip at left-turn start.
    left_keys=[aligned[0]]
    for key in aligned[1:]:
        left_keys.append(np.ascontiguousarray(np.flip(key,axis=1)))

    left_seq,left_mids=build_seq(
        left_keys,
        lambda a,b,t:rife_rgba(torch,model,a,b,t)
    )

    left_frame_dir=OUT/"rife4x_left_frames"
    left_frame_dir.mkdir(exist_ok=True)
    for idx,frame in enumerate(left_seq):
        Image.fromarray(frame).save(left_frame_dir/f"frame_{idx:02d}.png")

    metrics["methods"]["RIFE_4.25_left_same_idle"]=seq_metrics(
        left_seq,left_keys,left_mids,"RIFE_4.25_left_same_idle"
    )

    # Same timing / same checkerboard: current Android-style SRC_OVER vs RIFE 4x.
    bg=checker()
    side_frames=[]
    current_seq=methods["current_SRC_OVER"]
    for left,right in zip(current_seq,seq):
        li=Image.fromarray((composite(left,bg)*255).astype(np.uint8))
        ri=Image.fromarray((composite(right,bg)*255).astype(np.uint8))
        pair=Image.new("RGB",(SIZE*2,SIZE),(238,238,238))
        pair.paste(li,(0,0)); pair.paste(ri,(SIZE,0))
        side_frames.append(pair)
    side_frames[0].save(
        OUT/"current_vs_rife4x.gif",
        save_all=True,
        append_images=side_frames[1:],
        duration=42,
        loop=0,
        optimize=False
    )

    # Density experiment: 2x / 4x / 8x. Generate each factor explicitly
    # because arbitrary-timestep RIFE can behave differently away from t=0.5.
    def build_factor(factor):
        out=[aligned[0]]
        mids_by_pair=[]
        for i in range(4):
            pair=[]
            for j in range(1,factor):
                t=j/factor
                z=rife_fn(aligned[i],aligned[i+1],t)
                out.append(z)
                if abs(t-0.5)<1e-8:
                    mid=z
            # seq_metrics expects [0.25, 0.5, 0.75]-like lists and reads index 1.
            # Replicate the exact midpoint for metric compatibility.
            pair=[mid,mid,mid]
            mids_by_pair.append(pair)
            out.append(aligned[i+1])
        return out,mids_by_pair

    metrics["rife_density"]={}
    for factor in (2,4,8):
        dseq,dmids=build_factor(factor)
        dname=f"RIFE_4.25_{factor}x"
        dm=seq_metrics(dseq,aligned,dmids,dname)
        dm["factor"]=factor
        dm["duration_560ms_effective_fps"]=(len(dseq)-1)/0.560
        dm["rgba_runtime_memory_mib_256px"]=len(dseq)*256*256*4/(1024*1024)
        metrics["rife_density"][dname]=dm
        save_gif(dseq,OUT/f"{dname}.gif")
except Exception as e:
    metrics["rife_error"]=repr(e)+"\n"+traceback.format_exc()

contact(methods,OUT/"comparison.png")
(OUT/"metrics.json").write_text(json.dumps(metrics,ensure_ascii=False,indent=2),encoding="utf-8")

lines=["# 橘团转身插帧实验","",
"实验对象：2.5.1 当前 0/22.5/45/67.5/90 度五张真实资源。正式分支未修改。","",
"## 已验证的结构性问题","",
"- 当前 Android 先画 A(alpha=1-t) 再以 SRC_OVER 画 B(alpha=t)。在 A/B 都不透明的重叠区，t=0.5 时最终 alpha=0.75，因此每个关键姿态区间中点都会有 25% 的透明度脉冲。","",
"## 方法指标"]
for name,m in metrics["methods"].items():
    core=[x["mid_core_alpha"] for x in m["pair_midpoints"] if x["mid_core_alpha"] is not None]
    edge=[x["mid_edge_ratio"] for x in m["pair_midpoints"]]
    lines.append(f"- {name}: 中点核心alpha={np.mean(core):.4f}; 中点边缘倍率={np.mean(edge):.3f}; 帧间变化CV={m['step_diff_cv']:.3f}; 平均锐度={m['mean_sharpness']:.1f}")
if metrics["rife_error"]:
    lines+=["","## RIFE 执行错误","",metrics["rife_error"][-4000:]]
(OUT/"REPORT.md").write_text("\n".join(lines),encoding="utf-8")
print((OUT/"REPORT.md").read_text())
