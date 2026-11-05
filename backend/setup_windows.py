"""Local interactive setup; API key is stored in Windows Credential Manager."""
from __future__ import annotations

import getpass
import os
import secrets
from pathlib import Path


def main() -> None:
    import keyring
    folder = Path(__file__).resolve().parent
    dest = folder / ".env"
    if os.name != "nt":
        print("建议在 Windows 上运行；当前平台密钥存储能力取决于系统 keyring 后端。")
    if dest.exists():
        raise SystemExit(".env 已存在；为了避免覆盖设备配对信息，本次未改写。请备份并检查后手动更新。")
    key = getpass.getpass("输入全新 DeepSeek API Key（不会显示）：").strip()
    if not key or len(key) < 12:
        raise SystemExit("未输入有效密钥；未进行配置")
    keyring.set_password("zhixing-compass", "deepseek-api-key", key)
    device_token = secrets.token_urlsafe(36)
    dest.write_text("APP_DEVICE_TOKEN=" + device_token + "\nDEEPSEEK_MODEL=deepseek-flash\nDEEPSEEK_BASE_URL=https://api.deepseek.com\n",encoding="utf-8")
    try:
        dest.chmod(0o600)
    except OSError:
        pass
    print("配置成功，密钥存储于系统凭据库。请妥善保存以下设备配对令牌：")
    print(device_token)
    print("启动命令：python -m uvicorn backend.main:app --host 127.0.0.1 --port 8000")


if __name__ == "__main__":
    main()