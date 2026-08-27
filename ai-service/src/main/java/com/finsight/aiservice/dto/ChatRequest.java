package com.finsight.aiservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "Message cannot be blank")
    @Size(max = 2000, message = "Message too long")
    private String message;

    // Session ID groups messages into a conversation
    // Client generates this UUID and sends it with every message
    // in the same conversation
    private String sessionId;
}