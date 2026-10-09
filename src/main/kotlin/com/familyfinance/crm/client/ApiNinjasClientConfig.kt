package com.familyfinance.crm.client

import com.familyfinance.crm.config.AppProperties
import feign.RequestInterceptor
import org.springframework.context.annotation.Bean

/**
 * Deliberately not a `@Configuration`: named only from [ApiNinjasClient], so
 * the key is attached to that client's requests and to nothing else.
 */
class ApiNinjasClientConfig {
    @Bean
    fun apiKeyInterceptor(properties: AppProperties): RequestInterceptor =
        RequestInterceptor { request -> request.header("X-Api-Key", properties.marketData.apiKey) }
}
