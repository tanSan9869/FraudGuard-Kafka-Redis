package com.fraudguard.dto;

import com.fraudguard.entity.TransactionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransactionStatusUpdateDTO {

    @NotNull(message = "Status is required")
    private TransactionStatus status;
}
