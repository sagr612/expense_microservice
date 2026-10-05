package com.greatest.expenseService.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greatest.expenseService.dto.ExpenseDto;
import com.greatest.expenseService.entities.Budget;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.ExpenseType;
import com.greatest.expenseService.entities.OutboxEvent;
import com.greatest.expenseService.repository.BudgetRepository;
import com.greatest.expenseService.repository.ExpenseRepository;
import com.greatest.expenseService.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQtMzItYnl0ZS1sb25nLWZvci1oczI1NiE=",
        "eureka.client.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb_integration;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExpenseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @BeforeEach
    void cleanUp() {
        expenseRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    private Authentication userAuth(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    @DisplayName("POST /api/v1/expenses creates an expense and persists an OutboxEvent")
    void createExpense_endToEnd_createsOutboxEvent() throws Exception {
        String externalId = UUID.randomUUID().toString();

        ExpenseDto request = ExpenseDto.builder()
                .externalId(externalId)
                .amount(BigDecimal.valueOf(250.75))
                .category(CategoryType.FOOD)
                .type(ExpenseType.EXPENSE)
                .title("Groceries")
                .currency("INR")
                .transactionDate(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/v1/expenses")
//                        .with(jwt().jwt(builder -> builder.claim("userId", "user-int-123")))
                        .with(authentication(userAuth("user-123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalId").value(externalId))
                .andExpect(jsonPath("$.amount").value(250.75))
                .andExpect(jsonPath("$.title").value("Groceries"));

        // Verify entity persisted in database
        assertThat(expenseRepository.findByUserIdAndExternalId("user-123", externalId)).isPresent();

        // Verify atomic outbox event was generated for Kafka publisher
        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("EXPENSE_CREATED");
        assertThat(outboxEvents.get(0).getAggregateId()).isEqualTo("user-123");
    }

    @Test
    @DisplayName("Idempotency: Repeating create request with same externalId returns existing expense without duplicate")
    void createExpense_idempotency_returnsExisting() throws Exception {
        String externalId = "idempotent-ext-id";

        ExpenseDto request = ExpenseDto.builder()
                .externalId(externalId)
                .amount(BigDecimal.valueOf(100.00))
                .category(CategoryType.SHOPPING)
                .type(ExpenseType.EXPENSE)
                .title("Book")
                .currency("INR")
                .transactionDate(LocalDate.now())
                .build();

        // First call
        mockMvc.perform(post("/api/v1/expenses")
                        .with(authentication(userAuth("user-123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Repeated retry with same externalId
        mockMvc.perform(post("/api/v1/expenses")
                        .with(authentication(userAuth("user-123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalId").value(externalId));

        // Ensure database has only 1 row
        assertThat(expenseRepository.countByUserId("user-123")).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/v1/expenses triggers BUDGET_ALERT outbox event when exceeding threshold")
    void createExpense_triggersBudgetAlert_createsBothOutboxEvents() throws Exception {
    LocalDate today=LocalDate.now();

        Budget budget = Budget.builder()
                .userId("user-123")
                .category(CategoryType.FOOD)
                .amount(BigDecimal.valueOf(100.00))
                .month(today.getMonthValue())
                .year(today.getYear())
                .build();
        budgetRepository.save(budget);

        ExpenseDto request = ExpenseDto.builder()
                .externalId(UUID.randomUUID().toString())
                .amount(BigDecimal.valueOf(85.00))
                .category(CategoryType.FOOD)
                .type(ExpenseType.EXPENSE)
                .title("Dinner")
                .currency("INR")
                .transactionDate(today)
                .build();


        mockMvc.perform(post("/api/v1/expenses")
                        .with(authentication(userAuth("user-123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        assertThat(outboxEvents).hasSize(2);
        assertThat(outboxEvents).extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder("EXPENSE_CREATED", "BUDGET_ALERT");

    }

}
