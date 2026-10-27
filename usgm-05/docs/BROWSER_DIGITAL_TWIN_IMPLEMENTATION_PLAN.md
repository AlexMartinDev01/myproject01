# USGM-05 浏览器高保真数字孪生：可执行实施规范

状态：**开发蓝图与需求冻结，不代表以下新增功能已实现或验证。**

## 0. 最新硬件 BOM（覆盖曾经的 TM1637 决定）

- 实体：5kg 圆形透明亚克力秤台（内置四线应变梁与 HX711 模块）、ESP32-C3 SuperMini 4MB、MAX98357A I2S 功放、4Ω 3W 喇叭、合适的 5V USB 供电与安全连接材料。
- 明确不要：TM1637/OLED/任何独立数显、RGB 灯、蜂鸣器、面包板、外壳。
- 手机网页是唯一的可视化显示和阈值输入；手机断开后设备继续称重、分级、播报与记录。
- **真实地秤显示驱动旁路监听**属于以后独立适配任务；本实验平台输入来自 HX711 套件或其模拟模型，不得混淆。

## 1. 三种运行环境，必须互相标明来源

1. `BROWSER_MODEL`：独立浏览器数字孪生，精细 3D 元器件、可连线、虚拟 HX711 采样、原 C 分级核心 WASM、原手机 UI、音频同源文件 WebAudio、事件存储和 CSV/JSON。可无互联网安装于本机并运行。
2. `WOKWI_FIRMWARE`：官方 Wokwi ESP32-C3＋HX711 模型运行 ESP-IDF 仿真固件。ESP32-C3 I2S 不支持；只能验证数字协议及播报触发，不能验证 MAX98357A 真实喇叭。需要 Wokwi Gateway 才能访问模拟 ESP32 的真实 HTTP 服务（可能涉及付费）。
3. `PHYSICAL_HARDWARE`：真实 ESP32-C3＋HX711＋MAX98357A；唯一能验证电平、电源、称重误差与实际音量的来源。

三种模式不可把模拟数据标成实测。统一事件数据应包含 `source`、`raw`、`weight_mg`、`grade`、`clock_ms`、`event_id`、`calibration_version`、`threshold_version`、`accepted` 与参考真值（可空）。

## 2. 视觉三维与产品 1:1 参考

- 前端 React + Vite + TypeScript + Three.js（`@react-three/fiber` 可选）；资源 glTF/GLB。
- 第一版 3D 模型：用户指定 5kg 亚克力圆盘、白/银色称重梁、PCB HX711、四线输出接头、ESP32-C3 SuperMini 带 USB-C、MAX98357A、4Ω 3W 喇叭、五伏电源线、四芯称重线、三芯 I2S 与两芯 SPK±。
- 外观建模数据：用户商品正面图片可做视觉参照；需要多角度照片、实际板型号、整体尺寸、丝印等才能声称外观尺寸 1:1，不得臆造背面焊点。
- 3D 导线以引脚坐标绘制，连线节点与实际网表共享同一身份。可高亮/拔插 GPIO0 HX711 DT、GPIO1 SCK；GPIO4/5/6 I2S BCLK/LRC/DIN；GND 与供电、SPK+/SPK-（不得接地）。
- 断 HX711 数据线 → 采样超时并禁止新分级；断声音信号线 → 等级仍可产生，但音频链路模拟报警；断主机 5V → 所有服务停止。错误接法采用规则拦截，并不声称 SPICE 电气认证。

## 3. 单一业务核心和事件流

```text
3D 秤台操作 / 故障注入
    ↓
LoadCellModel(kg、噪声、零点漂移、采样率、饱和)
    ↓
HX711Model(raw 24bit-like codes + conversion ready/error)
    ↓
CalibrationEngine(raw→整数mg, 去皮/参考砝码标定)
    ↓
usgm_core.c → Emscripten WASM【不得复制成另一套 JS if-else】
    ↓
SharedStore(lastFrame, acceptedEvents, totals, source)
    ├─ React 3D 可视化/连接状态
    ├─ 原始固件手机 HTML/JS（通过 API Adapter，不复制业务规则）
    ├─ Web Audio 播放固件同源 PCM
    └─ 本地日志与 CSV/JSON/HTML报告
```

- 浏览器 WASM 和真实 ESP-IDF 两条链路使用同一 `usgm_core.c`、`usgm_core.h`，需用相同样本对两边做差异测试。
- 现有 `firmware/main/assets/index.html`、`app.js`、`report.mjs` 尽量原封不动复用；将 `fetch('/api/...')` 通过统一 `Transport` 转发到本地模拟 API 或真实固件 HTTP。
- `GET /api/status`、`POST /api/settings`、`POST /api/simulate`、`POST /api/cal/zero`、`POST /api/cal/span`、`GET /api/report.json`、`GET /api/report.csv` 等沿用现有契约，不新增不同语义。
- 采用主动订阅向 3D/手机即时同步；真实 ESP 固件现有默认通过前端轮询，WebSocket 只是可选后续优化，不能冒称已实现。
- 独立手机模拟机壳只呈现上述实际网页，没有额外实体数码管模型；可以给桌面用户展示辅助调试仪表，但不得标注为已采购器件。

## 4. 音频的严谨边界

- 浏览器 WebAudio 播放已存在的中文 PCM WAV/RAW 源素材，小果/中果/大果事件触发一次，不依赖网络；浏览器需要用户首次主动点击解锁音频。
- 3D 喇叭可同步做轻微振膜动画；这只是可视化，不代表 MAX98357A 输出电压、音质、功率已被真实仿真。
- 官方 Wokwi ESP32-C3 I2S 目前不支持：`https://docs.wokwi.com/guides/esp32`；切勿声称 Wokwi 已真实播放 MAX98357A 中文语音。
- 稳定、采样、断线场景应验证声音与计数都只触发一次；音频队列饱和须报告且不影响称重业务。

## 5. 三套实施阶段与验收门槛

**P0：跑通可信的 2D／3D 混合闭环**
- 底层同一 C WASM；1:1 API adapter；原手机 UI；原 PCM；同事件流；数值/事件/报告自动化测试。
- 三颗样本 75/95/135g（默认阈值 80/110g）各一次，重复放置不重复计数；取走后重新布防；设置 100/120g 后 95g 判为小果。
- 虚拟断线、未标定、过载、零点和跨度、未验证参考等级不算准确率。

**P1：高保真 3D 元器件与可连接导线**
- 可旋转/缩放、独立 PCB 节点、原用户圆形套件外观和透明部件；引脚选中与网表关联；断线故障演示；可以移动或组合线束。
- 采用照片多角度比对及已知尺寸验收；未提供的尺寸标为估计。

**P2：对照 Wokwi 固件与实物**
- 相同标准测试集下，WASM 与 Wokwi `usgm_core` 结果比较，并记录不同数据模型的数值误差和软硬件不支持项。
- 后续接入真实板，手机访问 ESP32 SoftAP；实物体积、噪声、延迟、功放音量、接线电平检查。
- GitHub CI 运行 unit/integration/Playwright；浏览器录像必须展示真实操作录制，不用动画冒充 Wokwi。

## 6. 建议新目录

```text
usgm-05/
  browser-digital-twin/
    src/scene/        # 3D model loading, wire mesh and placement UI
    src/models/       # scales, boards, speaker GLB + manifest
    src/simulation/   # loadcell, HX711, calibration, fault injection
    src/bridge/       # mock/live ESP32 API transport and event bus
    src/core-wasm/    # 编译自 firmware/components/usgm_core
    src/phone/        # 真实手机 UI 的包裹与适配
    src/audio/        # PCM WebAudio playback
    src/reports/      # 同源 report.mjs
    tests/            # 无源 HTML 假动画；浏览器端到端测试
  firmware/           # 已有 ESP-IDF 实际固件，不随意改写
  wokwi/              # 原始 Wokwi 数字电路验证
```

## 7. 严格定义交付“完成”

- `BROWSER_MODEL` 端到端手动及自动测试均有输出：屏幕录制、事件日志、帧对比、报告。
- 分级核心以真实 C 编译的 WASM 验证，手机改动能真正改变分级核心设置。
- 所有器件和连线坐标与已验证网表一致，缺少产品尺寸时附未核实清单。
- 不把浏览器模型称为 HX711/功放实物验收，或假装浏览器就是 Wokwi MCU 模拟器。
- 手机连接/离线配置模式与实体设备模式逻辑保持一致；实物不能跑的功能不作为“成功”。

## 8. 官方技术资料

- Three.js GLB: https://threejs.org/manual/en/loading-3d-models.html
- C → WebAssembly: https://developer.mozilla.org/en-US/docs/WebAssembly/Guides/Existing_C_to_Wasm
- Wokwi ESP32 peripherals: https://docs.wokwi.com/guides/esp32
- Wokwi IoT Gateway: https://docs.wokwi.com/guides/esp32-wifi
