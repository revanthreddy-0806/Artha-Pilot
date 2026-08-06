# Phase 3 Verification Report

**Date:** 2026-08-06
**Branch:** `develop` (no merges, no pushes, no tag moves)
**Scope:** Verify every **Phase 3** requirement in the Project Specification (`specs/master_prompt_finance_app.pdf`) directly against the code, implement anything missing, and prove it with builds/tests.

---

## 1. Spec source

`specs/master_prompt_finance_app.pdf` (6 pages, extracted via pypdf). Phase 3 section verbatim:

> **Phase 3 — Risk Appetite Assessment**
> - Build a risk questionnaire (standard financial risk-tolerance question set)
> - Combine questionnaire answers with spending behavior (EMI-to-income ratio, savings rate, expense volatility) into a risk score
> - Keep the scoring model simple and explainable (rule-based or logistic regression) — must be defensible in a viva

Relevant non-negotiable constraint: *"Keep the risk-scoring and recommendation logic explainable/rule-based, not an opaque black box."* Relevant deliverable: *"Risk-scoring module with documented logic."*

## 2. Verification outcome (headline)

| Requirement | Spec clause | Status before | Status now |
|---|---|---|---|
| P3-R1 | Risk questionnaire (standard set) | **MISSING** — no questionnaire anywhere in repo | ✅ Implemented |
| P3-R2 | Combine answers + spending behaviour (EMI-to-income, savings rate, expense volatility) into one score | Partial — behaviour scoring only; **no questionnaire, no expense volatility** | ✅ Implemented (50/50 rule-based blend) |
| P3-R3 | Simple, explainable, rule-based model, viva-defensible | Partial (rule-based, but no documented combine step) | ✅ Implemented + documented |
| P3-D1 | Risk-scoring module with documented logic (deliverable) | Partial | ✅ Documented in `risk_service.py` + this report |

**Phase 3 completion: 100%** (all spec requirements verified in code; all gates green).

## 3. Requirement-by-requirement evidence

### P3-R1 — Risk questionnaire (standard financial risk-tolerance question set) ✅

Backend (single source of truth for questions and internal scoring):
- `backend/app/services/risk_service.py` — `RISK_QUESTIONNAIRE`: **10 standard risk-tolerance questions** (time horizon, investment goal, 20% drop reaction, loss tolerance, income stability, emergency fund, investing knowledge, prolonged market fall, risk–reward preference, savings discipline), each with 4 graded options (1 = conservative … 4 = aggressive).
- `backend/app/api/v1/risk.py` — `GET /api/v1/risk/questions` (public, static) returns `{questions:[{id,text,options:[{id,label}]}]}`. Points are **not** exposed to the client (no cheating).
- `backend/app/schemas/risk.py` — `QuestionListResponse`, `RiskQuestion`, `QuestionOption`.
- Android `ui/risk/RiskScreen.kt` — full questionnaire UI (question cards, radio options, progress "n/10 answered", submit button); `ui/risk/RiskViewModel.kt` — `selectAnswer()` / `submit()` state machine.
- Evidence (tests): `test_risk_api.py::test_questions_are_a_standard_set` (≥8 questions, each with ≥3 options), `test_questions_do_not_leak_scoring_points`.

### P3-R2 — Combine answers + spending behaviour into a risk score ✅

- **Questionnaire tolerance score:** `risk_service.score_questionnaire()` → 0–100 (sum of chosen option points / max points, half-up scaled); `tolerance_band()` → Conservative (<40) / Moderate (40–69) / Aggressive (≥70).
- **Spending behaviour score:** `risk_service.score_risk()` — rule-based weights (`ScoreWeights`), now including **expense volatility**:
  - base 30 + income stability 10 + **expense stability 10 (inverse of expense volatility)** + savings rate 20 + utilization 20 + EMI-to-income 10 = max 100.
  - `RiskMetrics.expense_std_ratio` computed in `insights_service._risk_metrics()` (std/mean of monthly expenses) — spec metric "expense volatility".
  - EMI-to-income ratio ✓ (`emi_load_ratio` from recurring EMI detection), savings rate ✓ (`savings_rate`).
- **Combination:** `risk_service.combined_risk_score(tolerance, behaviour)` = `round(0.5·tolerance + 0.5·behaviour)` (half-up), clamped 0–100; behaviour-only fallback before the questionnaire is completed. Defensible viva answer: *"risk appetite = 50% stated preference + 50% demonstrated capacity."*
- **Persistence:** `backend/app/models/risk_questionnaire.py` + `repositories/risk_repository.py` (one row per user, `answers` JSON, `tolerance_score`, `tolerance_band`); alembic migration `0004_risk_questionnaire` (verified `upgrade head` on a scratch SQLite DB: 0001→0004).
- **API:**
  - `PUT /api/v1/risk/questionnaire` (auth) — validates all questions answered + known options (422 otherwise), upserts, returns `{tolerance_score, tolerance_band, assessment}`.
  - `GET /api/v1/risk/profile` (auth) — `RiskAssessment`: `score`, `band` (Conservative/Moderate/Aggressive), `summary`, `factors`, `tolerance_score/band`, `behavior_score`, `questionnaire_completed`, plus existing loan-eligibility fields.
- Android: `data/remote/dto/InsightsDtos.kt` (`RiskQuestionDto`, `RiskQuestionnaireSubmitDto`, `RiskQuestionnaireResultDto`, extended `RiskProfileDto`), `data/remote/ArthaApi.kt` (3 new endpoints), `Mappers.kt`, `data/repository/InsightsRepository.kt` (`fetchRiskQuestions`, `submitRiskQuestionnaire`), `Models.kt` (extended `RiskProfile` + `RiskQuestion`), `ui/risk/RiskScreen.kt` (ToleranceCard: "Final score = 50% questionnaire + 50% spending behaviour").
- Evidence (tests): `test_risk_api.py` (10 scenarios incl. blend math with real transactions, 422 rejections, isolation, re-submit overwrite), `test_risk_service.py` (questionnaire min/max/mixed, bands, combine values, volatility lowers score).

### P3-R3 — Simple, explainable, rule-based model ✅

- No ML/black box in scoring: pure functions in `risk_service.py` with documented weights and factor breakdowns.
- `build_assessment_factors()` returns per-component explanations ("Risk questionnaire 90/100 (Aggressive)", "Spending behaviour 75/100", "Income stability", "Expense stability", "Savings rate", "Expense utilization", "EMI / loan load", "Data history") — shown on the Android screen and machine-readable in the API.
- `build_appetite_summary()` produces plain-language justification of the combined score.
- Viva-defensibility: the whole model is ~10 named thresholds + 6 fixed weights + a 50/50 blend — fully traceable.

## 4. Bugs / gaps found and fixed this session

| # | Issue | Fix |
|---|---|---|
| 1 | **No risk questionnaire existed** anywhere (backend or Android) despite spec Phase 3 | Implemented full questionnaire (P3-R1) |
| 2 | **Expense volatility not part of the risk score** (only income stability was) | Added `expense_std_ratio` metric + `expense_stability` weight + factor |
| 3 | Risk score never combined questionnaire answers (no way to state risk preference) | Added tolerance scoring + 50/50 combine + persistence |
| 4 | Android Risk tab could not complete the questionnaire (only displayed a behaviour score) | Added questionnaire flow (retake, answer validation, submit) |
| 5 | No persistence layer / migration for questionnaire answers | New table + alembic 0004 |
| 6 | (Refactor-induced) `risk_profile` referenced removed locals | Rewired to extracted `_risk_metrics()`; caught by tests |

## 5. Tests added / updated

Backend (`backend/tests`):
- `unit/test_risk_service.py` — updated for new `RiskMetrics.expense_std_ratio`; **+13 new unit tests** (questionnaire scoring extremes/mixed, missing/unknown answer rejection, band boundaries, combine blend incl. half-up rounding + clamping, volatility effects, assessment factors).
- `integration/test_risk_api.py` — **new, 12 tests**: question set shape, no point leakage, auth required, behaviour-only initial profile, conservative submit → score 13 (0.5·25+0.5·0), aggressive submit over real transactions → ≥70, tolerance persists on `/risk/profile`, 422 on missing/unknown answers, re-submit overwrites, cross-user isolation.

Android (`android/app/src/test`):
- `ui/risk/RiskViewModelTest.kt` — **+4 tests** (questionnaire shown when not completed, submit-all flow, submit-gated-on-all-answers, retake flow) + fakes updated.
- `data/remote/MappersTest.kt` — **+2 tests** (extended profile mapping incl. tolerance/behaviour fields; legacy behaviour-only payload; question DTO mapping).
- `data/remote/ArthaApiTest.kt` — **+2 tests** (risk questions parsing; questionnaire submit request/response, incl. path + request body assertions) + updated risk-profile fixture to the new `/risk/profile` contract.
- 3 other ViewModel test fakes updated for the extended repository interface.

## 6. Build / test status (final, all green)

| Gate | Result |
|---|---|
| Backend `pytest` (`backend/tests`) | **172/172 passed** (was 149) — 0 failures, 0 errors |
| Backend `compileall` (app + alembic) | Clean (exit 0) |
| Alembic `upgrade head` (scratch SQLite) | 0001→0002→0003→0004 OK |
| `pip-audit -r requirements.txt` | requirements.txt **unchanged** by this work; prior full-transitive run: 0 known vulnerabilities |
| Android `testDebugUnitTest` | **87/87 passed** (was 79) — 12 suites, 0 failures |
| Android `assembleDebug` | BUILD SUCCESSFUL |
| Android `assembleRelease` (R8) | BUILD SUCCESSFUL (placeholder-URL warning unchanged, by design) |
| Android `lintDebug` | **0 errors, 5 warnings** (4× NewerVersionAvailable, 1× ObsoleteSdkInt — all pre-existing/informational) |

## 7. Evidence index

Backend: `app/services/risk_service.py` (questionnaire + scoring + combine), `app/services/insights_service.py::_risk_metrics/risk_assessment`, `app/api/v1/risk.py` (`/risk/questions`, `/risk/questionnaire`, `/risk/profile`), `app/schemas/risk.py`, `app/models/risk_questionnaire.py`, `app/repositories/risk_repository.py`, `alembic/versions/0004_risk_questionnaire.py`, `app/api/v1/__init__.py` (router registered).

Android: `data/remote/ArthaApi.kt` (3 endpoints), `data/remote/dto/InsightsDtos.kt`, `data/remote/Mappers.kt`, `data/repository/InsightsRepository.kt`, `domain/model/Models.kt`, `ui/risk/RiskViewModel.kt`, `ui/risk/RiskScreen.kt` (questionnaire UI + tolerance card + band colors), `ui/navigation/AppNavHost.kt` (Risk tab — unchanged).

Tests: `backend/tests/unit/test_risk_service.py`, `backend/tests/integration/test_risk_api.py`, `android/app/src/test/.../ui/risk/RiskViewModelTest.kt`, `.../data/remote/MappersTest.kt`, `.../data/remote/ArthaApiTest.kt`.

## 8. Out of scope (per instructions)

- **Not worked on:** Phase 4 (recommendation engine), Phase 5 (chatbot), Phase 6 (testing/polish/report).
- **No release engineering:** no push, no tag move, no `main` merge, no signing/keystore changes.
- `specs/master_prompt_finance_app.pdf` left untracked (user-provided input, not part of the product).

## 9. Conclusion

Every Phase 3 requirement in the Project Specification is now implemented and verified directly in code with passing tests and clean builds. **Phase 3 completion: 100%.** Stopping here — no further phase work performed.
