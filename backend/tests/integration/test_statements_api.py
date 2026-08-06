"""Integration tests for statement upload and parsing."""
import pytest

from app.core.config import Settings
from app.core.errors import AppError
from app.services.statement_service import StatementService


def test_upload_csv_success(client, auth_headers, sample_csv_bytes):
    resp = client.post(
        "/api/v1/statements/upload",
        headers=auth_headers,
        files={"file": ("statement.csv", sample_csv_bytes, "text/csv")},
    )
    assert resp.status_code == 201
    body = resp.json()
    assert body["parse_status"] == "processed"
    assert body["transaction_count"] == 7
    assert body["period_start"] == "2025-04-01"
    assert body["period_end"] == "2025-04-10"
    assert len(body["transactions"]) == 7
    categories = {t["category"] for t in body["transactions"]}
    assert "Food & Dining" in categories
    assert "Salary" in categories
    assert "Cash" in categories


def test_upload_pdf_success(client, auth_headers, sample_pdf_bytes):
    resp = client.post(
        "/api/v1/statements/upload",
        headers=auth_headers,
        files={"file": ("statement.pdf", sample_pdf_bytes, "application/pdf")},
    )
    assert resp.status_code == 201
    body = resp.json()
    assert body["parse_status"] == "processed"
    assert body["transaction_count"] == 5
    assert body["bank_name"] == "HDFC BANK"
    assert len(body["transactions"]) == 5


def test_upload_unsupported_type(client, auth_headers):
    resp = client.post(
        "/api/v1/statements/upload",
        headers=auth_headers,
        files={"file": ("statement.xlsx", b"data", "application/vnd.ms-excel")},
    )
    assert resp.status_code == 400
    assert resp.json()["error"]["code"] == "unsupported_file_type"


def test_upload_too_large(client, auth_headers):
    big = b"x" * (10 * 1024 * 1024 + 1)
    resp = client.post(
        "/api/v1/statements/upload",
        headers=auth_headers,
        files={"file": ("big.csv", big, "text/csv")},
    )
    assert resp.status_code == 413
    assert resp.json()["error"]["code"] == "file_too_large"


def test_upload_garbage_pdf(client, auth_headers):
    resp = client.post(
        "/api/v1/statements/upload",
        headers=auth_headers,
        files={"file": ("broken.pdf", b"not a pdf", "application/pdf")},
    )
    assert resp.status_code == 422
    assert resp.json()["error"]["code"] == "statement_parse_failed"


def test_upload_requires_auth(client, sample_csv_bytes):
    resp = client.post(
        "/api/v1/statements/upload",
        files={"file": ("statement.csv", sample_csv_bytes, "text/csv")},
    )
    assert resp.status_code == 401


def test_upload_sanitizes_path_traversal_filename(client, auth_headers, sample_csv_bytes):
    resp = client.post(
        "/api/v1/statements/upload",
        headers=auth_headers,
        files={"file": ("../../etc/evil.csv", sample_csv_bytes, "text/csv")},
    )
    assert resp.status_code == 201
    assert resp.json()["filename"] == "evil.csv"


def test_upload_truncates_overlong_filename(client, auth_headers, sample_csv_bytes):
    long_name = "a" * 300 + ".csv"
    resp = client.post(
        "/api/v1/statements/upload",
        headers=auth_headers,
        files={"file": (long_name, sample_csv_bytes, "text/csv")},
    )
    assert resp.status_code == 201
    assert len(resp.json()["filename"]) == 255
    assert resp.json()["filename"].endswith(".csv")


def test_upload_rejects_oversize_content_length(client, auth_headers, sample_csv_bytes, monkeypatch):
    monkeypatch.setattr(
        "app.api.v1.statements.get_settings",
        lambda: Settings(MAX_UPLOAD_MB=1),
    )
    big = b"x" * (2 * 1024 * 1024)
    resp = client.post(
        "/api/v1/statements/upload",
        headers=auth_headers,
        files={"file": ("big.csv", big, "text/csv")},
    )
    assert resp.status_code == 413
    assert resp.json()["error"]["code"] == "file_too_large"


def test_service_rejects_oversize_content(client, db_session, monkeypatch):
    from app.repositories.user_repository import UserRepository

    user = UserRepository(db_session).create("big@example.com", "Big", "hash")
    monkeypatch.setattr(
        "app.services.statement_service.get_settings",
        lambda: Settings(MAX_UPLOAD_MB=1),
    )
    with pytest.raises(AppError) as exc_info:
        StatementService(db_session).ingest(user.id, "big.csv", b"x" * (2 * 1024 * 1024))
    assert exc_info.value.status_code == 413
    assert exc_info.value.error_code == "file_too_large"


def test_list_statements_paginated(client, auth_headers, sample_csv_bytes, sample_pdf_bytes):
    client.post(
        "/api/v1/statements/upload",
        headers=auth_headers,
        files={"file": ("a.csv", sample_csv_bytes, "text/csv")},
    )
    client.post(
        "/api/v1/statements/upload",
        headers=auth_headers,
        files={"file": ("b.csv", sample_csv_bytes, "text/csv")},
    )
    resp = client.get("/api/v1/statements", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["total"] == 2
    assert len(body["items"]) == 2
    assert body["items"][0]["filename"] == "b.csv"
    assert body["items"][0]["parse_status"] == "processed"
    assert body["items"][0]["transaction_count"] == 7

    page = client.get("/api/v1/statements?page=1&page_size=1", headers=auth_headers).json()
    assert page["total"] == 2
    assert len(page["items"]) == 1


def test_list_statements_empty(client, auth_headers):
    resp = client.get("/api/v1/statements", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["total"] == 0


def test_list_statements_requires_auth(client):
    resp = client.get("/api/v1/statements")
    assert resp.status_code == 401
