# 星语 Tarot · 高保真交互原型 V0.1

本项目对应《AI塔罗APP_完整产品需求说明书_PRD_V1.0》，是**可运行网页/PWA 技术原型**，目前不是 APK，也没有真实 Android 真机验收。

## 已实现

- 完整的移动端页面流程：首页 → 开始占卜 → 提问 → 牌阵 → 洗牌 → 切牌 → 抽牌 → 翻牌 → 解读 → 追问 → 本地记录
- RWS 78 张牌的唯一稳定 ID / 22 张大阿尔卡那 / 56 张小阿尔卡那 / 三种固定牌阵
- 浏览器加密安全随机数 + Fisher–Yates 洗牌；无放回抽牌；逆位在抽牌时随机固定；抽牌后不能单独重抽
- HTML/CSS GPU 3D 翻牌、洗牌和切牌交互；`assets/gifs` 中提供动效素材与独立 GIF 演示
- 78 张可放大 SVG 矢量牌图（**开发占位画面**，不是完整 RWS 插画）
- 本地牌局历史、离线示例解读、本地继续追问；可选 DeepSeek 代理服务端正式解读
- 提交 AI 前显式征得用户同意；未配置 API Key 时不会冒充 DeepSeek 实时回答

## 快速运行

```bash
cd AI_Tarot_Prototype_V0.1
python -m http.server 8765
```

然后在浏览器打开 `http://localhost:8765`。移动端打开同一局域网内电脑的 IP 地址与端口（需根据网络设置调整防火墙）。

## 使用 DeepSeek（须更换泄露的旧密钥）

先在 DeepSeek 控制台撤销/重置已发在聊天中的密钥，使用**新密钥**，只保存在服务端 `.env`：

```bash
cp .env.example .env
# 编辑 .env，将 DEEPSEEK_API_KEY 改为新密钥，不要提交 Git
pip install -r requirements.txt
python -m uvicorn backend.main:app --host 127.0.0.1 --port 8765
```

无需同时运行 `http.server`。打开 `http://127.0.0.1:8765`，到解读页点击“使用 DeepSeek 生成正式解读”，明确确认后才会向后端发起请求。

**注意：** 不能将 API Key 直接写在客户端、网页 JS 或 APK 内。当前原型并未使用用户在聊天中发送的 Key，也未测试该 Key 的有效性。开发后端使用 `deepseek-flash`、`/chat/completions`、JSON 模式，并校验输出不得增删卡牌。正式部署必须补充账号鉴权、服务端抽牌权威、持久限额、审计及隐私合规评估。

## 动效素材

- `assets/gifs/AI_flip_sun.gif`：AI 生成的太阳牌翻转 GIF（原生约 358px，作为预览）
- `assets/gifs/AI_flip_star.gif`：AI 生成的星星牌揭示 GIF（原生约 358px，作为预览）
- `assets/gifs/shuffle_hq.gif`、`cut_hq.gif`、`draw_hq.gif`、`flip_hq.gif`：使用本项目可控矢量牌图制作的独立高清序列 GIF（如果随项目交付）
- `motion_lab.html`：基于 DOM/CSS 的交互动作预览；最终交互使用 CSS/WebGL/Flutter 原生动画，比 GIF 更高清、流畅、可触控。

## 测试

```bash
python -m pytest -q tests
```

## V1.0 尚需工作

1. 原生 Flutter 或 Android 工程、签名、APK/AAB 构建与真机性能测试。
2. 78 张传统意义上完整而准确的原创插画、牌义双向数据库的审校。
3. 企业级反滥用策略、密钥托管、服务端可信抽牌与隐私合规。
4. 真正的 DeepSeek 调用效果回归测试：本原型没有使用聊天里发送的密钥。

塔罗用于娱乐和自我探索，不应作为现实中的确定预测。

### PWA 安装体验

已提供 `manifest.webmanifest`、192/512 图标和 Service Worker，方便在支持的 Android 浏览器中通过“添加到主屏幕”体验。PWA 和原生 APK 不是同一种交付。首次打开时会缓存前端文件和 78 张开发牌面供离线抽牌；DeepSeek 解读仍需要网络。建议在 HTTPS 域名上部署，或在本机 localhost 调试。

### 当前验收记录（2026-11-06）

- Python 规则/API 单元测试：4 项通过。
- Node.js 交互状态机模拟：从提问、选阵到抽牌、揭牌、解读、保存和恢复完整通过。
- FastAPI TestClient 检查：根页面及必要资源返回 200；`/.env`、后端源码等访问返回 404；无密钥调用返回 503。
- GIF 规格：4 段 720 × 960、48 帧、24 帧/秒、约 2 秒；相应 MP4 同步导出。
- 未运行真实浏览器视觉自动化截图和 Android APK 真机测试；随包 6 张手机 UI 图为设计渲染稿，不是实机截图。
- 未调用 DeepSeek 真实计费 API，也未使用用户聊天中发送的密钥。
