package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.repository.OutboxRepository;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.example.request.AuthRequestDTO;
import org.example.request.RefreshTokenRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {"spring.flyway.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RefreshTokenIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    private UserInfo testUser;
    private final String rawPassword = "Password123";

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        outboxRepository.deleteAll();
        userRepository.deleteAll();
        testUser = userRepository.save(UserInfo.builder()
                .userId(UUID.randomUUID().toString())
                .username("token_tester")
                .password(passwordEncoder.encode(rawPassword))
                .email("token_tester@example.com")
                .phoneNumber("+12345678901")
                .roles(new HashSet<>(Set.of(UserRole.ROLE_USER)))
                .build());
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    @DisplayName("Login returns valid JWT access token and saves SHA-256 hashed refresh token")
    void login_returnsValidTokensAndPersistsHashedRefreshToken() throws Exception {

        AuthRequestDTO loginRequest = AuthRequestDTO.builder().username("token_tester").password(rawPassword).build();

        MvcResult result = mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

        String rawRefreshToken = response.get("token").asText();

        String expectedHash = hashToken(rawRefreshToken);
        Optional<RefreshToken> storedToken = refreshTokenRepository.findByTokenHash(expectedHash);
        assertTrue(storedToken.isPresent(), "Hashed refresh token must exist in the database");
        assertEquals(testUser.getUserId(), storedToken.get().getUserInfo().getUserId(), "User IDs don't match");

    }


    @Test
    @DisplayName("Rotating a refresh token revokes old token and issues a new valid pair")
    void refreshToken_rotatesTokenSuccessfully() throws Exception {
        // initial login
        AuthRequestDTO loginRequest = AuthRequestDTO.builder().username("token_tester").password(rawPassword).build();

        MvcResult loginResult = mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String rawToken1 = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();
        String hash1 = hashToken(rawToken1);

        //rotate token
        RefreshTokenRequestDTO rotateReq = new RefreshTokenRequestDTO(rawToken1);
        MvcResult rotateResult = mockMvc.perform(post("/auth/v1/refreshToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rotateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String rawToken2 = objectMapper.readTree(rotateResult.getResponse().getContentAsString()).get("token").asText();
        String hash2 = hashToken(rawToken2);

        assertNotEquals(rawToken1, rawToken2, "New refresh token must be different from the old one");
        assertTrue(refreshTokenRepository.findByTokenHash(hash1).isEmpty(), "Old token hash must be removed");
        assertTrue(refreshTokenRepository.findByTokenHash(hash2).isPresent(), "New token hash must be saved");

    }

    @Test
    @DisplayName("Replaying an already-rotated refresh token fails with 401 Unauthorized")
    void refreshToken_rejectsReuseOfRotatedToken() throws Exception {
        // 1. Login
        AuthRequestDTO loginRequest = AuthRequestDTO.builder()
                .username("token_tester")
                .password(rawPassword)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        // 2. First rotation (valid)
        RefreshTokenRequestDTO rotateRequest = new RefreshTokenRequestDTO(rawToken);
        mockMvc.perform(post("/auth/v1/refreshToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rotateRequest)))
                .andExpect(status().isOk());
        // 3. Replay attack: attempt to use the same old token again
        mockMvc.perform(post("/auth/v1/refreshToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rotateRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401));
    }


    @DisplayName("Expired refresh token returns 401 Unauthorized and is deleted from database")
    void refreshToken_rejectsExpiredToken() throws Exception {
        String expiredRawToken = UUID.randomUUID().toString();
        String expiredHash = hashToken(expiredRawToken);

        refreshTokenRepository.save(RefreshToken.builder()
                .userInfo(testUser)
                .tokenHash(expiredHash)
                .expiryDate(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        RefreshTokenRequestDTO req = new RefreshTokenRequestDTO(expiredRawToken);

        mockMvc.perform(post("/auth/v1/refreshToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                ).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401));

        assertTrue(refreshTokenRepository.findByTokenHash(expiredHash).isEmpty(), "Expired token should have been deleted upon access attempt");
    }

    @Test
    @DisplayName("Concurrent rotation with the same token: exactly one request succeeds and race is prevented")
    void refreshToken_concurrentRotation_onlyOneSucceeds() throws Exception {
        // 1. Login to get a valid token
        AuthRequestDTO loginRequest = AuthRequestDTO.builder()
                .username("token_tester")
                .password(rawPassword)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                ).andExpect(status().isOk())
                .andReturn();

        String rawToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        // 2. concurrency test
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failure = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    //runner arrives at starting point
                    readyLatch.countDown();
                    // runner waits for pistol to shoot
                    startLatch.await();
                    RefreshTokenRequestDTO request = new RefreshTokenRequestDTO(rawToken);
                    MvcResult result = mockMvc.perform(post("/auth/v1/refreshToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))).andReturn();

                    int status = result.getResponse().getStatus();

                    if (status == 200) {
                        success.incrementAndGet();
                    } else if (status == 401 || status == 409) {
                        failure.incrementAndGet();
                    }
                } catch (Exception e) {
                    failure.incrementAndGet();
                }
            }));
        }
// wait until both worker threads are actively parked at starting line
        readyLatch.await();
        // fire the starter pistol both threads release simultaneously
        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();


        assertEquals(1, success.get(), "Exactly one concurrent rotation request must succeed");
        assertEquals(1, failure.get(), "The duplicate concurrent rotation request must be rejected");


        // Exactly one valid token should remain in DB for this user
        List<RefreshToken> remainingTokens = refreshTokenRepository.findByUserInfo(testUser);
        assertEquals(1, remainingTokens.size(), "User should have exactly one active refresh token");

    }
}
