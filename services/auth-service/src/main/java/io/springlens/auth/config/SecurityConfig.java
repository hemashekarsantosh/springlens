package io.springlens.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${springlens.internal.allowed-cidr:127.0.0.1/32}")
    private String allowedInternalCidr;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/v1/auth/github/callback").permitAll()
                        .requestMatchers("/v1/billing/webhooks/stripe").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Internal endpoints — cluster IPs only
                        .requestMatchers("/internal/**")
                        .access(new org.springframework.security.web.access.expression.WebExpressionAuthorizationManager(
                                "hasIpAddress('" + allowedInternalCidr + "') or hasIpAddress('10.0.0.0/8') " +
                                "or hasIpAddress('172.16.0.0/12') or hasIpAddress('127.0.0.1')"))
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                .build();
    }
}
