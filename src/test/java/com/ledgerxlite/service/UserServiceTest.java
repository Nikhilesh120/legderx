package com.ledgerxlite.service;

import com.ledgerxlite.domain.User;
import com.ledgerxlite.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository   userRepository;
    @Mock PasswordEncoder  passwordEncoder;

    @InjectMocks UserService service;

    @Test @DisplayName("register with existing email → IllegalArgumentException")
    void duplicateEmail() {
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);
        assertThatThrownBy(() -> service.registerUser("existing@test.com", "pass"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already registered");
    }

    @Test @DisplayName("register → password is BCrypt encoded, never stored plain")
    void passwordEncoded() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("mypassword")).thenReturn("$2a$12$hashedvalue");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User user = service.registerUser("new@test.com", "mypassword");

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$hashedvalue");
        assertThat(user.getPasswordHash()).doesNotContain("mypassword");
        verify(passwordEncoder).encode("mypassword");
    }

    @Test @DisplayName("register → new user starts ACTIVE")
    void newUserIsActive() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User user = service.registerUser("active@test.com", "pass");
        assertThat(user.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
    }

    @Test @DisplayName("suspendUser → status becomes SUSPENDED")
    void suspendUser() throws Exception {
        User user = new User("u@test.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        service.suspendUser(1L);

        assertThat(user.getStatus()).isEqualTo(User.UserStatus.SUSPENDED);
    }

    @Test @DisplayName("suspend non-existent user → IllegalArgumentException")
    void suspendNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.suspendUser(99L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("BCrypt hash is not reversible — integration sanity check")
    void bcryptNotReversible() {
        PasswordEncoder real = new BCryptPasswordEncoder(12);
        String hash = real.encode("secret");
        assertThat(hash).doesNotContain("secret");
        assertThat(real.matches("secret", hash)).isTrue();
        assertThat(real.matches("wrong",  hash)).isFalse();
    }
}
