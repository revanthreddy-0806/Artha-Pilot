package com.arthapilot.app.ui.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadViewModelTest {

    @Test
    fun `display name wins over uri last path segment`() {
        assertEquals(
            "synthetic_bank_statement_test.csv",
            resolveFileName("synthetic_bank_statement_test.csv", "document/1000179817"),
        )
    }

    @Test
    fun `missing display name falls back to the uri last path segment`() {
        assertEquals("document:1000179817", resolveFileName(null, "document:1000179817"))
        assertEquals("statement.pdf", resolveFileName(null, "tree/some/path/statement.pdf"))
    }

    @Test
    fun `blank display name falls back to the uri last path segment`() {
        assertEquals("my.csv", resolveFileName("   ", "my.csv"))
        assertEquals("my.csv", resolveFileName("", "my.csv"))
        assertNull(resolveFileName("   ", null))
        assertNull(resolveFileName(null, null))
    }

    @Test
    fun `csv extension maps to text slash csv`() {
        assertEquals("text/csv", mimeTypeFor("statement.csv"))
        assertEquals("text/csv", mimeTypeFor("STATEMENT.CSV"))
    }

    @Test
    fun `pdf extension maps to application slash pdf`() {
        assertEquals("application/pdf", mimeTypeFor("statement.pdf"))
    }

    @Test
    fun `unknown extension has no inferred mime`() {
        assertNull(mimeTypeFor("document:1000179817"))
        assertNull(mimeTypeFor("statement.txt"))
    }

    @Test
    fun `mime resolution prefers extension over provider mime`() {
        assertEquals("text/csv", resolveMimeType("statement.csv", "application/octet-stream"))
        assertEquals("application/pdf", resolveMimeType("statement.pdf", "text/plain"))
    }

    @Test
    fun `mime resolution falls back to provider mime for unknown extensions`() {
        assertEquals("application/pdf", resolveMimeType("document:17", "application/pdf"))
        assertEquals("application/octet-stream", resolveMimeType("document:17", null))
    }
}