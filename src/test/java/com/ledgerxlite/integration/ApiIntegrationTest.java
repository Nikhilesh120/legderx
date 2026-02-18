package com.ledgerxlite.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerxlite.dto.RegisterUserRequest;
import com.ledgerxlite.dto.TransactionRequest;
import com.ledgerxlite.dto.TransferRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive API integration test.
 * Tests the full workflow: register → login → deposit → withdraw → transfer
 * 
 * Uses @SpringBootTest to load the full application context with real transaction management.
 * Uses H2 in-memory database (configured in application-test.yml).
 * 
 * This verifies:
 * - All APIs return correct HTTP status codes
 * - Transaction management works (no "no transaction" errors)
 * - JWT authentication works end-to-end
 * - Idempotency works (duplicate referenceId returns same result)
 * - Ownership checks work (can't access other user's wallet)
 * - Business logic works (insufficient balance rejection, etc.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // State shared across test methods (executed in order)
    private static String user1Token;
    private static Long user1WalletId;
    private static String user2Token;
    private static Long user2WalletId;

    // ── 1. Registration ───────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /users/register - Create first user")
    void registerUser1() throws Exception {
        RegisterUserRequest request = new RegisterUserRequest(
                "user1@test.com",
                "Password123!",
                "USD"
        );

        MvcResult result = mockMvc.perform(post("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("user1@test.com"))
                .andExpect(jsonPath("$.walletId").exists())
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        user1WalletId = objectMapper.readTree(response).get("walletId").asLong();
    }

    @Test
    @Order(2)
    @DisplayName("POST /users/register - Create second user")
    void registerUser2() throws Exception {
        RegisterUserRequest request = new RegisterUserRequest(
                "user2@test.com",
                "Password456!",
                "USD"
        );

        MvcResult result = mockMvc.perform(post("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.walletId").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        user2WalletId = objectMapper.readTree(response).get("walletId").asLong();
    }

    @Test
    @Order(3)
    @DisplayName("POST /users/register - Duplicate email rejected")
    void registerDuplicateEmail() throws Exception {
        RegisterUserRequest request = new RegisterUserRequest(
                "user1@test.com",
                "DifferentPass123!",
                "USD"
        );

        mockMvc.perform(post("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already registered")));
    }

    // ── 2. Authentication ─────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("POST /auth/login - User 1 login success")
    void loginUser1() throws Exception {
        String loginRequest = """
                {
                    "email": "user1@test.com",
                    "password": "Password123!"
                }
                """;

        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.email").value("user1@test.com"))
                .andExpect(jsonPath("$.userId").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        user1Token = objectMapper.readTree(response).get("token").asText();
    }

    @Test
    @Order(11)
    @DisplayName("POST /auth/login - User 2 login success")
    void loginUser2() throws Exception {
        String loginRequest = """
                {
                    "email": "user2@test.com",
                    "password": "Password456!"
                }
                """;

        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        user2Token = objectMapper.readTree(response).get("token").asText();
    }

    @Test
    @Order(12)
    @DisplayName("POST /auth/login - Wrong password rejected")
    void loginWrongPassword() throws Exception {
        String loginRequest = """
                {
                    "email": "user1@test.com",
                    "password": "WrongPassword!"
                }
                """;

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid email or password")));
    }

    @Test
    @Order(13)
    @DisplayName("POST /auth/login - Non-existent user rejected")
    void loginNonExistentUser() throws Exception {
        String loginRequest = """
                {
                    "email": "nobody@test.com",
                    "password": "Password123!"
                }
                """;

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest))
                .andExpect(status().isNotFound());
    }

    // ── 3. Wallet Access (with authentication) ────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("GET /wallets/{id} - Without token returns 401")
    void getWalletUnauthenticated() throws Exception {
        mockMvc.perform(get("/wallets/" + user1WalletId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(21)
    @DisplayName("GET /wallets/{id} - Own wallet returns 200")
    void getOwnWallet() throws Exception {
        mockMvc.perform(get("/wallets/" + user1WalletId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(user1WalletId))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @Order(22)
    @DisplayName("GET /wallets/{id} - Other user's wallet returns 403")
    void getOtherUsersWallet() throws Exception {
        mockMvc.perform(get("/wallets/" + user2WalletId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("permission")));
    }

    @Test
    @Order(23)
    @DisplayName("GET /wallets/99999 - Non-existent wallet returns 404")
    void getNonExistentWallet() throws Exception {
        mockMvc.perform(get("/wallets/99999")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    // ── 4. Deposit ─────────────────────────────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("POST /wallets/{id}/deposit - Valid deposit succeeds")
    void depositSuccess() throws Exception {
        TransactionRequest request = new TransactionRequest(
                new BigDecimal("100.00"),
                UUID.randomUUID().toString(),
                "Test deposit"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/deposit")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.referenceId").value(request.getReferenceId()));

        // Verify balance updated
        mockMvc.perform(get("/wallets/" + user1WalletId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    @Order(31)
    @DisplayName("POST /wallets/{id}/deposit - Idempotent (duplicate referenceId)")
    void depositIdempotent() throws Exception {
        String refId = UUID.randomUUID().toString();
        TransactionRequest request = new TransactionRequest(
                new BigDecimal("50.00"),
                refId,
                "Idempotent test"
        );

        // First request
        mockMvc.perform(post("/wallets/" + user1WalletId + "/deposit")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50.00));

        // Second request with same referenceId - should return same entry
        mockMvc.perform(post("/wallets/" + user1WalletId + "/deposit")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.referenceId").value(refId));

        // Balance should only increase once
        mockMvc.perform(get("/wallets/" + user1WalletId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.00)); // 100 + 50, not 100 + 50 + 50
    }

    @Test
    @Order(32)
    @DisplayName("POST /wallets/{id}/deposit - Negative amount rejected")
    void depositNegativeAmount() throws Exception {
        TransactionRequest request = new TransactionRequest(
                new BigDecimal("-10.00"),
                UUID.randomUUID().toString(),
                "Negative test"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/deposit")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("positive")));
    }

    @Test
    @Order(33)
    @DisplayName("POST /wallets/{id}/deposit - Zero amount rejected")
    void depositZeroAmount() throws Exception {
        TransactionRequest request = new TransactionRequest(
                BigDecimal.ZERO,
                UUID.randomUUID().toString(),
                "Zero test"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/deposit")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(34)
    @DisplayName("POST /wallets/{id}/deposit - Missing referenceId rejected")
    void depositMissingReferenceId() throws Exception {
        TransactionRequest request = new TransactionRequest(
                new BigDecimal("10.00"),
                null,
                "Missing ref test"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/deposit")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(35)
    @DisplayName("POST /wallets/{id}/deposit - Cannot deposit to other user's wallet")
    void depositToOtherUserWallet() throws Exception {
        TransactionRequest request = new TransactionRequest(
                new BigDecimal("10.00"),
                UUID.randomUUID().toString(),
                "Unauthorized deposit"
        );

        mockMvc.perform(post("/wallets/" + user2WalletId + "/deposit")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── 5. Withdraw ────────────────────────────────────────────────────────────

    @Test
    @Order(40)
    @DisplayName("POST /wallets/{id}/withdraw - Valid withdraw succeeds")
    void withdrawSuccess() throws Exception {
        TransactionRequest request = new TransactionRequest(
                new BigDecimal("30.00"),
                UUID.randomUUID().toString(),
                "Test withdrawal"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/withdraw")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(-30.00))
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"));

        // Verify balance decreased
        mockMvc.perform(get("/wallets/" + user1WalletId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(120.00)); // 150 - 30
    }

    @Test
    @Order(41)
    @DisplayName("POST /wallets/{id}/withdraw - Insufficient balance rejected")
    void withdrawInsufficientBalance() throws Exception {
        TransactionRequest request = new TransactionRequest(
                new BigDecimal("500.00"),
                UUID.randomUUID().toString(),
                "Overdraft attempt"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/withdraw")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient")));
    }

    // ── 6. Transfer ────────────────────────────────────────────────────────────

    @Test
    @Order(50)
    @DisplayName("POST /wallets/{id}/transfer - Prepare user2 for transfer")
    void prepareUser2() throws Exception {
        // Give user2 some funds
        TransactionRequest request = new TransactionRequest(
                new BigDecimal("200.00"),
                UUID.randomUUID().toString(),
                "Setup for transfer test"
        );

        mockMvc.perform(post("/wallets/" + user2WalletId + "/deposit")
                .header("Authorization", "Bearer " + user2Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(51)
    @DisplayName("POST /wallets/{id}/transfer - Valid transfer succeeds")
    void transferSuccess() throws Exception {
        TransferRequest request = new TransferRequest(
                user2WalletId,
                new BigDecimal("20.00"),
                UUID.randomUUID().toString(),
                "Test transfer"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/transfer")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debit.amount").value(-20.00))
                .andExpect(jsonPath("$.credit.amount").value(20.00));

        // Verify both balances updated
        mockMvc.perform(get("/wallets/" + user1WalletId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00)); // 120 - 20

        mockMvc.perform(get("/wallets/" + user2WalletId)
                .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(220.00)); // 200 + 20
    }

    @Test
    @Order(52)
    @DisplayName("POST /wallets/{id}/transfer - Transfer to self rejected")
    void transferToSelf() throws Exception {
        TransferRequest request = new TransferRequest(
                user1WalletId,
                new BigDecimal("10.00"),
                UUID.randomUUID().toString(),
                "Self transfer"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/transfer")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("same wallet")));
    }

    @Test
    @Order(53)
    @DisplayName("POST /wallets/{id}/transfer - Transfer with insufficient balance rejected")
    void transferInsufficientBalance() throws Exception {
        TransferRequest request = new TransferRequest(
                user2WalletId,
                new BigDecimal("500.00"),
                UUID.randomUUID().toString(),
                "Overdraft transfer"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/transfer")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient")));
    }

    // ── 7. Transaction History ────────────────────────────────────────────────

    @Test
    @Order(60)
    @DisplayName("GET /wallets/{id}/transactions - Returns all transactions")
    void getTransactionHistory() throws Exception {
        mockMvc.perform(get("/wallets/" + user1WalletId + "/transactions")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @Order(61)
    @DisplayName("GET /wallets/{id}/transactions - Cannot access other user's history")
    void getOtherUserTransactionHistory() throws Exception {
        mockMvc.perform(get("/wallets/" + user2WalletId + "/transactions")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isForbidden());
    }

    // ── 8. Transaction Lookup by Reference ID ─────────────────────────────────

    @Test
    @Order(70)
    @DisplayName("GET /transactions/{refId} - Find transaction by referenceId")
    void findTransactionByReferenceId() throws Exception {
        // First create a transaction
        String refId = UUID.randomUUID().toString();
        TransactionRequest request = new TransactionRequest(
                new BigDecimal("5.00"),
                refId,
                "Lookup test"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/deposit")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Now look it up
        mockMvc.perform(get("/transactions/" + refId)
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId").value(refId))
                .andExpect(jsonPath("$.amount").value(5.00));
    }

    @Test
    @Order(71)
    @DisplayName("GET /transactions/{refId} - Non-existent referenceId returns 404")
    void findNonExistentTransaction() throws Exception {
        mockMvc.perform(get("/transactions/NON_EXISTENT_REF_ID")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());
    }

    // ── 9. Health Check ────────────────────────────────────────────────────────

    @Test
    @Order(80)
    @DisplayName("GET /actuator/health - Health endpoint accessible")
    void healthCheck() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
