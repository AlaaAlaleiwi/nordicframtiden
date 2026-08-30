package com.nordicframtiden.security.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  private final JwtService jwtService;
  private final AppUserRepository userRepository;

  public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository userRepository) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
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
      Claims claims = jwtService.validateAccessToken(token);
      String username = claims.getSubject();
      AppUser user = userRepository.findByUsername(username)
          .filter(AppUser::isEnabled)
          .orElseThrow(() -> new IllegalArgumentException("Active user not found"));

      var authorities = buildAuthorities(user);

      var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
      auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(auth);

    } catch (Exception e) {
      SecurityContextHolder.clearContext();
      log.debug("Bearer token rejected for {} {}", request.getMethod(), request.getRequestURI());
    }

    chain.doFilter(request, response);
  }

  private List<GrantedAuthority> buildAuthorities(AppUser user) {
    List<GrantedAuthority> auths = new ArrayList<>();

    user.getRoles().forEach(role ->
        auths.add(new SimpleGrantedAuthority("ROLE_" + role.name())));
    user.getPermissions().forEach(permission ->
        auths.add(new SimpleGrantedAuthority("PERM_" + permission.name())));

    return auths;
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    return null;
  }
}
