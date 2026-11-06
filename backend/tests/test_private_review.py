"""Regression tests for opt-in, single-request private diary review."""
import hashlib
from fastapi.testclient import TestClient
import backend.main as gateway

client = TestClient(gateway.app)
HEADERS = {"X-Device-Token": "test-review-device"}
QUESTION = "请根据选中的日记做本周回顾"
PRIVATE = "周一完成作业，周二读了一篇论文"


def setup_function():
    gateway.review_pending.clear()


def test_review_is_opt_in_and_legacy_route_still_blocked(monkeypatch):
    monkeypatch.setenv("APP_DEVICE_TOKEN", "test-review-device")
    monkeypatch.setenv("CLOUD_GATEWAY_MODE", "1")
    monkeypatch.delenv("CLOUD_PRIVATE_REVIEW_ENABLED", raising=False)
    assert client.get("/health", headers=HEADERS).json()["private_review_enabled"] is False
    response = client.post("/v1/review/consent", headers=HEADERS,
        json={"digest": hashlib.sha256(PRIVATE.encode()).hexdigest(),
              "request_id": "request-1001", "question": QUESTION})
    assert response.status_code == 403
    assert client.post("/v1/consent", headers=HEADERS, json={
        "private_text": PRIVATE, "request_id": "request-1001",
        "question": QUESTION}).status_code == 403


def test_explicit_review_token_is_bound_to_exact_text_and_used_once(monkeypatch):
    monkeypatch.setenv("APP_DEVICE_TOKEN", "test-review-device")
    monkeypatch.setenv("CLOUD_GATEWAY_MODE", "1")
    monkeypatch.setenv("CLOUD_PRIVATE_REVIEW_ENABLED", "1")
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    assert client.get("/health", headers=HEADERS).json()["private_review_enabled"] is True
    consent = client.post("/v1/review/consent", headers=HEADERS,
        json={"digest": hashlib.sha256(PRIVATE.encode()).hexdigest(),
              "request_id": "request-1002", "question": QUESTION})
    assert consent.status_code == 200
    token = consent.json()["token"]
    req = {"private_text": PRIVATE, "request_id": "request-1002",
           "question": QUESTION, "consent_token": token}
    assert client.post("/v1/review", headers=HEADERS, json=req).status_code == 503
    assert client.post("/v1/review", headers=HEADERS, json=req).status_code == 403


def test_review_rejects_changed_diary_without_consuming_other_token(monkeypatch):
    monkeypatch.setenv("APP_DEVICE_TOKEN", "test-review-device")
    monkeypatch.setenv("CLOUD_PRIVATE_REVIEW_ENABLED", "true")
    consent = client.post("/v1/review/consent", headers=HEADERS,
        json={"digest": hashlib.sha256(PRIVATE.encode()).hexdigest(),
              "request_id": "request-1003", "question": QUESTION})
    token = consent.json()["token"]
    forged = {"private_text": PRIVATE + "我没有授权的额外文字",
              "request_id": "request-1003", "question": QUESTION, "consent_token": token}
    assert client.post("/v1/review", headers=HEADERS, json=forged).status_code == 403


def test_review_requires_device_auth(monkeypatch):
    monkeypatch.setenv("APP_DEVICE_TOKEN", "test-review-device")
    monkeypatch.setenv("CLOUD_PRIVATE_REVIEW_ENABLED", "1")
    assert client.post("/v1/review/consent",
        json={"digest": hashlib.sha256(PRIVATE.encode()).hexdigest(),
              "request_id": "request-1004", "question": QUESTION}).status_code == 401
