package com.nordicframtiden.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;


import static org.assertj.core.api.Assertions.*;

 
@SpringBootTest
@WithMockUser(roles = "ADMIN")
class AdminServiceIT {

  

  @Autowired AdminService adminService;
 
  @Test
  void reset_password_changes_password_hash_or_value() {
    var created = adminService.createAdminWithProfile(
        true, "Reset Me", "reset@nordic.se", "0700000000"
    );

    var afterReset = adminService.resetAdminPassword(created.id());

    assertThat(afterReset.password()).isNotBlank();
    assertThat(afterReset.password()).isNotEqualTo(created.password());
  }
}
