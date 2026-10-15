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
    val bot: BotProperties = BotProperties(),
) {
    /**
     * The Telegram bot, a trusted client that acts on behalf of whoever is
     * writing to it. Optional: with no token every bot request is refused.
     */
    data class BotProperties(
        /** Supplied via BOT_API_TOKEN; blank means the bot is switched off. */
        val token: String = "",
    ) {
        val isConfigured: Boolean get() = token.isNotBlank()

        /** Never let the token reach a log line or a bind-failure report. */
        override fun toString() = "BotProperties(token=***)"
    }

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
        /** KASE's site, asked for the stocks of KZT accounts; needs no key. */
        val kaseBaseUrl: String = "https://kase.kz",
        /** When KASE's closing prices are fetched, in the app timezone: after its trading day. */
        val kaseRefreshCron: String = "0 30 18 * * *",
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
