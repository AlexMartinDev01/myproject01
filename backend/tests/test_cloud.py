"""Cloud deploy guardrail: health is public but all privileged operations stay private."""
from fastapi.testclient import TestClient
import backend.main as gateway

client = TestClient(gateway.app)
HEADERS = {"X-Device-Token": "test-cloud-token"}

def test_public_liveness_does_not_reveal_secrets(monkeypatch):
    monkeypatch.setenv("APP_DEVICE_TOKEN", "test-cloud-token")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "unpublished-test-secret")
    response = client.get("/livez")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
    assert "unpublished-test-secret" not in response.text
    assert client.get("/health").status_code == 401

def test_cloud_blocks_legacy_client_journal_consent(monkeypatch):
    monkeypatch.setenv("APP_DEVICE_TOKEN", "test-cloud-token")
    monkeypatch.setenv("CLOUD_GATEWAY_MODE", "1")
    response = client.post("/v1/consent", headers=HEADERS, json={
        "private_text": "不能发送的私人日记",
        "request_id": "req-cloud-001",
        "question": "分析这些日记"
    })
    assert response.status_code == 403
    assert "云端" in response.json()["detail"]

def test_cloud_blocks_private_chat_even_with_forged_token(monkeypatch):
    monkeypatch.setenv("APP_DEVICE_TOKEN", "test-cloud-token")
    monkeypatch.setenv("CLOUD_GATEWAY_MODE", "1")
    response = client.post("/v1/chat", headers=HEADERS, json={
        "question": "总结日记",
        "private_text": "私密文本",
        "request_id": "req-cloud-002",
        "consent_token": "fake"
    })
    assert response.status_code == 403

def test_cloud_keeps_auth_and_nonprivate_chat_contract(monkeypatch):
    monkeypatch.setenv("APP_DEVICE_TOKEN", "test-cloud-token")
    monkeypatch.setenv("CLOUD_GATEWAY_MODE", "1")
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    response = client.post("/v1/chat", json={"question": "你好"})
    assert response.status_code == 401
    response = client.post("/v1/chat", headers=HEADERS, json={"question": "你好"})
    assert response.status_code == 503
