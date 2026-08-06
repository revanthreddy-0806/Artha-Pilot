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

## 11. Final Sign-Off Addendum (2026-08-06)

Independent principal-engineer verification (clean-room clone + fresh Docker/DB + live UAT + full audit) confirmed the release and found **no release-blocking defects**. All verified issues were fixed, re-tested, and are summarized here; final figures supersede those above.

### 11.1 Final verified numbers
| Gate | Result |
|---|---|
| Backend suite | ✅ **149/149** (142 baseline + 7 sign-off tests), 0 failed, coverage **94%**, `compileall` clean |
| Android unit tests | ✅ **79/79** (12 suites; +4 `TokenAuthenticatorTest`) |
| Android build gates | ✅ `assembleDebug` · `assembleRelease` (R8) · `lintDebug` 0 errors / 5 benign warnings |
| Live UAT (fresh Dockerized stack, `uat_e2e.ps1`) | ✅ **47/47** — includes 401/expired/forged-JWT flows, refresh rotation + reuse rejection, client-side logout (no server endpoint, 404), CSV 7 + PDF 5, 413 >10 MB, filters + 422, correction persistence, cross-user isolation, budgets/insights/risk, DB persistence across `docker compose restart backend`, register rate limit (429 at 11+) |

### 11.2 Verified fixes applied
- **Backend**: validation errors no longer log/echo plaintext request bodies (`errors.py`); refresh-rotation concurrency race → 401 instead of 500 (`auth_service.py`); budget upsert race converges instead of 500 (`budget_repository.py`); `DEBUG` defaults to `False`.
- **Android**: **logout wipes the Room cache before clearing tokens** — fixes both the doc/code mismatch (Phase 2 claimed Room wipe + `/auth/logout`; there is no server logout endpoint) and real tenant-isolation leakage (next account could previously render the prior account's cached data offline and replay its pending corrections); `TokenAuthenticator` clears tokens only on 401 refresh failures (5xx/429 no longer log the user out); passphrase first-run generation is single-flight (Mutex) so the DB is always opened with the persisted passphrase; cleartext network config moved to `src/debug` (release bans cleartext); statement `createdAt` real timestamp; `unauthorized` 401s map to the proper client error type.

### 11.3 Documented limitations (not fixed at sign-off — see table with rationale in ENGINEERING_REPORT §40.5)
- JWTs (incl. 7-day refresh) at rest plaintext in DataStore → token-at-rest encryption is the v4 backlog priority (Keystore already covers the DB).
- Release `API_BASE_URL` is a placeholder — supply the real endpoint when packaging (release-blocking checklist item).
- Prod-compose hardening: set `ENVIRONMENT=production` + strong `JWT_SECRET_KEY` in production so the secret guard fires; CORS wildcard is the dev default.
- Async-upload CPU work on the event loop, `float` money, extension-only upload sniffing, per-process rate limiter, XFF trust, refresh-family revocation — all documented hardening backlog.

### 11.4 Decision
**✅ GO (GO WITH DOCUMENTED BACKLOG).** Zero verified defects remain in the tagged code; every non-fixed item is an explicitly documented limitation or a production-deployment configuration. `main` remains untouched; tag `v3.0.0-phase3` will be re-cut from the final verified commit on `develop`.

*Approval from the client remains required before any production promotion.*

---

## 12. Release Engineering Pass (2026-08-06)

Final Release-Engineering pass on `develop`: dependency-CVE remediation + release-configuration wiring + full gate re-run. **This section supersedes §10–11 for any conflicts.**

### 12.1 Commits added on `develop`
| Commit | Scope |
|---|---|
| `ac7f506` | `fix(backend)`: bump pinned deps to patched releases for 2026 CVEs |
| `5ec2e0f` | `build(android)`: wiring for release signing (gitignored keystore.properties) + configurable production API URL |

### 12.2 Build / Test / Lint Status (re-run after the changes)
| Gate | Result |
|---|---|
| `.\gradlew.bat :app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `.\gradlew.bat :app:assembleRelease` | ✅ BUILD SUCCESSFUL (R8 minify + shrink; **unsigned** — see 12.4.1) |
| `.\gradlew.bat :app:testDebugUnitTest` | ✅ **79/79** (12 suites, 0 failures) |
| `.\gradlew.bat :app:lintDebug` | ✅ **0 errors / 5 warnings** (4 version notices, 1 `v26` false positive) |
| Backend `python -m pytest` | ✅ **149/149**, coverage **94%** |
| Backend `compileall` + `pip-audit` (full transitive) | ✅ clean · **"No known vulnerabilities found"** |
| Toolchain | Gradle 9.6.1 · AGP 9.3.1 · Kotlin/Compose 2.3.10 · Java 17 · SDK 37/26 · Python 3.12 |

### 12.3 Security Audit (delta vs §4)
| Item | Verdict |
|---|---|
| Vulnerable pinned deps (pypdf 5.1.0, PyJWT 2.10.1, python-multipart 0.0.20, starlette 0.41.3 via fastapi 0.115.6) | ✅ **Remediated** — pypdf 6.15.0 · PyJWT 2.13.0 · python-multipart 0.0.32 · fastapi 0.141.1 (starlette 1.4.1); full transitive scan clean |
| Release signing | ✅ **Wired** via gitignored `keystore.properties`; **no secrets committed**; builds warn loudly when unsigned |
| Release URL | ✅ **Configurable** via `ARTHA_PILOT_API_BASE_URL` (gradle property or env); placeholder fallback + build-time warning |
| Debug/release separation (verified in APKs) | ✅ Debug: `http://10.0.2.2:8000`, cleartext config debug-only → absent from release; release not debuggable, HTTPS-only, R8 mapping generated |

### 12.4 Technical Debt / Known Issues (updated)
- Unsigned release APK until the operator provisions `android/keystore.properties` (see 12.5). [BLOCKER at ship, not a code defect]
- Release `API_BASE_URL` placeholder until `ARTHA_PILOT_API_BASE_URL` is set. [BLOCKER at ship, not a code defect]
- Unchanged from prior sections: Paging 3, shared rate-limit store (Redis), instrumentation tests, prod-compose hardening (`ENVIRONMENT`, JWT secret, CORS), token-at-rest encryption (v4), event-loop upload work.

### 12.5 Readiness Score (final)
| Category | Score |
|---|---|
| Build gates (debug/release-R8/tests/lint) | 20/20 |
| Security posture | 19/20 (signing + URL are operator-supplied; wiring verified) |
| Performance | 18/20 (paging growth noted) |
| Test coverage & UAT | 19/20 (instrumentation suite outstanding) |
| Architecture & code quality | 19/20 |
| Documentation & process | 20/20 |
| **Total** | **95 / 100** |

### 12.6 Go / No-Go Decision (final)
**✅ GO (CONDITIONAL) — codebase is release-ready on `develop`.**
- All build/test/lint/security gates green; all prior release-blocking code findings closed (incl. dependency CVEs).
- Release is **not shippable as-is** only because two artifacts require operator inputs that cannot live in the repo: (1) a release keystore to sign APKs, (2) the production HTTPS `API_BASE_URL`. Both are one-step configuration actions now documented at the point of use.
- `main` remains untouched. Tag `v3.0.0-phase3` is unchanged (still `b71cf4c`); recut at final-verified head by the owner.
