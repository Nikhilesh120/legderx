package com.ledgerxlite.controller;

import com.ledgerxlite.domain.LedgerEntry;
import com.ledgerxlite.domain.Wallet;
import com.ledgerxlite.service.TransactionService;
import com.ledgerxlite.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
@WithMockUser(username = "test-user", roles = {"USER"})
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private WalletService walletService;

    // ─────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────

    private static Wallet blankWallet() throws Exception {
        Constructor<Wallet> ctor = Wallet.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static LedgerEntry stubEntry() {
        return mock(LedgerEntry.class, RETURNS_DEEP_STUBS);
    }

    // ─────────────────────────────────────────────────────
    // DEPOSIT
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /wallets/{id}/deposit → 200 OK")
    void deposit_success() throws Exception {
        when(walletService.findById(10L)).thenReturn(Optional.of(blankWallet()));
        when(transactionService.deposit(any(), any(), any(), any()))
                .thenReturn(stubEntry());

        mockMvc.perform(post("/wallets/10/deposit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "amount": 100,
                              "referenceId": "REF-1"
                            }
                        """))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────
    // WITHDRAW
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /wallets/{id}/withdraw → 200 OK")
    void withdraw_success() throws Exception {
        when(walletService.findById(10L)).thenReturn(Optional.of(blankWallet()));
        when(transactionService.withdraw(any(), any(), any(), any()))
                .thenReturn(stubEntry());

        mockMvc.perform(post("/wallets/10/withdraw")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "amount": 50,
                              "referenceId": "REF-2"
                            }
                        """))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────
    // TRANSFER
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /wallets/{from}/transfer → 200 OK")
    void transfer_success() throws Exception {
        when(walletService.findById(10L)).thenReturn(Optional.of(blankWallet()));

        // ⚠️ Prepare entries BEFORE passing them into when(...).thenReturn(...)
        // Stubbing a mock inside another when() call causes UnfinishedStubbingException.
        LedgerEntry debitEntry = stubEntry();
        LedgerEntry creditEntry = stubEntry();
        when(debitEntry.getReferenceId()).thenReturn("REF-3-OUT");

        when(transactionService.transfer(any(), any(), any(), any(), any()))
                .thenReturn(new LedgerEntry[]{debitEntry, creditEntry});

        mockMvc.perform(post("/wallets/10/transfer")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "toWalletId": 20,
                              "amount": 25,
                              "referenceId": "REF-3"
                            }
                        """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /wallets/{id}/transfer same wallet → 400 BAD REQUEST")
    void transfer_invalid_sameWallet() throws Exception {
        when(walletService.findById(10L)).thenReturn(Optional.of(blankWallet()));
        when(transactionService.transfer(eq(10L), eq(10L), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Source and destination wallets must be different"));

        mockMvc.perform(post("/wallets/10/transfer")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "toWalletId": 10,
                              "amount": 25,
                              "referenceId": "REF-INVALID"
                            }
                        """))
                .andExpect(status().isBadRequest());
    }
}