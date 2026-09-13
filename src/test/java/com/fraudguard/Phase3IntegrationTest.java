package com.fraudguard;

import tools.jackson.databind.ObjectMapper;
import com.fraudguard.dto.TransactionRequestDTO;
import com.fraudguard.dto.TransactionResponseDTO;
import com.fraudguard.entity.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public class Phase3IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    public void testRateLimiterAndCaching() throws Exception {
        // 1. Create a transaction
        TransactionRequestDTO req = new TransactionRequestDTO();
        req.setAccountId("TEST-ACC-1");
        req.setAmount(new BigDecimal("100.00"));
        req.setCurrency("USD");
        req.setMerchant("Test Merchant");
        req.setTransactionType(TransactionType.PURCHASE);

        MvcResult result = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andReturn();

        TransactionResponseDTO created = objectMapper.readValue(result.getResponse().getContentAsString(), TransactionResponseDTO.class);
        assertNotNull(created.getId());

        // 2. Fetch the transaction (should hit DB, populate cache)
        mockMvc.perform(get("/api/v1/transactions/" + created.getId()))
                .andExpect(status().isOk());

        // Verify it is in cache
        Object cached = redisTemplate.opsForValue().get("tx:" + created.getId());
        assertNotNull(cached, "Transaction should be in cache");

        // 3. Test Rate Limiter by making > 100 requests (we can simulate by mocking or just hammering it if it's fast)
        // Since limit is 100, hammering 101 requests might take a few seconds. We'll do it.
        boolean rateLimited = false;
        for (int i = 0; i < 110; i++) {
            int status = mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andReturn().getResponse().getStatus();
            if (status == 429) {
                rateLimited = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(rateLimited, "Should have hit rate limit (429)");
    }
}
