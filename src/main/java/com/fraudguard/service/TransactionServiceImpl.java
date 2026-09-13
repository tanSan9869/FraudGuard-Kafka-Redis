package com.fraudguard.service;

import com.fraudguard.dto.TransactionRequestDTO;
import com.fraudguard.dto.TransactionResponseDTO;
import com.fraudguard.dto.TransactionStatusUpdateDTO;
import com.fraudguard.entity.Transaction;
import com.fraudguard.entity.TransactionStatus;
import com.fraudguard.exception.ResourceNotFoundException;
import com.fraudguard.repository.TransactionRepository;
import com.fraudguard.config.KafkaConfig;
import com.fraudguard.dto.TransactionEvent;
import org.springframework.kafka.core.KafkaTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionCacheService cacheService;

    @Override
    @Transactional
    public TransactionResponseDTO createTransaction(TransactionRequestDTO requestDTO) {
        Transaction transaction = new Transaction();
        transaction.setAccountId(requestDTO.getAccountId());
        transaction.setAmount(requestDTO.getAmount());
        transaction.setCurrency(requestDTO.getCurrency());
        transaction.setMerchant(requestDTO.getMerchant());
        transaction.setTransactionType(requestDTO.getTransactionType());
        transaction.setStatus(TransactionStatus.PENDING); // Default status

        Transaction savedTransaction = transactionRepository.save(transaction);
        TransactionResponseDTO responseDTO = mapToDTO(savedTransaction);
        
        String traceId = UUID.randomUUID().toString();
        TransactionEvent event = TransactionEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("TRANSACTION_CREATED")
                .transaction(responseDTO)
                .timestamp(ZonedDateTime.now())
                .traceId(traceId)
                .build();
                
        kafkaTemplate.send(KafkaConfig.TOPIC_RAW, transaction.getAccountId(), event);
        cacheService.evictAccountTransactions(savedTransaction.getAccountId());

        return responseDTO;
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponseDTO getTransactionById(UUID id) {
        TransactionResponseDTO cached = cacheService.getTransaction(id);
        if (cached != null && cached.getStatus() != TransactionStatus.PENDING) {
            return cached;
        }

        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        TransactionResponseDTO dto = mapToDTO(transaction);
        if (dto.getStatus() != TransactionStatus.PENDING) {
            cacheService.putTransaction(dto);
        }
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponseDTO> getTransactions(String accountId, TransactionStatus status, Pageable pageable) {
        boolean cacheable = accountId != null && status == null && pageable.getPageNumber() == 0;
        if (cacheable) {
            Page<TransactionResponseDTO> cached = cacheService.getAccountTransactions(accountId);
            if (cached != null) {
                return cached;
            }
        }

        Page<Transaction> transactions;
        if (accountId != null && status != null) {
            transactions = transactionRepository.findByAccountIdAndStatus(accountId, status, pageable);
        } else if (accountId != null) {
            transactions = transactionRepository.findByAccountId(accountId, pageable);
        } else if (status != null) {
            transactions = transactionRepository.findByStatus(status, pageable);
        } else {
            transactions = transactionRepository.findAll(pageable);
        }
        Page<TransactionResponseDTO> page = transactions.map(this::mapToDTO);
        if (cacheable) {
            cacheService.putAccountTransactions(accountId, page);
        }
        return page;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponseDTO> getTransactionsByAccountId(String accountId, Pageable pageable) {
        if (pageable.getPageNumber() == 0) {
            Page<TransactionResponseDTO> cached = cacheService.getAccountTransactions(accountId);
            if (cached != null) {
                return cached;
            }
        }
        
        Page<TransactionResponseDTO> page = transactionRepository.findByAccountId(accountId, pageable).map(this::mapToDTO);
        
        if (pageable.getPageNumber() == 0) {
            cacheService.putAccountTransactions(accountId, page);
        }
        return page;
    }

    @Override
    @Transactional
    public TransactionResponseDTO updateTransactionStatus(UUID id, TransactionStatusUpdateDTO statusUpdateDTO) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        
        transaction.setStatus(statusUpdateDTO.getStatus());
        Transaction updatedTransaction = transactionRepository.save(transaction);
        
        TransactionResponseDTO dto = mapToDTO(updatedTransaction);
        cacheService.evictTransaction(id);
        cacheService.evictAccountTransactions(updatedTransaction.getAccountId());
        
        return dto;
    }

    private TransactionResponseDTO mapToDTO(Transaction transaction) {
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
