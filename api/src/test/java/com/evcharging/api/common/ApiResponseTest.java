package com.evcharging.api.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void success_응답을_생성한다() {
        ApiResponse<String> response = ApiResponse.success("hello");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.error()).isNull();
    }

    @Test
    void error_응답을_생성한다() {
        ApiResponse<Void> response = ApiResponse.error("NOT_FOUND", "찾을 수 없습니다");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error().code()).isEqualTo("NOT_FOUND");
        assertThat(response.error().message()).isEqualTo("찾을 수 없습니다");
    }
}
