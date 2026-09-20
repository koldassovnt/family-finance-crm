package com.familyfinance.crm.dto

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.AccessLevel
import com.familyfinance.crm.domain.ShareAccess
import com.familyfinance.crm.goal
import com.familyfinance.crm.service.GoalWithProgress
import com.familyfinance.crm.service.Readable
import com.familyfinance.crm.user
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The access badge is the part of a response a client is most likely to trust
 * blindly, so these assert on the serialized shape rather than on the Kotlin
 * objects: what a viewer actually receives is the thing that matters.
 */
class MappersTest {
    private val json = ObjectMapper().registerKotlinModule()

    private val owner = user()
    private val viewer = user(email = "viewer@example.com")
    private val savings = account(owner, balance = "250000")

    private fun sharedGoal(): Readable<GoalWithProgress> =
        Readable.Shared(
            resource =
                GoalWithProgress(
                    goal = goal(owner, savings, targetAmount = "1000000"),
                    progressPercent = BigDecimal("25.00"),
                    achieved = false,
                ),
            owner = owner,
            access = ShareAccess.VIEWER,
        )

    @Test
    fun `a goal's linked account makes no claim about access at all`() {
        val body = json.readTree(json.writeValueAsString(sharedGoal().toGoalResponse()))

        // The outer resource is the thing the caller asked for, so it is badged.
        assertEquals("VIEWER", body["access"].asText())
        // The account nested inside it is not, and must not look like it is: it
        // once read `"access": "OWNER"` here, about an account that 404s for
        // this very caller.
        val linked = body["linkedAccount"]
        assertFalse(linked.has("access"), "a nested account must not carry an access badge")
        assertFalse(linked.has("owner"), "a nested account must not name an owner")
    }

    @Test
    fun `a shared goal still discloses the linked account's balance, which is the point`() {
        val linked = json.readTree(json.writeValueAsString(sharedGoal().toGoalResponse()))["linkedAccount"]

        // Progress is balance over target; dropping this would make the response
        // a false promise rather than a narrower one — see phase-8-sharing.md.
        assertEquals(BigDecimal("250000"), linked["balance"].decimalValue())
        assertEquals("Main", linked["name"].asText())
    }

    @Test
    fun `a shared response names the owner it belongs to`() {
        val response = sharedGoal().toGoalResponse()

        assertEquals(AccessLevel.VIEWER, response.access)
        assertEquals(owner.displayName, response.owner?.displayName)
    }

    @Test
    fun `your own response badges OWNER and names nobody`() {
        val own =
            Readable.Own(
                GoalWithProgress(
                    goal = goal(viewer, account(viewer), targetAmount = "1000"),
                    progressPercent = BigDecimal.ZERO,
                    achieved = false,
                ),
            )

        val response = own.toGoalResponse()

        assertEquals(AccessLevel.OWNER, response.access)
        assertNull(response.owner)
    }

    @Test
    fun `an account read on its own is badged, since it is what the caller asked for`() {
        val readable = Readable.Shared(resource = savings, owner = owner, access = ShareAccess.VIEWER)

        val body = json.readTree(json.writeValueAsString(readable.toResponse()))

        assertTrue(body.has("access"))
        assertEquals("VIEWER", body["access"].asText())
        assertEquals(owner.displayName, body["owner"]["displayName"].asText())
    }
}
