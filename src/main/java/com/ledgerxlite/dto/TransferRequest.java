package com.ledgerxlite.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO for transfer requests between wallets.
 */
public class TransferRequest {

    @NotNull(message = "Destination wallet ID is required")
    private Long toWalletId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;

    @NotBlank(message = "Reference ID is required")
    private String referenceId;

    private String description;

    // Constructors
    public TransferRequest() {
    }

    public TransferRequest(Long toWalletId, BigDecimal amount, String referenceId, String description) {
        this.toWalletId = toWalletId;
        this.amount = amount;
        this.referenceId = referenceId;
        this.description = description;
    }

    // Getters and Setters
    public Long getToWalletId() {
        return toWalletId;
    }

    public void setToWalletId(Long toWalletId) {
        this.toWalletId = toWalletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
