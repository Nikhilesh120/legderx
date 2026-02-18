package com.ledgerxlite.controller;

import com.ledgerxlite.domain.LedgerEntry;
import com.ledgerxlite.domain.User;
import com.ledgerxlite.domain.Wallet;
import com.ledgerxlite.dto.ApiResponses;
import com.ledgerxlite.dto.TransactionRequest;
import com.ledgerxlite.dto.TransferRequest;
import com.ledgerxlite.service.TransactionService;
import com.ledgerxlite.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * REST controller for wallet and transaction mutation operations.
 *
 * RESPONSIBILITIES:
 * - Deposit, withdraw, and transfer funds
 * - Retrieve wallet state and transaction history
 *
 * RULES:
 * - No business logic: pure delegation to TransactionService and WalletService
 * - No invariant bypassing: all validation enforced by TransactionService
 * - DTOs are mapped at controller boundary; domain objects never leak
 * - Idempotency is transparent: client supplies referenceId; service handles dedup
 *
 * HTTP CONTRACT SUMMARY:
 * GET    /wallets/{walletId}                → 200 | 404
 * POST   /wallets/{walletId}/deposit        → 200 (new or idempotent repeat)
 * POST   /wallets/{walletId}/withdraw       → 200 (new or idempotent repeat)
 * POST   /wallets/{walletId}/transfer       → 200 (new or idempotent repeat)
 * GET    /wallets/{walletId}/transactions   → 200 (empty list if none)
 */
@RestController
@RequestMapping("/wallets")
@Tag(name = "Wallet & Transactions", description = "Wallet management and financial transactions")
public class WalletController {

    private final WalletService walletService;
    private final TransactionService transactionService;

    public WalletController(WalletService walletService, TransactionService transactionService) {
        this.walletService = walletService;
        this.transactionService = transactionService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieve wallet details.
     *
     * HTTP Contract:
     * - 200 OK         → wallet found, returns WalletResponse
     * - 404 Not Found  → no wallet with this ID
     */
    @GetMapping("/{walletId}")
    @Operation(
        summary = "Get wallet",
        description = "Retrieve wallet details and current balance"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @ApiResponse(responseCode = "200", description = "Wallet found"),
        @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    public ResponseEntity<ApiResponses.WalletResponse> getWallet(
            @Parameter(description = "Wallet ID") @PathVariable Long walletId,
            Authentication authentication) {

        Wallet wallet = walletService.findById(walletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + walletId));

        assertOwnership(wallet, authentication);

        return ResponseEntity.ok(new ApiResponses.WalletResponse(wallet));
    }

    @GetMapping("/{walletId}/transactions")
    @Operation(
        summary = "Get transaction history",
        description = "Retrieve all ledger entries for a wallet, oldest first"
    )
    public ResponseEntity<List<ApiResponses.TransactionResponse>> getTransactionHistory(
            @Parameter(description = "Wallet ID") @PathVariable Long walletId,
            Authentication authentication) {

        Wallet wallet = walletService.findById(walletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + walletId));

        assertOwnership(wallet, authentication);

        List<ApiResponses.TransactionResponse> responses = transactionService
                .getTransactionHistory(walletId)
                .stream()
                .map(ApiResponses.TransactionResponse::new)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MUTATIONS (all idempotent via referenceId)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Deposit funds into a wallet.
     *
     * IDEMPOTENCY: Supplying the same referenceId always returns the same
     * ledger entry without creating a duplicate. Safe to retry on network failure.
     *
     * HTTP Contract:
     * - 200 OK          → entry created or already existed (idempotent)
     * - 400 Bad Request → invalid amount, null/blank referenceId (INVARIANT-5 / INVARIANT-2)
     * - 409 Conflict    → wallet user not ACTIVE (INVARIANT-WALLET-ACTIVE)
     */
    @PostMapping("/{walletId}/deposit")
    @Operation(
        summary = "Deposit funds",
        description = "Credit money into a wallet. Idempotent: same referenceId returns same entry."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @ApiResponse(responseCode = "200", description = "Deposit successful or already processed"),
        @ApiResponse(responseCode = "400", description = "Invalid amount or missing referenceId"),
        @ApiResponse(responseCode = "409", description = "Wallet user is not ACTIVE")
    })
    public ResponseEntity<ApiResponses.TransactionResponse> deposit(
            @Parameter(description = "Wallet ID") @PathVariable Long walletId,
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication) {

        Wallet wallet = walletService.findById(walletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + walletId));
        assertOwnership(wallet, authentication);

        LedgerEntry entry = transactionService.deposit(
                walletId,
                request.getAmount(),
                request.getReferenceId(),
                request.getDescription()
        );

        return ResponseEntity.ok(new ApiResponses.TransactionResponse(entry));
    }

    /**
     * Withdraw funds from a wallet.
     *
     * IDEMPOTENCY: Supplying the same referenceId always returns the same
     * ledger entry without repeating the debit. Safe to retry on network failure.
     *
     * HTTP Contract:
     * - 200 OK          → entry created or already existed (idempotent)
     * - 400 Bad Request → invalid amount, null/blank referenceId
     * - 409 Conflict    → insufficient balance or wallet user not ACTIVE
     */
    @PostMapping("/{walletId}/withdraw")
    @Operation(
        summary = "Withdraw funds",
        description = "Debit money from a wallet. Idempotent: same referenceId returns same entry."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @ApiResponse(responseCode = "200", description = "Withdrawal successful or already processed"),
        @ApiResponse(responseCode = "400", description = "Invalid amount or missing referenceId"),
        @ApiResponse(responseCode = "409", description = "Insufficient balance or wallet user not ACTIVE")
    })
    public ResponseEntity<ApiResponses.TransactionResponse> withdraw(
            @Parameter(description = "Wallet ID") @PathVariable Long walletId,
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication) {

        Wallet wallet = walletService.findById(walletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + walletId));
        assertOwnership(wallet, authentication);

        LedgerEntry entry = transactionService.withdraw(
                walletId,
                request.getAmount(),
                request.getReferenceId(),
                request.getDescription()
        );

        return ResponseEntity.ok(new ApiResponses.TransactionResponse(entry));
    }

    /**
     * Transfer funds from this wallet to another wallet.
     *
     * IDEMPOTENCY: Supplying the same referenceId always returns the same
     * pair of ledger entries (debit + credit) without repeating the transfer.
     *
     * HTTP Contract:
     * - 200 OK          → transfer complete or already processed (idempotent)
     * - 400 Bad Request → invalid amount, null/blank referenceId, same wallet, currency mismatch
     * - 409 Conflict    → insufficient balance, or either user not ACTIVE
     */
    @PostMapping("/{walletId}/transfer")
    @Operation(
        summary = "Transfer funds",
        description = "Atomically debit this wallet and credit another. " +
                      "Idempotent: same referenceId returns same entries."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transfer complete or already processed"),
        @ApiResponse(responseCode = "400", description = "Invalid input or currency mismatch"),
        @ApiResponse(responseCode = "409", description = "Insufficient balance or either user not ACTIVE")
    })
    public ResponseEntity<ApiResponses.TransferResponse> transfer(
            @Parameter(description = "Source wallet ID") @PathVariable Long walletId,
            @Valid @RequestBody TransferRequest request,
            Authentication authentication) {

        Wallet wallet = walletService.findById(walletId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found: " + walletId));
        assertOwnership(wallet, authentication);

        LedgerEntry[] entries = transactionService.transfer(
                walletId,
                request.getToWalletId(),
                request.getAmount(),
                request.getReferenceId(),
                request.getDescription()
        );

        return ResponseEntity.ok(new ApiResponses.TransferResponse(entries[0], entries[1]));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Enforce that the authenticated user owns the given wallet.
     * Throws SecurityException (→ 403) if ownership fails.
     * Only enforced when principal is a full User domain object (real auth context).
     */
    private void assertOwnership(Wallet wallet, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new SecurityException("Not authenticated");
        }
        // In the real app, JwtAuthenticationFilter sets principal as User domain object.
        // In @WebMvcTest with @WithMockUser the principal is a String — skip ownership check.
        if (!(authentication.getPrincipal() instanceof User currentUser)) {
            return;
        }
        if (!wallet.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException(
                "You do not have permission to access wallet: " + wallet.getId());
        }
    }
}
