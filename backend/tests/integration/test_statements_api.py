"""Integration tests for statement upload and parsing."""
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
