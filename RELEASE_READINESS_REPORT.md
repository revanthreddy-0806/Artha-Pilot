# Release Readiness Report — Artha Pilot v2.0.0-phase2

**Version:** 2.0.0-phase2 (Android client; Phases 1 + 1.5 backend baseline)
**Date:** 2026-08-05
**Reviewer:** Principal Engineer (automated audit + live verification)
**Branches:** `develop` (source of truth) · `main` untouched
**Tag:** `v2.0.0-phase2`

---

## 1. Build Status

| Gate | Command | Result |
|---|---|---|
| Debug APK | `.\gradlew.bat :app:assembleDebug` | ✅ BUILD SUCCESSFUL — `app-debug.apk` |
| Release APK | `.\gradlew.bat :app:assembleRelease` | ✅ BUILD SUCCESSFUL (incl. `lintVitalRelease`) |
| Unit tests | `.\gradlew.bat :app:testDebugUnitTest` | ✅ 37/37 |
| Lint | `.\gradlew.bat :app:lintDebug` | ✅ 0 errors, 5 warnings |
| Backend suite | `pytest` (Docker/venv) | ✅ 96/96 |
| Toolchain | Gradle 9.6.1 wrapper · AGP 9.3.1 · Kotlin/Compose 2.3.10 · KSP 2.3.10 · Java 17 · compileSdk 37 / minSdk 26 | ✅ |

## 2. Test Results

- **Android unit tests: 37/37 passing, 0 failures, 0 errors** (6 suites):
  - ApiErrorMapperTest 6 · ArthaApiTest (MockWebServer) 7 · MappersTest 7 · ApiExceptionTest 5 · AuthViewModelTest 6 · TransactionsViewModelTest 7 (incl. new offline-cache-fallback tests)
- **Backend: 96/96 passing** (regression check after dead-code cleanup).
- **User Acceptance Test (live Dockerized backend): 19/19 PASS** — register, login (+ wrong-password → 401 `invalid_credentials`), CSV upload (7 txns, `processed`), PDF upload (5 txns, `processed`), oversized file → 413 `file_too_large`, browse/paginate/category-filter, PATCH category correction + persistence, refresh-token rotation (reuse → 401), expired access token → 401 `invalid_token` + refresh recovery, missing token → 401 `missing_token`, logout semantics.

## 3. Lint Summary

**0 errors, 5 warnings** (all previously known and intentional):
- 4× `NewerVersionAvailable` (kotlin compose 2.4.10, retrofit 3.0.0 ×2, coroutines 1.11.0) — current versions pinned/verified; upgrades deferred.
- 1× `ObsoleteSdkInt` (`mipmap-anydpi-v26`) — false positive; adaptive icons require the `-v26` qualifier (AAPT fails without it).

## 4. Security Audit

| Item | Verdict |
|---|---|
| JWT storage / refresh rotation | ✅ DataStore, never logged; rotation with jti revocation verified live |
| DB encryption | ✅ SQLCipher 4.17.0, 32-byte SecureRandom passphrase, AES/GCM sealed in Android Keystore; passphrase pre-warmed off the main thread |
| Backup exclusions | ✅ `allowBackup=false` + `fullBackupContent=false` + `dataExtractionRules` (all domains excluded) |
| Cleartext policy | ✅ Dev-only hosts (10.0.2.2/localhost) via `network_security_config`; release should use HTTPS |
| Debug logging | ✅ Logging interceptor `BODY` only in debug builds, `NONE` in release |
| API base URL | ⚠️ Single `API_BASE_URL` for all build types (dev value `http://10.0.2.2:8000`); **release builds must override with an HTTPS URL before shipping to production** (Known Issue) |
| Backend | ✅ bcrypt(12), algorithm-pinned JWT, parameterized SQL, upload extension whitelist + 10 MB cap. ⚠️ Documented hardening backlog: prod compose defaults (JWT secret/ENVIRONMENT), CORS `*` dev default, rate limiting, magic-byte upload sniffing |

## 5. Performance Audit

| Item | Verdict |
|---|---|
| Main-thread blocking | ✅ Eliminated: passphrase warm-up off-main; no `getSharedPreferences`; all IO on `Dispatchers.IO`/OkHttp threads |
| Composition allocations | ✅ `Regex` hoisted out of per-item formatting; `LazyColumn` stable keys |
| Pagination | ✅ Page size 50 with `hasMore` guard, race-free `isLoading` flag |
| Memory | ✅ 10 MB client-side upload cap before reading; ⚠️ list grows unbounded across many pages (Paging 3 recommended for Phase 3, no correctness impact) |
| Room | ✅ Indexed `statementId`, `@Upsert` mirroring, Flow observation |

## 6. Technical Debt

- Unbounded in-memory transaction list across many pages → adopt Paging 3 (Phase 3).
- Duplicated category-name lists (HomeScreen filters/dialog vs backend `CATEGORIES`) — serve a `/meta/categories` endpoint in Phase 3 to keep a single source of truth.
- `statement` table written but not yet surfaced in UI (statements list is a Phase 3 feature).
- Minify/obfuscation disabled for release (`isMinifyEnabled=false`) — enable with R8 + DTO keep rules when signing a store build.
- `AppError.details` parameter unused on the backend (part of the error contract; keep).

## 7. Known Issues

1. **Release API URL**: release APK points at the emulator dev host. Must be overridden (HTTPS) for production. [MEDIUM]
2. **No instrumentation tests** — Room/SQLCipher/Compose/TokenAuthenticator verified by build, unit tests and live API UAT only; device/emulator run recommended before store release.
3. **Auth start-destination flash** on cold start while DataStore loads (cosmetic).
4. **Offline**: cached snapshot is shown offline; uploads and category corrections require connectivity (queuing is Phase 3).
5. **Backend prod hardening** deferred (documented Phase 1.5): rate limiting, refresh-revocation endpoint, CORS hardening, compose production defaults, magic-byte validation.
6. `mipmap-anydpi-v26` lint warning is a false positive.

## 8. Release Notes — v2.0.0-phase2

- Android client (Compose MVVM): login/register, JWT storage + transparent refresh rotation, statement upload (PDF + CSV) with client/server 10 MB caps, transaction browsing with pagination and category filters, category correction dialog, logout.
- Encrypted Room cache (SQLCipher + Keystore) with offline snapshot rendering.
- Release-readiness hardening: structured-concurrency cancellation handling, 401 error-message fidelity (`invalid_credentials` now surfaces), IO moved to the repository layer, main-thread startup stall removed, dead code removed (client + backend), offline fallback wired and tested.
- 37/37 Android unit tests, 96/96 backend tests, 19/19 UAT checks, 0 lint errors.

## 9. Readiness Score

| Category | Score |
|---|---|
| Build gates (debug/release/tests/lint) | 25/25 |
| Security posture | 18/20 (release URL override + prod hardening backlog) |
| Performance | 18/20 (paging growth noted) |
| Test coverage & UAT | 19/20 (instrumentation suite outstanding) |
| Architecture & code quality | 18/20 (technical debt list above) |
| Documentation & process | 20/20 |
| **Total** | **94 / 100** |

## 10. Go / No-Go Decision

**✅ GO — release-ready for v2.0.0-phase2 on `develop`.**

Rationale:
- All build gates green; zero lint errors; all tests passing (37 Android + 96 backend); 19/19 live UAT checks.
- Every verified audit issue fixed and re-verified (fix → rebuild → retest → repeat until green).
- Tag `v2.0.0-phase2` created on `develop`; `main` untouched per policy.

Conditional items for **production** (not blockers for this phase-2 release): HTTPS release URL, minify/signing, instrumentation suite, backend prod-compose hardening.

*Approval from the client remains required before Phase 3 begins.*
