package com.finsight.notificationservice.service;

import com.finsight.notificationservice.dto.EmailNotification;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Send an HTML email using a Thymeleaf template.
     *
     * Records the notification in the audit log regardless of
     * success or failure. This gives you a full history of
     * every notification attempt — essential for debugging
     * "I didn't receive the email" reports.
     */
    @Transactional
    public void sendEmail(EmailNotification notification) {
        String status = "SENT";
        String errorMessage = null;

        try {
            // Render HTML from Thymeleaf template
            Context context = new Context();
            notification.getVariables().forEach(context::setVariable);

            String htmlContent = templateEngine.process(
                    notification.getTemplateName(), context);

            // Build MIME message with HTML content
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, "UTF-8");

            helper.setTo(notification.getTo());
            helper.setSubject(notification.getSubject());
            helper.setText(htmlContent, true); // true = HTML
            helper.setFrom("noreply@finsight.com");

            mailSender.send(message);

            log.info("Email sent: to={} subject={} eventId={}",
                    notification.getTo(),
                    notification.getSubject(),
                    notification.getEventId());

        } catch (MessagingException e) {
            status = "FAILED";
            errorMessage = e.getMessage();
            log.error("Failed to send email: to={} eventId={}",
                    notification.getTo(),
                    notification.getEventId(), e);
            // Do not re-throw — we log the failure and move on
            // A failed notification is bad but should not crash the consumer
        }

        // Log every attempt — success or failure
        jdbcTemplate.update(
                "INSERT INTO notification_log " +
                        "(event_id, event_type, recipient_email, subject, " +
                        " status, error_message) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                notification.getEventId(),
                notification.getEventType(),
                notification.getTo(),
                notification.getSubject(),
                status,
                errorMessage
        );
    }
}