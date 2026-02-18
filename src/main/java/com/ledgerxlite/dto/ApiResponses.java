package com.ledgerxlite.dto;

import com.ledgerxlite.domain.User;
import com.ledgerxlite.domain.Wallet;
import com.ledgerxlite.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTOs for API endpoints.
 */
public class ApiResponses {

    /**
     * User registration response.
     */
    public static class UserResponse {
        private Long userId;
        private String email;
        private String status;
        private Long walletId;
        private BigDecimal balance;
        private String currency;
        private Instant createdAt;

        public UserResponse(User user, Wallet wallet) {
            this.userId = user.getId();
            this.email = user.getEmail();
            this.status = user.getStatus().toString();
            this.walletId = wallet.getId();
            this.balance = wallet.getBalance();
            this.currency = wallet.getCurrency();
            this.createdAt = user.getCreatedAt();
        }

        // Getters
        public Long getUserId() { return userId; }
        public String getEmail() { return email; }
        public String getStatus() { return status; }
        public Long getWalletId() { return walletId; }
        public BigDecimal getBalance() { return balance; }
        public String getCurrency() { return currency; }
        public Instant getCreatedAt() { return createdAt; }
    }

    /**
     * Wallet balance response.
     */
    public static class WalletResponse {
        private Long walletId;
        private Long userId;
        private BigDecimal balance;
        private String currency;
        private Instant updatedAt;

        public WalletResponse(Wallet wallet) {
            this.walletId = wallet.getId();
            this.userId = wallet.getUser().getId();
            this.balance = wallet.getBalance();
            this.currency = wallet.getCurrency();
            this.updatedAt = wallet.getUpdatedAt();
        }

        // Getters
        public Long getWalletId() { return walletId; }
        public Long getUserId() { return userId; }
        public BigDecimal getBalance() { return balance; }
        public String getCurrency() { return currency; }
        public Instant getUpdatedAt() { return updatedAt; }
    }

    /**
     * Transaction response.
     */
    public static class TransactionResponse {
        private Long transactionId;
        private Long walletId;
        private BigDecimal amount;
        private String type;
        private String referenceId;
        private String description;
        private Instant createdAt;

        public TransactionResponse(LedgerEntry entry) {
            this.transactionId = entry.getId();
            this.walletId = entry.getWallet().getId();
            this.amount = entry.getAmount();
            this.type = entry.getType().toString();
            this.referenceId = entry.getReferenceId();
            this.description = entry.getDescription();
            this.createdAt = entry.getCreatedAt();
        }

        // Getters
        public Long getTransactionId() { return transactionId; }
        public Long getWalletId() { return walletId; }
        public BigDecimal getAmount() { return amount; }
        public String getType() { return type; }
        public String getReferenceId() { return referenceId; }
        public String getDescription() { return description; }
        public Instant getCreatedAt() { return createdAt; }
    }

    /**
     * Transfer response.
     */
    public static class TransferResponse {
        private TransactionResponse debit;
        private TransactionResponse credit;
        private BigDecimal amount;
        private String referenceId;

        public TransferResponse(LedgerEntry debitEntry, LedgerEntry creditEntry) {
            this.debit = new TransactionResponse(debitEntry);
            this.credit = new TransactionResponse(creditEntry);
            this.amount = creditEntry.getAmount();
            this.referenceId = debitEntry.getReferenceId().replace("-OUT", "");
        }

        // Getters
        public TransactionResponse getDebit() { return debit; }
        public TransactionResponse getCredit() { return credit; }
        public BigDecimal getAmount() { return amount; }
        public String getReferenceId() { return referenceId; }
    }

    /**
     * Error response.
     */
    public static class ErrorResponse {
        private String error;
        private String message;
        private Instant timestamp;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
            this.timestamp = Instant.now();
        }

        // Getters
        public String getError() { return error; }
        public String getMessage() { return message; }
        public Instant getTimestamp() { return timestamp; }
    }
}
