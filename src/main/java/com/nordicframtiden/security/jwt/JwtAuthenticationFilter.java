package com.nordicframtiden.security.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain
  ) throws ServletException, IOException {

    String token = null;

    // 1️⃣ Authorization header
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      token = header.substring(7);
    }

    // 2️⃣ ACCESS_TOKEN cookie
    if (token == null && request.getCookies() != null) {
      for (Cookie c : request.getCookies()) {
        if ("ACCESS_TOKEN".equals(c.getName())) {
          token = c.getValue();
          break;
        }
      }
    }

    if (token == null) {
      chain.doFilter(request, response);
      return;
    }

    try {
      Claims claims = jwtService.parse(token).getBody();
      String username = claims.getSubject();

      @SuppressWarnings("unchecked")
      List<String> roles = (List<String>) claims.get("roles");

      var authorities = roles == null
          ? List.<SimpleGrantedAuthority>of()
          : roles.stream().map(SimpleGrantedAuthority::new).toList();

      var auth = new UsernamePasswordAuthenticationToken(
          username,
          null,
          authorities
      );

      SecurityContextHolder.getContext().setAuthentication(auth);

    } catch (Exception ignored) {
      // invalid / expired token → ignore
    }

    chain.doFilter(request, response);
  }
}