package com.fraudguard.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.fraudguard.dto.TransactionResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String TX_KEY_PREFIX = "tx:";
    private static final String ACCT_KEY_PREFIX = "acct:txs:";
    private static final Duration TTL = Duration.ofMinutes(5);

    public TransactionResponseDTO getTransaction(UUID id) {
        try {
            Object cached = redisTemplate.opsForValue().get(TX_KEY_PREFIX + id);
            if (cached instanceof TransactionResponseDTO dto) {
                return dto;
            }
            if (cached != null) {
                return objectMapper.convertValue(cached, TransactionResponseDTO.class);
            }
        } catch (Exception e) {
            log.warn("Redis error fetching transaction cache for id {}: {}", id, e.getMessage());
        }
        return null;
    }

    public void putTransaction(TransactionResponseDTO dto) {
        try {
            redisTemplate.opsForValue().set(TX_KEY_PREFIX + dto.getId(), dto, TTL);
        } catch (Exception e) {
            log.warn("Redis error putting transaction cache for id {}: {}", dto.getId(), e.getMessage());
        }
    }

    public void evictTransaction(UUID id) {
        try {
            redisTemplate.delete(TX_KEY_PREFIX + id);
        } catch (Exception e) {
            log.warn("Redis error evicting transaction cache for id {}: {}", id, e.getMessage());
        }
    }

    public Page<TransactionResponseDTO> getAccountTransactions(String accountId) {
        try {
            Object cached = redisTemplate.opsForValue().get(ACCT_KEY_PREFIX + accountId);
            if (cached != null) {
                List<TransactionResponseDTO> list = objectMapper.convertValue(cached, new TypeReference<List<TransactionResponseDTO>>() {});
                return new PageImpl<>(list, PageRequest.of(0, Math.max(list.size(), 1)), list.size());
            }
        } catch (Exception e) {
            log.warn("Redis error fetching account transactions cache for account {}: {}", accountId, e.getMessage());
        }
        return null;
    }

    public void putAccountTransactions(String accountId, Page<TransactionResponseDTO> page) {
        try {
            if (page.getNumber() == 0) {
                redisTemplate.opsForValue().set(ACCT_KEY_PREFIX + accountId, page.getContent(), TTL);
            }
        } catch (Exception e) {
            log.warn("Redis error putting account transactions cache for account {}: {}", accountId, e.getMessage());
        }
    }

    public void evictAccountTransactions(String accountId) {
        try {
            redisTemplate.delete(ACCT_KEY_PREFIX + accountId);
        } catch (Exception e) {
            log.warn("Redis error evicting account transactions cache for account {}: {}", accountId, e.getMessage());
        }
    }
}
