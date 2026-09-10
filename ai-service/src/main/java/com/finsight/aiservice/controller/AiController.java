package com.finsight.aiservice.controller;

import com.finsight.aiservice.dto.ChatRequest;
import com.finsight.aiservice.dto.ChatResponse;
import com.finsight.aiservice.service.RagService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final RagService ragService;
    private final Tracer tracer;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ChatRequest request) {

        Span span = tracer.nextSpan()
                .name("ai-chat-rag")
                .tag("userId", userId.toString())
                .tag("message", request.getMessage() != null ? request.getMessage() : "unknown")
                .start();

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            return ResponseEntity.ok(
                    ragService.chat(userId.toString(), request));
        } finally {
            span.end();
        }
    }
}