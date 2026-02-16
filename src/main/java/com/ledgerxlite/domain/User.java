package com.ledgerxlite.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * User entity representing an authenticated user in the system.
 * 
 * This is the root aggregate for user identity. Each user has exactly one wallet.
 * Users are mutable (status can change), but user creation is permanent.
 * 
 * Design decisions:
 * - No Lombok to keep behavior explicit and visible
 * - Status enum for account lifecycle management
 * - Email as natural unique identifier (business key)
 * - Password stored as hash only (never plaintext)
 * - createdAt is immutable after construction
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_status", columnList = "status")
    }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * User account status lifecycle.
     * Controls whether user can perform financial operations.
     */
    public enum UserStatus {
        ACTIVE,      // Normal operations allowed
        SUSPENDED,   // Temporarily disabled
        CLOSED       // Permanently disabled
    }

    /**
     * JPA requires a no-arg constructor.
     * Protected to prevent direct instantiation outside of JPA.
     */
    protected User() {
    }

    /**
     * Constructor for creating a new user.
     * 
     * @param email user's email (must be unique)
     * @param passwordHash bcrypt/argon2 hashed password
     */
    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = UserStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Business methods (controlled mutations)
    
    /**
     * Update password hash.
     * Used for password reset/change operations.
     */
    public void updatePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /**
     * Suspend user account.
     * Prevents financial operations but preserves data.
     */
    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    /**
     * Reactivate a suspended account.
     */
    public void activate() {
        if (this.status == UserStatus.CLOSED) {
            throw new IllegalStateException("Cannot reactivate a closed account");
        }
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Permanently close account.
     * This is irreversible.
     */
    public void close() {
        this.status = UserStatus.CLOSED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}
