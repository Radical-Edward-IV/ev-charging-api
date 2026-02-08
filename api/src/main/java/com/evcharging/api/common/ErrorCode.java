package com.evcharging.api.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    STATION_NOT_FOUND(HttpStatus.NOT_FOUND, "충전소를 찾을 수 없습니다"),
    CHARGER_NOT_FOUND(HttpStatus.NOT_FOUND, "충전기를 찾을 수 없습니다"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "충전 세션을 찾을 수 없습니다"),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "유효하지 않은 상태 전이입니다"),
    CHARGER_NOT_AVAILABLE(HttpStatus.CONFLICT, "충전기가 사용 가능 상태가 아닙니다"),
    SESSION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료된 충전 세션입니다"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
