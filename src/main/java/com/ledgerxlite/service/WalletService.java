package com.ledgerxlite.service;

import com.ledgerxlite.domain.User;
import com.ledgerxlite.domain.Wallet;
import com.ledgerxlite.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Service for wallet management operations.
 * 
 * Handles wallet creation and balance queries.
 * Balance modifications happen through TransactionService.
 */
@Service
@Transactional
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    /**
     * Create a new wallet for a user.
     * 
     * @param user the wallet owner
     * @param currency ISO 4217 currency code (USD, EUR, etc.)
     * @return created wallet
     * @throws IllegalArgumentException if user already has a wallet
     */
    public Wallet createWallet(User user, String currency) {
        if (walletRepository.existsByUserId(user.getId())) {
            throw new IllegalArgumentException("User already has a wallet: " + user.getEmail());
        }
        
        Wallet wallet = new Wallet(user, currency);
        return walletRepository.save(wallet);
    }

    /**
     * Find wallet by user ID.
     * 
     * @param userId user's ID
     * @return wallet if found
     */
    @Transactional(readOnly = true)
    public Optional<Wallet> findByUserId(Long userId) {
        return walletRepository.findByUserId(userId);
    }

    /**
     * Find wallet by ID.
     * 
     * @param walletId wallet's ID
     * @return wallet if found
     */
    @Transactional(readOnly = true)
    public Optional<Wallet> findById(Long walletId) {
        return walletRepository.findById(walletId);
    }

    /**
     * Get current balance for a wallet.
     * 
     * @param walletId wallet's ID
     * @return current balance
     * @throws IllegalArgumentException if wallet not found
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));
        
        return wallet.getBalance();
    }

    /**
     * Check if wallet has sufficient balance.
     * 
     * @param walletId wallet's ID
     * @param amount amount to check
     * @return true if sufficient balance
     * @throws IllegalArgumentException if wallet not found
     */
    @Transactional(readOnly = true)
    public boolean hasSufficientBalance(Long walletId, BigDecimal amount) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));
        
        return wallet.hasSufficientBalance(amount);
    }
}
