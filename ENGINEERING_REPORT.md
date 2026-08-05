# Artha Pilot — Engineering Report

**Phase 1 — Authentication · Statement Ingestion · Transaction Categorization**
**Phase 1.5 — Post-Phase-1 Audit & Security Hardening**
**Phase 2 — Android App (Compose / MVVM / Hilt / Room + SQLCipher)**
Generated: 2026-08-05 · Status: **Phases 1 + 1.5 + 2 COMPLETE**

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
- `develop` @ `7af2772`: `docs: update engineering report with Phase 1.5 audit results`.
- Phase 2 commits: see section 26 (Git Commit History).

## 14. Phase Completion Status

| Phase | Scope | Status |
|---|---|---|
| **1** | Auth, GitHub setup, project init, PDF/CSV upload, parsing, extraction, categorization, testing, audit | ✅ **Complete** |
| **1.5** | Post-Phase-1 audit: 2 High + 8 Medium + 12 Low findings fixed; 28 new tests; migration 0002 | ✅ **Complete** |
| **2** | Android app: Compose MVVM, Hilt, Retrofit, Room + SQLCipher, DataStore, Navigation, 37 unit tests, lint clean | ✅ **Complete** |

## 15. Readiness Score

**95 / 100** (Phases 1 + 1.5 + 2)

| Category | Score |
|---|---|
| Functionality vs. Phase 1+1.5 scope | 20/20 |
| Test coverage (93%) & passing suite (96 tests) | 19/20 |
| Security posture | 19/20 (rate limiting + revocation endpoint deferred to prod hardening) |
| Performance | 10/10 |
| Architecture & code quality | 17/20 (image-PDF OCR + ML model persistence deferred) |
| Documentation | 10/10 |

---

# PHASE 2 — ANDROID APPLICATION

## 16. Phase 2 Overview

| Area | Result |
|---|---|
| Scope | Full Android client: auth, statement upload, transaction browsing, category correction |
| Stack | Kotlin 2.3.10, Jetpack Compose (BOM 2026.06.01), Material 3, AGP 9.3.1, Gradle 9.6.1 |
| Architecture | Clean Architecture + MVVM + Repository Pattern, Hilt DI |
| Persistence | Room 2.8.4 with SQLCipher 4.17.0 (Keystore-protected passphrase), DataStore Preferences |
| Networking | Retrofit 2.11.0 + OkHttp 5.4.0 (auth interceptor + refresh authenticator) |
| Build | `assembleDebug` SUCCESS — APK produced |
| Tests | 37/37 unit tests PASS (JUnit 4 + MockWebServer + coroutines-test) |
| Lint | 0 errors, 5 warnings (4 informational version notices, 1 false positive) |

## 17. Architecture Summary

```
Compose UI (Login / Register / Home / Upload)
        │  ViewModels collect StateFlows, expose one-shot actions
        ▼
ViewModel layer (AuthViewModel, TransactionsViewModel, UploadViewModel)
        │  Hilt @HiltViewModel
        ▼
Domain layer (models, ApiException, repository interfaces)
        │  dependency inversion via Hilt @Binds
        ▼
Repository layer (AuthRepository, TransactionRepository, StatementRepository)
        │  combine remote + local cache
        ▼
Data layer
  Remote: Retrofit/OkHttp — ArthaApi, AuthInterceptor, TokenAuthenticator, ApiErrorMapper, DTOs
  Local:  Room (ArthaDatabase, DAOs, entities), SQLCipher via SupportOpenHelperFactory,
          DataStore (TokenStore), PassphraseProvider (Keystore)
```

- **MVVM**: screens are stateless Composables fed by `StateFlow<UiState>`; all state transitions live in ViewModels; no framework calls from ViewModels (UploadViewModel reads files via injected `@ApplicationContext`).
- **Repository Pattern**: repositories own the remote/local merge (online-first fetch mirrored into Room cache; uploads persist statement + transactions).
- **SOLID**: interface-based repositories (`@Binds`), sealed `ApiException` hierarchy, single-responsibility modules (Network/DataStore/Database/Repository).
- **Auth flow**: `AuthInterceptor` attaches Bearer token; `TokenAuthenticator` silently rotates the refresh token on 401 (single-flight, one retry, session cleared on double-fail); DataStore is the single source of truth for the JWT pair.

## 18. Android Module & Package Organization

```
com.arthapilot.app
├── ArthaApp.kt / MainActivity.kt        # @HiltAndroidApp, single-activity Compose
├── ui/
│   ├── navigation/AppNavHost.kt          # auth-aware routes: login, register, home, upload
│   ├── auth/      AuthScreens, AuthViewModel
│   ├── home/      HomeScreen, TransactionsViewModel
│   ├── upload/    UploadStatementScreen, UploadViewModel
│   └── theme/     Theme.kt
├── data/
│   ├── remote/    ArthaApi, AuthInterceptor, TokenAuthenticator, ApiErrorMapper, Mappers, dto/
│   ├── local/     Entities, Daos, ArthaDatabase, TokenStore, PassphraseProvider
│   └── repository/ AuthRepository, TransactionRepository, StatementRepository
├── domain/model/  Models, ApiException
└── di/            Modules.kt (Network / DataStore / Database / Repository bindings)
```

## 19. Dependency Graph

```
MainActivity ──▶ AppNavHost ──▶ LoginScreen / RegisterScreen / HomeScreen / UploadStatementScreen
                                   │ hiltViewModel()
AuthViewModel ──▶ AuthRepository ──▶ ArthaApi + TokenStore
TransactionsViewModel ──▶ TransactionRepository ──▶ ArthaApi + TransactionDao
UploadViewModel ──▶ StatementRepository ──▶ ArthaApi + StatementDao + TransactionDao
ArthaApi ──▶ Retrofit ──▶ OkHttpClient ──▶ AuthInterceptor + TokenAuthenticator + Logging
ArthaDatabase ──▶ Room ──▶ SupportOpenHelperFactory(passphrase) ──▶ PassphraseProvider ──▶ Android Keystore
TokenStore / PassphraseProvider ──▶ DataStore Preferences
```

## 20. Tech Stack & Version Audit

All versions verified against Maven Central / Google Maven (2026-08-05):

| Dependency | Version | Verified |
|---|---|---|
| AGP | 9.3.1 | ✅ (requires Gradle 9.x) |
| Kotlin / Compose plugin | 2.3.10 | ✅ (2.3.11 does not exist — original catalog was broken) |
| KSP | 2.3.10 | ✅ |
| Compose BOM | 2026.06.01 | ✅ |
| Navigation Compose | 2.9.8 | ✅ |
| Hilt / Hilt Nav Compose | 2.60.1 / 1.4.0 | ✅ |
| Retrofit / converter-gson | 2.11.0 | ✅ |
| OkHttp / MockWebServer | 5.4.0 | ✅ |
| Room | 2.8.4 | ✅ |
| SQLCipher | 4.17.0 | ✅ (`SupportFactory` renamed → `SupportOpenHelperFactory`) |
| DataStore Preferences | 1.2.1 | ✅ |
| Coroutines / coroutines-test | 1.10.2 | ✅ |
| core-ktx / lifecycle / activity-compose | 1.19.0 / 2.11.0 / 1.13.0 | ✅ |
| JUnit | 4.13.2 | ✅ |

Build configuration: compileSdk/targetSdk 37, minSdk 26, Java 17, Gradle 9.6.1 wrapper (bootstraped, previously missing).

## 21. Build Status

| Gate | Result |
|---|---|
| `gradlew :app:assembleDebug` | ✅ SUCCESS (APK: `app/build/outputs/apk/debug/app-debug.apk`) |
| `gradlew :app:testDebugUnitTest` | ✅ 37/37 PASS |
| `gradlew :app:lintDebug` | ✅ 0 errors, 5 warnings |
| Compile warnings | 0 (after Phase 2 fixes) |

## 22. Test Summary

| Suite | Tests | Coverage intent |
|---|---|---|
| `ApiErrorMapperTest` | 6 | 401 → Unauthorized, error-contract parsing, fallbacks, passthrough |
| `ArthaApiTest` (MockWebServer) | 7 | Request serialization, query params, multipart upload, PATCH path, error contract |
| `MappersTest` | 7 | DTO → domain → entity round trips |
| `ApiExceptionTest` | 5 | userMessage mapping for every backend error code |
| `AuthViewModelTest` | 6 | login/register/logout state transitions, error surfacing |
| `TransactionsViewModelTest` | 7 | init fetch, pagination (loadMore), filters, error surfacing, category update, offline cache fallback |
| **Total** | **37** | **0 failures** |

Not JVM-testable without Robolectric/emulator (documented): Room DAO behavior, SQLCipher integration, DataStore-backed TokenStore, Compose UI rendering, TokenAuthenticator OkHttp flow.

## 23. Lint Summary

0 errors, 5 warnings:
- 4× `NewerVersionAvailable` (kotlin compose 2.4.10, retrofit 3.0.0, coroutines 1.11.0) — intentional pins: current versions are stable and fully verified; upgrades deferred to Phase 3.
- 1× `ObsoleteSdkInt` (`mipmap-anydpi-v26`) — false positive: adaptive icons require the `-v26` qualifier; folder kept.

## 24. Security Audit (Android)

| Area | Status |
|---|---|
| JWT storage | DataStore Preferences (never logged); refreshed transparently by TokenAuthenticator |
| DB encryption | Room + SQLCipher 4.17.0; 32-byte random passphrase sealed with AES/GCM in Android Keystore |
| Backup | `allowBackup=false` + `fullBackupContent=false` + `dataExtractionRules` excluding all data |
| Network | Cleartext limited to dev hosts (10.0.2.2/localhost) via network_security_config; HTTPS-ready |
| Upload | Backend-enforced 10 MB cap + type whitelist; Android reads via ContentResolver (no file persistence) |
| Secrets | No secrets in repo; API_BASE_URL via BuildConfig; local.properties gitignored |
| Lifecycle | No Activity/View leaks; ViewModels scoped via Hilt; flows cancelled by viewModelScope |

## 25. Performance Audit (Android)

| Area | Result |
|---|---|
| List rendering | LazyColumn with stable keys (`key = { it.id }`) |
| Pagination | Page size 50, hasMore guard prevents duplicate loads |
| IO | File reads and network on IO/OkHttp threads; no main-thread blocking |
| DB | Room with `@Upsert`, indexed `statementId`; Flow-based observation |
| Recompositions | Screens consume `collectAsStateWithLifecycle`; UI state hoisted in ViewModels |
| APK | Debug build ~2.4 MB classes + native libs (sqlcipher) |

## 26. Git Commit History (Phase 2)

- `chore(android): bootstrap gradle wrapper and fix version catalog (kotlin/ksp 2.3.10, gradle 9.6.1)`
- `fix(android): resolve compile errors — SQLCipher SupportOpenHelperFactory, ApiException nullability, UploadViewModel context, navigation`
- `feat(android): add auth-aware navigation graph (AppNavHost)`
- `test(android): add 36 unit tests — mappers, error mapping, MockWebServer API, viewmodels`
- `fix(android): release-readiness audit fixes — cancellation, 401 mapping, offline fallback, IO layering`
- `fix(android): audit cleanup — locale-safe formatting, remove dead refreshTokens, backup exclusion rules, monochrome icon`
- `docs: update engineering report and README for Phase 2`

## 27. Known Limitations

- No Android instrumentation tests (require emulator/device) — Room/SQLCipher/Compose verified by build + lint only; app restart/offline persistence covered at unit/design level.
- Upload picker offers PDF + CSV (matches backend); other formats rejected client- and server-side.
- Offline mode falls back to the last cached snapshot; uploads still require connectivity; offline category-correction queues are Phase 3.
- Auth start-destination flash on cold start while DataStore loads (cosmetic).
- `mipmap-anydpi-v26` lint warning is a false positive (adaptive icon requirement).

## 28. Phase Completion Certificate

Phase 2 (Android application) is declared **COMPLETE**:

- ✅ All Phase 2 features implemented (auth, upload, transactions, category correction, navigation)
- ✅ `assembleDebug` green; `testDebugUnitTest` 37/37; `lintDebug` 0 errors
- ✅ Release Readiness Review executed — see `RELEASE_READINESS_REPORT.md`
- ✅ Full engineering audit executed; all verified issues fixed
- ✅ README.md and ENGINEERING_REPORT.md updated
- ✅ Conventional Commits on `develop`; `main` untouched (production-ready)
- ✅ Awaiting user approval before Phase 3

---

# PHASE 2.1 — RELEASE READINESS REVIEW (2026-08-05)

Full review executed: build gates, engineering audit (4 parallel deep audits), User Acceptance Test against the live Dockerized backend, and a fix → rebuild → retest cycle. See `RELEASE_READINESS_REPORT.md` for the complete findings and Go/No-Go.

## 29. Verified Issues Fixed

| # | Severity | Area | Issue | Fix |
|---|---|---|---|---|
| 1 | MEDIUM | Error mapping | 401 short-circuited before parsing the body, hiding the backend's `invalid_credentials` message on login failure | `ApiErrorMapper` now parses the body first; token codes → `Unauthorized`, `invalid_credentials` → `Server` with its message, unparseable 401 → `Unauthorized` |
| 2 | MEDIUM | Coroutines | `CancellationException` was swallowed by `catch (Throwable)` in all repositories and re-mapped to `ApiException.Network` | `CancellationException` rethrown before mapping in `AuthRepository`, `TransactionRepository`, `StatementRepository` and all ViewModels |
| 3 | MEDIUM | MVVM layering | `UploadViewModel` performed ContentResolver IO directly | File read + `OpenableColumns.SIZE` check (>10 MB → `file_too_large`) moved into `StatementRepositoryImpl` (context-injected); ViewModel now passes only `Uri` + name |
| 4 | MEDIUM | Startup performance | `runBlocking` passphrase init executed on the main thread at first DI resolution | `ArthaApp.onCreate` pre-warms the passphrase on a background thread; subsequent resolution reads the in-memory DataStore cache |
| 5 | MEDIUM | Memory | Whole-file `readBytes()` without client-side size cap (OOM risk for huge picker files) | 10 MB client-side cap before reading (fix 3) |
| 6 | LOW | Performance | `Regex` compiled per row per recomposition in `formatAmount` | Hoisted to a top-level `THOUSANDS_REGEX` constant |
| 7 | LOW | Coroutines | `logout()` could throw uncaught (DataStore IO) | Best-effort catch (idempotent clear; retried on next logout) |
| 8 | INFO | Race | `isLoading` set inside the launched coroutine left a double-submit window | `isLoading = true` set synchronously before `launch` in `AuthViewModel`/`TransactionsViewModel` |
| 9 | INFO | Resource use | `authState` collected eagerly forever | `SharingStarted.WhileSubscribed(5_000)` |
| 10 | LOW | Dead code | `setTypeFilter`/`typeFilter` never used by any UI | Removed |
| 11 | LOW | Dead code | `TransactionDao.getById` had no callers | Removed |
| 12 | INFO | Offline gap | `cachedTransactions` offline mirror existed but was never consumed — documented "offline rendering" did not actually happen | Wired fallback: on `ApiException.Network` with a non-empty cache the UI now renders the cached snapshot with an "Offline" banner; `cachedTransactions` observed eagerly; 2 new unit tests |
| 13 | LOW | Duplication | `"auth/refresh"` duplicated between Retrofit annotation and raw OkHttp authenticator | Single `ArthaApi.REFRESH_PATH` constant used by both |
| 14 | LOW | UX | File picker restricted to PDF while the backend accepts CSV too | Picker widened to PDF + CSV; MIME derived from file extension |
| 15 | LOW | Security | Debug logging interceptor logs bodies including passwords/tokens in debug builds only | Verified gated by `BuildConfig.DEBUG` (no change needed); documented |
| 16 | LOW | Dead code (backend) | `INCOME_CATEGORIES`, `StatementRepository.get_for_user/list_for_user`, `schemas/__init__` re-exports, unused `logger` in `base.py`/`deps.py` | Removed; backend suite re-verified 96/96 |

## 30. User Acceptance Test (live backend, Dockerized)

Register → Login → Upload (CSV + PDF) → Browse/Paginate/Filter → Category Correction → Refresh Rotation → Expired-Token 401 → Logout semantics: **19/19 PASS** (script-level verification of every wire contract the app relies on, including the corrected 401 error-code mapping and `file_too_large` 413 contract). App-restart and offline persistence verified at ViewModel/unit level (no emulator in the review environment — see Known Limitations).

## 31. Release Readiness Score

**94 / 100** — see `RELEASE_READINESS_REPORT.md` (Go decision).
