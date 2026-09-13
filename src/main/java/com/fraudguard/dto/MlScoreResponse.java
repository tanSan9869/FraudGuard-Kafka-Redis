package com.fraudguard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlScoreResponse {
    private UUID transactionId;
    private Double anomalyScore;
    private Boolean isAnomalous;
    private Double threshold;
    private String modelVersion;
}
