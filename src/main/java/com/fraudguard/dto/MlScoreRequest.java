package com.fraudguard.dto;

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
public class MlScoreRequest {
    private UUID id;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String merchant;
    private String transactionType;
    private ZonedDateTime createdAt;
}
