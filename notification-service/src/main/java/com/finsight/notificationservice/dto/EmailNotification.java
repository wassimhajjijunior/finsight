package com.finsight.notificationservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class EmailNotification {

    private String to;
    private String subject;
    private String templateName;
    private Map<String, Object> variables;
    private String eventId;
    private String eventType;
}