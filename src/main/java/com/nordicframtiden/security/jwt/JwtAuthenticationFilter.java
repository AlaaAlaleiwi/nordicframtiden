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
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
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

      @SuppressWarnings("unchecked")
      List<String> roles = (List<String>) claims.get("roles");

      var authorities = roles == null
          ? List.<SimpleGrantedAuthority>of()
          : roles.stream().map(SimpleGrantedAuthority::new).toList();

      var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);

      // ✅ add details (IP, session, etc.)
      auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

      SecurityContextHolder.getContext().setAuthentication(auth);

    } catch (Exception e) {
      // ✅ log so you can see what's happening
      System.out.println("JWT invalid or expired for " + request.getMethod() + " " + request.getRequestURI()
          + " : " + e.getMessage());
    }

    chain.doFilter(request, response);
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