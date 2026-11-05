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

## V0.3.2 AI 稳定性（新增）

- 当前 API 模型默认 `deepseek-flash`（官方支持）；可在 Windows 节点通过 `DEEPSEEK_MODEL=deepseek-v4-pro` 改成 Pro。
- 推荐在 Windows 上运行 `python -m backend.setup_windows`，输入**新生成、未公开**的 API Key，密钥写入系统凭据管理器而不是 APK。
- `GET /health` 的 `provider_configured=true` 不等于真实 DeepSeek 调用成功；请在手机设置页面点击“真实 AI 问答测试”。
- 关键提醒：曾在聊天中展示的旧 API Key 请立即撤销并重置。

本机普通笔记可以离线使用；网络 AI 的真实请求、设备配对和 Windows 服务持续运行需要由安装者配置。本项目不会把 API Key 放进仓库、CI 或安装包。

## V0.4 持续笔记（新增）

- 同一个 URL/书籍来源可追加多条阅读笔记，重复保存相同链接且带正文时，正文新增为笔记而不覆盖原来源。
- 来源详情支持“编辑来源正文和标题”“来源版本历史”“追加一条阅读笔记”。
- 阅读笔记支持章节、独立标题、编辑、删除、历史恢复；历史恢复会新建修订版本，旧版本不可变。
- SQLite 数据库从 Schema 2 升级到 Schema 3：原 `entries` 和日记安全存储不动，新增 `source_notes`、`note_revisions`、`source_versions`，并为旧来源保存一个原文基线快照。
- 普通知识 JSON 备份格式升级到 `zhixing-compass-v2`，可恢复笔记及历史；仍兼容 v1 普通数据备份。**不包含私密日记与 PDF 等二进制附件。**
- 云端 Flutter CI 执行数据迁移、笔记历史和 UI 保存操作的自动化回归测试，但必须另外完成实际安装升级和真机测试才可公开发行。

### 特别注意 Android 安装签名

当前 GitHub Actions 中 Flutter 新生成的 Android 项目默认使用开发用签名；它**不能保证跨次云构建的签名一致**。
如果已有旧版 APP 的用户数据，**不要卸载旧版再安装新 APK**：旧版私密日记不会包含在普通 JSON 备份中，卸载可能导致永久丢失。
正式升级验证与公开发布前必须建立由仓库管理员在 GitHub Secrets 管理的**稳定 Android 签名密钥**，实际测试同包名、同签名和版本号递增升级。不要把签名私钥提交进公开 GitHub 仓库。

V0.4 在「账户与隐私」中使用 Android 系统保存文件对话框保存 `.json` 备份，便于实际迁移；注意该备份仍不包含私密日记和原始二进制附件。