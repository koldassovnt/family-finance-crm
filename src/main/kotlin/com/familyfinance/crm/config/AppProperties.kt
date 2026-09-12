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
) {
    data class JwtProperties(
        /** Signing secret; supplied via the JWT_SECRET environment variable. */
        @field:NotBlank(message = "must be set (env JWT_SECRET)")
        val secret: String,
        @field:Min(1)
        val expiryDays: Long,
    )
}
