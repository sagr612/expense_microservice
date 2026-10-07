package org.example.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    @Value("${brevo.api-key}")
    private String apiKey;

    @Value("${brevo.sender-email:noreply@expensetracker.com}")
    private String senderEmail;

    @Value("${brevo.sender-name:Expense Tracker}")
    private String senderName;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory(){{
                setConnectTimeout(3000);
                setReadTimeout(5000);
            }})
            .build();

    @PostConstruct
    public void init() {
        boolean isConfigured = apiKey != null && !apiKey.isBlank();
        log.info("Brevo email service initialized (API key configured: {}, sender: {} <{}>)",
                isConfigured, senderName, senderEmail);
        if ("noreply@expensetracker.com".equalsIgnoreCase(senderEmail)) {
            log.warn("Brevo sender-email is using default 'noreply@expensetracker.com'. " +
                    "Brevo will reject deliveries unless this domain is verified in your Brevo account.");
        }
    }

    public void sendEmail(String to, String subject, String htmlBody) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Brevo API key is not configured. Email to {} was skipped.", to);
            return;
        }
        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "htmlContent", htmlBody
        );

        try {
            String response = restClient.post()
                    .uri("https://api.brevo.com/v3/smtp/email")
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            log.info("Email accepted by Brevo for {} (sender: {}). Brevo response: {}", to, senderEmail, response);
        } catch (Exception e) {
            log.error("Failed to send email to {} via sender {}: {}", to, senderEmail, e.getMessage());
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }
}