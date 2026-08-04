# Artha Pilot — Engineering Report

**Phase 1 — Authentication · Statement Ingestion · Transaction Categorization**
**Phase 1.5 — Post-Phase-1 Audit & Security Hardening**
Generated: 2026-08-04 · Status: **COMPLETE** (awaiting Phase 2 approval)

---

## 1. Completed Features

| # | Feature | Status | Verification |
|---|---------|--------|--------------|
| 1 | GitHub repo scaffolding (structure, .gitignore, README, LICENSE) | ✅ | File audit |
| 2 | Backend project initialization (FastAPI, layered architecture) | ✅ | Compile + boot |
| 3 | PostgreSQL + Docker Compose (db + backend), Alembic migrations | ✅ | `docker compose up` + live API |
| 4 | JWT authentication (register, login, refresh rotation, me) | ✅ | 15 integration tests |
| 5 | BCrypt password hashing (cost 12) with strength validation | ✅ | Unit + API tests |
| 6 | PDF & CSV statement upload (10 MB cap, type whitelist) | ✅ | Live curl + tests |
| 7 | Statement parsing: CSV column auto-detection (HDFC/ICICI/SBI/generic) | ✅ | 7 unit tests |
| 8 | Statement parsing: PDF text extraction + balance-inference heuristic | ✅ | 4 unit tests + live |
| 9 | Transaction extraction (date/description/amount/type/balance) | ✅ | Fixture-validated |
| 10 | 3-tier categorization: rules → TF-IDF+LogReg → fallback | ✅ | 9 unit tests + live |
| 11 | Paginated, filtered transaction listing (category/type/date range) | ✅ | 6 integration tests |
| 12 | Category correction endpoint (feedback loop) + data isolation | ✅ | 4 integration tests |
| 13 | Structured logging + unified error contract (all status codes) | ✅ | Error audit |
| 14 | Unit + integration suite with coverage | ✅ | 96 tests, **93%** |
| 15 | Phase 1.5 hardening: refresh rotation, upload streaming guard, prod secret guard, CORS env, 404/405 contract, race handling, rule longest-match, enum single-source | ✅ | 28 new tests + live smoke |

## 2. Architecture Summary

Clean Architecture (layered, dependency-inverted, SOLID):

```
API (routers, deps)  →  Services (business logic)  →  Repositories (persistence)
                            ↓                                ↓
                       ML engine / Parsers (strategies)    ORM models (SQLAlchemy)
```

- **Controller layer** — thin FastAPI routers; all auth via `HTTPBearer` dependency; per-request `AuthService` shared through a cached dependency.
- **Service layer** — `AuthService`, `StatementService`, `TransactionService`; owns business rules and raises typed `AppError`.
- **Repository layer** — per-aggregate repositories; ORM contained here; data access switched by `DATABASE_URL` (PostgreSQL/SQLite).
- **Domain layer** — pure dataclasses (`RawTransaction`, `ParsedStatement`) and the single source of truth for `TransactionType`/`ParseStatus` enums; zero framework imports.
- **ML engine** — lazy, thread-safe singleton model; batched `categorize_many` (single vectorize/predict pass); longest-match keyword rules before ML.
- **Parsers** — Strategy pattern (`StatementParser` protocol) with per-format implementations, dispatch in `parsers/__init__.py`.

## 3. Folder Structure

```
Artha-Pilot/
├── .gitignore / README.md / LICENSE / docker-compose.yml
├── backend/
│   ├── app/
│   │   ├── api/            # v1/ (auth, statements, transactions, health) + deps.py
│   │   ├── core/           # config, security (JWT+bcrypt), logging, errors
│   │   ├── domain/         # pure entities
│   │   ├── ml/             # classifier.py, categories.py, seed_data.py
│   │   ├── models/         # SQLAlchemy: user, statement, transaction
│   │   ├── parsers/        # base, csv_parser, pdf_parser
│   │   ├── repositories/   # user, statement, transaction
│   │   ├── schemas/        # Pydantic contracts
│   │   ├── services/       # auth, statement, transaction
│   │   ├── db.py, main.py
│   ├── alembic/            # env.py + versions/0001_initial.py
│   ├── tests/              # unit/ + integration/ + fixtures/
│   ├── Dockerfile, requirements.txt, .env.example, alembic.ini
├── android/                # Phase 2 (stack + API contract documented)
├── docs/
└── ENGINEERING_REPORT.md
```

## 4. API Summary

| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | – | 201 | 409, 422 |
| POST | `/api/v1/auth/login` | – | 200 | 401, 422 |
| POST | `/api/v1/auth/refresh` | – | 200 | 401, 422 |
| GET | `/api/v1/auth/me` | Bearer | 200 | 401 |
| POST | `/api/v1/statements/upload` | Bearer | 201 | 400, 401, 413, 422 |
| GET | `/api/v1/transactions` | Bearer | 200 | 401, 422 |
| PATCH | `/api/v1/transactions/{id}/category` | Bearer | 200 | 401, 404, 422 |
| GET | `/api/v1/health` | – | 200 | 500 |

Error contract: `{"error": {"code", "message", "details?"}}` — codes: `email_already_registered`, `invalid_credentials`, `account_disabled`, `invalid_refresh_token`, `missing_token`, `invalid_token`, `unsupported_file_type`, `file_too_large`, `statement_parse_failed`, `invalid_category`, `invalid_transaction_type`, `transaction_not_found`, `validation_error`, `internal_error`.

## 5. Database Schema

```
users        (id PK, email UNIQUE, full_name, password_hash, is_active, created_at, updated_at)
statements   (id PK, user_id FK→users CASCADE, filename, file_type, bank_name,
              account_number, period_start, period_end, parse_status enum,
              transaction_count, error_message, created_at)
transactions (id PK, statement_id FK→statements CASCADE, user_id FK→users CASCADE,
              date, description, amount, transaction_type enum, balance,
              category, confidence, is_recurring, raw_line, created_at)
revoked_tokens (jti PK, user_id FK→users CASCADE, expires_at, revoked_at)   ← migration 0002
```
Indexes: users.email, statements.user_id, transactions (statement_id, user_id, date, category), revoked_tokens.user_id.

## 6. Dependencies

- **Backend**: fastapi 0.115, uvicorn 0.34, sqlalchemy 2.0, psycopg2-binary, alembic 1.14, pydantic 2.10 + pydantic-settings, PyJWT 2.10, bcrypt 4.2, pypdf 5.1, scikit-learn 1.6, numpy 1.26, joblib, python-multipart, email-validator; pytest + pytest-cov + httpx (tests); reportlab (fixtures).
- **Infra**: Docker Desktop 4.84, PostgreSQL 16-alpine, Python 3.12, Git 2.55, JDK 17 (Phase 2).

## 7. Security Measures

- BCrypt password hashing (cost factor 12), never stored/returned in plaintext; passwords capped at **72 bytes** to prevent bcrypt truncation.
- JWT HS256: short-lived access (15 min) + refresh (7 days) with **type claim enforcement** and **true rotation** — every refresh carries a `jti`; the consumed token is persisted to `revoked_tokens` and **reuse returns 401** (verified by API test + live smoke).
- **Production fail-fast guard**: the app refuses to start with `ENVIRONMENT=production` while `JWT_SECRET_KEY` is any known default (unit-tested).
- Password policy: ≥8 chars, upper + lower + digit (Pydantic validator).
- **Per-user data scoping**: every query filters by authenticated `user_id`; cross-user access returns 404 (verified by isolation test).
- File upload: extension whitelist (csv/pdf), 10 MB cap **enforced before buffering** (Content-Length pre-check + chunked reads with early abort), filenames sanitized (path components stripped, ≤255 chars), no persistence of raw files to disk.
- CORS origins configurable via `CORS_ORIGINS` env (JSON list; `["*"]` development default).
- SQLAlchemy ORM throughout — no raw SQL with user input; no eval/exec/subprocess/pickle in app code.
- No secrets in repo: `.env` gitignored, only `.env.example` committed; no tokens/passwords logged (audit grep clean).
- Structured logging with warning/exception levels for auth failures (no credential leakage).
- Errors sanitized for serialization (no internal exception objects leaked to clients); **404/405 responses now use the unified error contract** (registered `HTTPException` handler).
- Non-numeric token subject → 401 `invalid_token` (not 500); duplicate-email registration race → 409 (IntegrityError handled).

## 8. Test Results

```
96 passed, 0 failed, 1 warning (sklearn deprecation, benign)
Coverage: 93% overall (TOTAL 1123 statements, 77 missed)
  core/config.py  100%    core/security.py 100%    schemas/auth.py 100%
  services/statement 98%    services/auth 93%    ml/classifier 86%
  revoked_token_repository 100%    deps 97%    statements api 94%
```
- Unit: security (9), classifier (11), CSV parser (7), PDF parser (4), parser helpers (7), config (6), auth schemas (7).
- Integration: auth API (20), statements API (9), transactions API (11).
- New tests added in Phase 1.5: refresh-token rotation + reuse rejection, non-numeric sub → 401, 404/405 error contract, registration race → 409, production secret guard, CORS parsing, 72-byte password cap, oversize upload (router + service), filename sanitization/truncation, longest-match rules.
- Live smoke (real PostgreSQL via Docker): register → login → CSV upload (7 txns, correct categories) → refresh → **rotated-token reuse rejected (401)** → 404 contract → error paths. Health `ok/database connected`.

## 9. Performance Metrics

| Benchmark | Result |
|---|---|
| Batch categorization (2,000 txns) | **0.036 s (≈55,000 txns/s)** |
| Model init (lazy, first call) | < 2 s |
| Full suite runtime | 14–17 s |
| Docker stack boot (db healthy) | < 20 s |
| API response (health, pg connected) | < 10 ms |

## 10. Error Audit Report

| Class | Findings | Resolution |
|---|---|---|
| Compile | None | `compileall` clean |
| Runtime | 4 test failures at first run (Phase 1) | Fixed: schema date typing, PDF amount suffix-regex, description marker stripping, validation error serialization |
| API errors | 422 handler crashed (500) on non-serializable `ctx` | Added `_sanitize_validation_errors` |
| **API errors** | **404/405 returned default `{"detail"}` shape, violating the error contract** | **Registered `HTTPException` handler → `not_found`/`method_not_allowed` (Phase 1.5)** |
| DB errors | None | ORM + typed repositories |
| UI | N/A (Phase 2) | — |
| Memory | No leaks; lazy singleton model, streaming read of uploads | — |
| **Security** | **Uploads buffered fully before 10 MB check → memory-exhaustion vector** | **Content-Length pre-check + chunked reads (Phase 1.5)** |
| **Security** | **JWT default secret accepted in production → forgeable tokens** | **Fail-fast startup guard (Phase 1.5)** |
| **Security** | **"Refresh rotation" re-issued tokens without invalidating the old one** | **`jti` + `revoked_tokens` table; reuse → 401 (Phase 1.5)** |
| **Security** | **CORS `*` unconditional** | **Env-driven `CORS_ORIGINS` (Phase 1.5)** |
| **Security** | **Non-numeric token `sub` → 500; registration race → 500; bcrypt truncation at 72 bytes** | **401 / 409 / 72-byte schema cap (Phase 1.5)** |
| Architecture | One service re-instantiated per call (`AuthService(db)` × 2) | Refactored to single cached instance per request |
| **Architecture** | **`TransactionType`/`ParseStatus` defined in both domain and models (drift risk)** | **Single definition in domain; models import it (Phase 1.5)** |
| **Rules** | **`AMAZON` keyword shadowed `AMAZON PRIME RENEWAL` (Shopping vs Bills)** | **Longest-match rule engine (Phase 1.5)** |
| **Code smells** | **Dead code: unused `get_logger`, no-op exception re-raise, unused domain `User`, duplicate IRCTC rule, stale docstring, dead config `UPLOAD_DIR`/uploads volume, manual response mapping** | **Removed/simplified (Phase 1.5)** |
| Dead code | None remaining | AST scan |
| Duplication | None | AST scan |

**Bugs found & fixed during Phase 1:**
1. `period_start/end` schema typed `str` while ORM returns `date` → 500 on upload response.
2. PDF `_AMOUNT_RE` matched number suffixes ("550.00" inside "24550.00") → wrong amounts/balance; fixed with negative lookbehind + full-digit matching.
3. Sequential amount-stripping corrupted descriptions ("0.00" removed inside "24550.00"); replaced with single-pass regex.
4. Marker cleanup stripped legitimate "CREDIT" narrations (SALARY CREDIT); now only strips markers directly following amounts.
5. `clean_amount("Rs. 500.50")` → None (stray "." from "Rs."); reworked to digit-first parsing with last-dot decimal rule.
6. 422 handler serialization crash on `ctx` containing ValueError.

**Bugs found & fixed during Phase 1.5 audit (2026-08-04):**
7. Upload size cap enforced only after full buffering → 413 pre-check + chunked read with early abort.
8. JWT default secret usable in production → startup guard raises.
9. Refresh "rotation" never invalidated the old token → `jti`-based revocation; reuse tested and verified live.
10. `int(subject)` on non-numeric `sub` → 500; now 401 `invalid_token`.
11. Concurrent duplicate registration → `IntegrityError` 500; now 409.
12. bcrypt silently truncates >72-byte passwords → schema cap with explicit byte check.
13. `>`255-char filenames → DB error 500; sanitize + truncate (extension preserved).
14. Keyword rule `AMAZON` shadowed `AMAZON PRIME RENEWAL` → longest-match engine.
15. 404/405 bypassed the unified error contract → `HTTPException` handler.
16. Overlong SQL statements marked failed with truncation; period fields untyped — cleaned.
17. Uploaded filename path components ("../../evil.csv") not stripped → basename sanitization.

## 11. Remaining Risks

- **Scanned/image PDFs** unsupported (text extraction only) — acceptable for now; OCR is a future enhancement.
- PDF balance-inference heuristic depends on consistent balances; statements missing balance columns degrade to marker-based parsing.
- Seed dataset (~150 samples) bounds ML accuracy; feedback endpoint enables future retraining loops.
- No rate limiting on auth endpoints yet (deferred to production hardening).
- No refresh-token logout/revocation endpoint (tokens only rotate; a stolen active token remains valid until expiry).
- ML model retrained per process on first request; no `joblib` persistence (multi-worker deployments each carry a copy).
- Docker `postgres:16-alpine` volume has no backup strategy yet.

## 12. Future Improvements

- Android app (Phase 2): Compose + Hilt + Retrofit + Room + SQLCipher.
- OCR for scanned statements; more bank-format parsers (regression fixtures).
- ML retraining pipeline from user category corrections + model versioning + model persistence.
- EMI/recurring detection, dashboard analytics, risk assessment, chatbot (Phases 3+).
- GitHub Actions CI (lint, tests, coverage gate), container image registry.
- Production hardening: rate limiting, refresh-token revocation list/endpoint, HTTPS ingress, secrets rotation.

## 13. Git Commit History

- `main` @ `e1afe5d`: Phase 1 baseline.
- `develop` @ `04271ec`: `security: harden auth, uploads, and deployment config` (Phase 1.5).
- `develop` @ `18851be`: `fix: honor error contract for 404/405; longest-match rules; single-source enums` (Phase 1.5).

## 14. Phase Completion Status

| Phase | Scope | Status |
|---|---|---|
| **1** | Auth, GitHub setup, project init, PDF/CSV upload, parsing, extraction, categorization, testing, audit | ✅ **Complete** |
| **1.5** | Post-Phase-1 audit: 2 High + 8 Medium + 12 Low findings fixed; 28 new tests; migration 0002 | ✅ **Complete** |
| 2 | Android app (Compose/MVVM/Hilt/Room/SQLCipher) | ⏳ Waiting for approval |

## 15. Readiness Score

**95 / 100**

| Category | Score |
|---|---|
| Functionality vs. Phase 1+1.5 scope | 20/20 |
| Test coverage (93%) & passing suite (96 tests) | 19/20 |
| Security posture | 19/20 (rate limiting + revocation endpoint deferred to prod hardening) |
| Performance | 10/10 |
| Architecture & code quality | 17/20 (image-PDF OCR + ML model persistence deferred) |
| Documentation | 10/10 |
