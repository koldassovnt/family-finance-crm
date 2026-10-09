package com.familyfinance.crm

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableScheduling

// Auth is JWT-only; without this exclusion Boot still auto-configures an
// in-memory user and logs a generated password on every start.
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableFeignClients
@EnableScheduling
class FamilyFinanceCrmApplication

fun main(args: Array<String>) {
    runApplication<FamilyFinanceCrmApplication>(*args)
}
