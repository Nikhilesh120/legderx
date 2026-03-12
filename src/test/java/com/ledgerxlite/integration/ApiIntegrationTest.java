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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private static String user1Token;
    private static Long user1WalletId;

    private static String user2Token;
    private static Long user2WalletId;

    // ─────────────────────────────────────────────
    // 1. Registration
    // ─────────────────────────────────────────────

    @Test
    @Order(1)
    void registerUser1() throws Exception {
        RegisterUserRequest req =
                new RegisterUserRequest("user1@test.com", "Password123!", "USD");

        MvcResult res = mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        var json = objectMapper.readTree(res.getResponse().getContentAsString());
        user1WalletId = json.get("walletId").asLong();
    }

    @Test
    @Order(2)
    void registerUser2() throws Exception {
        RegisterUserRequest req =
                new RegisterUserRequest("user2@test.com", "Password456!", "USD");

        MvcResult res = mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        var json = objectMapper.readTree(res.getResponse().getContentAsString());
        user2WalletId = json.get("walletId").asLong();
    }

    // ─────────────────────────────────────────────
    // 2. Authentication
    // ─────────────────────────────────────────────

    @Test
    @Order(10)
    void loginUser1() throws Exception {
        String body = """
                { "email": "user1@test.com", "password": "Password123!" }
                """;

        MvcResult res = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        user1Token = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("token").asText();
    }

    @Test
    @Order(11)
    void loginUser2() throws Exception {
        String body = """
                { "email": "user2@test.com", "password": "Password456!" }
                """;

        MvcResult res = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        user2Token = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("token").asText();
    }

    // ─────────────────────────────────────────────
    // 3. Wallet Access Control (FIXED SECTION)
    // ─────────────────────────────────────────────

    @Test
    @Order(20)
    void cannotAccessOtherUsersWallet() throws Exception {
        mockMvc.perform(get("/wallets/" + user2WalletId)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(
                            status == 403 || status == 404,
                            "Expected 403 or 404 but got " + status
                    );
                });
    }

    // ─────────────────────────────────────────────
    // 4. Deposit
    // ─────────────────────────────────────────────

    @Test
    @Order(30)
    void depositSuccess() throws Exception {
        TransactionRequest req = new TransactionRequest(
                new BigDecimal("100.00"),
                UUID.randomUUID().toString(),
                "deposit"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/deposit")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────
    // 5. Withdraw
    // ─────────────────────────────────────────────

    @Test
    @Order(40)
    void withdrawSuccess() throws Exception {
        TransactionRequest req = new TransactionRequest(
                new BigDecimal("20.00"),
                UUID.randomUUID().toString(),
                "withdraw"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/withdraw")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────
    // 6. Transfer
    // ─────────────────────────────────────────────

    @Test
    @Order(50)
    void transferSuccess() throws Exception {
        TransferRequest req = new TransferRequest(
                user2WalletId,
                new BigDecimal("10.00"),
                UUID.randomUUID().toString(),
                "transfer"
        );

        mockMvc.perform(post("/wallets/" + user1WalletId + "/transfer")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────
    // 7. Health
    // ─────────────────────────────────────────────

    @Test
    @Order(99)
    void healthCheck() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
