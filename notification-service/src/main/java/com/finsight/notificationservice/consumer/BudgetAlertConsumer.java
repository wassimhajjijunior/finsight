package com.finsight.notificationservice.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.notificationservice.dto.EmailNotification;
import com.finsight.notificationservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetAlertConsumer {

    private final EmailService emailService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "budget.threshold.exceeded",
            groupId = "notification-service-group"
    )
    @Transactional
    public void handleBudgetAlert(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String eventKey) {

        log.debug("Received budget.threshold.exceeded: key={}", eventKey);

        // Idempotency check
        Boolean alreadyProcessed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM processed_events " +
                        "WHERE event_id = ?",
                Boolean.class, eventKey
        );

        if (Boolean.TRUE.equals(alreadyProcessed)) {
            log.info("Duplicate budget alert skipped: {}", eventKey);
            return;
        }

        try {
            Map<String, Object> event = objectMapper.readValue(
                    payload, new TypeReference<>() {});

            // Extract event data
            String userId = (String) event.get("userId");
            String budgetName = (String) event.get("budgetName");
            String category = (String) event.get("category");
            String currency = (String) event.get("currency");
            Object amountLimitObj = event.get("amountLimit");
            Object spentAmountObj = event.get("spentAmount");
            Object spentPctObj = event.get("spentPercentage");
            Object alertThresholdObj = event.get("alertThreshold");

            BigDecimal amountLimit = new BigDecimal(
                    amountLimitObj.toString());
            BigDecimal spentAmount = new BigDecimal(
                    spentAmountObj.toString());
            BigDecimal remaining = amountLimit.subtract(spentAmount);
            double spentPct = Double.parseDouble(
                    spentPctObj.toString());

            // Build template variables
            Map<String, Object> variables = new HashMap<>();
            variables.put("budgetName", budgetName);
            variables.put("category", category);
            variables.put("currency", currency);
            variables.put("amountLimit",
                    amountLimit.setScale(2, RoundingMode.HALF_UP));
            variables.put("spentAmount",
                    spentAmount.setScale(2, RoundingMode.HALF_UP));
            variables.put("remainingAmount",
                    remaining.setScale(2, RoundingMode.HALF_UP));
            variables.put("spentPercentage",
                    String.format("%.1f", spentPct));
            variables.put("alertThreshold", alertThresholdObj);

            // In a real system, you would look up the user's email
            // from auth-service via Feign. For now we use a placeholder
            // that works with Mailhog — update this when you add
            // a user lookup Feign client
            String userEmail = "user-" + userId + "@finsight.dev";

            EmailNotification notification = EmailNotification.builder()
                    .to(userEmail)
                    .subject("⚠ Budget Alert: " + budgetName +
                            " is " + String.format("%.0f", spentPct) +
                            "% spent")
                    .templateName("budget-alert")
                    .variables(variables)
                    .eventId(eventKey)
                    .eventType("budget.threshold.exceeded")
                    .build();

            emailService.sendEmail(notification);

            // Mark as processed after successful handling
            jdbcTemplate.update(
                    "INSERT INTO processed_events " +
                            "(event_id, event_type) VALUES (?, ?)",
                    eventKey, "budget.threshold.exceeded"
            );

        } catch (Exception e) {
            log.error("Error processing budget alert: {}",
                    eventKey, e);
            throw new RuntimeException(
                    "Failed to process budget alert", e);
        }
    }
}