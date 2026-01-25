package com.nordicframtiden.security.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith("Bearer ")) {
      chain.doFilter(request, response);
      return;
    }

    String token = header.substring(7);

    try {
      Claims claims = jwtService.parse(token).getBody();
      String username = claims.getSubject();

      if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles", List.class);

        var authorities = roles == null ? List.<SimpleGrantedAuthority>of()
            : roles.stream().map(SimpleGrantedAuthority::new).toList();

        var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    } catch (Exception ignored) {}

    chain.doFilter(request, response);
  }
}