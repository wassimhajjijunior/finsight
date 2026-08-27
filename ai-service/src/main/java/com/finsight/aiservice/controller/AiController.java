package com.finsight.aiservice.controller;

import com.finsight.aiservice.dto.ChatRequest;
import com.finsight.aiservice.dto.ChatResponse;
import com.finsight.aiservice.service.RagService;
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

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ChatRequest request) {

        return ResponseEntity.ok(
                ragService.chat(userId.toString(), request));
    }
}