package com.familyfinance.crm.config

import com.familyfinance.crm.security.JsonAccessDeniedHandler
import com.familyfinance.crm.security.JsonAuthenticationEntryPoint
import com.familyfinance.crm.security.JwtService
import com.familyfinance.crm.security.ROLE_CLAIM
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.ObjectPostProcessor
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val jwtService: JwtService,
    private val authenticationEntryPoint: JsonAuthenticationEntryPoint,
    private val accessDeniedHandler: JsonAccessDeniedHandler,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            // Stateless bearer-token auth: no session to fixate, no cookie to forge.
            .csrf { it.disable() }
            .cors { }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .anonymous { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { registry ->
                registry
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(PUBLIC_PATHS)
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { server ->
                server.jwt { it.decoder(jwtService.decoder).jwtAuthenticationConverter(roleConverter()) }
                server.bearerTokenResolver(ignoringPublicPaths())
                server.authenticationEntryPoint(authenticationEntryPoint)
                server.withObjectPostProcessor(reportingServiceFailures())
            }.exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }.build()

    /** `role: OWNER` becomes `ROLE_OWNER`, which is what `hasRole('OWNER')` checks. */
    private fun roleConverter() =
        JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(
                JwtGrantedAuthoritiesConverter().apply {
                    setAuthoritiesClaimName(ROLE_CLAIM)
                    setAuthorityPrefix("ROLE_")
                },
            )
        }

    /**
     * A public path never looks at the token. Otherwise a client that attaches
     * its stored token to every call would be refused a fresh login once that
     * token expired — and a stale header would fail a health check.
     */
    private fun ignoringPublicPaths(): BearerTokenResolver {
        val default = DefaultBearerTokenResolver()
        return BearerTokenResolver { request -> if (PUBLIC_PATHS.matches(request)) null else default.resolve(request) }
    }

    /**
     * By default a failure to *check* a token (database down) is rethrown and
     * escapes the chain as a container error page; routed to the entry point
     * instead, it becomes the JSON 503 every client expects.
     */
    private fun reportingServiceFailures() =
        object : ObjectPostProcessor<BearerTokenAuthenticationFilter> {
            override fun <O : BearerTokenAuthenticationFilter> postProcess(filter: O): O =
                filter.apply {
                    setAuthenticationFailureHandler(
                        AuthenticationEntryPointFailureHandler(authenticationEntryPoint).apply {
                            setRethrowAuthenticationServiceException(false)
                        },
                    )
                }
        }
}

private val PUBLIC_PATHS: RequestMatcher =
    PathPatternRequestMatcher.withDefaults().let { paths ->
        OrRequestMatcher(
            listOf(
                "/api/v1/auth/login",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs",
                "/v3/api-docs/**",
                "/actuator/health",
                "/actuator/info",
            ).map { paths.matcher(it) },
        )
    }
