import httpx
import pytest
from fastapi.testclient import TestClient
import backend.main as gateway

CLIENT = TestClient(gateway.app)
HEAD = {"X-Device-Token": "local-test"}


@pytest.fixture(autouse=True)
def secrets(monkeypatch):
    monkeypatch.setenv("APP_DEVICE_TOKEN", "local-test")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "offline-unit-test-key")
    gateway.pending.clear()


class FakeClient:
    def __init__(self, response_status=200, body=None, **kwargs):
        self.response_status = response_status
        self.body = body or {"choices": [{"message": {"content": "连接测试成功"}}]}
    async def __aenter__(self): return self
    async def __aexit__(self, *args): return None
    async def post(self, url, **kwargs):
        assert url.startswith("https://api.deepseek.com/")
        assert kwargs["json"]["model"] == "deepseek-flash"
        request = httpx.Request("POST", url)
        return httpx.Response(self.response_status, json=self.body, request=request)


def test_model_defaults_to_active_flash():
    assert gateway.DEEPSEEK_MODEL == "deepseek-flash"


def test_health_only_proves_configuration():
    response = CLIENT.get("/health", headers=HEAD)
    assert response.status_code == 200
    assert response.json()["provider_tested"] is False
    assert response.json()["provider_configured"] is True


def test_actual_chat_contract_with_mock_provider(monkeypatch):
    monkeypatch.setattr(gateway.httpx, "AsyncClient", FakeClient)
    response = CLIENT.post("/v1/chat", headers=HEAD, json={"question": "请只回答连接测试成功"})
    assert response.status_code == 200
    assert response.json()["answer"] == "连接测试成功"
    assert response.json()["model"] == "deepseek-flash"


@pytest.mark.parametrize("upstream,description", [
    (401, "密钥"), (402, "余额"), (404, "模型"), (429, "频繁"), (503, "不可用")
])
def test_provider_errors_are_actionable(monkeypatch, upstream, description):
    monkeypatch.setattr(gateway.httpx, "AsyncClient", lambda **kw: FakeClient(response_status=upstream))
    response = CLIENT.post("/v1/chat", headers=HEAD, json={"question": "验证异常"})
    assert response.status_code == 502
    assert description in response.json()["detail"]
    assert "offline-unit-test-key" not in str(response.json())