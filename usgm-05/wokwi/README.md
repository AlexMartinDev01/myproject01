# USGM-05：Wokwi 虚拟称重实验台

这是一份**可交叉编译、已绘制接线、并提供自动化场景的仿真工程**，不等于已经完成虚拟芯片运行验收，更不等于真实设备验收。

## 1. 虚拟接线
- Wokwi ESP32-C3 DevKitM-1：与实物 SuperMini 使用相同 ESP32-C3 GPIO 编号，外观不同。
- 5kg Wokwi HX711：3V3→VCC、GND→GND、GPIO0←DT、GPIO1→SCK。**这是虚拟逻辑接法；Wokwi 官方文档将模块 VCC 标为 5V，购买的真实 HX711 应确认是否支持 3.3V、输出是否为 3.3V。**
- I²S 逻辑分析仪：GPIO4→D0(BCLK)、GPIO5→D1(WS)、GPIO6→D2(DIN)、公共 GND→GND。
- **真实 MAX98357A 喇叭**在 Wokwi 的标准元件库中没有对应完整声学模型；逻辑分析仪只证明 I²S 波形，不能代替功放发声、音质和供电验证。实物接法仍是 GPIO4/5/6→MAX98357A 的 BCLK/LRC/DIN，功放专用 5V 供电，SPK+/- 接 4Ω 3W 喇叭。

## 2. 仿真构建（ESP-IDF v5.3）
在项目 `usgm-05/firmware` 目录中，使用已启用的 ESP-IDF 终端执行：

```bash
idf.py -B build-wokwi -D 'SDKCONFIG_DEFAULTS=sdkconfig.defaults;sdkconfig.wokwi.defaults' set-target esp32c3
idf.py -B build-wokwi build
```

然后在 VS Code Wokwi 扩展中打开 `usgm-05/firmware` 工作区并启动 Wokwi。配置文件是 `diagram.json` 与 `wokwi.toml`。ESP-IDF 完整固件由 `build-wokwi/flasher_args.json` 提供。

**注意：仿真构建专用 `CONFIG_USGM_WOKWI_MODE=y`，不得烧录到真实传感器实验板。** 实体开发板必须使用普通构建（不带此配置）。

## 3. 操作测试
双击 Wokwi 的 HX711，将负载（单位 kg）设为：
- 0kg，保持至少 1.2 秒（重新布防）；
- 0.075kg，预期在终端出现 `USGM_GRADE grade=small`；
- 回到 0kg、保持至少 1.2 秒；
- 0.095kg，预期 `USGM_GRADE grade=medium`；
- 回到 0kg、保持至少 1.2 秒；
- 0.135kg，预期 `USGM_GRADE grade=large`。

现成 Wokwi 自动测试场景：`wokwi/three_grades.scenario.yaml`。如果有 Wokwi CLI Token，可执行：

```bash
cd usgm-05/firmware
wokwi-cli . --scenario ../wokwi/three_grades.scenario.yaml --timeout 120000
```

场景的串口预期日志**只有 Wokwi 真实执行后才能算测试通过**。当前助手没有 Wokwi CLI Token，不能谎称已运行虚拟芯片。

## 4. 手机网页的仿真
实体固件通过自己的 `USGM-05` SoftAP 热点提供网页；Wokwi 仿真构建改为连接模拟的 `Wokwi-GUEST`，沿用相同 HTTP/API/页面代码。Wokwi Private IoT Gateway 可将网页映射到 `http://localhost:9080`，能否使用取决于 Wokwi 账号权限。**电脑访问虚拟 HTTP 页面不等于实际手机接入真实热点已通过。**

## 5. 精度和报告的边界
Wokwi 官方 5kg HX711 模型约 0–2100 原始计数，对应 0–5000g。仿真版使用模型已知的约 0.42 count/g 转换值，以验证放取和分级过程，**并不运行真实称重传感器的零点、跨度、温漂、噪声标定**。该模型约 2.38g/count，不要用它证明 ±1g 精度和重量边界误差。

仿真 HTTP 状态包含 `environment=WOKWI_VIRTUAL_HX711`；报告 API 将 `can_claim_hardware_accuracy` 固定为 `false`，CSV 事件会标注 `WOKWI_VIRTUAL_HX711`。没有独立真值，准确率依然不能报告。

## 6. 哪些测试尚未完成
- 真实 Wokwi 云仿真运行与三档场景：需要实际 Wokwi 会话和运行日志；
- MAX98357A 音质、功放输出、真实 USB 5V 供电：需要实物；
- ESP32-C3 SuperMini 与 DevKitM-1 的实体引脚差异：须按实际板核对；
- 你原有地秤的显示信号旁路监听：尚未实现真实型号协议。

参考官方文档：
https://docs.wokwi.com/parts/wokwi-hx711
https://docs.wokwi.com/vscode/esp-idf
https://docs.wokwi.com/vscode/project-config
https://docs.wokwi.com/wokwi-ci/automation-scenarios

## Wokwi I2S 限制

Wokwi 2026 年 10 月的 ESP32 仿真兼容性表中，ESP32-C3 的 I2S 标记为不支持（https://docs.wokwi.com/guides/esp32）。因此只有仿真固件 `CONFIG_USGM_WOKWI_MODE` 会输出 `WOKWI_AUDIO_INTENT` 串口日志，实际设备使用的 `MAX98357A` I2S 固件不变。HX711 3.3V 仿真接线不代表实物电气规格。
