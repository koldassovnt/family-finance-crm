package com.familyfinance.crm.tools

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * Prints the BCrypt hash to paste into `db/bootstrap-owner.sql`, since the
 * first OWNER is inserted by hand rather than through any endpoint.
 *
 * Run with: `./gradlew printPasswordHash -Ppassword='...'`
 *
 * Lives in the test source set deliberately — it must not ship in the app jar.
 */
fun main(args: Array<String>) {
    val password = args.firstOrNull()
    if (password.isNullOrBlank()) {
        System.err.println("Usage: ./gradlew printPasswordHash -Ppassword='your-password'")
        return
    }
    println(BCryptPasswordEncoder().encode(password))
}
