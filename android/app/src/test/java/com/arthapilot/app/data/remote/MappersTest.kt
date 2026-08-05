package com.arthapilot.app.data.remote

import com.arthapilot.app.data.local.StatementEntity
import com.arthapilot.app.data.local.TransactionEntity
import com.arthapilot.app.data.remote.dto.LoginRequestDto
import com.arthapilot.app.data.remote.dto.RegisterRequestDto
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