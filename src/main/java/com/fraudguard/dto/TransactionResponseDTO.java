package com.fraudguard.dto;

import com.fraudguard.entity.TransactionStatus;
import com.fraudguard.entity.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponseDTO {
    private UUID id;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String merchant;
    private TransactionType transactionType;
    private TransactionStatus status;
    private Double anomalyScore;
    private String modelVersion;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
