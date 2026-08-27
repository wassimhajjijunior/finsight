package com.finsight.aiservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatResponse {

    private String answer;
    private String sessionId;
    private List<String> sourcesUsed;   // which transactions were retrieved
    private boolean usedLiveData;       // true if tool calling fetched live data
}