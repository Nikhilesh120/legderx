package com.ledgerxlite.repository;

import com.ledgerxlite.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for Wallet entity.
 * 
 * Provides data access operations for wallet management with proper locking
 * to prevent concurrent balance corruption.
 * 
 * Custom Queries Explained:
 * 
 * 1. findByUserId(Long userId)
 *    WHY: One-to-one User-Wallet relationship requires efficient wallet lookup by user.
 *    USAGE: Retrieve user's wallet for balance display, transaction processing.
 *    PERFORMANCE: Backed by unique index (idx_wallets_user_id) for O(log n) lookup.
 *    LOCKING: No lock - use for read-only operations (balance display).
 * 
 * 2. findByIdForUpdate(Long walletId)
 *    WHY: CRITICAL for financial safety - prevents concurrent balance updates.
 *    USAGE: Called before any balance modification (credit/debit operations).
 *    LOCKING: PESSIMISTIC_WRITE - database-level row lock until transaction commits.
 *    PREVENTS: Lost updates, race conditions, balance corruption.
 *    EXAMPLE SCENARIO:
 *      - Transaction A locks wallet (balance = $100)
 *      - Transaction B attempts to lock same wallet → WAITS
 *      - Transaction A debits $50 (balance = $50) → COMMITS
 *      - Transaction B acquires lock and sees updated balance ($50)
 *      - Without lock: Both could debit from $100, causing lost update
 * 
 * Financial Safety Guarantees:
 * - Pessimistic locking ensures serialized balance updates
 * - Combined with @Version in Wallet entity for double protection
 * - Service layer MUST use findByIdForUpdate before any credit/debit
 * 
 * Design Notes:
 * - No @Transactional here (service layer manages transaction boundaries)
 * - Lock is acquired when query executes and released on transaction commit/rollback
 * - Lock timeout is database-dependent (usually 30-60 seconds)
 */
@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /**
     * Find wallet by user ID (read-only, no lock).
     * 
     * Used for:
     * - Displaying wallet balance to user
     * - Read-only operations
     * 
     * @param userId the user's ID
     * @return Optional containing Wallet if found, empty otherwise
     */
    Optional<Wallet> findByUserId(Long userId);

    /**
     * Find wallet by ID with PESSIMISTIC_WRITE lock.
     * 
     * CRITICAL: This method MUST be used before any balance modification.
     * 
     * How it works:
     * 1. Acquires database row lock (SELECT ... FOR UPDATE)
     * 2. Other transactions attempting to lock same wallet will WAIT
     * 3. Lock is released when transaction commits or rolls back
     * 
     * Used for:
     * - Credit operations (deposits, transfers in)
     * - Debit operations (withdrawals, transfers out, fees)
     * - Any operation that modifies wallet balance
     * 
     * Example SQL generated:
     * SELECT * FROM wallets WHERE id = ? FOR UPDATE
     * 
     * @param walletId the wallet's ID
     * @return Optional containing locked Wallet if found, empty otherwise
     * @throws jakarta.persistence.PessimisticLockException if lock cannot be acquired
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :walletId")
    Optional<Wallet> findByIdForUpdate(@Param("walletId") Long walletId);

    /**
     * Check if a wallet exists for a given user.
     * 
     * Used for:
     * - Validating user has a wallet before operations
     * - Preventing duplicate wallet creation
     * 
     * @param userId the user's ID
     * @return true if wallet exists, false otherwise
     */
    boolean existsByUserId(Long userId);
}
