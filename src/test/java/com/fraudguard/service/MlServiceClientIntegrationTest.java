package com.fraudguard.service;

import com.fraudguard.dto.MlScoreRequest;
import com.fraudguard.dto.MlScoreResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MlServiceClientIntegrationTest {

    @Autowired
    private MlServiceClient mlServiceClient;

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("ml.service.url", () -> wireMockServer.baseUrl() + "/score");
    }

    @Test
    void testScoreTransaction_Success() {
        stubFor(post(urlEqualTo("/score"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "transactionId": "123e4567-e89b-12d3-a456-426614174000",
                                  "anomalyScore": 0.85,
                                  "isAnomalous": true,
                                  "threshold": 0.65,
                                  "modelVersion": "isoforest-v1"
                                }
                                """)
                        .withStatus(200)));

        MlScoreRequest request = MlScoreRequest.builder()
                .id(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
                .accountId("ACC123")
                .amount(new BigDecimal("5000.00"))
                .currency("USD")
                .transactionType("TRANSFER")
                .build();

        MlScoreResponse response = mlServiceClient.scoreTransaction(request);

        assertNotNull(response);
        assertEquals(0.85, response.getAnomalyScore());
        assertTrue(response.getIsAnomalous());
        assertEquals("isoforest-v1", response.getModelVersion());
    }

    @Test
    void testScoreTransaction_TimeoutFallback() {
        stubFor(post(urlEqualTo("/score"))
                .willReturn(aResponse()
                        .withFixedDelay(4000) // 4 seconds delay, client timeout is 3s
                        .withStatus(200)));

        MlScoreRequest request = MlScoreRequest.builder()
                .id(UUID.randomUUID())
                .accountId("ACC123")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .transactionType("DEPOSIT")
                .build();

        MlScoreResponse response = mlServiceClient.scoreTransaction(request);

        assertNull(response); // Expected fallback behavior returning null
    }
}
