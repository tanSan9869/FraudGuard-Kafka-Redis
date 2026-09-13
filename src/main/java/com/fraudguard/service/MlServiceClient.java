package com.fraudguard.service;

import com.fraudguard.dto.MlScoreRequest;
import com.fraudguard.dto.MlScoreResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Service
public class MlServiceClient {

    private final RestClient restClient;
    private final String mlServiceUrl;

    public MlServiceClient(@Value("${ml.service.url:http://localhost:8000/score}") String mlServiceUrl) {
        this.mlServiceUrl = mlServiceUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public MlScoreResponse scoreTransaction(MlScoreRequest request) {
        try {
            log.debug("Calling ML service for transaction: {}", request.getId());
            return restClient.post()
                    .uri(mlServiceUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MlScoreResponse.class);
        } catch (Exception ex) {
            log.error("Failed to get score from ML service for transaction: {}", request.getId(), ex);
            return null; // Fallback handled by caller
        }
    }
}
