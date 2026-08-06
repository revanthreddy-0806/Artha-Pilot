package com.arthapilot.app.data.remote

import com.arthapilot.app.data.local.StatementEntity
import com.arthapilot.app.data.local.TransactionEntity
import com.arthapilot.app.data.remote.dto.BudgetDto
import com.arthapilot.app.data.remote.dto.CategoryAmountDto
import com.arthapilot.app.data.remote.dto.InsightsSummaryDto
import com.arthapilot.app.data.remote.dto.LoginRequestDto
import com.arthapilot.app.data.remote.dto.MonthTrendDto
import com.arthapilot.app.data.remote.dto.RecurringItemDto
import com.arthapilot.app.data.remote.dto.RiskFactorDto
import com.arthapilot.app.data.remote.dto.RiskProfileDto
import com.arthapilot.app.data.remote.dto.RegisterRequestDto
import com.arthapilot.app.data.remote.dto.StatementItemDto
import com.arthapilot.app.data.remote.dto.StatementUploadResponseDto
import com.arthapilot.app.data.remote.dto.TokenResponseDto
import com.arthapilot.app.data.remote.dto.TransactionDto
import com.arthapilot.app.data.remote.dto.TransactionPageDto
import com.arthapilot.app.data.remote.dto.UserDto
import org.junit.Assert.assertEquals
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
            band = "Excellent",
            summary = "Looks great",
            factors = listOf(RiskFactorDto("Savings rate", "60.0%", "positive")),
            income = 100000.0,
            monthlyExpenses = 40000.0,
            savingsRate = 0.6,
            emiLoadRatio = 0.1,
            safeEmiAmount = 30000.0,
            maxLoanAmount = 1398000.0,
            maxLoanTenureMonths = 60,
        )
        val profile = dto.toDomain()
        assertEquals(82, profile.score)
        assertEquals("Excellent", profile.band)
        assertEquals(1, profile.factors.size)
        assertEquals(1398000.0, profile.maxLoanAmount!!, 0.0)
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