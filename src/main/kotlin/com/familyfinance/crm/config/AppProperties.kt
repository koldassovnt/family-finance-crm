package com.familyfinance.crm.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.ZoneId

@Validated
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    /** Fixed app timezone — everything date-dependent resolves against it. */
    val timezone: ZoneId,
    @field:Valid
    val jwt: JwtProperties,
    val bootstrapOwner: BootstrapOwnerProperties = BootstrapOwnerProperties(),
    @field:Valid
    val marketData: MarketDataProperties = MarketDataProperties(),
) {
    /**
     * Prices and exchange rates from API Ninjas. Optional: with no key the app
     * runs exactly as before and holdings simply report no current value.
     */
    data class MarketDataProperties(
        /** Supplied via API_NINJAS_KEY; blank means market data is switched off. */
        val apiKey: String = "",
        val baseUrl: String = "https://api.api-ninjas.com",
        /** The most calls sent to each of the three APIs in one day. */
        @field:Min(0)
        val dailyLimit: Int = 30,
        /** When the daily refresh runs, in the app timezone. */
        val refreshCron: String = "0 0 8 * * *",
    ) {
        val isConfigured: Boolean get() = apiKey.isNotBlank()

        /** Never let the key reach a log line or a bind-failure report. */
        override fun toString() = "MarketDataProperties(apiKey=***, baseUrl=$baseUrl, dailyLimit=$dailyLimit, refreshCron=$refreshCron)"
    }

    /**
     * Seeds the single `OWNER` on a start that finds none — the bootstrap step
     * `00-` refers to. Supplied via OWNER_EMAIL, OWNER_DISPLAY_NAME and
     * OWNER_PASSWORD; blank means unset, since compose passes unset variables
     * as empty strings. Ignored entirely once an `OWNER` exists.
     */
    data class BootstrapOwnerProperties(
        val email: String = "",
        val displayName: String = "",
        val password: String = "",
    ) {
        val isConfigured: Boolean get() = listOf(email, displayName, password).any { it.isNotBlank() }

        /** Never let the password reach a log line or a bind-failure report. */
        override fun toString() = "BootstrapOwnerProperties(email=$email, displayName=$displayName, password=***)"
    }

    data class JwtProperties(
        /** Signing secret; supplied via the JWT_SECRET environment variable. */
        @field:NotBlank(message = "must be set (env JWT_SECRET)")
        val secret: String,
        @field:Min(1)
        val expiryDays: Long,
    )
}
