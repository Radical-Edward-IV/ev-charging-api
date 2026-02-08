package com.evcharging.api.api.auth;

import com.evcharging.api.domain.member.Member;

public record SignupResponse(Long id, String email, String name) {
    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId(), member.getEmail(), member.getName());
    }
}
