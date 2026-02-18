package com.ledgerxlite.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for user registration request.
 */
public class RegisterUserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 10, message = "Currency must be 3-10 characters (e.g., USD, EUR)")
    private String currency;

    // Constructors
    public RegisterUserRequest() {
    }

    public RegisterUserRequest(String email, String password, String currency) {
        this.email = email;
        this.password = password;
        this.currency = currency;
    }

    // Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
