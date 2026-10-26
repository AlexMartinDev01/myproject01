# USGM-05 — ESP32-C3 低成本重量分级 / 中文语音 / 离线实验台

> **状态：软件原型与桌面模拟/静态测试可执行；真实 ESP32、HX711、MAX98357A 和任何原电子秤的硬件实测尚未完成。**
>
> 本项目用于你正在采购的 **5kg 圆形称重架（已带 HX711）+ ESP32-C3 SuperMini + MAX98357A + 4Ω 3W 喇叭**。没有 RGB 灯、蜂鸣器、OLED、面包板或外壳。
>
> **特别重要**：HX711 是实验用数字称重输入；它**不是**“旁路监听现有地秤显示驱动数据”。后者因尚无实际地秤芯片/波形，不能预先编写已经验证正确的解码固件。这版实现真实 HX711 输入与完整分级软件，为将来替换重量输入留下核心模块。

## 1. 当前代码已经实现

- 可复用的纯 C 重量区间判定、稳定性状态机、空秤重新布防、防重复计数、断线保护；使用整数毫克比较边界。
- 两种输入模式：`SIMULATION`（默认，可用手机设置模拟重量）和 `HX711`（经零点与跨度标定后读取 5kg 称重套件）。模式不能在有事件的批次里混用。
- 喇叭播报：MAX98357A I²S + 4Ω 3W 扬声器，单声道 16kHz PCM 语音资源。**录音为离线合成占位音频，发音清晰度未做真人验收；如不清楚，须用清晰有授权的普通话录音替换**。
- 手机本地 Wi-Fi SoftAP 页面：`USGM-05`，访问 `http://192.168.4.1`，不需要互联网。通过 `idf.py menuconfig` 修改公开示例的 Wi-Fi 密码。
- 手机设置大中小果边界、稳定判定时间、模拟输入、HX711 去皮与跨度校准；分级及音频在手机断开后仍运行。
- 独立事件日志（SPIFFS + 校验行）、断电恢复完整记录、参考等级追加记录；CSV 导出、JSON 分页、手机打印 PDF 报告。
- 报告 **只对人工独立标注的已接受事件**计算准确率。没有独立参考值时输出“不可报告准确率”；模拟事件明确标为 SIMULATION。
- 原型保存上限 **512 条分级事件/批次**，达到上限会显示存储故障，不默默覆盖历史记录。导出后再新建批次。

## 2. 硬件接线（第一阶段，自建 HX711 实验秤）

请先看清你买来的 **5kg HX711 套件四针接口丝印**，不要按线色猜 VCC/GND/DT/SCK。

| 模块端 | ESP32-C3 SuperMini | 说明 |
|---|---|---|
| HX711 VCC | 3V3 | **仅限确认 HX711 成品模块支持 3.3V 供电时** |
| HX711 GND | GND | 本套实验的公共参考地 |
| HX711 DT / DOUT | GPIO0 | 3.3V 输入，严禁送入 5V 逻辑 |
| HX711 SCK / PD_SCK | GPIO1 | 输出时钟 |
| MAX98357A VIN | USB 5V 供电支路 | **不得从 ESP32 3.3V 引脚驱动 3W 功放** |
| MAX98357A GND | GND | 共参考地 |
| MAX98357A BCLK | GPIO4 | I²S |
| MAX98357A LRC / WS | GPIO5 | I²S |
| MAX98357A DIN | GPIO6 | I²S |
| MAX98357A SPK+ / SPK− | 喇叭两端 | 桥接输出，**SPK− 不得接 GND** |

开发板和功放的电源地需要合理汇接、去耦；不要用单个可能限流的 3.3V 稳压输出直接供功放。用杜邦线连接仅适合低压桌面实验，接线必须固定，防止短路。未确定 5kg 套件的**实际引脚顺序、3.3V 模块兼容性与输出电平**前，**不要通电直连 ESP32**。

ESP32-C3 SuperMini 不同厂商可能占用不同 GPIO。建议购入前核对板子的引脚分布和 USB/启动脚占用；本固件不使用 GPIO2/8/9（启动配置引脚）。

### 显示信号旁路监听不包含在当前已验证硬件范围

将来想监听真实地秤“原 MCU → 显示驱动器”的线路，需要实测 PCB 的数字接口、电压、时序、地与隔离条件。**不能把地秤上的未知三根线直接接入 GPIO0/1/3。** 这是单独的硬件研发任务，尚无法验证，不允许宣传“支持所有电子秤”。

## 3. 准备开发环境

- Windows、Linux 或 macOS，安装 **ESP-IDF v5.3.x**，使用其 Python 和 Xtensa/RISC-V 工具链。
- 将工程放进本地目录，在已激活 ESP-IDF 环境的终端执行：

```bash
cd firmware
idf.py set-target esp32c3
idf.py menuconfig   # 修改 USGM-05 experimental settings → Wi-Fi SoftAP password
idf.py build
idf.py -p YOUR_SERIAL_PORT flash monitor
```

例如 Windows 可用 ESP-IDF PowerShell 工具终端。不要混淆 ESP32-S3 与 ESP32-C3；本工程目标芯片是 ESP32-C3。`firmware/partitions.csv` 针对 **4MB Flash**。

> **云端构建已验证：** GitHub Actions [40340871013](https://github.com/AlexMartinDev01/myproject01/actions/runs/40340871013) 中的 ESP-IDF v5.3 ESP32-C3 交叉编译和链接均通过。**尚未在真实 ESP32-C3 上烧录、启动、试听或测量 HX711**，不能以编译通过替代板上测试。

## 4. 首次使用

1. 不接 HX711，先通过 USB 给 ESP32 供电、烧录固件；默认 `SIMULATION`。
2. 手机连接 `USGM-05` 本地热点，打开 `http://192.168.4.1`，用软件模拟输入验证分级；手机可能提示“此 Wi-Fi 无互联网”，这是正常现象。
3. 导出模拟批次的 CSV，然后确认清空，切换 `HX711`。
4. 核实电压后连接 HX711：空秤时点击“零点标定”，等待显示 `标定完成`；放上已知重量砝码，输入其质量后点击“跨度标定”，再等待完成。
5. **拿未参与标定的砝码复测**。如果偏差或漂移不满足需要，先排查称重传感器固定、供电与标定；不要直接开始给果品出准确率报告。
6. 把大中小果阈值保存到设备，然后测试放果 → 稳定 → 喇叭播报 → 计数一次 → 取走 → 下一颗。
7. 测试批次结束后，在报告页人工录入独立参考等级，刷新报告，导出 CSV 或用浏览器“打印 / 保存 PDF”。

### 推荐初始分界值（仅举例）

- 最低有效重量：20g；小果：20–<80g；中果：80–<110g；大果：≥110g。
- 500ms 稳定窗口、1g 波动容忍、300ms 空秤确认。分度值、标定质量和实际工作环境可能要求重新调参。

## 5. 本地 API

| HTTP | 路由 | 说明 |
|---|---|---|
| GET | `/api/status` | 实时重量、模式、校准、存储、数量、配置 |
| POST | `/api/settings` | `small_upper_mg`, `medium_upper_mg`，可选 `min_mg`, `stable_ms`, `jitter_mg` |
| POST | `/api/simulate` | `weight_mg`，只在模拟模式有效 |
| POST | `/api/mode` | `{ "mode":"SIMULATION" }` 或 `{ "mode":"HX711" }` |
| POST | `/api/cal/zero` | HX711 零点采样；先确保空秤 |
| POST | `/api/cal/span` | `{ "reference_mg":100000 }` 跨度标定 |
| POST | `/api/batch/new` | **永久清空当前批次事件**（前端二次确认） |
| GET | `/api/events?offset=0&limit=50` | 单次最多 50 条事件 |
| POST | `/api/reference` | `{ "event_id":1,"reference_grade":2 }`；1小2中3大 |
| GET | `/api/report.json` | 可核验子集的报告摘要 |
| GET | `/api/report.csv` | 下载原始记录（带当时阈值） |

### 报告的科学边界

设备不可能自己知道某个水果的真实等级；如果缺失独立参考数据，报告仅能统计分类数量，**不能计算准确率**。未被检测到的放果事件也不会自动出现在日志里，若要计算漏检率、覆盖率，应另外提供人工记录的总尝试次数。当前 `HX711` 模式只是实验电子秤，不代表真实地秤显示通信监听已验证。

## 6. 严格测试

在能运行 GCC、Node.js 22 和 Python 3 的计算机上：

```bash
./tools/run_native_tests.sh
```

包含：纯 C 分级状态机的 AddressSanitizer/UndefinedBehaviorSanitizer 测试、2,500 次确定性放取压力测试、JavaScript 报告数学与 HTML 安全单元测试，以及音频/分区/UI 构建资源一致性测试。

**测试通过仍不代表硬件正确。** 在 ESP32 上需要额外完成板级编译、烧录、音频质量、供电、电平、HX711 标定准确度、长时间运行、真实地秤信号解码和原秤不受干扰验证。详细见 `docs/TEST_PLAN.md`、`docs/TEST_RESULTS.md`。

## 7. 安全与原型限制

- 无外壳裸板只限绝缘固定的实验环境；不能作为现场出售或长期使用的成品。
- SPIFFS 开发原型设置了 `format_if_mount_failed=true`：首次空白分区会初始化；若文件系统损坏可能格式化导致数据丢失。**经常导出日志，量产前必须改为受控恢复策略。**
- HTTP 位于本地 Wi-Fi 而非 HTTPS。务必通过 `idf.py menuconfig` 更换默认热点密码，限制连接者；不能当成无须安全审查的量产网络服务。
- 未实现 OTA、屏幕原始数字信号监听、多类型显示协议适配、独立漏检人工试验录入、完整计量认证或工业防护；不要将这些列为“已完成”。
- 喇叭预录音频仅作研发占位，中文播报的实际可懂度需要现场试听和必要的音频替换。

## 8. 项目结构

```text
firmware/            ESP-IDF 固件
  components/usgm_core/  与硬件无关的分级状态机
  main/              HX711 / I²S / SoftAP / 存储 / 本地 HTTP
  main/assets/       手机页面、报告模块及 PCM 音频
web/                 (页面代码位于 firmware/main/assets)
tests/               C、Node.js、Python 自动化测试
tools/               测试脚本、离线音频示例制作脚本
docs/                接线、安全、测试和验收记录
```
