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
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
        .authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
    .requestMatchers("/auth/**").permitAll()

    .requestMatchers("/api/admins/**").hasRole("ADMIN")

    // ✅ allow pharmacists (USER) to access their own schedule endpoint
    .requestMatchers("/api/schedules/me").hasAnyRole("ADMIN","USER")

    // ✅ keep admin-only schedule listing/CRUD rules if you want
    .requestMatchers("/api/schedules/**").hasAnyRole("ADMIN","STAFF") // or ADMIN only if that's your intention

    .requestMatchers("/api/staff-schedules/**").hasAnyRole("ADMIN","STAFF")

    .requestMatchers("/api/**").authenticated()
    .anyRequest().authenticated()
)
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();

    // ✅ must be exact origins, no "*"
    config.setAllowedOrigins(List.of(
        "http://localhost:5173",
        "https://nordicframtiden-frontend-34c6b049a0f5.herokuapp.com"
    ));

    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

    // ✅ include headers your axios sends + common ones
    config.setAllowedHeaders(List.of(
        "Content-Type",
        "Authorization",
        "X-Requested-With",
        "X-XSRF-TOKEN",
        "Cache-Control",
        "Pragma"
    ));

    // ✅ allow cookies
    config.setAllowCredentials(true);

    // optional (not required for cookies to work, but fine)
    config.setExposedHeaders(List.of("Set-Cookie"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}