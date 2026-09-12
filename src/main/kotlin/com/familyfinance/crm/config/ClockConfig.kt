package com.familyfinance.crm.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfig {
    /** Injected rather than calling `LocalDate.now()` inline, so tests can fix "today". */
    @Bean
    fun clock(properties: AppProperties): Clock = Clock.system(properties.timezone)
}
