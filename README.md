# Artha Pilot

**AI-powered Personal Finance Android application** — B.Tech Final Year Major Project.

Artha Pilot helps users take control of their money with AI-assisted bank statement analysis: upload statements, auto-extract and categorize transactions, track recurring expenses, and get rule-based investment guidance — all scoped to the user's own financial data.

## Features

| Area | Details |
|------|---------|
| Platform | Android (Kotlin + Jetpack Compose, Material 3, MVVM + Clean Architecture) |
| Backend | FastAPI (Python 3.12), PostgreSQL, Docker |
| AI | TF-IDF + Logistic Regression transaction categorization, rule-based engines |
| Data | Room offline cache (Android) + SQLCipher encryption + DataStore token store |
| Security | JWT auth (rotation), BCrypt password hashing, Keystore-protected DB passphrase, backups disabled |
| Parsing | PDF & CSV bank statement upload, auto-format detection |
| Insights | Expense dashboard, EMI/recurring detection, risk assessment (Phase 3) |

## Repository Layout

```
Artha-Pilot/
├── backend/     # FastAPI service (auth, statements, transactions, ML)
├── android/     # Android app: Compose UI, MVVM, Hilt, Retrofit, Room + SQLCipher
├── docs/        # Engineering reports and documentation
├── scripts/     # Utility scripts
├── docker-compose.yml
├── ENGINEERING_REPORT.md
└── PHASE_2_COMPLETION_REPORT.md
```

## Quick Start (Backend)

```bash
# Option A: Docker (recommended)
docker compose up --build

# Option B: Local
cd backend
python -m venv .venv && .venv\Scripts\activate   # Windows
pip install -r requirements.txt
cp .env.example .env
alembic upgrade head
uvicorn app.main:app --reload
```

API docs: `http://localhost:8000/docs`

## Quick Start (Android)

```bash
cd android
./gradlew.bat :app:assembleDebug       # Windows
# Debug APK: android/app/build/outputs/apk/debug/app-debug.apk
```

- The debug build targets the local backend at `http://10.0.2.2:8000/api/v1/` (emulator → host). Override via `buildConfigField("String", "API_BASE_URL", ...)` in `android/app/build.gradle.kts`.
- Cleartext HTTP is allowed only for the dev hosts in `network_security_config.xml`; release builds should use HTTPS.

## Architecture

Clean Architecture + layered design (API → Services → Repositories → Domain), Repository Pattern, SOLID principles, structured logging, and a 3-tier categorization cascade (rules → ML → fallback).

**Android:** MVVM with Jetpack Compose; Hilt DI; Retrofit + OkHttp (auth interceptor + refresh authenticator); Room with SQLCipher encryption (passphrase sealed in Android Keystore); DataStore Preferences for the JWT pair; offline cache mirrors the last successful fetch.

See [ENGINEERING_REPORT.md](ENGINEERING_REPORT.md) for detailed architecture, schema, API reference, and phase status.

## Development Workflow

- `main` — production-ready (protected)
- `develop` — integration branch (default for development)
- Feature branches merged via Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`

## License

MIT — see [LICENSE](LICENSE).
