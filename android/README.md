# Android App

Artha Pilot Android application (Kotlin + Jetpack Compose).

**Status: Planned — Phase 2** (Phase 1 delivered the backend foundation: authentication, statement parsing, transaction categorization).

## Planned Stack

- Kotlin, Jetpack Compose, Material 3
- MVVM + Clean Architecture
- Hilt (DI), Retrofit (API), Navigation Compose
- Room (offline cache) + SQLCipher (encryption)
- JWT auth flows

## Backend Contract

Base URL: `http://localhost:8000/api/v1`

| Endpoint | Method | Purpose |
|---|---|---|
| `/auth/register` | POST | Create account, receive JWT pair |
| `/auth/login` | POST | Authenticate, receive JWT pair |
| `/auth/refresh` | POST | Rotate refresh token |
| `/auth/me` | GET | Current user profile |
| `/statements/upload` | POST | Upload PDF/CSV statement |
| `/transactions` | GET | Paginated, filtered transactions |
| `/transactions/{id}/category` | PATCH | Correct category (feedback) |
