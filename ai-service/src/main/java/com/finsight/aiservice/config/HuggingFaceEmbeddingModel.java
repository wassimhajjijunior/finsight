package com.finsight.aiservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring AI HuggingFace starter (2.0.0-M2) has no EmbeddingModel.
 * This calls the HF Inference API directly for embedding generation.
 */
@Component
public class HuggingFaceEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceEmbeddingModel.class);

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;

    public HuggingFaceEmbeddingModel(
            @Value("${spring.ai.huggingface.base-url}") String baseUrl,
            @Value("${spring.ai.huggingface.api-key}") String apiKey,
            @Value("${spring.ai.huggingface.embedding.options.model}") String model) {
        this.restTemplate = new RestTemplate();
        this.apiUrl = baseUrl + "/models/" + model;
        this.apiKey = apiKey;
        log.info("HuggingFace embedding model: model={} url={}", model, apiUrl);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        List<float[]> vectors = callHuggingFaceApi(texts);

        List<Embedding> embeddingList = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            embeddingList.add(new Embedding(vectors.get(i), i));
        }
        return new EmbeddingResponse(embeddingList);
    }

    @Override
    public float[] embed(Document document) {
        return callHuggingFaceApi(List.of(document.getText())).get(0);
    }

    @Override
    public float[] embed(String text) {
        return callHuggingFaceApi(List.of(text)).get(0);
    }

    private List<float[]> callHuggingFaceApi(List<String> texts) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("inputs", texts), headers);

        try {
            ResponseEntity<float[][]> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, float[][].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("HF embedding: {} texts → {} vectors",
                        texts.size(), response.getBody().length);
                return List.of(response.getBody());
            }
            throw new RuntimeException("HuggingFace API returned empty response");
        } catch (Exception e) {
            log.error("HF embedding failed for {} texts", texts.size(), e);
            throw new RuntimeException("HF embedding failed: " + e.getMessage(), e);
        }
    }
}
