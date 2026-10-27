# 源代码复用审计

- 原始项目：USGM-05 现有浏览器/手机仿真 HTML，用户此前已提供，同源固件页面和 C 核心 WebAssembly。
- 真实业务算法：`docs/usgm_core.c` / `docs/usgm_core.h`，wasm binary `docs/usgm_core_original.wasm`，SHA256 `c5315a4fef7e29b0b7dc9b050f2439f7cb2d565017ed7f189d7f93761c5cc973`。
- 原移动端网页源码快照：`docs/index.html`、`docs/app.js`、`docs/report.mjs`。
- 新版浏览器图形部分：程序化三维几何、轨道相机及引脚/导线拓扑规则；**不是 SPICE 真实电路**。
- 手机 iframe 来自原项目已嵌入的固件网页；不是新绘制的伪手机。
- 虚拟原始采样值：根据模拟负载生成，不是原传感器实测。
- 声音：原 PCM 文件经 WebAudio 播放，不是 MAX98357A 电声仿真。
- 不采购 TM1637/OLED/RGB/蜂鸣器/面包板。
