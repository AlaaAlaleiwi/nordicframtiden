package com.nordicframtiden.security;
 
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class UsersConfig {

  @Bean
  UserDetailsService userDetailsService(PasswordEncoder encoder) {
    UserDetails user = User.withUsername("user")
        .password(encoder.encode("password"))
        .authorities("ROLE_USER")
        .build();

    UserDetails admin = User.withUsername("admin")
        .password(encoder.encode("password"))
        .authorities("ROLE_ADMIN")
        .build();

    return new InMemoryUserDetailsManager(user, admin);
  }
}