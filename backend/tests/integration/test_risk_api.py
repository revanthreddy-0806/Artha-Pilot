"""Integration tests for the risk-appetite questionnaire and assessment endpoints."""
from datetime import date

import pytest

from app.domain.entities import ParseStatus, TransactionType
from app.models.statement import Statement
from app.models.transaction import Transaction


def _register_user(client) -> tuple[dict[str, str], int]:
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "risk@example.com", "full_name": "Risk User", "password": "StrongPass123"},
    )
    assert resp.status_code == 201, resp.text
    headers = {"Authorization": f"Bearer {resp.json()['access_token']}"}
    me = client.get("/api/v1/auth/me", headers=headers).json()
    return headers, me["id"]


def _complete_answers(questions: list[dict], option_index: int = 0) -> dict[str, str]:
    return {
        q["id"]: q["options"][option_index]["id"]
        for q in questions
    }


def _shift_month(offset: int) -> tuple[int, int]:
    """Return the (year, month) that is ``offset`` months from the current month."""
    today = date.today()
    index = today.year * 12 + (today.month - 1) + offset
    return index // 12, index % 12 + 1


def _insert_transactions(db_session, user_id: int, rows: list[tuple[int, int, float, str, str, str]]):
    """Insert rows as (day, month_offset, amount, description, type, category); 0 = current month."""
    statement = Statement(
        user_id=user_id,
        filename="risk.csv",
        file_type="csv",
        parse_status=ParseStatus.PROCESSED,
        transaction_count=len(rows),
    )
    db_session.add(statement)
    db_session.flush()
    for day, month_offset, amount, description, txn_type, category in rows:
        year, month = _shift_month(month_offset)
        db_session.add(
            Transaction(
                statement_id=statement.id,
                user_id=user_id,
                date=date(year, month, day),
                description=description,
                amount=amount,
                transaction_type=TransactionType(txn_type),
                category=category,
            )
        )
    db_session.commit()


def test_questions_are_a_standard_set(client):
    resp = client.get("/api/v1/risk/questions")
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["questions"]) >= 8
    for q in body["questions"]:
        assert q["id"]
        assert q["text"]
        assert len(q["options"]) >= 3
        for opt in q["options"]:
            assert opt["id"]
            assert opt["label"]


def test_questions_do_not_leak_scoring_points(client):
    resp = client.get("/api/v1/risk/questions")
    assert resp.status_code == 200
    body = resp.json()
    for q in body["questions"]:
        for opt in q["options"]:
            assert "points" not in opt


def test_profile_requires_auth(client):
    resp = client.get("/api/v1/risk/profile")
    assert resp.status_code == 401


def test_questionnaire_requires_auth(client):
    resp = client.put("/api/v1/risk/questionnaire", json={"answers": {}})
    assert resp.status_code == 401


def test_initial_profile_is_behaviour_only(client, auth_headers):
    resp = client.get("/api/v1/risk/profile", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["questionnaire_completed"] is False
    assert body["tolerance_score"] is None
    assert body["tolerance_band"] is None
    assert body["band"] in {"Conservative", "Moderate", "Aggressive"}
    assert 0 <= body["score"] <= 100


def test_submit_questionnaire_stores_and_combines(client, auth_headers):
    questions = client.get("/api/v1/risk/questions").json()["questions"]
    conservative = _complete_answers(questions, option_index=0)
    resp = client.put("/api/v1/risk/questionnaire", json={"answers": conservative}, headers=auth_headers)
    assert resp.status_code == 200, resp.text
    result = resp.json()
    assert result["tolerance_score"] == 25
    assert result["tolerance_band"] == "Conservative"

    profile = result["assessment"]
    assert profile["questionnaire_completed"] is True
    assert profile["tolerance_score"] == 25
    assert profile["tolerance_band"] == "Conservative"
    assert profile["score"] == 13  # half-up blend: 0.5*25 + 0.5*(behaviour 0)


def test_submit_aggressive_answers_raise_score_with_good_behaviour(client, db_session):
    headers, user_id = _register_user(client)
    _insert_transactions(
        db_session,
        user_id,
        [
            (1, -2, 100_000, "ACME SALARY", "credit", "Salary"),
            (2, -1, 100_000, "ACME SALARY", "credit", "Salary"),
            (1, -2, -30_000, "RENT PAYMENT", "debit", "Rent"),
            (2, -1, -30_000, "RENT PAYMENT", "debit", "Rent"),
            (5, -2, -10_000, "GROCERIES", "debit", "Groceries"),
            (5, -1, -10_000, "GROCERIES", "debit", "Groceries"),
        ],
    )
    questions = client.get("/api/v1/risk/questions").json()["questions"]
    aggressive = _complete_answers(questions, option_index=3)
    resp = client.put("/api/v1/risk/questionnaire", json={"answers": aggressive}, headers=headers)
    assert resp.status_code == 200, resp.text
    result = resp.json()
    assert result["tolerance_score"] == 100
    assert result["tolerance_band"] == "Aggressive"
    assessment = result["assessment"]
    assert assessment["score"] >= 70  # 100 tolerance blended with strong behaviour


def test_profile_returns_tolerance_after_submit(client, auth_headers):
    questions = client.get("/api/v1/risk/questions").json()["questions"]
    answers = _complete_answers(questions, option_index=1)
    client.put("/api/v1/risk/questionnaire", json={"answers": answers}, headers=auth_headers)
    resp = client.get("/api/v1/risk/profile", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["questionnaire_completed"] is True
    assert body["tolerance_band"] == "Moderate"
    assert len(body["factors"]) > 2


def test_submit_missing_answer_rejected(client, auth_headers):
    questions = client.get("/api/v1/risk/questions").json()["questions"]
    answers = _complete_answers(questions, option_index=0)
    del answers[questions[0]["id"]]
    resp = client.put("/api/v1/risk/questionnaire", json={"answers": answers}, headers=auth_headers)
    assert resp.status_code == 422


def test_submit_unknown_option_rejected(client, auth_headers):
    questions = client.get("/api/v1/risk/questions").json()["questions"]
    answers = _complete_answers(questions, option_index=0)
    answers[questions[0]["id"]] = "bogus"
    resp = client.put("/api/v1/risk/questionnaire", json={"answers": answers}, headers=auth_headers)
    assert resp.status_code == 422


def test_resubmit_overwrites_previous_answers(client, auth_headers):
    questions = client.get("/api/v1/risk/questions").json()["questions"]
    client.put("/api/v1/risk/questionnaire", json={"answers": _complete_answers(questions, 0)}, headers=auth_headers)
    client.put("/api/v1/risk/questionnaire", json={"answers": _complete_answers(questions, 3)}, headers=auth_headers)
    resp = client.get("/api/v1/risk/profile", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["tolerance_score"] == 100


def test_users_are_isolated(client):
    resp_a = client.post(
        "/api/v1/auth/register",
        json={"email": "riska@example.com", "full_name": "Risk A", "password": "StrongPass123"},
    )
    resp_b = client.post(
        "/api/v1/auth/register",
        json={"email": "riskb@example.com", "full_name": "Risk B", "password": "StrongPass123"},
    )
    headers_a = {"Authorization": f"Bearer {resp_a.json()['access_token']}"}
    headers_b = {"Authorization": f"Bearer {resp_b.json()['access_token']}"}
    questions = client.get("/api/v1/risk/questions").json()["questions"]
    client.put(
        "/api/v1/risk/questionnaire",
        json={"answers": _complete_answers(questions, 3)},
        headers=headers_a,
    )
    profile_b = client.get("/api/v1/risk/profile", headers=headers_b).json()
    assert profile_b["questionnaire_completed"] is False
    profile_a = client.get("/api/v1/risk/profile", headers=headers_a).json()
    assert profile_a["questionnaire_completed"] is True


def _statement_csv() -> str:
    """Two consecutive months of recent activity, relative to the current date."""

    def d(month_offset: int, day: int) -> str:
        year, month = _shift_month(month_offset)
        return f"{day:02d}/{month:02d}/{year:04d}"

    return f"""\
Date,Narration,Chq/Ref Number,Value Dat,Withdrawal Amt.,Deposit Amt.,Closing Balance
{d(-2, 1)},SALARY CREDIT,1001,{d(-2, 1)},,100000.00,200000.00
{d(-2, 2)},RENT PAYMENT,1002,{d(-2, 2)},30000.00,,170000.00
{d(-2, 5)},GROCERIES,1003,{d(-2, 5)},12000.00,,158000.00
{d(-2, 10)},HOME LOAN EMI,1004,{d(-2, 10)},25000.00,,133000.00
{d(-1, 1)},SALARY CREDIT,1006,{d(-1, 1)},,100000.00,229000.00
{d(-1, 2)},RENT PAYMENT,1007,{d(-1, 2)},30000.00,,199000.00
{d(-1, 5)},GROCERIES,1008,{d(-1, 5)},12000.00,,187000.00
{d(-1, 10)},HOME LOAN EMI,1009,{d(-1, 10)},25000.00,,162000.00
"""


def test_risk_profile_uses_real_uploaded_statement_data(client):
    """Positive stored amounts must drive correct spending metrics end-to-end."""
    headers, _ = _register_user(client)
    resp = client.post(
        "/api/v1/statements/upload",
        headers=headers,
        files={"file": ("statement.csv", _statement_csv().encode(), "text/csv")},
    )
    assert resp.status_code == 201, resp.text

    profile = client.get("/api/v1/risk/profile", headers=headers).json()
    assert profile["income"] == 100_000
    assert profile["monthly_expenses"] == 67_000
    assert profile["savings_rate"] == pytest.approx(0.33, abs=0.01)
    assert profile["emi_load_ratio"] == pytest.approx(0.25, abs=0.01)
    assert profile["safe_emi_amount"] == 5_000
    assert 0 <= profile["score"] <= 100

    factor_values = {f["name"]: f["value"] for f in profile["factors"]}
    assert "Savings rate" in factor_values
    assert "EMI / loan load" in factor_values

    recurring = client.get("/api/v1/insights/recurring", headers=headers).json()["items"]
    by_description = {item["description"]: item for item in recurring}
    assert "HOME LOAN EMI" in by_description
    assert by_description["HOME LOAN EMI"]["kind"] == "emi"