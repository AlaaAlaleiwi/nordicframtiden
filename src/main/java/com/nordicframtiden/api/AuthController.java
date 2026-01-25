package com.nordicframtiden.api;
 

import com.nordicframtiden.security.jwt.JwtService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthenticationManager authManager;
  private final JwtService jwtService;

  public AuthController(AuthenticationManager authManager, JwtService jwtService) {
    this.authManager = authManager;
    this.jwtService = jwtService;
  }

  record LoginRequest(@NotBlank String username, @NotBlank String password) {}
  record TokenResponse(String accessToken) {}

  @PostMapping("/login")
  public TokenResponse login(@RequestBody LoginRequest req) {
    Authentication auth = authManager.authenticate(
        new UsernamePasswordAuthenticationToken(req.username(), req.password())
    );

    List<String> roles = auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .toList();

    String token = jwtService.generateAccessToken(auth.getName(), Map.of("roles", roles));
    return new TokenResponse(token);
  }
}