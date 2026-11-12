# Changelog

All notable changes to Artha Pilot are documented here, per release.

## [Unreleased] - 2026-11-12

### Tests
- Made the insights-trends, risk-profile and what-if integration fixtures date-relative. They hardcoded May–August 2026 transaction dates while the services use rolling 3-month windows, so four tests failed once the calendar moved past the fixtures. Assertions are unchanged; the suite is now stable over time.
- Verified baseline on this date: **258 backend tests passed**; **145 Android unit tests passed** (19 suites).

### CI
- Added `.github/workflows/ci.yml`: Python 3.12 backend test job plus a JDK 21 Android job that runs unit tests, `assembleDebug` and `lintDebug` against SDK 37.0 / build-tools 37.0.0. Triggers on pushes to `main`/`develop`, pull requests and manual dispatch; no secrets required.

### Transaction Management
- Added manual transaction CRUD (`POST`, `PATCH`, `DELETE /api/v1/transactions/{id}`) with `statement_id` nullable via Alembic migration `0006_nullable_statement_id`.
- Added transaction search, filtering, and sorting (`q` case-insensitive `ILIKE`, `category`, `transaction_type`, `start_date`, `end_date`, `min_amount`, `max_amount`, `sort_by`, `sort_dir` with deterministic ID tie-breaker and pagination).
- Added per-statement transaction drill-down using `statement_id` filtering and a clickable `StatementCard` modal in `StatementsScreen`.
- Added transaction deletion confirmation UI and form validation (required description, amount greater than zero, valid `YYYY-MM-DD` date).
- Added statement content-signature deduplication (`409 duplicate_statement`).

### Cross-Screen Reactive State
- Wired `DataInvalidationBus` (`StateFlow<Long>` monotonic revision counter with `requestId` guards) across Insights, Recurring, Budgets, Statements, Risk, and Recommendations.
- Category edits and risk questionnaire submissions now notify dependent screens.
- Updated verification metrics: **258 backend tests** and **145 Android unit tests (19 suites)** passing.

### Documentation
- Updated `README.md` for the implemented Phase 4 risk/recommendation/assistant features, assistant configuration, testing commands, the SQLite-vs-PostgreSQL verification gap, CI, and an explicit implemented-vs-backlog status.

## [Phase 4] — 2026-08-10

Phase 4 is committed on `main` but is **untagged**; the Android `versionName` remains `3.0.0`. No Phase 4 release-readiness audit has been performed.

### Backend
- **Explainable risk engine:** completed the risk-tolerance questionnaire (`GET /risk/questions`, `PUT /risk/questionnaire`, `GET /risk/profile`), persistence in migration `0004_risk_questionnaire`, and a combined 0–100 assessment (50% questionnaire tolerance + 50% spending behaviour) with safe-EMI and loan-capacity estimates.
- **Recommendations:** `GET /recommendations` builds a three-month income/expense context and returns a deterministic equity/debt/gold allocation for the risk band, with per-asset rationale, caution/info notes and an educational disclaimer. `POST /recommendations/what-if` simulates a category expense reduction without mutating stored data; invalid scenarios return `422 invalid_scenario`.
- **Market reference data:** daily (not real-time) Yahoo Finance chart closes are cached in migration `0005_market_data` and returned with `live` / `cached` / `unavailable` status. Unavailability degrades gracefully and never fabricates a price.
- **Grounded assistant:** `POST /assistant/chat` aggregates verified insights, recurring items, risk, recommendations and market references into a fenced JSON context; an OpenAI-compatible model may only rephrase those values. Supports up to 20 prior history messages per request, treats user/merchant text as data (prompt-injection resistance), caps response length, and returns `503 llm_not_configured` when no provider is configured.
- **Configuration:** LLM settings are loaded from `backend/.env` via Compose `env_file`; `.env` is gitignored and excluded from the image via `.dockerignore`. Gemini is supported through its OpenAI-compatible endpoint.
- Hardened statement intelligence (PDF parsing, classifier cache/evaluation) and resolved transaction-categorization conflicts.

### Android
- Completed the risk-tolerance questionnaire flow, including answer validation, submit/retake, and the combined tolerance/behaviour score display.
- Added the Investment Suggestions screen: spending context, allocation breakdown with rationale, caution notes, market reference cards, and the what-if simulator.
- Added the **Ask** tab: grounded assistant chat with suggested prompts, multi-turn history, and thinking/error states (network failures surface as an error message).
- Navigation now exposes six bottom tabs (Transactions / Insights / Budgets / Recurring / Risk / Ask); recommendations open from Risk and statement history from Home.
- Stabilized SQLCipher startup, token persistence and statement upload; corrected insights trend labels; raised the assistant chat read timeout.

## [v3.0.0-phase3] — 2026-08-06

### Backend
- **Insights API**: monthly summary, monthly trends (1–24 months), category breakdown, recurring/EMI list, risk profile with safe-loan eligibility estimate.
- **Budgets API**: per-category monthly budgets (PUT upsert, DELETE, current-month spent/percentage/overrun); migration 0003 `budgets` table.
- **Recurring/EMI detection**: pure-logic detector over the transaction history; `is_recurring` flags refreshed after every statement ingest.
- **Statement history**: paginated `GET /statements` with totals.
- **Categories**: `GET /categories` as the authoritative taxonomy.
- **Auth rate limiting**: register 10/min, login + refresh 60/min per client (429 under the unified error contract).
- 46 new tests → suite at **142 passed**; new error codes `invalid_month`, `budget_not_found`, `rate_limited`.

### Android
- New tabs: **Insights** (summary + 6-month chart + category breakdown), **Budgets** (CRUD + overrun states), **Recurring** (EMI/recurring list with next expected dates), **Risk** (score ring + loan eligibility), **Statements** (paged history).
- Home: dynamic category chips from the API, budget-overrun and EMI-due alerts, recurring badge on transactions.
- Offline category-correction queue (Room v2 migration) replayed on reconnect.
- Release hardening: versionCode 3 / versionName 3.0.0, per-build API URLs, R8 minify + resource shrinking.
- 38 new tests → suite at **75 passed**; lint 0 errors.

### Sign-off hardening (2026-08-06, final verification)
- Backend: validation errors no longer log/echo plaintext request bodies; refresh-rotation and budget-upsert concurrency races return 401/409-ish responses instead of 500s; `DEBUG` defaults to `False`. +7 tests → **149 passed**, coverage **94%**.
- Android: **logout now wipes the Room cache** before clearing tokens (fixes tenant-isolation leak — prior account's cached data/pending corrections are no longer visible or replayable by the next account); refresh failures no longer force-log-out on transient 5xx/429 (only 401 clears the session); first-run passphrase generation made single-flight; cleartext network config restricted to debug builds; statement `createdAt` is a real timestamp; `unauthorized` 401s map to the correct client error. +4 tests → **79 passed** (12 suites).
- Live UAT against a fresh Dockerized stack: **47/47** checks green (auth/refresh/expiry/forgery flows, uploads incl. 413 >10 MB, filters, correction persistence, cross-user isolation, budgets/insights/risk, DB persistence across container restart, rate limiting 429 at 11+).
- Clean-room verification: fresh clone → fresh venv → fresh Docker volume → migrations auto-applied at 0003 → all gates green; `main` untouched.

### Release-engineering pass (post sign-off; commits `ac7f506`, `5ec2e0f`)
- **Backend dependency CVE remediation** (`requirements.txt`): pypdf 5.1.0 → 6.15.0 (PDF DoS CVE-2026-57204 + other advisories), PyJWT 2.10.1 → 2.13.0 (crit-header bypass GHSA-752w-5fwx-jx9f / CVE-2026-32597), python-multipart 0.0.20 → 0.0.32 (multipart DoS, path traversal), fastapi 0.115.6 → 0.141.1 (starlette 0.41.3 → 1.4.1), pytest 8.3.4 → 9.0.3 (dev-only). `pip-audit` full transitive scan → **0 known vulnerabilities**; 149/149 tests, coverage **94%**.
- **Android release config** — signing wired from gitignored `android/keystore.properties` (template `keystore.properties.example`; no secrets committed); release `API_BASE_URL` resolved from `ARTHA_PILOT_API_BASE_URL` (gradle property/env) with placeholder warning. Verified in artifacts: release APK not debuggable, no cleartext network config, R8 mapping generated, debug URL absent from release.
- Gates re-run after changes: `assembleDebug`/`assembleRelease` ✅, `testDebugUnitTest` **79/79**, `lintDebug` **0 errors / 5 warnings**.

## [v2.0.0-phase2] — 2026-08-05

### Android
- Full Compose client: login/register, JWT storage with transparent refresh rotation, PDF/CSV upload, transaction browsing (pagination + filters), category correction, logout, encrypted Room cache with offline snapshot rendering, release-readiness hardening (cancellation handling, 401 fidelity, IO layering).
- 37 unit tests, lint clean, `assembleDebug` + `assembleRelease` green.

## [v1.0.0-phase1] — 2026-08-04

### Backend
- FastAPI service: JWT auth with refresh rotation + jti revocation, BCrypt(12) password hashing, PDF/CSV statement parsing with auto-format detection, 3-tier ML categorization (rules → TF-IDF + Logistic Regression → fallback), paginated/filtered transactions, category correction feedback, unified error contract, structured logging.
- Phase 1.5 audit: upload streaming guard, prod secret guard, CORS env, 404/405 contract, race handling, longest-match rules, enum single-source; migration 0002 `revoked_tokens`.
- 96 tests, 93% coverage.
