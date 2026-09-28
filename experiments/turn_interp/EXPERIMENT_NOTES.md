# 拾光盒橘团转身：2.5.1 根因与插帧实验记录

## 实验边界
- 正式基线：`shiguangbox-v1@a9bdadc89f5937534be4d363e9b32ece9fd56c5b`（V2.5.1）
- 实验分支：`experiment/turn-interpolation-eval`
- 正式 App 代码未修改；实验分支只增加 workflow、评测脚本和结果文件。
- 输入严格使用 Gradle 构建时真正进入 APK 的 0° / 22.5° / 45° / 67.5° / 90° 五张资源。

## 已确认根因

### 1. 当前双图 SRC_OVER 不是“恒定不透明度 Crossfade”
当前 Android 每帧先绘制 A(alpha=1-t)，再用默认 SRC_OVER 绘制 B(alpha=t)。

两图在同一像素均完全不透明时：
```
alpha_out = t + (1-t)*(1-t) = 1 - t + t^2
```
t=0.5 时 alpha=0.75，因此每个相邻关键姿态中点都会产生理论 25% 的透明度脉冲。

实测当前方案四个区间中点核心 alpha：
- 0→22.5: 0.7451
- 22.5→45: 0.7450
- 45→67.5: 0.7450
- 67.5→90: 0.7450

这证明用户观察到的“闪”至少有一部分是合成公式本身造成的，并非掉帧。

### 2. 构建时实际关键帧资源规格不统一
运行时 0° 资源由 `pet_assets/orange_idle` 解码，实际为 512×512；其余若干方向资源为 256×256。
虽然 PetRigView 会按 alpha bounds 对齐脚底、中心和可见高度，但不同源分辨率仍会带来边缘/纹理采样差异。

### 3. 对齐后姿态仍有显著结构差
统一为 256×256、同脚底、同可见高度后：
- 0→22.5：silhouette IoU 0.763，轮廓 Chamfer 7.67 px，宽度 +10.8%
- 22.5→45：IoU 0.803，Chamfer 5.74 px，宽度 -6.5%
- 45→67.5：IoU 0.786，Chamfer 6.17 px，宽度 +5.2%
- 67.5→90：IoU 0.878，Chamfer 3.24 px，宽度 +2.8%

说明五张图不是同一 rig 的严格连续旋转，尤其 0→22.5 和 45→67.5 差异较大。

## 对照实验

全部方法先做相同的 Anchor 标准化：
- 256×256 canvas
- 可见高度 220 px
- X 居中
- 脚底 y=246
- 透明 PNG 使用 premultiplied RGB + alpha 分离处理

### A. 当前 Android SRC_OVER
17 帧；模拟当前两张 Mesh 图用互补 alpha 先后 SRC_OVER。

结果：
- 中点核心 alpha：0.7450
- 中点半透明区域：约 99.6%
- 中点边缘倍率：1.221
- 平均帧间变化：0.0501
- 帧间变化 CV：0.320
- 重心步长 CV：0.481

视觉：明显周期性变淡 + 双轮廓，是当前“闪”的直接复现。

### B. 正确 premultiplied cross-dissolve
17 帧；先在离屏数据中线性混合 premultiplied RGB 和 alpha，再作为单张图显示。

结果：
- 中点核心 alpha：0.9922
- 半透明区域：约 22.7%（四区间约 16%~27%）
- 中点边缘倍率：1.279
- 平均帧间变化：0.0224
- 帧间变化 CV：0.243

视觉：透明度闪烁基本消失，但由于两个关键姿态五官/轮廓不一致，中间帧出现双眼、双耳、双尾巴/鬼影。
结论：仅修合成方式能解决“闪暗”，不能解决 Pose Jump。

### C. Farneback 双向光流
17 帧；在 premultiplied RGBA 上双向 warp 后合成。

结果：
- 中点核心 alpha：0.9846
- 中点半透明区域：约 11.8%
- 中点边缘倍率：0.985
- 平均帧间变化：0.03125
- 平均锐度：612.7

视觉：双影大幅减少，但耳朵、脸、爪子出现拉扯/软化。
结论：correspondence/warp 路线是对的，但传统光流不够稳。

### D. Practical-RIFE 4.25
官方 Practical-RIFE 源码 + 4.25 权重；CPU GitHub Actions 实际推理。
RGB 采用 premultiplied 数据，Alpha 单独送入模型并重建 RGBA。

4× / 17 帧结果：
- 中点核心 alpha：0.9883
- 中点半透明区域：约 4.6%
- 中点边缘倍率：0.970
- 平均帧间变化：0.03221
- 帧间变化 CV：0.276
- 重心步长 CV：0.319
- 平均锐度：1021.2

视觉：当前四组中最接近目标。透明闪烁与双轮廓基本消失；仍在 0→22.5、45→67.5 的耳朵/眼睛/抬爪区域存在轻微结构重解释，来源是关键帧自身不一致。

## RIFE 帧密度实验（560ms）
| 倍率 | 总帧数 | 等效 FPS | 平均单步变化 | 帧间变化 CV | 重心步长 CV | 256px ARGB 内存 |
|---|---:|---:|---:|---:|---:|---:|
| 2× | 9 | 14.29 | 0.05442 | 0.273 | 0.303 | 2.25 MiB |
| 4× | 17 | 28.57 | 0.03221 | 0.276 | 0.319 | 4.25 MiB |
| 8× | 33 | 57.14 | 0.01796 | 0.621 | 0.706 | 8.25 MiB |

8× 虽然平均单步变化更小，但任意 timestep 输出的步长均匀性明显变差，因此不应简单认为“越多帧越顺”。当前资源下 4× / 17 帧是更稳妥的平衡点。

## 工具链故障与修正记录
1. 首轮：Pillow 无法创建 WebP decoder；改为 OpenCV 优先解码。
2. 修正输入口径：发现 res 内 idle 可能被 Gradle preBuild 覆盖，改为直接解码 `pet_assets/orange_idle`，复现真实 APK 输入。
3. 暴露 512×512 vs 256×256 原始 canvas 不一致；原始指标改为归一化坐标，算法比较统一做 Anchor 标准化。
4. 首次 RIFE 推理缺 `torchvision`；补 `torchvision==0.17.2` 后 RIFE 4.25 CPU 推理成功。
5. Hugging Face Jobs 计算通道返回 402 Payment Required，因此正式实验改用 GitHub Actions CPU，保证可复现。

## 对其他路线的工程判断
- AnimeInterp：方法方向适合卡通，但官方参考实现要求非常旧的 PyTorch 环境，并且测试代码硬编码 CUDA、依赖预计算 SGM flow；直接拿当前仓库资源做公平、可复现试验需要先做较大的现代化移植。
- Skeleton-Driven Inbetweening：需要 foreground/occlusion mask、骨架、z-order、T-junction 等交互标注；官方 README 还把 3D rotation support 列为后续研究项。对当前 0°→90° yaw 转身并非低成本直接替代。
- AnimeInbet/TPS line inbetweening：核心输入域是线稿/线结构，不是当前已经完整上色、带透明 alpha 的桌宠 sprite，因此不作为第一落地路线。

## 下一版建议（尚未写入正式分支）
1. 立即废弃运行时“两张 Bitmap 互补 alpha + SRC_OVER”的转身合成。
2. 离线统一关键帧：canvas / 可见高度 / 脚底 / 中心 / 色彩 / 纹理分辨率。
3. 对 0→22.5 和 45→67.5 两个最差区间先修正关键 Pose，再跑 RIFE 4.25。
4. RIFE 采用 4×，生成 17 帧右转序列；左转在 Canvas 层镜像，避免复制整套 Bitmap。
5. Android 转身时每个 VSYNC 只显示一张已经生成好的帧；不 Crossfade、不双 Mesh、不实时 AI。
6. 转身完成后再启动明显 WindowManager 横移；停止时先停止位移再倒序播放 16→0。
7. 生成后自动检查：alpha area、centroid、silhouette IoU/Chamfer、边缘数、异常锐度；异常帧人工复核。
