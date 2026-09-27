package com.greatest.expenseService;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQtMzItYnl0ZS1sb25nLWZvci1oczI1NiE=",
        "eureka.client.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@ActiveProfiles("test")
public class ExpenseServiceApplicationTests {

    @Test
    @DisplayName("Spring application context loads successfully")
    void contextLoads() {

    }
}
