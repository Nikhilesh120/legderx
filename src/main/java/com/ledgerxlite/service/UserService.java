package com.ledgerxlite.service;

import com.ledgerxlite.domain.User;
import com.ledgerxlite.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for user management operations.
 *
 * Password hashing uses BCryptPasswordEncoder (cost factor 12).
 * Plaintext passwords are never stored or logged.
 */
@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository  userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Register a new user with a BCrypt-hashed password.
     *
     * @param email         user's email (must be unique)
     * @param plainPassword plaintext password — hashed immediately, never stored
     * @return created user
     * @throws IllegalArgumentException if email already registered
     */
    public User registerUser(String email, String plainPassword) {
        log.info("=== USER REGISTRATION START ===");
        log.info("Step 1: Attempting to register user - email={}", email);
        
        log.debug("Step 2: Checking if email already exists");
        if (userRepository.existsByEmail(email)) {
            log.warn("Step 2: ✗ Email already registered - email={}", email);
            log.info("=== USER REGISTRATION FAILED (DUPLICATE EMAIL) ===");
            throw new IllegalArgumentException("Email already registered: " + email);
        }
        log.debug("Step 2: ✓ Email is available");
        
        log.debug("Step 3: Hashing password with BCrypt (cost=12)");
        String hash = passwordEncoder.encode(plainPassword);
        log.debug("Step 3: ✓ Password hashed successfully");
        
        log.debug("Step 4: Creating user entity");
        User   user = new User(email, hash);
        user = userRepository.save(user);
        log.info("Step 4: ✓ User created successfully - userId={}, email={}, status={}", 
                user.getId(), user.getEmail(), user.getStatus());
        
        log.info("=== USER REGISTRATION SUCCESS ===");
        log.info("Summary: userId={}, email={}", user.getId(), email);
        
        return user;
    }

    /** Find user by email. */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        log.debug("Looking up user by email: {}", email);
        return userRepository.findByEmail(email);
    }

    /** Find user by ID. */
    @Transactional(readOnly = true)
    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    /** Suspend an account (reversible). */
    public void suspendUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.suspend();
        userRepository.save(user);
    }

    /** Re-activate a suspended account. */
    public void activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.activate();
        userRepository.save(user);
    }

    /** Permanently close an account. */
    public void closeUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.close();
        userRepository.save(user);
    }
}
