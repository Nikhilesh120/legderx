package com.ledgerxlite.controller;

import com.ledgerxlite.domain.User;
import com.ledgerxlite.domain.Wallet;
import com.ledgerxlite.dto.ApiResponses;
import com.ledgerxlite.dto.RegisterUserRequest;
import com.ledgerxlite.service.UserService;
import com.ledgerxlite.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user management operations.
 */
@RestController
@RequestMapping("/users")
@Tag(name = "User Management", description = "User registration and account management")
public class UserController {

    private final UserService userService;
    private final WalletService walletService;

    public UserController(UserService userService, WalletService walletService) {
        this.userService = userService;
        this.walletService = walletService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Creates a new user account with a wallet")
    public ResponseEntity<ApiResponses.UserResponse> registerUser(
            @Valid @RequestBody RegisterUserRequest request) {

        // UserService.registerUser handles BCrypt password hashing
        User user = userService.registerUser(request.getEmail(), request.getPassword());

        // Create wallet for user
        Wallet wallet = walletService.createWallet(user, request.getCurrency());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponses.UserResponse(user, wallet));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID", description = "Retrieve user details by user ID")
    public ResponseEntity<ApiResponses.UserResponse> getUser(@PathVariable Long userId) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        
        Wallet wallet = walletService.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user: " + userId));
        
        return ResponseEntity.ok(new ApiResponses.UserResponse(user, wallet));
    }

    @PostMapping("/{userId}/suspend")
    @Operation(summary = "Suspend user", description = "Suspend a user account")
    public ResponseEntity<Void> suspendUser(@PathVariable Long userId) {
        userService.suspendUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/activate")
    @Operation(summary = "Activate user", description = "Activate a suspended user account")
    public ResponseEntity<Void> activateUser(@PathVariable Long userId) {
        userService.activateUser(userId);
        return ResponseEntity.noContent().build();
    }
}
