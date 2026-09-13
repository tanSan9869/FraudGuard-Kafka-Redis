package com.fraudguard.service;

import com.fraudguard.config.KafkaConfig;
import com.fraudguard.dto.MlScoreRequest;
import com.fraudguard.dto.MlScoreResponse;
import com.fraudguard.dto.TransactionEvent;
import com.fraudguard.dto.TransactionResponseDTO;
import com.fraudguard.entity.Transaction;
import com.fraudguard.entity.TransactionStatus;
import com.fraudguard.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionCacheService cacheService;
    private final MlServiceClient mlServiceClient;

    @KafkaListener(topics = KafkaConfig.TOPIC_RAW, groupId = "fraudguard-transaction-processor")
    @Transactional
    public void consumeRawTransaction(TransactionEvent event, Acknowledgment acknowledgment) {
        try {
            if (event == null || event.getTransaction() == null || event.getTransaction().getId() == null) {
                log.error("Skipping malformed transaction event: {}", event);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Received raw transaction event: {}, TraceId: {}", event.getEventId(), event.getTraceId());
            // Check for Idempotency
            Optional<Transaction> existingTransaction = transactionRepository.findById(event.getTransaction().getId());
            if (existingTransaction.isPresent()) {
                Transaction transaction = existingTransaction.get();
                if (event.getEventId().equals(transaction.getProcessedEventId())) {
                    log.warn("Duplicate processing detected for event: {}. Skipping...", event.getEventId());
                    acknowledgment.acknowledge();
                    return;
                }

                // Score the transaction using the ML Service
                MlScoreRequest scoreRequest = MlScoreRequest.builder()
                        .id(transaction.getId())
                        .accountId(transaction.getAccountId())
                        .amount(transaction.getAmount())
                        .currency(transaction.getCurrency())
                        .merchant(transaction.getMerchant())
                        .transactionType(transaction.getTransactionType().name())
                        .createdAt(transaction.getCreatedAt())
                        .build();

                MlScoreResponse scoreResponse = mlServiceClient.scoreTransaction(scoreRequest);

                if (scoreResponse != null) {
                    transaction.setAnomalyScore(scoreResponse.getAnomalyScore());
                    transaction.setModelVersion(scoreResponse.getModelVersion());
                    if (Boolean.TRUE.equals(scoreResponse.getIsAnomalous())) {
                        transaction.setStatus(TransactionStatus.FLAGGED);
                    } else {
                        transaction.setStatus(TransactionStatus.APPROVED);
                    }
                } else {
                    // Fallback policy: FLAGGED
                    log.warn("ML Service unavailable or failed for transaction: {}. Falling back to FLAGGED status.", transaction.getId());
                    transaction.setStatus(TransactionStatus.FLAGGED);
                }
                transaction.setProcessedEventId(event.getEventId());
                transactionRepository.save(transaction);

                // Publish to processed topic
                TransactionEvent processedEvent = TransactionEvent.builder()
                        .eventId(UUID.randomUUID())
                        .eventType("TRANSACTION_PROCESSED")
                        .transaction(toResponseDto(transaction))
                        .timestamp(ZonedDateTime.now())
                        .traceId(event.getTraceId())
                        .build();

                kafkaTemplate.send(KafkaConfig.TOPIC_PROCESSED, transaction.getAccountId(), processedEvent);
                
                cacheService.evictTransaction(transaction.getId());
                cacheService.evictAccountTransactions(transaction.getAccountId());
                
                log.info("Successfully processed transaction {} and published to {}", transaction.getId(), KafkaConfig.TOPIC_PROCESSED);
            } else {
                log.error("Transaction not found in DB for id: {}", event.getTransaction().getId());
                throw new IllegalStateException("Transaction missing in database for id: " + event.getTransaction().getId());
            }

            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing transaction event: {}", event.getEventId(), ex);
            throw ex; // Re-throw to trigger retry/DLQ mechanism
        }
    }

    private static TransactionResponseDTO toResponseDto(Transaction transaction) {
        return TransactionResponseDTO.builder()
                .id(transaction.getId())
                .accountId(transaction.getAccountId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .merchant(transaction.getMerchant())
                .transactionType(transaction.getTransactionType())
                .status(transaction.getStatus())
                .anomalyScore(transaction.getAnomalyScore())
                .modelVersion(transaction.getModelVersion())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}
