# 星语 AI 塔罗 V0.1：云端开发与部署

> 当前状态：已创建云端配置，**尚未上传 GitHub，尚未部署 Render，尚未完成生产级 API 安全**。

## 1. 代码仓库

建议在本人 GitHub 账号下新建专用仓库 `starweave-ai-tarot`。不要使用其他项目的仓库，避免覆盖现有文件。

本项目已添加 `.gitignore`、`.github/workflows/ci.yml`、`.devcontainer/devcontainer.json` 和 `render.yaml`，可直接作为仓库根目录提交。不要把此前聊天中公开过的 DeepSeek Key 提交至 Git 仓库，立即撤销旧 Key。

## 2. 云端编辑（GitHub Codespaces）

在 GitHub 仓库点击 Code → Codespaces → Create codespace。容器会自动安装 Python 依赖；在 Codespaces 终端运行：

```bash
uvicorn backend.main:app --host 0.0.0.0 --port 8765
```

在 Ports 面板通过 8765 端口打开网站预览。不要使用公开端口暴露未鉴权的 DeepSeek 接口。修改代码后 commit + push。Codespaces 是否可用及使用额度由 GitHub 帐号套餐和设置决定。

## 3. 自动测试（GitHub Actions）

push / PR 自动执行 Python 和 JavaScript 测试，检查仓库是否意外跟踪 `.env` 文件。这不是完整的秘密扫描，也不能替代人工审核。建议在 GitHub 仓库开启 secret scanning 和分支保护（若账号支持）。

## 4. 云端部署（Render）

在 https://dashboard.render.com/ 将 GitHub 仓库连为一个 Web Service，或创建 Blueprint 读取仓库根目录 `render.yaml`。

- Build: `pip install -r requirements.txt`
- Start: `uvicorn backend.main:app --host 0.0.0.0 --port $PORT`
- Health: `/api/health`
- Render 自动分配 HTTPS URL；关联分支更新后可自动重新部署。

**默认不要在公开部署中设置 DeepSeek API Key**。现有后端只实现单进程/IP 内存限流，不能防止公开服务被刷费。上线前需要注册/登录认证、可持久化的额度与防滥用机制、服务端权威抽牌与合规告知；完成上述安全事项后，再在 Render 环境变量中设置一把新生成的 `DEEPSEEK_API_KEY`，不要写到 `.env`、源码、GitHub Issues、截图或 README 中。

## 5. 本版非正式产品

本版是 Web/PWA 原型，不是 Flutter 安卓安装包。部分卡牌为原创占位图。未经真实设备/云端部署验收，不应宣称为可正式上线的付费产品。
