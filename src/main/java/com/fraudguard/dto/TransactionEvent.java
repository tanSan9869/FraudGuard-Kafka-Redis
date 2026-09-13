package com.fraudguard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    private UUID eventId;
    private String eventType;
    private TransactionResponseDTO transaction;
    private ZonedDateTime timestamp;
    private String traceId;
}
