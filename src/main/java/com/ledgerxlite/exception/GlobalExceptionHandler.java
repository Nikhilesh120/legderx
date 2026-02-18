package com.ledgerxlite.exception;

import com.ledgerxlite.dto.ApiResponses;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Centralized exception mapper for all REST endpoints.
 *
 * INVARIANT → HTTP STATUS MAPPING:
 *
 * Exception Type                    | HTTP Status | When
 * ----------------------------------|-------------|------------------------------------------
 * IllegalArgumentException          | 400         | Invalid input (amount, referenceId, etc.)
 * MethodArgumentNotValidException   | 400         | Bean Validation failure on DTO fields
 * NoSuchElementException            | 404         | Wallet / transaction not found
 * IllegalStateException             | 409         | Business rule violation (balance, status)
 * DataIntegrityViolationException   | 409         | DB unique constraint (concurrent dupe ref)
 * Exception (fallback)              | 500         | Unexpected system errors
 *
 * RULES:
 * - Internal exception messages are passed through; they contain INVARIANT-X labels
 * - No stack traces in responses (security)
 * - All responses use ErrorResponse shape for consistency
 * - Validation errors return field-level map (400)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─────────────────────────────────────────────────────────────────────────
    // 403 FORBIDDEN — Ownership / access violation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles wallet ownership violations.
     * WalletController throws SecurityException when authenticated user
     * attempts to access a wallet they do not own.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleForbidden(SecurityException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ApiResponses.ErrorResponse("FORBIDDEN", ex.getMessage()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 400 BAD REQUEST — Invalid input
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles INVARIANT-5, INVARIANT-2, and other input guard clause failures.
     * TransactionService throws IllegalArgumentException for:
     *   - amount <= 0 (INVARIANT-5)
     *   - null/blank referenceId (INVARIANT-2)
     *   - same-wallet transfer (INVARIANT-DISTINCT-WALLETS)
     *   - currency mismatch (INVARIANT-CURRENCY-MATCH)
     *   - wallet not found (INVARIANT-WALLET-EXISTS)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponses.ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    /**
     * Handles @Valid/@NotNull/@DecimalMin annotation failures on request DTOs.
     * Returns a field → message map instead of generic error for clearer API feedback.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 404 NOT FOUND — Resource does not exist
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles wallet-not-found and transaction-not-found lookups from controllers.
     * Controllers throw NoSuchElementException when Optional is empty on read paths.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponses.ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 409 CONFLICT — Business rule / state violation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles business rule violations from TransactionService:
     *   - Insufficient balance (INVARIANT-10)
     *   - Wallet user not ACTIVE (INVARIANT-WALLET-ACTIVE)
     *   - Balance change mismatch post-condition (INVARIANT-1)
     *   - Money not conserved in transfer (INVARIANT-8)
     *   - Incomplete transfer detected (INVARIANT-8)
     *   - Negative balance detected post-condition (INVARIANT-10)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiResponses.ErrorResponse("CONFLICT", ex.getMessage()));
    }

    /**
     * Handles concurrent duplicate referenceId inserts that bypass the
     * service-level idempotency check (race window between check and DB write).
     *
     * WHY THIS EXISTS:
     * TransactionService checks idempotency after acquiring the wallet lock,
     * which eliminates most races. However, in extreme concurrency scenarios,
     * the DB unique constraint on ledger_entries.reference_id is the final
     * safety net. This handler maps that DB exception cleanly to 409 Conflict
     * so clients retry and get the existing entry via the idempotency path.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiResponses.ErrorResponse(
                        "CONFLICT",
                        "Duplicate transaction detected. " +
                        "Retry with the same referenceId to retrieve the existing entry."
                ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 500 INTERNAL SERVER ERROR — Unexpected failures
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Safety net for any unhandled exception.
     * Message is deliberately generic — internal detail must not leak to clients.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponses.ErrorResponse(
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred. Please contact support."
                ));
    }
}
