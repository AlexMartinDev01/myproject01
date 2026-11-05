# 知行罗盘 · Render 云端 AI 部署说明

这个分支基于 V0.5.0，不更改已有 APK。目标是让**普通 AI 问答**无需 Windows 电脑开机即可使用。

## 一键创建云端网关

[点击从 GitHub 部署到 Render](https://render.com/deploy?repo=https://github.com/AlexMartinDev01/myproject01/tree/feature/knowledge-compass-cloud)

1. 登录 Render，必要时授权访问 GitHub 的 `AlexMartinDev01/myproject01`。
2. 查看 Blueprint：仅创建一个 Python Web Service，测试阶段计划为 **Free**。请先核对费用。
3. Render 在初次创建时会要求你填写两个秘密变量：
   - `DEEPSEEK_API_KEY`：在 DeepSeek 控制台撤销之前公开的旧密钥，并使用新密钥。
   - `APP_DEVICE_TOKEN`：**自己创建一串独立的高强度随机字符串（建议至少 32 位）**。务必复制保存，稍后在 App 中输入。此令牌不是 DeepSeek Key。
4. 其余模型和部署命令已写在根目录 `render.yaml`，不用手工设置。
5. 确认创建，待 Render 控制台服务状态为 **Live**。抄下服务的 `https://....onrender.com` 地址。

## 手机 App 设置

打开安装好的知行罗盘 → 我的 → 设备与 AI 设置：

- AI 服务地址：Render 提供的 `https://....onrender.com`（不加 `/health`）。
- 设备配对令牌：刚刚在 Render 设置的 `APP_DEVICE_TOKEN`。
- 点击保存 → 测试节点 → **真实 AI 问答测试**。

测试节点只能说明应用响应且已配置密钥；真实问答成功才说明 DeepSeek 真正可用并会计费。

## 隐私、安全与限制

- 云端请求可能由第三方 Render 与 DeepSeek 处理。**当前云端网关拒绝旧版日记授权与 PDF 解析请求，但旧 APK 可能在收到拒绝前已将请求内容传到网关。请勿在旧版中选择私密日记分析或 PDF 云端解析**，直到 App 更新并明确提示云端处理地点。
- 不要把 API Key 写进 GitHub、APK 或聊天。不要把设备令牌发到聊天。
- 这是**单人开发预览服务**，不能向其他用户公开同一个设备令牌。
- Render Free 在闲置后可能休眠，现有手机节点连接测试只有 12 秒超时，冷启动时可能提示失败；稍后重新尝试或者升级到有持续实例的付费计划。
- Render Free 不保证适合生产；不要把任何长期唯一副本放在服务端临时磁盘。移动端仍然独立保存日记和笔记。
- 未来正式版需要：稳定 Android 签名、用户身份隔离、按用户使用量限流、计费保护、云授权说明、共享的一次性授权存储。
- **此 Blueprint 关闭自动部署**。你审核代码更新后才手工部署新版本。

## 测试

本分支增加 `backend/tests/test_cloud.py`，验证无密钥泄露的存活检查、缺少设备令牌拒绝访问、云端禁用旧版日记访问等。并保留原先的 Backend/Flutter CI 测试。

本服务没有自动执行真实 DeepSeek 调用；上线后请在手机上做真实问答测试，并在 Render Dashboard 查看是否调用成功。 
