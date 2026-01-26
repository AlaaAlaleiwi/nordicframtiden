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
    throws IOException, ServletException {

  String token = null;

  if (request.getCookies() != null) {
    for (Cookie c : request.getCookies()) {
      if ("ACCESS_TOKEN".equals(c.getName())) {
        token = c.getValue();
      }
    }
  }

  if (token == null) {
    chain.doFilter(request, response);
    return;
  }

  Claims claims = jwtService.parse(token).getBody();
  String username = claims.getSubject();

  @SuppressWarnings("unchecked")
  List<String> roles = (List<String>) claims.get("roles");

  var auth = new UsernamePasswordAuthenticationToken(
      username,
      null,
      roles.stream().map(SimpleGrantedAuthority::new).toList()
  );

  SecurityContextHolder.getContext().setAuthentication(auth);
  chain.doFilter(request, response);
}
}