# USGM-05 实际测试记录（2026-10-26）

> 只记录真正执行过的测试，不把电脑模拟测试描述为板上或原电子秤实测。

| 项目 | 实际执行 | 结果 | 证据 |
|---|---|---|---|
| C 核心边界/状态机 | GCC C11, AddressSanitizer + UndefinedBehaviorSanitizer | PASS | 83 assertions |
| C 大量完整放取周期 | 固定随机种子，2500次完整放取，含重复读数 | PASS | small 986, medium 522, large 992，合计2500 |
| JS 报告指标单元测试 | Node.js `node --test` | PASS | 6/6 |
| Python 资源和接口检查 | Python unittest | PASS | 6/6 |
| JS 语法检查 | Node.js `--check` | PASS | `app.js`、`report.mjs` |
| Python 语法检查 | `compileall` | PASS | `tools/`、`tests/` |
| ESP-IDF 5.3 ESP32-C3 RISC-V 交叉编译及链接 | GitHub Actions `espressif/idf:release-v5.3` | **PASS** | [云端构建运行 40340871013](https://github.com/AlexMartinDev01/myproject01/actions/runs/40340871013)，提交 `c3c618f` |
| ESP32-C3 烧录/启动 | 没有用户实体开发板 | **NOT RUN** | 待硬件 |
| HX711 标定精度 | 没有用户实体传感器/参考砝码 | **NOT RUN** | 待硬件 |
| MAX98357A 实际音量/清晰度 | 没有喇叭/音频实物 | **NOT RUN** | 待硬件 |
| 原地秤显示信号监听 | 缺少目标电子秤PCB/实测协议 | **NOT IMPLEMENTED / NOT RUN** | 不能宣称已适配所有地秤 |
| 72小时压力与EMC | 无实物/工装 | **NOT RUN** | 待硬件 |

## 通过定义

- `PASS`：实际执行且退出状态为0，日志未发现断言失败。
- `NOT RUN`：当前没有相应运行环境或硬件，绝不写作通过。
- `NOT IMPLEMENTED`：缺失特定原秤协议/显示硬件，必须以后开发，无法以HX711实验替代。

## 当前已知不足

1. Flash 日志在 SPIFFS 挂载失败时使用开发原型自动格式化；正式产品需修改为有备份的受控恢复。
2. 首批语音资源由本地离线语音合成产生，中文语音清晰度尚未做真人试听，需要替换合格音频。
3. ESP-IDF 5.3 云端交叉编译及链接已通过，但仍**未在实际 ESP32-C3 开发板上烧录和启动**；交叉编译不能替代电气、传感器或扬声器验收。
4. 电子秤显示信号并不通用；当前代码只对自建HX711称重实验提供实物读取驱动，真实显示监听仍未实现。
5. 全部事件只记录已成功识别/确认的放取周期；想计算漏检率，需另行录入人工完整放取尝试次数。
6. 无外壳裸板仅供桌面实验，不能承诺其满足工业现场长期使用要求。

## GitHub CI 编译审计追踪

- 初次构建因 `http.c` 单行条件语句触发 `-Werror=misleading-indentation` 而失败，已修复。
- 第二次构建通过 C 编译，在链接嵌入音频/网页资源时因符号名不匹配而失败，已修复。
- 第三次构建 GitHub Actions [40340871013](https://github.com/AlexMartinDev01/myproject01/actions/runs/40340871013)：`native` 和 `esp-idf` 两项均 **PASS**，固件完成 ESP32-C3 交叉编译及链接。
- 浏览器端到端 Playwright 测试尝试执行，但此运行环境阻止浏览器访问本地测试 URL（`ERR_BLOCKED_BY_ADMINISTRATOR`），**未通过，不计入通过测试**。JS 报告逻辑的 Node.js 单元测试通过。
- 真实开发板和原地秤的物理测试仍未执行，不能根据 CI 编译成功宣称硬件能正常运行。
