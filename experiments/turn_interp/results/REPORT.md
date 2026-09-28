# 橘团转身插帧实验

实验对象：2.5.1 当前 0/22.5/45/67.5/90 度五张真实资源。正式分支未修改。

## 已验证的结构性问题

- 当前 Android 先画 A(alpha=1-t) 再以 SRC_OVER 画 B(alpha=t)。在 A/B 都不透明的重叠区，t=0.5 时最终 alpha=0.75，因此每个关键姿态区间中点都会有 25% 的透明度脉冲。

## 方法指标
- current_SRC_OVER: 中点核心alpha=0.7450; 中点边缘倍率=1.221; 帧间变化CV=0.320; 平均锐度=740.1
- true_premul_blend: 中点核心alpha=0.9922; 中点边缘倍率=1.279; 帧间变化CV=0.243; 平均锐度=841.8
- farneback_flow: 中点核心alpha=0.9846; 中点边缘倍率=0.985; 帧间变化CV=0.294; 平均锐度=612.7

## RIFE 执行错误

ModuleNotFoundError("No module named 'torchvision'")
Traceback (most recent call last):
  File "/home/runner/work/myproject01/myproject01/experiments/turn_interp/evaluate.py", line 83, in <module>
    torch,model=load_rife()
  File "/home/runner/work/myproject01/myproject01/experiments/turn_interp/evaluate.py", line 34, in load_rife
    from train_log.RIFE_HDv3 import Model
  File "/tmp/Practical-RIFE/train_log/RIFE_HDv3.py", line 11, in <module>
    from model.loss import *
  File "/tmp/Practical-RIFE/model/loss.py", line 5, in <module>
    import torchvision.models as models
ModuleNotFoundError: No module named 'torchvision'
