package com.nordicframtiden.security;

import com.nordicframtiden.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationFilter jwtFilter) throws Exception {

        return http
                // ✅ IMPORTANT: wire Spring CORS using the bean below
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Bearer tokens are accepted only from Authorization headers, so browsers
                // cannot attach authentication ambiently to cross-site requests.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()

                        .requestMatchers("/api/admins/**").hasRole("ADMIN")

                        .requestMatchers("/api/users/me").authenticated()
                        .requestMatchers("/api/users/**").hasAnyRole("ADMIN", "STAFF")

                        .requestMatchers("/api/settings/**").hasRole("ADMIN")

                        .requestMatchers("/api/salaries/payslip/me", "/api/salaries/me/**").authenticated()
                        .requestMatchers("/api/salaries/**")
                        .hasAnyAuthority("ROLE_ADMIN", "PERM_SALARIES")

                        // ✅ allow pharmacists (USER) to access their own schedule endpoint
                        .requestMatchers("/api/schedules/me").hasAnyRole("ADMIN", "USER","STAFF")

                        // ✅ keep admin-only schedule listing/CRUD rules if you want
                        .requestMatchers("/api/schedules/**").hasAnyRole("ADMIN", "STAFF") // or ADMIN only if that's
                                                                                           // your intention
                        .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()
                        .requestMatchers("/api/staff-schedules/**").hasAnyRole("ADMIN", "STAFF")

                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // ✅ Exact frontend origins
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:3000",
                "https://nordicframtiden-frontend-34c6b049a0f5.herokuapp.com",
                "https://nordicframtiden-frontend-644311628279.europe-north1.run.app",
                "https://nordicframtiden-frontend-mbtjtlqpcq-lz.a.run.app"));

        // ✅ IMPORTANT: include PATCH (preflight is failing because PATCH isn't allowed)
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // ✅ Allow headers used by axios + preflight
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin"));

        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
