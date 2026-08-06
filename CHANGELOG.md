# Changelog

All notable changes to Artha Pilot are documented here, per release.

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
