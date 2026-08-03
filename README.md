# Artha Pilot

**AI-powered Personal Finance Android application** — B.Tech Final Year Major Project.

Artha Pilot helps users take control of their money with AI-assisted bank statement analysis: upload statements, auto-extract and categorize transactions, track recurring expenses, and get rule-based investment guidance — all scoped to the user's own financial data.

## Features

| Area | Details |
|------|---------|
| Platform | Android (Kotlin + Jetpack Compose) |
| Backend | FastAPI (Python 3.12), PostgreSQL, Docker |
| AI | TF-IDF + Logistic Regression transaction categorization, rule-based engines |
| Data | Room offline cache (Android) + SQLCipher encryption |
| Security | JWT auth, BCrypt password hashing, HTTPS-ready |
| Parsing | PDF & CSV bank statement upload, auto-format detection |
| Insights | Expense dashboard, EMI/recurring detection, risk assessment |

## Repository Layout

```
Artha-Pilot/
├── backend/     # FastAPI service (auth, statements, transactions, ML)
├── android/     # Android app (Phase 2)
├── docs/        # Engineering reports and documentation
├── scripts/     # Utility scripts
├── docker-compose.yml
├── ENGINEERING_REPORT.md
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

## Architecture

Clean Architecture + layered design (API → Services → Repositories → Domain), Repository Pattern, SOLID principles, structured logging, and a 3-tier categorization cascade (rules → ML → fallback).

See [ENGINEERING_REPORT.md](ENGINEERING_REPORT.md) for detailed architecture, schema, API reference, and phase status.

## Development Workflow

- `main` — production-ready (protected)
- `develop` — integration branch (default for development)
- Feature branches merged via Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`

## License

MIT — see [LICENSE](LICENSE).
