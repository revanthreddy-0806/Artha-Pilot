# Release Readiness Report — Artha Pilot v3.0.0-phase3

**Version:** 3.0.0-phase3 (Insights · Budgets · Recurring/EMI · Risk · Statement history · Offline queue)
**Date:** 2026-08-06
**Reviewer:** Principal Engineer (automated audit + live verification)
**Branches:** `develop` (source of truth) · `main` untouched
**Tag:** `v3.0.0-phase3`

---

## 1. Build Status

| Gate | Command | Result |
|---|---|---|
| Debug APK | `.\gradlew.bat :app:assembleDebug` | ✅ BUILD SUCCESSFUL — `app-debug.apk` |
| Release APK (R8 minify + shrink) | `.\gradlew.bat :app:assembleRelease` | ✅ BUILD SUCCESSFUL (incl. `lintVitalRelease`) |
| Unit tests | `.\gradlew.bat :app:testDebugUnitTest` | ✅ 75/75 (11 suites) |
| Lint | `.\gradlew.bat :app:lintDebug` | ✅ 0 errors, 5 warnings |
| Backend suite | `python -m pytest -q` (venv) | ✅ 142/142 |
| Backend syntax | `python -m compileall` | ✅ clean |
| Toolchain | Gradle 9.6.1 · AGP 9.3.1 · Kotlin/Compose 2.3.10 · KSP 2.3.10 · Java 17 · compileSdk 37 / minSdk 26 · FastAPI/Python 3.12 | ✅ |

## 2. Test Results

- **Android unit tests: 75/75 passing** (11 suites): ApiErrorMapperTest 6 · ArthaApiTest 15 (MockWebServer, incl. 8 new Phase 3 endpoint contracts) · MappersTest 13 · ApiExceptionTest 5 · AuthViewModelTest 6 · TransactionsViewModelTest 11 · InsightsViewModelTest 3 · BudgetsViewModelTest 5 · RecurringViewModelTest 2 · RiskViewModelTest 3 · StatementsViewModelTest 5.
- **Backend: 142/142 passing** (was 96; +46): recurring detector 11, risk service 7, rate limiter 7, insights API 7, budgets API 7, meta API 3, statements pagination +3.
- **User Acceptance Test (live Dockerized backend): 31/31 PASS** — register/login, categories (18 unique), CSV upload (7 txns), PDF upload (5 txns), statements pagination (total=2, newest first, auth 401), insights summary/trends (6 months sorted)/breakdown (non-empty month, sorted desc)/invalid month 422, recurring list, risk profile (score band + tenure 60), budgets (set/update/list with spent+percentage/delete 204/empty list/delete missing 404/invalid category 422), register rate limiting (10/min honored: no 429 in first 10, 429 at 11+), refresh rotation.

## 3. Lint Summary

**0 errors, 5 warnings** (all known and intentional):
- 4× `NewerVersionAvailable` (kotlin compose 2.4.10, retrofit 3.0.0 ×2, coroutines 1.11.0) — current versions pinned/verified; upgrades deferred.
- 1× `ObsoleteSdkInt` (`mipmap-anydpi-v26`) — false positive; adaptive icons require the `-v26` qualifier.

## 4. Security Audit

| Item | Verdict |
|---|---|
| JWT storage / refresh rotation | ✅ DataStore, never logged; rotation with jti revocation verified live |
| DB encryption | ✅ SQLCipher 4.17.0, 32-byte SecureRandom passphrase, AES/GCM sealed in Android Keystore |
| Backup exclusions | ✅ `allowBackup=false` + `fullBackupContent=false` + `dataExtractionRules` (all domains excluded) |
| Cleartext policy | ✅ Dev-only hosts via `network_security_config`; release uses HTTPS URL slot |
| API base URL | ⚠️ Per-build `API_BASE_URL` now: debug `http://10.0.2.2:8000/api/v1/`, release HTTPS placeholder — **release URL must be set to the real production endpoint before shipping** (Known Issue) |
| R8 minification | ✅ Release minified + resource-shrunk; DTO keep rules retained |
| Auth rate limiting | ✅ Register 10/min, login/refresh 60/min per client; 429 `rate_limited` under the unified error contract; verified live |
| Backend | ✅ bcrypt(12), algorithm-pinned JWT, parameterized SQL, upload whitelist + 10 MB cap. ⚠️ Documented hardening backlog: prod compose defaults (JWT secret/ENVIRONMENT), CORS `*` dev default, magic-byte upload sniffing, shared rate-limit store for multi-worker |

## 5. Performance Audit

| Item | Verdict |
|---|---|
| Main-thread blocking | ✅ None — all IO on IO/OkHttp threads; passphrase pre-warmed off-main |
| Insights aggregation | ✅ Single query per month window; trends computed in Python over one fetch; ~55k txns/s classifier unaffected |
| Pagination | ✅ Transactions + statements page size 50 with `hasMore` guard; ⚠️ unbounded in-memory list across many pages (Paging 3 recommended, no correctness impact) |
| Memory | ✅ 10 MB client-side upload cap; offline queue grows only with pending corrections |
| Room | ✅ `@Upsert` mirroring, Flow observation, migration 1→2 applied via `MIGRATION_1_2` |

## 6. Technical Debt

- Unbounded in-memory transaction/statement lists → adopt Paging 3.
- In-memory fixed-window rate limiter is per-process → shared store (Redis) for multi-worker deployments.
- No instrumentation tests (Room/SQLCipher/Compose UI/TokenAuthenticator) — emulator/device run recommended before store release.
- `AppError.details` unused on the backend (part of the error contract; keep).
- Risk model is rule-based, no bureau data (educational scope, by design).

## 7. Known Issues

1. **Release API URL**: release build points at a placeholder HTTPS URL; must be overridden with the real endpoint before shipping. [MEDIUM]
2. **No instrumentation tests** — build + unit + live API UAT only. [MEDIUM]
3. **Auth start-destination flash** on cold start while DataStore loads (cosmetic). [LOW]
4. **Backend prod hardening** deferred (documented Phase 1.5 + Phase 3): prod compose defaults, CORS hardening, magic-byte validation, Redis rate-limit store. [MEDIUM]
5. **Offline**: category corrections queue and replay; uploads still require connectivity. [LOW]
6. `mipmap-anydpi-v26` lint warning is a false positive. [INFO]

## 8. Release Notes — v3.0.0-phase3

- **Insights**: monthly summary + savings rate, 6-month income/expense chart, category breakdown, 5-tab bottom navigation (Transactions / Insights / Budgets / Recurring / Risk), statement history screen.
- **Budgets**: per-category monthly limits with live spent %, overrun states, add/edit/delete; home-screen alerts for overruns and EMIs due within 7 days.
- **Recurring/EMI detection**: automatic detection on ingest (≥2 distinct months, 25–40-day interval, confidence score); EMI classification for loans/housing/credit-card debits.
- **Risk profile**: 0–100 credit-health score with factor breakdown; safe-EMI capacity (30% of income) and estimated loan principal (10.5% p.a., 60 months).
- **Offline queue**: category corrections made offline persist (Room v2) and replay on reconnect.
- **Backend**: rate limiting on auth (10/min register, 60/min login+refresh); migrations auto-applied at container start.
- 142/142 backend + 75/75 Android tests, 31/31 UAT checks, 0 lint errors.

## 9. Readiness Score

| Category | Score |
|---|---|
| Build gates (debug/release-R8/tests/lint) | 20/20 |
| Security posture | 18/20 (release URL override + prod hardening backlog) |
| Performance | 18/20 (paging growth noted) |
| Test coverage & UAT | 19/20 (instrumentation suite outstanding) |
| Architecture & code quality | 19/20 (debt list above) |
| Documentation & process | 20/20 |
| **Total** | **95 / 100** (up from 94/100 at v2) |

## 10. Go / No-Go Decision

**✅ GO — release-ready for v3.0.0-phase3 on `develop`.**

Rationale:
- All build gates green (debug, release-with-R8, 75/75 unit tests, lint 0 errors, 142/142 backend).
- 31/31 live UAT checks against the Dockerized stack, including a bug found during UAT (migrations not applied at container start) — fixed (Dockerfile now runs `alembic upgrade head`) and re-verified.
- Tag `v3.0.0-phase3` created on `develop`; `main` untouched per policy.

Conditional items for **production** (not blockers for this phase-3 release): real HTTPS release URL, store signing, instrumentation suite, backend prod-compose hardening, shared rate-limit store.

*Approval from the client remains required before any production promotion.*
