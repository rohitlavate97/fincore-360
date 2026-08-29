package com.fincore.shared.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val authenticationEntryPoint: CustomAuthenticationEntryPoint,
    private val accessDeniedHandler: CustomAccessDeniedHandler,
    private val jwtDecoder: JwtDecoder
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun jwtAuthenticationConverter(): CustomJwtAuthenticationConverter {
        return CustomJwtAuthenticationConverter()
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { headers ->
                headers
                    .contentSecurityPolicy { it.policyDirectives("default-src 'self'; frame-ancestors 'none'") }
                    .frameOptions { it.deny() }
                    .httpStrictTransportSecurity {
                        it.includeSubDomains(true)
                        it.maxAgeInSeconds(31536000)
                    }
                    .referrerPolicy {
                        it.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                    }
                    .permissionsPolicy {
                        it.policy("geolocation=(), camera=(), microphone=()")
                    }
            }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/actuator/health/**",
                    "/actuator/info",
                    "/api/v1/auth/**"
                ).permitAll()
                it.requestMatchers(
                    "/actuator/prometheus",
                    "/actuator/metrics/**"
                ).hasRole("ADMIN")
                it.requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).hasRole("ADMIN")
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.decoder(jwtDecoder)
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
                oauth2.authenticationEntryPoint(authenticationEntryPoint)
                oauth2.accessDeniedHandler(accessDeniedHandler)
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): org.springframework.web.cors.CorsConfigurationSource {
        val configuration = org.springframework.web.cors.CorsConfiguration().apply {
            allowedOrigins = listOf("http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:5173")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-ID")
            exposedHeaders = listOf("X-Correlation-ID")
            allowCredentials = true
        }
        val source = org.springframework.web.cors.UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
