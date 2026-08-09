package com.arthapilot.app.data.remote

import com.arthapilot.app.data.local.StatementEntity
import com.arthapilot.app.data.local.TransactionEntity
import com.arthapilot.app.data.remote.dto.AllocationItemDto
import com.arthapilot.app.data.remote.dto.BudgetDto
import com.arthapilot.app.data.remote.dto.CategoryAmountDto
import com.arthapilot.app.data.remote.dto.ChatResponseDto
import com.arthapilot.app.data.remote.dto.InsightsSummaryDto
import com.arthapilot.app.data.remote.dto.LoginRequestDto
import com.arthapilot.app.data.remote.dto.MarketQuoteDto
import com.arthapilot.app.data.remote.dto.MonthTrendDto
import com.arthapilot.app.data.remote.dto.RecommendationContextDto
import com.arthapilot.app.data.remote.dto.RecommendationDto
import com.arthapilot.app.data.remote.dto.RecommendationNoteDto
import com.arthapilot.app.data.remote.dto.RecurringItemDto
import com.arthapilot.app.data.remote.dto.RiskFactorDto
import com.arthapilot.app.data.remote.dto.RiskProfileDto
import com.arthapilot.app.data.remote.dto.RiskQuestionDto
import com.arthapilot.app.data.remote.dto.RiskQuestionOptionDto
import com.arthapilot.app.data.remote.dto.RegisterRequestDto
import com.arthapilot.app.data.remote.dto.StatementItemDto
import com.arthapilot.app.data.remote.dto.StatementUploadResponseDto
import com.arthapilot.app.data.remote.dto.TokenResponseDto
import com.arthapilot.app.data.remote.dto.TransactionDto
import com.arthapilot.app.data.remote.dto.TransactionPageDto
import com.arthapilot.app.data.remote.dto.UserDto
import com.arthapilot.app.data.remote.dto.WhatIfBlockDto
import com.arthapilot.app.data.remote.dto.WhatIfImpactDto
import com.arthapilot.app.data.remote.dto.WhatIfResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {

    @Test
    fun `token dto maps to domain`() {
        val dto = TokenResponseDto(accessToken = "acc", refreshToken = "ref", tokenType = "bearer")
        val tokens = dto.toDomain()
        assertEquals("acc", tokens.accessToken)
        assertEquals("ref", tokens.refreshToken)
        assertEquals("bearer", tokens.tokenType)
    }

    @Test
    fun `user dto maps to domain`() {
        val dto = UserDto(1, "a@b.com", "Ada", true, "2026-01-01T00:00:00Z")
        val user = dto.toDomain()
        assertEquals(1L, user.id)
        assertEquals("a@b.com", user.email)
        assertEquals("Ada", user.fullName)
        assertEquals(true, user.isActive)
        assertEquals("2026-01-01T00:00:00Z", user.createdAt)
    }

    @Test
    fun `transaction dto maps to domain and entity`() {
        val dto = TransactionDto(
            id = 5,
            statementId = 9,
            date = "2026-01-15",
            description = "Swiggy",
            amount = 214.5,
            transactionType = "debit",
            balance = 1000.0,
            category = "Food & Dining",
            confidence = 0.91,
            isRecurring = false,
        )
        val domain = dto.toDomain()
        assertEquals(5L, domain.id)
        assertEquals("Food & Dining", domain.category)

        val entity = dto.toEntity()
        assertEquals("Swiggy", entity.description)
        assertEquals(214.5, entity.amount, 0.0)

        assertEquals(dto.toDomain(), entity.toDomain())
    }

    @Test
    fun `transaction page dto maps to domain`() {
        val dto = TransactionPageDto(
            items = listOf(transactionDto(id = 1), transactionDto(id = 2)),
            total = 2,
            page = 1,
            pageSize = 50,
        )
        val page = dto.toDomain()
        assertEquals(2, page.items.size)
        assertEquals(2, page.total)
        assertEquals(50, page.pageSize)
    }

    @Test
    fun `statement upload dto maps to domain and entity`() {
        val dto = StatementUploadResponseDto(
            id = 3,
            filename = "jan.pdf",
            fileType = "pdf",
            bankName = "HDFC",
            accountNumber = null,
            periodStart = "2026-01-01",
            periodEnd = "2026-01-31",
            parseStatus = "parsed",
            transactionCount = 2,
            errorMessage = null,
            transactions = listOf(transactionDto(id = 1)),
        )
        val result = dto.toDomain()
        assertEquals(3L, result.id)
        assertEquals("parsed", result.parseStatus)
        assertEquals(1, result.transactions.size)

        val entity: StatementEntity = dto.toStatementEntity()
        assertEquals("jan.pdf", entity.filename)
        assertEquals(2, entity.transactionCount)
    }

    @Test
    fun `register request carries full name under snake_case key`() {
        val dto = RegisterRequestDto(fullName = "Ada Lovelace", email = "a@b.com", password = "secret123")
        assertEquals("Ada Lovelace", dto.fullName)
    }

    @Test
    fun `login request carries plain keys`() {
        val dto = LoginRequestDto(email = "a@b.com", password = "secret123")
        assertEquals("a@b.com", dto.email)
    }

    @Test
    fun `budget dto maps to domain`() {
        val dto = BudgetDto(
            category = "Groceries",
            monthlyLimit = 8000.0,
            spent = 10000.0,
            percentage = 125.0,
            overrun = true,
        )
        val budget = dto.toDomain()
        assertEquals("Groceries", budget.category)
        assertEquals(125.0, budget.percentage, 0.0)
        assertEquals(true, budget.overrun)
    }

    @Test
    fun `insights summary dto maps to domain`() {
        val dto = InsightsSummaryDto(
            month = "2026-07",
            income = 100000.0,
            expenses = 50000.0,
            savings = 50000.0,
            savingsRate = 0.5,
            transactionCount = 5,
            topCategories = listOf(CategoryAmountDto("Rent", 25000.0, 50.0)),
        )
        val summary = dto.toDomain()
        assertEquals(0.5, summary.savingsRate!!, 0.0)
        assertEquals(1, summary.topCategories.size)
        assertEquals("Rent", summary.topCategories[0].category)
    }

    @Test
    fun `month trend dto maps to domain`() {
        val trend = MonthTrendDto("2026-07", 100.0, 40.0, 60.0).toDomain()
        assertEquals("2026-07", trend.month)
        assertEquals(60.0, trend.savings, 0.0)
    }

    @Test
    fun `statement item dto maps to domain and entity`() {
        val dto = StatementItemDto(
            id = 4,
            filename = "apr.csv",
            fileType = "csv",
            bankName = "HDFC",
            periodStart = "2026-04-01",
            periodEnd = "2026-04-30",
            parseStatus = "processed",
            transactionCount = 7,
            errorMessage = null,
            createdAt = "2026-08-01T10:00:00Z",
        )
        val info = dto.toDomain()
        assertEquals(4L, info.id)
        assertEquals("processed", info.parseStatus)

        val entity: StatementEntity = dto.toStatementEntity()
        assertEquals("2026-08-01T10:00:00Z", entity.createdAt)
        assertEquals(info, entity.toStatementInfo())
    }

    @Test
    fun `recurring item dto maps to domain`() {
        val dto = RecurringItemDto(
            transactionId = 11,
            description = "SBI CAR LOAN EMI",
            amount = 12500.0,
            category = "Bills & Recurring",
            kind = "emi",
            intervalDays = 30,
            occurrences = 3,
            confidence = 0.75,
            firstDate = "2026-05-05",
            lastDate = "2026-07-05",
            nextExpectedDate = "2026-08-04",
        )
        val item = dto.toDomain()
        assertEquals("emi", item.kind)
        assertEquals(3, item.occurrences)
        assertEquals("2026-08-04", item.nextExpectedDate)
    }

    @Test
    fun `risk profile dto maps to domain`() {
        val dto = RiskProfileDto(
            score = 82,
            band = "Aggressive",
            summary = "Looks great",
            factors = listOf(RiskFactorDto("Savings rate", "60.0%", "positive")),
            income = 100000.0,
            monthlyExpenses = 40000.0,
            savingsRate = 0.6,
            emiLoadRatio = 0.1,
            safeEmiAmount = 30000.0,
            maxLoanAmount = 1398000.0,
            maxLoanTenureMonths = 60,
            toleranceScore = 90,
            toleranceBand = "Aggressive",
            behaviorScore = 75,
            questionnaireCompleted = true,
        )
        val profile = dto.toDomain()
        assertEquals(82, profile.score)
        assertEquals("Aggressive", profile.band)
        assertEquals(1, profile.factors.size)
        assertEquals(1398000.0, profile.maxLoanAmount!!, 0.0)
        assertEquals(90, profile.toleranceScore)
        assertEquals("Aggressive", profile.toleranceBand)
        assertEquals(75, profile.behaviorScore)
        assertTrue(profile.questionnaireCompleted)
    }

    @Test
    fun `risk profile dto maps legacy behaviour-only payload`() {
        val dto = RiskProfileDto(
            score = 55,
            band = "Moderate",
            summary = "Behaviour only",
            factors = emptyList(),
            income = null,
            monthlyExpenses = 40000.0,
            savingsRate = 0.2,
            emiLoadRatio = null,
            safeEmiAmount = 10000.0,
            maxLoanAmount = null,
            maxLoanTenureMonths = 60,
            toleranceScore = null,
            toleranceBand = null,
            behaviorScore = 55,
            questionnaireCompleted = null,
        )
        val profile = dto.toDomain()
        assertNull(profile.toleranceScore)
        assertEquals(55, profile.behaviorScore)
        assertEquals(false, profile.questionnaireCompleted)
    }

    @Test
    fun `risk question dto maps to domain`() {
        val dto = RiskQuestionDto(
            id = "horizon",
            text = "How soon do you need the money?",
            options = listOf(
                RiskQuestionOptionDto(id = "under-1y", label = "Within 1 year"),
                RiskQuestionOptionDto(id = "7y-plus", label = "Beyond 7 years"),
            ),
        )
        val question = dto.toDomain()
        assertEquals("horizon", question.id)
        assertEquals(2, question.options.size)
        assertEquals("Within 1 year", question.options[0].label)
    }

    @Test
    fun `recommendation dto maps to domain`() {
        val dto = RecommendationDto(
            status = "ok",
            riskScore = 75,
            riskBand = "Aggressive",
            toleranceScore = 80,
            behaviorScore = 70,
            insufficientReason = null,
            context = RecommendationContextDto(
                monthsUsed = 2,
                monthlyIncome = 100000.0,
                monthlyExpenses = 42000.0,
                savingsRate = 0.58,
                investableMonthly = 58000.0,
            ),
            allocation = listOf(
                AllocationItemDto("equity", "Equity", 60, "Growth"),
                AllocationItemDto("debt", "Debt", 25, "Stability"),
                AllocationItemDto("gold", "Gold", 15, "Hedge"),
            ),
            allocationSummary = "split",
            notes = listOf(RecommendationNoteDto("caution", "Build an emergency fund.")),
            market = listOf(
                MarketQuoteDto("^NSEI", "NIFTY 50", 23600.0, 0.42, "2026-08-09T12:00:00Z", "Yahoo", "live"),
            ),
            educationalDisclaimer = "Educational only",
        )
        val recommendation = dto.toDomain()
        assertEquals("ok", recommendation.status)
        assertEquals(75, recommendation.riskScore)
        assertEquals("Aggressive", recommendation.riskBand)
        assertEquals(3, recommendation.allocation.size)
        assertEquals(60, recommendation.allocation[0].percentage)
        assertEquals("Build an emergency fund.", recommendation.notes[0].text)
        assertEquals(23600.0, recommendation.market[0].price, 0.0)
        assertEquals("live", recommendation.market[0].status)
        assertEquals(58000.0, recommendation.context!!.investableMonthly, 0.0)
    }

    @Test
    fun `insufficient recommendation maps with null context`() {
        val dto = RecommendationDto(
            status = "insufficient",
            riskScore = 0,
            riskBand = "Conservative",
            toleranceScore = null,
            behaviorScore = 0,
            insufficientReason = "No transactions uploaded yet.",
            context = null,
            allocation = emptyList(),
            allocationSummary = null,
            notes = emptyList(),
            market = emptyList(),
            educationalDisclaimer = "Educational only",
        )
        val recommendation = dto.toDomain()
        assertEquals("insufficient", recommendation.status)
        assertNull(recommendation.context)
        assertTrue(recommendation.allocation.isEmpty())
        assertEquals("No transactions uploaded yet.", recommendation.insufficientReason)
    }

    @Test
    fun `what if result dto maps to domain`() {
        val dto = WhatIfResultDto(
            category = "Dining",
            monthlyReduction = 5000.0,
            monthsUsed = 2,
            currentMonthlySpend = 12000.0,
            monthlyIncome = 100000.0,
            original = WhatIfBlockDto(42000.0, 58000.0, 0.58),
            simulated = WhatIfBlockDto(37000.0, 63000.0, 0.63),
            impact = WhatIfImpactDto(5000.0, 5000.0, 60000.0),
            narrative = "Savings rise.",
            isSimulation = true,
            educationalDisclaimer = "Educational only",
        )
        val result = dto.toDomain()
        assertEquals("Dining", result.category)
        assertEquals(63000.0, result.simulated.monthlySavings, 0.0)
        assertEquals(60000.0, result.impact.annualisedExtraInvestable, 0.0)
        assertTrue(result.isSimulation)
    }

    @Test
    fun `assistant reply dto maps to domain`() {
        val dto = ChatResponseDto(
            answer = "You spend 42000 this month.",
            groundedContext = mapOf("profile" to mapOf("transaction_count" to 3)),
            disclaimer = "Educational only",
        )
        val reply = dto.toDomain()
        assertEquals("You spend 42000 this month.", reply.answer)
        assertEquals("Educational only", reply.disclaimer)
    }

    private fun transactionDto(id: Long) = TransactionDto(
        id = id,
        statementId = 9,
        date = "2026-01-15",
        description = "desc",
        amount = 10.0,
        transactionType = "debit",
        balance = null,
        category = "Uncategorized",
        confidence = null,
        isRecurring = false,
    )
}