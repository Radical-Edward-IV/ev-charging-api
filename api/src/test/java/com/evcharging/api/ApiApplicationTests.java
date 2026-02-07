package com.evcharging.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = {
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.sql.init.mode=never",
	"openapi.service-key="
})
class ApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
