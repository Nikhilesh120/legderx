package com.ledgerxlite.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerxlite.domain.User;
import com.ledgerxlite.domain.Wallet;
import com.ledgerxlite.dto.TransactionRequest;
import com.ledgerxlite.dto.TransferRequest;
import com.ledgerxlite.service.TransactionService;
import com.ledgerxlite.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests — HTTP contract verification.
 * No database. No full Spring context.
 */
@WebMvcTest(WalletController.class)
class WalletControllerTest {

    @Autowired MockMvc         mockMvc;
    @Autowired ObjectMapper    objectMapper;

    @MockBean TransactionService transactionService;
    @MockBean WalletService      walletService;

    // A stub wallet (user ownership not enforced in @WithMockUser context)
    private Wallet stubWallet;

    @BeforeEach
    void setUp() throws Exception {
        User stubUser = new User("stub@test.com", "hash");
        var userIdField = User.class.getDeclaredField("id");
        userIdField.setAccessible(true);
        userIdField.set(stubUser, 1L);

        stubWallet = new Wallet(stubUser, "USD");
        var walletIdField = Wallet.class.getDeclaredField("id");
        walletIdField.setAccessible(true);
        walletIdField.set(stubWallet, 1L);
    }

    // ── wallet not found → 404 ───────────────────────────────────────────────

    @Test @WithMockUser @DisplayName("GET /wallets/{id} unknown wallet → 404")
    void getWalletNotFound() throws Exception {
        when(walletService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/wallets/99"))
               .andExpect(status().isNotFound());
    }

    // ── deposit bad requests → 400 ───────────────────────────────────────────

    @Test @WithMockUser @DisplayName("POST deposit with missing amount → 400")
    void depositMissingAmount() throws Exception {
        var req = new TransactionRequest(null, "REF-1", null);

        mockMvc.perform(post("/wallets/1/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
               .andExpect(status().isBadRequest());
    }

    @Test @WithMockUser @DisplayName("POST deposit with missing referenceId → 400")
    void depositMissingRef() throws Exception {
        var req = new TransactionRequest(BigDecimal.TEN, "", null);

        mockMvc.perform(post("/wallets/1/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
               .andExpect(status().isBadRequest());
    }

    // ── invariant violation → 409 ────────────────────────────────────────────

    @Test @WithMockUser @DisplayName("POST deposit on inactive wallet → 409")
    void depositInactiveWallet() throws Exception {
        when(walletService.findById(1L)).thenReturn(Optional.of(stubWallet));
        when(transactionService.deposit(anyLong(), any(), anyString(), any()))
            .thenThrow(new IllegalStateException("INVARIANT-WALLET-ACTIVE VIOLATION"));

        var req = new TransactionRequest(BigDecimal.TEN, "REF-1", null);

        mockMvc.perform(post("/wallets/1/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test @WithMockUser @DisplayName("POST withdraw insufficient balance → 409")
    void withdrawInsufficientBalance() throws Exception {
        when(walletService.findById(1L)).thenReturn(Optional.of(stubWallet));
        when(transactionService.withdraw(anyLong(), any(), anyString(), any()))
            .thenThrow(new IllegalStateException("INVARIANT-10 VIOLATION: Insufficient balance"));

        var req = new TransactionRequest(new BigDecimal("9999"), "REF-1", null);

        mockMvc.perform(post("/wallets/1/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
               .andExpect(status().isConflict());
    }

    // ── transfer bad input → 400 ─────────────────────────────────────────────

    @Test @WithMockUser @DisplayName("POST transfer same wallet → 400")
    void transferSameWallet() throws Exception {
        when(walletService.findById(1L)).thenReturn(Optional.of(stubWallet));
        when(transactionService.transfer(anyLong(), anyLong(), any(), anyString(), any()))
            .thenThrow(new IllegalArgumentException("INVARIANT-DISTINCT-WALLETS VIOLATION"));

        var req = new TransferRequest(1L, BigDecimal.TEN, "REF-1", null);

        mockMvc.perform(post("/wallets/1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
               .andExpect(status().isBadRequest());
    }

    // ── transaction history → 200 ────────────────────────────────────────────

    @Test @WithMockUser @DisplayName("GET /wallets/{id}/transactions → 200 with list")
    void transactionHistory() throws Exception {
        when(walletService.findById(1L)).thenReturn(Optional.of(stubWallet));
        when(transactionService.getTransactionHistory(1L)).thenReturn(List.of());

        mockMvc.perform(get("/wallets/1/transactions"))
               .andExpect(status().isOk())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    // ── unauthenticated → 401 ────────────────────────────────────────────────

    @Test @DisplayName("GET /wallets/{id} without token → 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/wallets/1"))
               .andExpect(status().isUnauthorized());
    }
}
