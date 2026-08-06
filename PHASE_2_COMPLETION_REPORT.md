# Phase 2 Completion Report — Android Application

**Project:** Artha Pilot — AI-Powered Personal Finance
**Phase:** 2 (Android client)
**Status:** ✅ **COMPLETE**
**Date:** 2026-08-05
**Branch:** `develop` (targets the Phase 1 + 1.5 FastAPI backend)

---

## 1. Features Implemented

| Feature | Description | Status |
|---|---|---|
| Authentication | Login + Register against the Phase 1 backend; JWT access/refresh pair stored in DataStore | ✅ |
| Token rotation | OkHttp `TokenAuthenticator` silently refreshes access token on 401 (single-flight, one retry, session cleared on double failure) | ✅ |
| Statement upload | PDF statement picker via system FilePicker/ContentResolver; multipart upload to `/statements/upload`; server parses, extracts and categorizes | ✅ |
| Transaction browsing | Home dashboard: monthly expense summary, category breakdown, transaction list with balance + category chips | ✅ |
| Infinite pagination | Page size 50 with `hasMore` guard; next page auto-fetched on scroll | ✅ |
| Category correction | Users can correct a transaction's category; PATCH round-trips to server | ✅ |
| Offline cache | Last successful fetch mirrored into Room; on network failure the Home screen renders the cached snapshot with an offline banner | ✅ |
| Encrypted storage | Room DB encrypted with SQLCipher 4.17.0; 32-byte random passphrase sealed (AES/GCM) in Android Keystore | ✅ |
| Backup protection | `allowBackup=false`, `fullBackupContent=false`, `dataExtractionRules` exclude all data | ✅ |
| Session management | Logout clears DataStore tokens + wipes the Room cache (client-side only; there is no `/auth/logout` endpoint — refresh tokens remain valid until natural expiry) | ✅ |

## 2. Screens Completed

| Screen | Route | Highlights |
|---|---|---|
| **Login** | `login` | Email + password, inline validation, error snackbar, loading state, navigation to Home on success |
| **Register** | `register` | Name/email/password, confirm password, pre-fills from previous screen, back to Login |
| **Home** | `home` | Greeting, total balance, expense summary, category breakdown, filtered transaction list (All/Income/Expense), logout |
| **Upload Statement** | `upload` | File selection card, format hint, submit, last upload summary, navigation back to Home |

All screens: Material 3, edge-to-edge, reactive `StateFlow<UiState>` driven, `collectAsStateWithLifecycle`.

## 3. APIs Integrated

| Endpoint (v1) | Method | Used By |
|---|---|---|
| `/auth/login` | POST | AuthViewModel → AuthRepository |
| `/auth/register` | POST | AuthViewModel → AuthRepository |
| `/auth/me` | GET | Session validation |
| `/auth/refresh` | POST | TokenAuthenticator (refresh rotation) |
| `/statements/upload` | POST (multipart) | UploadViewModel → StatementRepository |
| `/transactions` | GET | TransactionRepository (offset pagination + filters) |
| `/transactions/{id}/category` | PATCH | TransactionRepository (category correction) |
| `/categories` | GET | Category list for Home breakdown + correction |

Error contract: all failures map through `ApiErrorMapper` → sealed `ApiException` → user-facing message.

## 4. Database Integration

- **Room 2.8.4** with SQLCipher 4.17.0 encryption.
- Entities: `TransactionEntity`, `StatementEntity` (categories resolved from remote / categories endpoint).
- DAOs use `@Upsert` (idempotent mirroring) and indexed `statementId` for fast lookups.
- Schema versioned via Room migrations; passphrase managed by `PassphraseProvider` (Keystore-backed, survives app restarts only on the same device/keystore).

## 5. Offline Support

- Online-first fetch: repository queries remote, mirrors the page into Room, exposes a combined `StateFlow`.
- On network failure, the repository falls back to cached Room rows (last good snapshot); the UI shows an offline indicator.
- Uploads require connectivity (documented limitation).

## 6. Authentication Status

- Emulated host backend at `http://10.0.2.2:8000/api/v1/` (override via BuildConfig `API_BASE_URL` in `android/app/build.gradle.kts`).
- Access token attached to every request by `AuthInterceptor`; refresh handled transparently by `TokenAuthenticator`; expired/invalid session redirects to Login via AppNavHost auth-state effect.
- Passwords are never stored; BCrypt hashing remains server-side (Phase 1).

## 7. Upload Flow Status

1. User taps **Upload Statement** → system file picker (PDF).
2. `UploadViewModel.onFileSelected(Uri)` reads bytes on `Dispatchers.IO` via ContentResolver (no temp file).
3. `okhttp3.MultipartBody` posts to `/statements/upload` with filename + content type.
4. On success the summary (statement id + transaction count) is shown and the Home list refreshes.

## 8. Testing Metrics

| Metric | Value |
|---|---|
| Unit test suites | 6 |
| Unit tests | **37** |
| Failures | **0** |
| Gate command | `.\gradlew.bat :app:testDebugUnitTest` |
| MockWebServer integration tests | 7 (ArthaApiTest) |
| ViewModel tests | 13 (AuthViewModelTest 6 + TransactionsViewModelTest 7) |
| Mapper / error mapping tests | 18 (MappersTest 7 + ApiErrorMapperTest 6 + ApiExceptionTest 5) |
| Backend suite (Phase 1 + 1.5) | 96/96 |
| UAT (live Dockerized backend) | 19/19 PASS — register, login, CSV+PDF upload, browse/paginate/filter, category correction, refresh rotation, expired-token 401, logout semantics |
| Coverage of pure JVM logic | ~90% of JVM-testable classes |
| Not covered (needs emulator) | Room DAOs, SQLCipher, DataStore-backed TokenStore, Compose UI, TokenAuthenticator |

## 9. Build Metrics

| Gate | Result |
|---|---|
| `.\gradlew.bat :app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| Output | `app/build/outputs/apk/debug/app-debug.apk` |
| `.\gradlew.bat :app:lintDebug` | ✅ 0 errors / 5 warnings |
| Compiler warnings | 0 |
| Lint warnings breakdown | 4× `NewerVersionAvailable` (informational, intentional pins) + 1× `ObsoleteSdkInt` (false positive, see section 11) |
| Toolchain | AGP 9.3.1, Gradle 9.6.1, Kotlin/Compose 2.3.10, KSP 2.3.10, Java 17, compileSdk 37 / minSdk 26 |

## 10. Remaining Work for Phase 3

- **Insights dashboard**: expense breakdown charts/annual trends, spending forecasts, category budgets.
- **EMI / recurring detection UI**: surface detected recurring/EMI transactions with calendar view.
- **Risk assessment**: credit-health score and "safe loan eligibility" UI pulling Phase 1.5/Phase 3 endpoints.
- **Offline-first**: queue category corrections + uploads for retry; full offline transaction capture.
- **Instrumentation tests**: Compose UI tests, Room/SQLCipher, TokenAuthenticator (emulator).
- **Release hardening**: HTTPS-only config, R8/ProGuard minification, release signing, dependency upgrades (retrofit 3.x, coroutines 1.11).
- **Notifications**: budget overrun and EMI due-date push alerts.

## 11. Acknowledgements / Notes

- Lint `ObsoleteSdkInt` on `mipmap-anydpi-v26` is a known false positive: adaptive icons require the `-v26` qualifier and AAPT hard-fails without it; icon folder retained.
- Version catalog was corrected during bootstrap: the originally pinned `kotlin = "2.3.11"` did not exist (Compose plugin line tops out at 2.3.10); corrected to `2.3.10` for both Kotlin and KSP. All other pinned versions were verified against Maven Central / Google Maven.
- Gradle wrapper (9.6.1) was bootstrapped into the repository (previously missing) so builds are reproducible.
- Detailed engineering audit: see `ENGINEERING_REPORT.md` sections 16–28.