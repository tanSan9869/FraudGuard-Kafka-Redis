package com.fraudguard.service;

import com.fraudguard.config.KafkaConfig;
import com.fraudguard.dto.MlScoreRequest;
import com.fraudguard.dto.MlScoreResponse;
import com.fraudguard.dto.TransactionEvent;
import com.fraudguard.dto.TransactionResponseDTO;
import com.fraudguard.entity.Transaction;
import com.fraudguard.entity.TransactionStatus;
import com.fraudguard.entity.TransactionType;
import com.fraudguard.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private TransactionCacheService cacheService;

    @Mock
    private MlServiceClient mlServiceClient;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private TransactionEventConsumer consumer;

    private Transaction transaction;
    private TransactionEvent event;

    @BeforeEach
    void setUp() {
        transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .accountId("ACC123")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .transactionType(TransactionType.PURCHASE)
                .status(TransactionStatus.PENDING)
                .createdAt(ZonedDateTime.now())
                .build();

        event = TransactionEvent.builder()
                .eventId(UUID.randomUUID())
                .transaction(TransactionResponseDTO.builder()
                        .id(transaction.getId())
                        .accountId(transaction.getAccountId())
                        .amount(transaction.getAmount())
                        .currency(transaction.getCurrency())
                        .transactionType(transaction.getTransactionType())
                        .status(transaction.getStatus())
                        .createdAt(transaction.getCreatedAt())
                        .build())
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    @Test
    void testConsumeRawTransaction_Approved() {
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        
        MlScoreResponse scoreResponse = MlScoreResponse.builder()
                .transactionId(transaction.getId())
                .anomalyScore(0.1)
                .isAnomalous(false)
                .modelVersion("v1")
                .build();
        
        when(mlServiceClient.scoreTransaction(any(MlScoreRequest.class))).thenReturn(scoreResponse);

        consumer.consumeRawTransaction(event, acknowledgment);

        assertEquals(TransactionStatus.APPROVED, transaction.getStatus());
        assertEquals(0.1, transaction.getAnomalyScore());
        
        verify(transactionRepository).save(transaction);
        verify(kafkaTemplate).send(eq(KafkaConfig.TOPIC_PROCESSED), eq(transaction.getAccountId()), any(TransactionEvent.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testConsumeRawTransaction_Flagged() {
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        
        MlScoreResponse scoreResponse = MlScoreResponse.builder()
                .transactionId(transaction.getId())
                .anomalyScore(0.9)
                .isAnomalous(true)
                .modelVersion("v1")
                .build();
        
        when(mlServiceClient.scoreTransaction(any(MlScoreRequest.class))).thenReturn(scoreResponse);

        consumer.consumeRawTransaction(event, acknowledgment);

        assertEquals(TransactionStatus.FLAGGED, transaction.getStatus());
        verify(transactionRepository).save(transaction);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testConsumeRawTransaction_Fallback() {
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        
        when(mlServiceClient.scoreTransaction(any(MlScoreRequest.class))).thenReturn(null);

        consumer.consumeRawTransaction(event, acknowledgment);

        assertEquals(TransactionStatus.FLAGGED, transaction.getStatus());
        verify(transactionRepository).save(transaction);
        verify(acknowledgment).acknowledge();
    }
}
