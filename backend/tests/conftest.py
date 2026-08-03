"""Shared test fixtures and bootstrap.

IMPORTANT: DATABASE_URL must be set BEFORE importing app modules because
the SQLAlchemy engine is created at import time.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

os.environ["DATABASE_URL"] = "sqlite:///./test_artha.db"
os.environ["JWT_SECRET_KEY"] = "test-secret-key"
os.environ["ENVIRONMENT"] = "test"

import pytest
from fastapi.testclient import TestClient

from app.db import Base, engine, SessionLocal
from app.main import app


@pytest.fixture(autouse=True)
def _reset_db():
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture
def db_session():
    session = SessionLocal()
    yield session
    session.close()


@pytest.fixture
def auth_headers(client: TestClient) -> dict[str, str]:
    resp = client.post(
        "/api/v1/auth/register",
        json={
            "email": "tester@example.com",
            "full_name": "Test User",
            "password": "StrongPass123",
        },
    )
    assert resp.status_code == 201, resp.text
    token = resp.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def sample_csv_bytes() -> bytes:
    path = os.path.join(os.path.dirname(__file__), "fixtures", "sample_hdfc.csv")
    with open(path, "rb") as f:
        return f.read()


@pytest.fixture
def sample_pdf_bytes() -> bytes:
    from tests.fixtures import make_pdf

    path = os.path.join(os.path.dirname(__file__), "fixtures", "sample_statement.pdf")
    if not os.path.exists(path):
        make_pdf.build(path)
    with open(path, "rb") as f:
        return f.read()
