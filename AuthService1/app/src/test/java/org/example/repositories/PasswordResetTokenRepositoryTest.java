package org.example.repositories;


import org.example.entities.PasswordResetToken;
import org.example.entities.UserInfo;
import org.example.repository.PasswordResetTokenRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@TestPropertySource(properties = "spring.flyway.enabled=false")
public class PasswordResetTokenRepositoryTest {
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    TestEntityManager testEntityManager;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldDeleteExpiredTokens() {
        UserInfo user1 = userRepository.save(UserInfo.builder().userId("u-1").username("bob").password("Password123!").email("test@gmail.com").phoneNumber("1234567891").build());

        PasswordResetToken expiredToken = new PasswordResetToken();
        expiredToken.setId("t-1");
        expiredToken.setToken("expired-token");
        expiredToken.setUser(user1);
        expiredToken.setExpiresAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(expiredToken);


        UserInfo user2 = userRepository.save(UserInfo.builder().userId("u-2").username("alice").password("Password123!").email("alice@gmail.com").phoneNumber("1234567892").build());

        PasswordResetToken validToken = new PasswordResetToken();
        validToken.setId("t-2");
        validToken.setToken("valid-token");
        validToken.setUser(user2);
        validToken.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(validToken);


        int deleted = passwordResetTokenRepository.deleteExpiredTokens(Instant.now());

        assertEquals(1, deleted);
        assertTrue(passwordResetTokenRepository.findByToken("expired-token").isEmpty());
        assertTrue(passwordResetTokenRepository.findByToken("valid-token").isPresent());


    }

    @Test
    void shouldCascadeDeleteWhenUserIsDeleted() {
        UserInfo user = userRepository.save(UserInfo.builder().userId("u-3").username("bob1").password("Password123!").email("test1@gmail.com").phoneNumber("12134567891").build());

        PasswordResetToken token = new PasswordResetToken();
        token.setId("t-3");
        token.setToken("cc-token");
        token.setUser(user);
        token.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(token);

        testEntityManager.flush();
        testEntityManager.clear();



        userRepository.deleteById("u-3");
        testEntityManager.flush();
        testEntityManager.flush();

        Optional<PasswordResetToken> found = passwordResetTokenRepository.findByToken("cc-token");
        assertTrue(found.isEmpty());
    }


}
