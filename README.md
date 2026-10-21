# Artha Pilot

**AI-powered Personal Finance Android application** — B.Tech Final Year Major Project.

Artha Pilot helps users take control of their money with AI-assisted bank statement analysis: upload statements, auto-extract and categorize transactions, track recurring expenses, plan budgets, assess credit health, review explainable investment suggestions, and ask a grounded assistant questions about their own data. Every figure about the user's finances is computed by the backend from the authenticated user's own statements; market references come from a separate public source and are labeled as such. The assistant may only explain these verified values.

## Features

| Area | Details |
|------|---------|
| Platform | Android (Kotlin + Jetpack Compose, Material 3, MVVM + Clean Architecture) |
| Backend | FastAPI (Python 3.12), PostgreSQL 16 in Docker (SQLite for the test suite) |
| AI | TF-IDF + Logistic Regression transaction categorization; grounded LLM assistant over verified data; deterministic rule-based risk and recommendation engines |
| Data | Room offline cache (Android) + SQLCipher encryption + DataStore token store |
| Security | JWT access/refresh rotation with revocation, BCrypt password hashing, Keystore-protected DB passphrase, backups disabled, auth rate limiting |
| Parsing | PDF & CSV bank statement upload, auto-format detection, 10 MB upload cap |
| Insights | Expense dashboard, monthly trends, category breakdown, EMI/recurring detection, credit-risk score + loan eligibility, monthly budgets, statement history (Phase 3) |
| Risk | 0–100 credit-health score, risk-tolerance questionnaire blended 50/50 with spending behaviour, safe-EMI and loan-capacity estimates |
| Recommendations | Explainable equity/debt/gold allocation by risk band, rationale and caution notes, daily-refreshed market reference quotes, read-only what-if simulator (Phase 4) |
| Assistant | Grounded chat over the user's verified insights, recurring items, risk profile, recommendations and market references; prompt-injection fencing; returns `llm_not_configured` (503) instead of inventing answers (Phase 4) |
| Offline | Room cache + queued category corrections replayed when connectivity returns (Phase 3) |
| Quality | GitHub Actions CI; 239 backend tests and 119 Android unit tests (18 suites) passing locally on 2026-10-21; Android lint 0 errors (15 dependency/plugin version notices + 1 benign obsolete-SDK-folder warning) |

## Project Status

**Implemented and verified in the codebase**

- Authentication: register/login, BCrypt hashing, JWT access + rotating refresh tokens with revoked-token handling, auth rate limiting.
- Statements: PDF/CSV upload, format detection, parsing, transaction history, filters, pagination, category correction.
- Insights: monthly summary/trends, category breakdown, budgets with overrun alerts, recurring/EMI detection, statement history.
- Risk: spending-behaviour score, risk-tolerance questionnaire, combined assessment, safe-EMI and loan-capacity estimates.
- Recommendations: risk-band allocation with rationale, market reference data with graceful unavailable/cached states, what-if simulation that never mutates stored data.
- Assistant: grounded, per-user chat that only explains values produced by the backend; multi-turn history (up to 20 prior messages per request, server-enforced); graceful 503 when no LLM is configured.
- Android: six-tab Compose UI (Transactions / Insights / Budgets / Recurring / Risk / Ask), recommendations opened from the Risk tab, statements opened from Home, encrypted Room cache, offline correction queue.

**Not implemented (documented backlog)**

- Token-at-rest encryption for the DataStore JWT pair.
- Paging 3 for transaction/statement lists (current lists are bounded by page size but accumulate in memory).
- Android instrumentation tests (Room/SQLCipher/Compose/TokenAuthenticator); only JVM unit tests exist.
- Shared rate-limit store for multi-worker deployments (current limiter is per-process).
- Production Compose hardening (`ENVIRONMENT=production`, CORS allow-list) and release operations (signing keystore, real HTTPS API URL).
- A Phase 4 release-readiness audit. Phase 4 is committed on `main` but **untagged**, and the Android `versionName` remains `3.0.0`; the latest release tag is `v3.0.0-phase3`.

## Repository Layout

```
Artha-Pilot/
├── .github/workflows/ci.yml   # Backend + Android CI
├── backend/                   # FastAPI service (auth, statements, transactions, ML, insights, recommendations, assistant)
├── android/                   # Android app: Compose UI, MVVM, Hilt, Retrofit, Room + SQLCipher
├── docs/                      # Documentation index
├── docker-compose.yml
├── CHANGELOG.md
├── ENGINEERING_REPORT.md
├── PHASE_2_COMPLETION_REPORT.md
├── PHASE_3_COMPLETION_REPORT.md
├── PHASE_3_VERIFICATION_REPORT.md
└── RELEASE_READINESS_REPORT.md
```

## Quick Start (Backend)

Requirements: Python 3.12, or Docker with a recent Compose v2 (the compose file uses the long `env_file` syntax).

```bash
# Option A: Docker (recommended; PostgreSQL + migrations applied at container start)
docker compose up --build

# Option B: Local (requires a running PostgreSQL instance)
cd backend
python -m venv .venv
.venv\Scripts\activate                            # Windows
# source .venv/bin/activate                       # Linux/macOS
pip install -r requirements.txt
cp .env.example .env                              # Windows: Copy-Item .env.example .env
alembic upgrade head
uvicorn app.main:app --reload
```

API docs: `http://localhost:8000/docs` (disabled when `ENVIRONMENT=production`).

Stop the Docker stack with `docker compose down`; this keeps the `pgdata` volume and its database.

### Grounded assistant configuration

The assistant is optional. It stays disabled — and `POST /api/v1/assistant/chat` returns `503 llm_not_configured` — until **all three** of `LLM_BASE_URL`, `LLM_API_KEY` and `LLM_MODEL` are set. Configure them in `backend/.env` (gitignored and excluded from the Docker image):

```dotenv
LLM_PROVIDER=openai-compatible
LLM_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai/   # example: Gemini OpenAI-compatible endpoint
LLM_API_KEY=your-real-key
LLM_MODEL=<provider-model-id>
LLM_TIMEOUT_SECONDS=30
LLM_MAX_RESPONSE_CHARS=2400
```

Any OpenAI-compatible endpoint works. With Docker Compose the file is loaded through `env_file`, so the key is never part of the repository or image. Never commit a real key.

## Quick Start (Android)

Requirements: JDK 17+ (CI uses Temurin 21) and Android SDK platform `37.0` + build-tools `37.0.0`. If you are not using Android Studio, create `android/local.properties` (gitignored) with:

```properties
sdk.dir=/path/to/Android/Sdk
# Windows example (escape the drive colon or lint reports PropertyEscape):
sdk.dir=C\:/Users/you/AppData/Local/Android/Sdk
```

```bash
cd android
./gradlew.bat :app:assembleDebug       # Windows
# ./gradlew :app:assembleDebug         # Linux/macOS
# Debug APK: android/app/build/outputs/apk/debug/app-debug.apk
```

- The debug build targets `http://127.0.0.1:8000/api/v1/`. On an emulator or device, run `adb reverse tcp:8000 tcp:8000` so that address reaches the host backend. Cleartext HTTP is permitted **only** in debug builds; the debug network config also allows `10.0.2.2` if you override the URL.
- **Release builds**:
  - API endpoint: set `ARTHA_PILOT_API_BASE_URL` (gradle property `-P` or environment variable) to the production HTTPS URL before packaging. `assembleRelease` warns if the placeholder or a non-HTTPS URL is used.
  - Signing: copy `android/keystore.properties.example` to `android/keystore.properties`, fill in your release keystore details, and place the keystore under `android/`. The file is gitignored — **never commit signing secrets**. Without it, release APKs build **unsigned** (not shippable) with a loud warning.

## Testing and CI

### Backend

```bash
cd backend
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt   # Windows
# .venv/bin/python -m pip install -r requirements.txt          # Linux/macOS
.venv\Scripts\python.exe -m pytest -q
```

Expected on this commit: **239 passed** (~38 s). The suite sets `DATABASE_URL=sqlite:///./test_artha.db` in `tests/conftest.py` and recreates the schema per test, so it is fast and self-contained.

**SQLite vs PostgreSQL/Docker:** the unit/integration suite does **not** verify PostgreSQL-specific migration/transaction behaviour, Docker networking, container startup, or the live network stack. Those require the Compose stack (`docker compose up --build`) and a manual end-to-end run. CI runs only the SQLite suite; it does not start PostgreSQL.

### Android

```bash
cd android
./gradlew.bat :app:testDebugUnitTest   # 119 tests / 18 suites
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:lintDebug           # 0 errors, 16 warnings
```

The 16 lint warnings are 15 dependency/plugin update notices (`AndroidGradlePluginVersion`, `GradleDependency`, `NewerVersionAvailable`) for intentionally pinned versions, plus one `ObsoleteSdkInt` warning: `res/mipmap-anydpi-v26` is redundant because `minSdkVersion` is 26. The latter is harmless; merging it into `mipmap-anydpi` is an optional cleanup, and dependency upgrades are out of scope for this change. Unit tests are JVM-only and use MockWebServer — no emulator or device is required. Instrumentation tests are not implemented yet.

### GitHub Actions

`.github/workflows/ci.yml` runs on pushes to `main`/`develop`, pull requests, and manual dispatch:

- **Backend (Python 3.12):** installs `backend/requirements.txt` and runs `python -m pytest -q`.
- **Android (JDK 21):** installs Android SDK 37/build-tools 37, then runs unit tests, `assembleDebug`, and `lintDebug`.

The workflow needs no secrets and no production credentials.

## Architecture

Clean Architecture + layered design (API → Services → Repositories → Domain), Repository Pattern, SOLID principles, structured logging, and a 3-tier categorization cascade (rules → ML → fallback).

**Android:** MVVM with Jetpack Compose; Hilt DI; Retrofit + OkHttp (auth interceptor + refresh authenticator); Room with SQLCipher encryption (passphrase sealed in Android Keystore); DataStore Preferences for the JWT pair; offline cache mirrors the last successful fetch and queues category corrections for replay when connectivity returns.

**Phase 3 (v3.0.0):** monthly insights (summary, 6-month trends, category breakdown), monthly budgets per category with overrun alerts, recurring/EMI detection with next-expected dates, credit-risk score (0–100) with a safe-loan-eligibility estimate, statement history, auth rate limiting, and home-screen alerts for budget overruns and EMIs due within a week.

**Phase 4 (untagged):** explainable risk and recommendations plus a grounded assistant.

- `GET /api/v1/recommendations` builds a three-month spending context, selects a deterministic equity/debt/gold mix from the risk band, and returns allocation rationale, caution/info notes, and market references.
- `POST /api/v1/recommendations/what-if` simulates cutting one category by a monthly amount. It is read-only: stored transactions and risk profiles are never mutated.
- Market references come from public Yahoo Finance daily chart data, are cached in `market_data_snapshots` (migration `0005`), and degrade to `cached` or `unavailable` instead of fabricating prices.
- `GET /api/v1/risk/questions`, `PUT /api/v1/risk/questionnaire`, and `GET /api/v1/risk/profile` implement the tolerance questionnaire (migration `0004`) and the combined score (50% questionnaire + 50% spending behaviour).
- `POST /api/v1/assistant/chat` builds a fenced JSON context from verified insights, recurring items, risk, recommendations and market data, then asks an OpenAI-compatible model to rephrase it. The model never calculates figures; user text is treated as data, not instructions; answers are length-capped and accompanied by an educational disclaimer.

See [ENGINEERING_REPORT.md](ENGINEERING_REPORT.md) for deeper architecture and [CHANGELOG.md](CHANGELOG.md) for release history. Release-readiness status for v3.0.0: [RELEASE_READINESS_REPORT.md](RELEASE_READINESS_REPORT.md).

## Development Workflow

- `main` — production-ready (protected)
- `develop` — integration branch (default for development)
- Feature branches merged via Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`
- CI must be green for backend tests, Android unit tests, debug build and lint.

## License

MIT — see [LICENSE](LICENSE).
