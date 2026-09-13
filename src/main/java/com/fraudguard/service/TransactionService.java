package com.fraudguard.service;

import com.fraudguard.dto.TransactionRequestDTO;
import com.fraudguard.dto.TransactionResponseDTO;
import com.fraudguard.dto.TransactionStatusUpdateDTO;
import com.fraudguard.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TransactionService {
    TransactionResponseDTO createTransaction(TransactionRequestDTO requestDTO);
    TransactionResponseDTO getTransactionById(UUID id);
    Page<TransactionResponseDTO> getTransactions(String accountId, TransactionStatus status, Pageable pageable);
    Page<TransactionResponseDTO> getTransactionsByAccountId(String accountId, Pageable pageable);
    TransactionResponseDTO updateTransactionStatus(UUID id, TransactionStatusUpdateDTO statusUpdateDTO);
}
