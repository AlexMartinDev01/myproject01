import os
import pytest
from fastapi.testclient import TestClient
from backend.main import app, pending

@pytest.fixture(autouse=True)
def setup_env(monkeypatch):
    monkeypatch.setenv("APP_DEVICE_TOKEN", "test-device-token")
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    pending.clear()

client = TestClient(app)
HEADERS = {"X-Device-Token": "test-device-token"}


def test_device_auth():
    assert client.get("/health").status_code == 401
    assert client.get("/health", headers={"X-Device-Token": "bad"}).status_code == 401
    response = client.get("/health", headers=HEADERS)
    assert response.status_code == 200
    assert not response.json()["provider_configured"]


def test_missing_key_is_real_error():
    req = {"question": "测试连接", "mode": "ask"}
    resp = client.post("/v1/chat", headers=HEADERS, json=req)
    assert resp.status_code == 503


def test_no_private_access_without_per_request_consent():
    req = {"question": "总结日记", "private_text": "私密的日记内容"}
    resp = client.post("/v1/chat", headers=HEADERS, json=req)
    assert resp.status_code == 403


def test_consent_is_one_shot_even_on_upstream_error():
    token_response = client.post("/v1/consent", headers=HEADERS,
        json={"private_text": "私密日记内容", "request_id": "request-A", "question": "请帮助我反思"})
    assert token_response.status_code == 200
    token = token_response.json()["token"]
    req = {"question": "请帮助我反思", "private_text": "私密日记内容",
           "request_id": "request-A", "consent_token": token}
    assert client.post("/v1/chat", headers=HEADERS, json=req).status_code == 503
    assert client.post("/v1/chat", headers=HEADERS, json=req).status_code == 403


def test_consent_scope_cannot_change():
    token = client.post("/v1/consent", headers=HEADERS,
        json={"private_text": "允许的文本", "request_id": "request-B", "question": "分析"}).json()["token"]
    req = {"question": "分析", "private_text": "偷偷扩展的文本",
           "request_id": "request-B", "consent_token": token}
    assert client.post("/v1/chat", headers=HEADERS, json=req).status_code == 403


def test_blank_question_rejected():
    assert client.post("/v1/chat", headers=HEADERS, json={"question": "   "}).status_code == 422


def test_consent_is_bound_to_exact_question():
    token = client.post("/v1/consent", headers=HEADERS,
        json={"private_text": "私人内容", "request_id": "request-C",
              "question": "原来的问题", "mode": "challenge"}).json()["token"]
    forged = {"question": "新的问题", "mode": "challenge", "private_text": "私人内容",
              "request_id": "request-C", "consent_token": token}
    assert client.post("/v1/chat", headers=HEADERS, json=forged).status_code == 403


def test_consent_cannot_change_ai_mode():
    token = client.post("/v1/consent", headers=HEADERS,
        json={"private_text": "私人内容", "request_id": "request-D",
              "question": "原来的问题", "mode": "review"}).json()["token"]
    forged = {"question": "原来的问题", "mode": "decision", "private_text": "私人内容",
              "request_id": "request-D", "consent_token": token}
    assert client.post("/v1/chat", headers=HEADERS, json=forged).status_code == 403