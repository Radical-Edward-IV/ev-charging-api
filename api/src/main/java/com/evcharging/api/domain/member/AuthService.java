package com.evcharging.api.domain.member;

import com.evcharging.api.api.auth.LoginRequest;
import com.evcharging.api.api.auth.LoginResponse;
import com.evcharging.api.api.auth.SignupRequest;
import com.evcharging.api.api.auth.SignupResponse;
import com.evcharging.api.common.BusinessException;
import com.evcharging.api.common.ErrorCode;
import com.evcharging.api.config.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(MemberRepository memberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        Member member = new Member(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                Role.USER);

        memberRepository.save(member);
        return SignupResponse.from(member);
    }

    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtTokenProvider.generateToken(member.getEmail(), member.getRole().name());
        return LoginResponse.of(token);
    }
}
