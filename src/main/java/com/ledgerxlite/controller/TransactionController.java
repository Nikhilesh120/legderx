package com.ledgerxlite.controller;

import com.ledgerxlite.dto.ApiResponses;
import com.ledgerxlite.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * REST controller for transaction lookup operations.
 *
 * RESPONSIBILITIES:
 * - Look up any ledger entry by its referenceId (system-wide, wallet-agnostic)
 *
 * CONTRACT:
 * - GET /transactions/{referenceId} → 200 OK | 404 Not Found
 *
 * RULES:
 * - No business logic in this controller
 * - Pure delegation to TransactionService only
 */
@RestController
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Lookup transactions by referenceId")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Find a ledger entry by its unique referenceId.
     *
     * Idempotency note: referenceId is the client-supplied idempotency key.
     * If found, returns the existing entry regardless of how many times called.
     *
     * HTTP Contract:
     * - 200 OK         → entry found, returns TransactionResponse
     * - 404 Not Found  → no entry exists for this referenceId
     */
    @GetMapping("/{referenceId}")
    @Operation(
        summary = "Find transaction by referenceId",
        description = "Retrieves a ledger entry by its unique referenceId. " +
                      "Use this to verify idempotent operation results."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transaction found"),
        @ApiResponse(responseCode = "404", description = "Transaction not found for given referenceId")
    })
    public ResponseEntity<ApiResponses.TransactionResponse> findByReferenceId(
            @Parameter(description = "Unique referenceId used when creating the transaction")
            @PathVariable String referenceId) {

        return transactionService.findByReferenceId(referenceId)
                .map(entry -> ResponseEntity.ok(new ApiResponses.TransactionResponse(entry)))
                .orElseThrow(() -> new NoSuchElementException(
                        "Transaction not found for referenceId: " + referenceId
                ));
    }
}
