package com.nordicframtiden.security;

import com.nordicframtiden.security.model.AppUser;
import com.nordicframtiden.security.model.Role;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountAuthorizationTest {

    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final AccountAuthorization authorization = new AccountAuthorization(userRepository);

    @Test
    void adminCanManageAnyAccountWithoutTargetLookup() {
        var authentication = authentication("ROLE_ADMIN");

        assertThat(authorization.canManage(authentication, 99L)).isTrue();
        verifyNoInteractions(userRepository);
    }

    @Test
    void staffWithPeoplePermissionCanManageOrdinaryUser() {
        var user = userWithRoles(Role.USER);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        assertThat(authorization.canManage(authentication("ROLE_STAFF", "PERM_PEOPLE"), 10L)).isTrue();
    }

    @Test
    void staffWithPeoplePermissionCannotManageStaffAccount() {
        var staff = userWithRoles(Role.STAFF);
        when(userRepository.findById(11L)).thenReturn(Optional.of(staff));

        assertThat(authorization.canManage(authentication("ROLE_STAFF", "PERM_PEOPLE"), 11L)).isFalse();
    }

    @Test
    void ordinaryUserCannotManageAnotherAccount() {
        assertThat(authorization.canManage(authentication("ROLE_USER"), 12L)).isFalse();
        verifyNoInteractions(userRepository);
    }

    private static UsernamePasswordAuthenticationToken authentication(String... authorities) {
        return new UsernamePasswordAuthenticationToken(
            "caller",
            null,
            List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }

    private static AppUser userWithRoles(Role... roles) {
        var user = new AppUser();
        user.setRoles(Set.of(roles));
        return user;
    }
}
