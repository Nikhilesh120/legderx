package com.ledgerxlite.repository;

import com.ledgerxlite.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for User entity.
 * 
 * Provides data access operations for user management and authentication.
 * 
 * Custom Queries Explained:
 * 
 * 1. findByEmail(String email)
 *    WHY: Email is the natural business key for user lookup during authentication.
 *    USAGE: Login, password reset, duplicate email detection.
 *    PERFORMANCE: Backed by unique index (idx_users_email) for O(log n) lookup.
 *    RETURNS: Optional to handle user-not-found cases gracefully.
 * 
 * Design Notes:
 * - No custom implementations needed (Spring Data derives queries from method names)
 * - No @Transactional here (managed by service layer)
 * - JpaRepository provides: save(), findById(), delete(), etc.
 * - Email lookup is case-sensitive (matches database column definition)
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email address.
     * 
     * Used for:
     * - User authentication (login)
     * - Email uniqueness validation
     * - Password reset flows
     * 
     * @param email the user's email address (case-sensitive)
     * @return Optional containing User if found, empty otherwise
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Check if a user exists with the given email.
     * 
     * Used for:
     * - Registration validation (prevent duplicate emails)
     * - Faster existence check without loading full entity
     * 
     * @param email the email to check
     * @return true if user exists, false otherwise
     */
    boolean existsByEmail(String email);
}
