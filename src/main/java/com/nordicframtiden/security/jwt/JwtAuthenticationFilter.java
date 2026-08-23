package com.nordicframtiden.security.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
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

    // ✅ If already authenticated, skip
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      chain.doFilter(request, response);
      return;
    }

    String token = resolveToken(request);
    if (token == null) {
      chain.doFilter(request, response);
      return;
    }

    try {
      Claims claims = jwtService.parse(token).getBody();
      String username = claims.getSubject();

      var authorities = buildAuthorities(claims);

      var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
      auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(auth);

    } catch (Exception e) {
      SecurityContextHolder.clearContext();
      System.out.println("JWT invalid/expired: " + request.getMethod() + " " + request.getRequestURI()
          + " -> " + e.getMessage());
    }

    chain.doFilter(request, response);
  }

  @SuppressWarnings("unchecked")
  private List<GrantedAuthority> buildAuthorities(Claims claims) {
    List<String> roles = (List<String>) claims.getOrDefault("roles", List.of());
    List<String> perms = (List<String>) claims.getOrDefault("perms", List.of());

    List<GrantedAuthority> auths = new ArrayList<>();

    // ✅ roles: accept "ADMIN" or "ROLE_ADMIN"
    for (String r : roles) {
      if (r == null || r.isBlank()) continue;
      String name = r.startsWith("ROLE_") ? r : "ROLE_" + r;
      auths.add(new SimpleGrantedAuthority(name));
    }

    // ✅ perms: accept "PEOPLE" or "PERM_PEOPLE"
    for (String p : perms) {
      if (p == null || p.isBlank()) continue;
      String name = p.startsWith("PERM_") ? p : "PERM_" + p;
      auths.add(new SimpleGrantedAuthority(name));
    }

    return auths;
  }

  private String resolveToken(HttpServletRequest request) {
    // 1) Authorization header
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      return header.substring(7);
    }

    // 2) ACCESS_TOKEN cookie
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie c : cookies) {
        if ("ACCESS_TOKEN".equals(c.getName())) {
          return c.getValue();
        }
      }
    }

    return null;
  }
}