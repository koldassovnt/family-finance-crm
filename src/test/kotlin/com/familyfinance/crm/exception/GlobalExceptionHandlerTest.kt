package com.familyfinance.crm.exception

import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `maps a constraint violation to a conflict rather than a server error`() {
        // Losing a race against a unique index is a conflict; without this the
        // caller would see a 500 and assume the service is broken.
        val response =
            handler.handleDataIntegrityViolation(
                DataIntegrityViolationException("duplicate key value violates unique constraint"),
            )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.CONFLICT, response.body?.code)
    }

    @Test
    fun `does not leak the underlying database message to the caller`() {
        val response =
            handler.handleDataIntegrityViolation(
                DataIntegrityViolationException("ERROR: duplicate key ... budget_versions_open_uk"),
            )

        assertEquals("That change conflicts with the current state; try again", response.body?.message)
    }

    @Test
    fun `maps an unexpected failure to a generic 500`() {
        val response = handler.handleUnexpected(IllegalStateException("boom"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Something went wrong", response.body?.message)
    }
}
