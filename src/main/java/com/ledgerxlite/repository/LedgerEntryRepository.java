package com.ledgerxlite.repository;

import com.ledgerxlite.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for LedgerEntry entity.
 * 
 * Provides data access operations for the immutable, append-only transaction ledger.
 * 
 * Custom Queries Explained:
 * 
 * 1. findByWalletIdOrderByCreatedAtAsc(Long walletId)
 *    WHY: Retrieve complete transaction history in chronological order.
 *    USAGE: Audit trails, balance reconciliation, transaction history display.
 *    PERFORMANCE: Backed by composite index (idx_ledger_wallet_created) for efficient lookup.
 *    ORDERING: ASC (oldest first) - important for replaying transactions.
 *    IMMUTABILITY: Read-only access to append-only log.
 * 
 * 2. existsByReferenceId(String referenceId)
 *    WHY: CRITICAL for idempotency - prevents duplicate transaction processing.
 *    USAGE: Check if transaction already processed before creating new entry.
 *    PERFORMANCE: Backed by unique index (idx_ledger_reference_id) for O(log n) lookup.
 *    GUARANTEES: Exactly-once semantics for financial transactions.
 * 
 *    IDEMPOTENCY FLOW:
 *      Client submits transaction with referenceId = "TXN-123"
 *      Service checks: existsByReferenceId("TXN-123")
 *      - If true: Return existing entry (transaction already processed)
 *      - If false: Create new entry
 *      - If concurrent: Database unique constraint on referenceId prevents duplicate
 * 
 * Financial Safety Guarantees:
 * - LedgerEntry is immutable (no update/delete operations allowed)
 * - save() only creates NEW entries (append-only)
 * - referenceId uniqueness enforced at database level
 * - Chronological ordering preserved for audit trails
 * 
 * Design Notes:
 * - No custom @Query needed for these (Spring Data derives from method names)
 * - No @Transactional here (service layer manages transaction boundaries)
 * - No delete operations (violates append-only rule)
 * - No update operations (violates immutability)
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * Find all ledger entries for a wallet, ordered chronologically.
     * 
     * Returns entries in creation order (oldest to newest).
     * Essential for:
     * - Displaying transaction history to users
     * - Audit trails and compliance reporting
     * - Balance reconciliation (sum of entries = wallet balance)
     * - Debugging balance discrepancies
     * 
     * Performance:
     * - Uses composite index (idx_ledger_wallet_created) 
     * - Efficient even for wallets with millions of entries
     * 
     * @param walletId the wallet's ID
     * @return List of entries in chronological order (oldest first)
     */
    List<LedgerEntry> findByWalletIdOrderByCreatedAtAsc(Long walletId);

    /**
     * Check if a ledger entry exists with the given reference ID.
     * 
     * CRITICAL for idempotency - prevents duplicate transaction processing.
     * 
     * Example usage in service layer:
     * <pre>
     * if (ledgerEntryRepository.existsByReferenceId(referenceId)) {
     *     throw new DuplicateTransactionException("Transaction already processed");
     * }
     * // Proceed to create new entry
     * </pre>
     * 
     * Guarantees:
     * - Exactly-once transaction semantics
     * - Safe retries (client can retry with same referenceId)
     * - Prevents accidental duplicate charges/credits
     * 
     * Performance:
     * - Backed by unique index (idx_ledger_reference_id)
     * - O(log n) lookup time
     * - Faster than loading full entity
     * 
     * @param referenceId the transaction reference ID (idempotency key)
     * @return true if entry exists, false otherwise
     */
    boolean existsByReferenceId(String referenceId);

    /**
     * Find a ledger entry by reference ID.
     * 
     * Used when client retries a transaction - we return the existing entry
     * instead of creating a duplicate.
     * 
     * @param referenceId the transaction reference ID
     * @return Optional containing entry if found, empty otherwise
     */
    java.util.Optional<LedgerEntry> findByReferenceId(String referenceId);

    /**
     * Count total entries for a wallet.
     * 
     * Used for:
     * - Pagination support
     * - Statistics (total transactions processed)
     * 
     * @param walletId the wallet's ID
     * @return count of entries
     */
    long countByWalletId(Long walletId);
}
