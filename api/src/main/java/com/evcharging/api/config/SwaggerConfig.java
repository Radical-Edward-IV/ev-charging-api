package com.evcharging.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI evChargingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EV 충전소 관리 API")
                        .description("한국환경공단 공공 API 기반의 EV 충전소 관리 REST API")
                        .version("v1.0.0")
                        .contact(new Contact().name("Kim Kwanghyeok")));
    }
}
