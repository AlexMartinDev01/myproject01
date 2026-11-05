# 知行罗盘 · Knowledge Compass — Android 开发预览版

> 当前交付：**有实际离线数据操作、AI 节点协议及服务端测试的开发预览源码**，不是经全功能验收的商业正式版。
>
> 本仓库不得包含真实 DeepSeek API Key。曾经在聊天记录中公开的密钥应立即轮换。

## 已实现与可验证

- Android Flutter 五大导航入口，PRD 的 14 个主要模块和三个辅助状态（部分辅助状态作为对话框/情境页）。
- SQLite 本地记录持久化：来源入箱、状态、知识卡、学习主题/任务、决策与复盘。
- 私密日记通过 Android 安全存储单独保存；默认知识搜索与 AI 资料构造排除日记。
- 每次私密日记 AI 使用前弹出预览/明确同意；后端一次性 90 秒授权令牌。
- FastAPI 私人 AI 网关：DeepSeek `deepseek-chat`，有认证的 `/v1/chat`、`/v1/consent`、`/health`。
- 用户提供真实正文才能生成基于材料的 AI 摘要；平台视频元信息不得被误当全文。
- PDF 文件复制进 Android 本地沙盒，连接 Windows 后可上传到 `/v1/pdf` 提取带页码文本（扫描 PDF 需另接 OCR）。
- GitHub Actions 云端 Flutter 构建流水线与 APK 工件上传。
- 后端鉴权/隐私授权自动测试与 Flutter 组件测试。

## 未完成（不能声称已验证）

- 抖音/小红书/B站/YouTube 的官方授权 API、字幕自动获取及一键 Android 系统分享接收（目前是手动粘贴或文件导入）。
- 公共账号体系、多用户云同步、冲突解决、加密全量备份与自动恢复。
- EPUB/DOCX 全文解析（首版可收文件；PDF 提取可用，扫描版 OCR 未集成）。
- 全文 FTS5 与向量混合检索、知识图谱、来源自动核验、公式化决策矩阵评分与权重敏感性分析。
- UI 逐像素对齐全部 17 张视觉原型；目前实现了统一配色、圆角、卡片和业务布局。
- Android 实机端到端测试以及 DeepSeek 真实 API 成功调用。连接并测试后方可确认。

## 开始使用 Android

1. 安装 Flutter 稳定版和 Android SDK。
2. 在项目根目录执行 `flutter create --project-name zhixing_compass --org com.zhixingcompass --platforms android .`。
3. 在生成的 AndroidManifest 中加入 `android.permission.INTERNET`（云构建流程会自动处理）。
4. 运行 `flutter pub get && flutter test && flutter run`。
5. 首次进入 App，点击“开始”，离线记录可立即使用。

## 启用 Windows / 私有 AI 服务

服务器应在用户可访问的可信网络内，**不要把没有 TLS 和认证的服务暴露公网**。

1. 在 Windows 电脑安装 Python 3.12+。
2. `pip install -r backend/requirements.txt`
3. 根据 `backend/.env.example` 生成本机环境变量：
   - `DEEPSEEK_API_KEY`：重新生成、未泄露的密钥。
   - `APP_DEVICE_TOKEN`：随机长令牌，供 App 配对，不是 DeepSeek API Key。
   - `DEEPSEEK_MODEL=deepseek-chat`。
4. 从仓库根目录运行 `python -m uvicorn backend.main:app --host 127.0.0.1 --port 8000`。
5. 通过可信的 HTTPS 反向代理/私有网络让 Android 可连接节点，在 App“设备与 AI 设置”中输入服务地址和 `APP_DEVICE_TOKEN`。
6. 点击“测试节点连接”，然后进入 AI 思考室进行一次真实问答确认。

如果 Windows 电脑没有可用的 HTTPS 访问路径，不能假设公网访问或普通 HTTP 能在 Android 正式构建里安全工作。

## 云端编译与下载

将整个项目提交到已启用 GitHub Actions 的 GitHub 仓库，检查 Actions → `Flutter Android APK · Knowledge Compass`。
构建必须真正通过 backend-tests、Flutter analyzer、Flutter tests 和 Android release build 才会输出 `KnowledgeCompass-Android-APK` 构件；下载 ZIP 解压后得到 `app-release.apk`。

⚠️ GitHub Actions 不会自动将你的 DeepSeek Secret 烧录进 APK。这是必要的密钥安全保障。

## 关键安全约束

- 私密日记每次发送前必须独立确认；AI 节点的一次性令牌不能跨请求复用。
- 被导入的网页、PDF 或字幕是证据数据，不得视为系统指令。
- AI 回答不代表事实核验；展示的来源 ID 仅表示允许参考的来源列表。
- 未有来源全文不生成伪造的“全文总结”。
- 开放注册、收费或面向大众公开发布前，需要先完成多用户隔离、数据保护、版权与平台接口审查、安全渗透测试。

## 项目结构

- `lib/main.dart` — 全局主题、页面容器与导航。
- `lib/screens.dart` — 业务页面和交互。
- `lib/core/store.dart` — 本地 SQLite、记录 CRUD、日记隔离。
- `lib/core/ai.dart` — AI 节点 API 和 PDF 上传。
- `backend/main.py` — DeepSeek 认证网关与 PDF 提取。
- `backend/tests/` — 认证与单次授权回归测试。
- `.github/workflows/android-apk.yml` — GitHub Actions 构建 APK。

完整需求来源：《知行罗盘完整 PRD 与技术架构 V2.0》。
