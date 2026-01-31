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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
SecurityFilterChain securityFilterChain(HttpSecurity http,
    JwtAuthenticationFilter jwtFilter) throws Exception {

  return http
      .cors(cors -> {})
      .csrf(csrf -> csrf.disable()) // 🔥 FIX
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
      .authorizeHttpRequests(auth -> auth
          .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
          .requestMatchers("/auth/**").permitAll()
          .requestMatchers("/api/admins/**").hasRole("ADMIN")
          .requestMatchers("/api/admins/**").hasRole("ADMIN")
          .requestMatchers("/api/schedules/**").hasAnyRole("ADMIN","STAFF")
          .anyRequest().authenticated())
      .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
      .build();
}
  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:5173","https://nordicframtiden-frontend-34c6b049a0f5.herokuapp.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}