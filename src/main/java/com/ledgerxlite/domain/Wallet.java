package com.ledgerxlite.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Wallet entity representing a user's financial account.
 * 
 * Critical financial rules:
 * - Balance is calculated from ledger entries (eventual consistency)
 * - Balance has NO public setter (modified only through business methods)
 * - One User → One Wallet relationship (1:1)
 * - BigDecimal used for all monetary values (never float/double)
 * - updatedAt tracks last balance modification
 * 
 * Design decisions:
 * - No cascading operations to prevent accidental deletions
 * - Balance precision: 19 digits, 4 decimal places (supports crypto and fiat)
 * - Currency stored as ISO 4217 code (USD, EUR, BTC, etc.)
 * - Optimistic locking via @Version to prevent concurrent balance corruption
 */
@Entity
@Table(
    name = "wallets",
    indexes = {
        @Index(name = "idx_wallets_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_wallets_currency", columnList = "currency")
    }
)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Current balance.
     * CRITICAL: This is a cached/denormalized value.
     * Source of truth is the sum of all LedgerEntry records.
     * 
     * Precision: 19 total digits, 4 decimal places
     * Supports values from -999,999,999,999,999.9999 to +999,999,999,999,999.9999
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    /**
     * Currency code (ISO 4217).
     * Examples: USD, EUR, GBP, BTC, ETH
     */
    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Version for optimistic locking.
     * Prevents concurrent balance updates from corrupting wallet state.
     * JPA automatically increments this on each update.
     */
    @Version
    private Long version;

    /**
     * JPA requires a no-arg constructor.
     */
    protected Wallet() {
    }

    /**
     * Create a new wallet for a user.
     * Initial balance is zero.
     * 
     * INVARIANT-7: Currency is immutable after creation.
     * 
     * @param user the wallet owner
     * @param currency ISO 4217 currency code (must not be null or blank)
     * @throws IllegalArgumentException if currency is invalid
     */
    public Wallet(User user, String currency) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(
                "INVARIANT-7 VIOLATION: Currency must be specified and cannot change"
            );
        }
        // Basic validation for currency format (3-10 chars, uppercase recommended)
        if (currency.length() < 3 || currency.length() > 10) {
            throw new IllegalArgumentException(
                "INVARIANT-7 VIOLATION: Currency code must be 3-10 characters (ISO 4217)"
            );
        }
        
        this.user = user;
        this.currency = currency.toUpperCase(); // Normalize to uppercase
        this.balance = BigDecimal.ZERO;
        this.updatedAt = Instant.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    /**
     * Get current balance.
     * WARNING: This is a cached value. For critical operations,
     * always verify against ledger entries.
     */
    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    // Business methods

    /**
     * Credit the wallet (add funds).
     * 
     * This should ONLY be called after a LedgerEntry is successfully created.
     * The entry creation and balance update must happen in the same transaction.
     * 
     * @param amount must be positive
     * @throws IllegalArgumentException if amount is negative or zero
     */
    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance = this.balance.add(amount);
        this.updatedAt = Instant.now();
    }

    /**
     * Debit the wallet (remove funds).
     * 
     * This should ONLY be called after a LedgerEntry is successfully created.
     * The entry creation and balance update must happen in the same transaction.
     * 
     * @param amount must be positive
     * @throws IllegalArgumentException if amount is negative or zero
     * @throws IllegalStateException if insufficient balance
     */
    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        
        BigDecimal newBalance = this.balance.subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        
        this.balance = newBalance;
        this.updatedAt = Instant.now();
    }

    /**
     * Check if wallet has sufficient funds.
     * 
     * @param amount amount to check
     * @return true if balance >= amount
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.balance.compareTo(amount) >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Wallet wallet = (Wallet) o;
        return Objects.equals(id, wallet.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Wallet{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", balance=" + balance +
                ", currency='" + currency + '\'' +
                ", updatedAt=" + updatedAt +
                ", version=" + version +
                '}';
    }
}
