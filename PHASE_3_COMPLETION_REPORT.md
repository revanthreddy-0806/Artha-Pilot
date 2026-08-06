# Phase 3 Completion Report — Insights, Budgets, Recurring/EMI, Risk & Offline Queue

**Project:** Artha Pilot — AI-Powered Personal Finance
**Phase:** 3 (Insights dashboard · budgets · recurring/EMI detection · risk profile · statement history · offline queuing)
**Status:** ✅ **COMPLETE**
**Date:** 2026-08-06
**Branch:** `develop` (tag `v3.0.0-phase3`)

---

## 1. Features Implemented

| Feature | Description | Status |
|---|---|---|
| Monthly insights | Income/expenses/savings summary, savings rate, top categories, 6-month trends chart, category breakdown | ✅ |
| Monthly budgets | Per-category limits with current-month spent %, overrun detection; add/edit/delete from the app | ✅ |
| Recurring / EMI detection | Auto-detects recurring debits and EMIs (interval, confidence, next expected date); flags refreshed after every upload | ✅ |
| Risk profile | 0–100 credit-health score with band + factor breakdown; safe-EMI capacity and estimated loan principal (10.5% p.a., 60 months) | ✅ |
| Statement history | Paginated statement list with status, transaction counts and offline mirror | ✅ |
| Categories API | `GET /categories` — single source of truth for chips and budget dialog | ✅ |
| Auth rate limiting | Register 10/min, login/refresh 60/min per client; 429 under the unified error contract | ✅ |
| Home alerts | Budget overruns (warning) and EMIs due within 7 days (info) surfaced on the Transactions tab | ✅ |
| Offline category queue | Corrections made offline persist to Room and replay to the server on next fetch | ✅ |
| Release hardening | versionCode 3 / v3.0.0, per-build API URLs, R8 minify + resource shrinking | ✅ |

## 2. Screens Completed (Android)

| Screen | Route | Highlights |
|---|---|---|
| Transactions (Home) | `home` | Alerts section, API-driven category chips, recurring badge, logout, history + upload buttons |
| Insights | `insights` | Summary card, 6-month income/expenses bar chart, category bars, offline banner |
| Budgets | `budgets` | Progress cards, overrun states, add/edit dialog (category dropdown), delete |
| Recurring | `recurring` | EMI/RECURRING badges, confidence, interval, next expected date |
| Risk | `risk` | Score ring + band color, factor impacts, loan-eligibility card |
| Statements | `statements` | Paged history, status chips, infinite scroll, offline cache fallback |
| Upload | `upload` | Unchanged (Phase 2), reachable from Home |

## 3. APIs Integrated (new in Phase 3)

| Endpoint (v1) | Method | Used By |
|---|---|---|
| `/categories` | GET | Home chips + budget dialog |
| `/statements` | GET | StatementsViewModel |
| `/budgets` | GET / PUT / DELETE | BudgetsViewModel |
| `/insights/summary` | GET | InsightsViewModel |
| `/insights/monthly-trends` | GET | InsightsViewModel |
| `/insights/category-breakdown` | GET | InsightsViewModel |
| `/insights/recurring` | GET | RecurringViewModel + Home alerts |
| `/insights/risk-profile` | GET | RiskViewModel |

## 4. Database Changes

- **Backend (migration 0003):** `budgets (id, user_id FK, category, monthly_limit, created_at, updated_at)` + unique `(user_id, category)` + user index.
- **Android (Room v2, `MIGRATION_1_2`):** `pending_changes (id, transactionId, newCategory, createdAt)` for the offline correction queue; `StatementDao.upsertAll` added for the history mirror.

## 5. Testing Metrics

| Metric | Backend | Android |
|---|---|---|
| Tests | **142** (was 96; +46) | **75** (was 37; +38) |
| New suites | recurring detector, risk service, rate limiter, insights API, budgets API, meta API | Insights/Budgets/Recurring/Risk/Statements ViewModels |
| Failures | 0 | 0 |
| Gate command | `python -m pytest -q` (venv) | `.\gradlew.bat testDebugUnitTest` |
| Lint | `compileall` clean | 0 errors, 5 warnings (informational) |
| Builds | — | `assembleDebug` + `assembleRelease` (R8) SUCCESS |

## 6. Key Verification Highlights

- EMI hint keywords and ≥₹15k 30-day non-Rent debits classify as `emi`; Rent stays `recurring`; credits are never flagged.
- Risk bands and the safe-EMI (30% income − existing EMI) + loan-principal formula unit-tested.
- Rate limiter: per-client isolation, window expiry, 429 contract; test env exempt.
- Android: PUT budgets serializes `monthly_limit` snake_case; DELETE → 204; pagination `hasMore` math verified (pageSize 50); offline queue replay keeps failing items queued.

## 7. Remaining Work / Known Limitations

- Instrumentation tests (Compose UI, Room/SQLCipher, TokenAuthenticator) still require an emulator.
- In-memory rate limiter is per-process (Redis needed for multi-worker production).
- Release `API_BASE_URL` is a placeholder HTTPS URL to be replaced before store release.
- Scanned/image PDFs remain unsupported (text extraction only).

## 8. Delivery Summary

- 9 Phase 3 commits on `develop` (4 backend, 5 android) — Conventional Commits; `main` untouched.
- Docs updated: README.md, ENGINEERING_REPORT.md, CHANGELOG.md, RELEASE_READINESS_REPORT.md.
- UAT against the live Dockerized backend: executed and recorded in RELEASE_READINESS_REPORT.md.
- Tag: `v3.0.0-phase3`.

## 9. Release Engineering Update (2026-08-06)

- **Dependencies**: pinned backend deps remediated against 2026 CVEs — pypdf 6.15.0, PyJWT 2.13.0, python-multipart 0.0.32, fastapi 0.141.1 (starlette 1.4.1). `pip-audit` full-transitive scan → 0 known vulnerabilities; 149/149 backend tests, coverage 94%.
- **Release config**: release signing wired via gitignored `android/keystore.properties`; production URL resolve from `ARTHA_PILOT_API_BASE_URL`. Verified debug/release separation (`http://10.0.2.2` and cleartext config are debug-only; release not debuggable, HTTPS-only).
- Gates re-run after changes: `assembleDebug`/`assembleRelease` ✅, `testDebugUnitTest` **79/79**, `lintDebug` **0 errors / 5 warnings**.
- See `ENGINEERING_REPORT.md` §41 and `RELEASE_READINESS_REPORT.md` §12.
