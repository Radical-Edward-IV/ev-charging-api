package com.evcharging.api.domain.member;

import com.evcharging.api.api.auth.LoginRequest;
import com.evcharging.api.api.auth.LoginResponse;
import com.evcharging.api.api.auth.SignupRequest;
import com.evcharging.api.api.auth.SignupResponse;
import com.evcharging.api.common.BusinessException;
import com.evcharging.api.config.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    MemberRepository memberRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    AuthService authService;

    @Test
    void signup_success() {
        given(memberRepository.existsByEmail("test@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("encoded");
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        SignupResponse response = authService.signup(
                new SignupRequest("test@example.com", "password123", "Test User"));

        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.name()).isEqualTo("Test User");
    }

    @Test
    void signup_duplicateEmail_throws() {
        given(memberRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("test@example.com", "password123", "Test User")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void login_success() {
        Member member = new Member("test@example.com", "encoded", "Test User", Role.USER);
        given(memberRepository.findByEmail("test@example.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("password123", "encoded")).willReturn(true);
        given(jwtTokenProvider.generateToken("test@example.com", "USER")).willReturn("jwt-token");

        LoginResponse response = authService.login(
                new LoginRequest("test@example.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_wrongPassword_throws() {
        Member member = new Member("test@example.com", "encoded", "Test User", Role.USER);
        given(memberRepository.findByEmail("test@example.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrongpass", "encoded")).willReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("test@example.com", "wrongpass")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void login_emailNotFound_throws() {
        given(memberRepository.findByEmail("noone@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("noone@example.com", "password123")))
                .isInstanceOf(BusinessException.class);
    }
}
