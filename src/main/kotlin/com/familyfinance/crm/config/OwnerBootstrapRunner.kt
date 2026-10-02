package com.familyfinance.crm.config

import com.familyfinance.crm.service.OwnerBootstrap
import com.familyfinance.crm.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Creates the `OWNER` on the first start, from OWNER_* environment variables.
 * There is deliberately no endpoint for it — see `phase-0-1-foundation-ledger.md`.
 */
@Component
class OwnerBootstrapRunner(
    private val userService: UserService,
    private val properties: AppProperties,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val config = properties.bootstrapOwner
        when (val result = userService.bootstrapOwner(config)) {
            is OwnerBootstrap.Created -> {
                log.info(
                    "Created the OWNER {}. Log in, change the password, then remove OWNER_PASSWORD from .env.",
                    result.email,
                )
            }

            OwnerBootstrap.NotConfigured -> {
                log.warn("No OWNER exists, so nobody can log in. Set OWNER_EMAIL, OWNER_DISPLAY_NAME and OWNER_PASSWORD, then restart.")
            }

            OwnerBootstrap.AlreadyExists -> {
                if (config.password.isNotBlank()) {
                    log.warn("OWNER_PASSWORD is set but ignored, because an OWNER already exists. Remove it from .env.")
                }
            }
        }
    }
}
