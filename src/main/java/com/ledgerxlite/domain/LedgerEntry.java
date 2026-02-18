package com.ledgerxlite.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * LedgerEntry entity - the source of truth for all financial movements.
 * 
 * CRITICAL FINANCIAL RULES (NON-NEGOTIABLE):
 * 1. Entries are IMMUTABLE - once created, never modified
 * 2. Entries are APPEND-ONLY - never deleted
 * 3. All fields are final after construction
 * 4. No setters exist (except for JPA's internal use)
 * 5. Positive amount = CREDIT (money in)
 * 6. Negative amount = DEBIT (money out)
 * 
 * Design decisions:
 * - referenceId provides idempotency (prevents duplicate transactions)
 * - EntryType categorizes the transaction for reporting
 * - amount uses BigDecimal with 19 digits precision, 4 decimal places
 * - Indexed on wallet_id and created_at for efficient queries
 * - Indexed on reference_id for idempotency checks
 * - No @Version needed - immutable entities don't need optimistic locking
 */
@Entity
@Table(
    name = "ledger_entries",
    indexes = {
        @Index(name = "idx_ledger_wallet_id", columnList = "wallet_id"),
        @Index(name = "idx_ledger_created_at", columnList = "created_at"),
        @Index(name = "idx_ledger_reference_id", columnList = "reference_id", unique = true),
        @Index(name = "idx_ledger_type", columnList = "type"),
        @Index(name = "idx_ledger_wallet_created", columnList = "wallet_id,created_at")
    }
)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    /**
     * Transaction amount.
     * Positive = CREDIT (incoming funds)
     * Negative = DEBIT (outgoing funds)
     * 
     * Precision: 19 total digits, 4 decimal places
     */
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    /**
     * Type of ledger entry for categorization and reporting.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private EntryType type;

    /**
     * Idempotency key to prevent duplicate transactions.
     * 
     * This should be a unique identifier provided by the client
     * (e.g., UUID, transaction ID from external system).
     * 
     * If a transaction with the same referenceId is submitted twice,
     * the second attempt will fail with a unique constraint violation,
     * ensuring exactly-once semantics.
     */
    @Column(name = "reference_id", nullable = false, unique = true, length = 255, updatable = false)
    private String referenceId;

    /**
     * Optional description/memo for the transaction.
     */
    @Column(length = 500, updatable = false)
    private String description;

    /**
     * Timestamp when this entry was created.
     * Immutable - represents the ledger entry creation time.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Entry type enumeration.
     * Categorizes transactions for reporting and reconciliation.
     */
    public enum EntryType {
        DEPOSIT,           // External funds coming in
        WITHDRAWAL,        // External funds going out
        TRANSFER_IN,       // Internal transfer - receiving side
        TRANSFER_OUT,      // Internal transfer - sending side
        FEE,              // Service fee deduction
        REFUND,           // Refund of a previous transaction
        ADJUSTMENT        // Manual correction (should be rare)
    }

    /**
     * JPA requires a no-arg constructor.
     * Private to prevent external instantiation.
     */
    private LedgerEntry() {
    }

    /**
     * Create a new ledger entry.
     * 
     * This is the ONLY way to create a ledger entry.
     * All fields are set at construction and become immutable.
     * 
     * @param wallet the wallet this entry belongs to
     * @param amount transaction amount (positive=credit, negative=debit)
     * @param type entry type classification
     * @param referenceId unique idempotency key
     * @param description optional transaction description
     */
    public LedgerEntry(Wallet wallet, BigDecimal amount, EntryType type, String referenceId, String description) {
        if (wallet == null) {
            throw new IllegalArgumentException("Wallet cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Amount cannot be null or zero");
        }
        if (type == null) {
            throw new IllegalArgumentException("Entry type cannot be null");
        }
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Reference ID cannot be null or blank");
        }

        this.wallet = wallet;
        this.amount = amount;
        this.type = type;
        this.referenceId = referenceId;
        this.description = description;
        this.createdAt = Instant.now();
    }

    /**
     * Convenience constructor for entries without description.
     */
    public LedgerEntry(Wallet wallet, BigDecimal amount, EntryType type, String referenceId) {
        this(wallet, amount, type, referenceId, null);
    }

    // Getters only - no setters (immutability)

    public Long getId() {
        return id;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public EntryType getType() {
        return type;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Convenience methods

    /**
     * Check if this entry is a credit (positive amount).
     */
    public boolean isCredit() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if this entry is a debit (negative amount).
     */
    public boolean isDebit() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Get the absolute value of the amount.
     * Useful for display purposes.
     */
    public BigDecimal getAbsoluteAmount() {
        return amount.abs();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LedgerEntry that = (LedgerEntry) o;
        return Objects.equals(referenceId, that.referenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referenceId);
    }

    @Override
    public String toString() {
        return "LedgerEntry{" +
                "id=" + id +
                ", walletId=" + (wallet != null ? wallet.getId() : null) +
                ", amount=" + amount +
                ", type=" + type +
                ", referenceId='" + referenceId + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
