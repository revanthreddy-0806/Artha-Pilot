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

## [v2.0.0-phase2] — 2026-08-05

### Android
- Full Compose client: login/register, JWT storage with transparent refresh rotation, PDF/CSV upload, transaction browsing (pagination + filters), category correction, logout, encrypted Room cache with offline snapshot rendering, release-readiness hardening (cancellation handling, 401 fidelity, IO layering).
- 37 unit tests, lint clean, `assembleDebug` + `assembleRelease` green.

## [v1.0.0-phase1] — 2026-08-04

### Backend
- FastAPI service: JWT auth with refresh rotation + jti revocation, BCrypt(12) password hashing, PDF/CSV statement parsing with auto-format detection, 3-tier ML categorization (rules → TF-IDF + Logistic Regression → fallback), paginated/filtered transactions, category correction feedback, unified error contract, structured logging.
- Phase 1.5 audit: upload streaming guard, prod secret guard, CORS env, 404/405 contract, race handling, longest-match rules, enum single-source; migration 0002 `revoked_tokens`.
- 96 tests, 93% coverage.
