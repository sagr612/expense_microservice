package com.greatest.notification_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"jwt.secret=dGVzdC1zZWNyZXQtMzItYnl0ZS1sb25nLWZvci1oczI1NiE=",
		"eureka.client.enabled=false",
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.kafka.listener.auto-startup=false"
})
class NotificationServiceApplicationTests {

	@Test
	@DisplayName("Spring application loads successfully")
	void contextLoads() {
	}

}
