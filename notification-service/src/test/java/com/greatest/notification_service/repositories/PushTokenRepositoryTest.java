package com.greatest.notification_service.repositories;

import com.greatest.notification_service.entities.PushToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PushTokenRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Test
    void findByUserId_returnsToken_whenExists() {
        PushToken token = PushToken.builder()
                .userId("user-1")
                .expoToken("ExponentPushToken[abc]")
                .build();
        entityManager.persist(token);
        entityManager.flush();

        Optional<PushToken> result = pushTokenRepository.findByUserId("user-1");

        assertThat(result).isPresent();
        assertThat(result.get().getExpoToken()).isEqualTo("ExponentPushToken[abc]");
    }

    @Test
    void findByUserId_returnsEmpty_whenNoTokenForUser() {
        Optional<PushToken> result = pushTokenRepository.findByUserId("nobody");

        assertThat(result).isEmpty();
    }

    @Test
    void deleteByUserId_removesOnlyThatUsersToken() {
        entityManager.persist(PushToken.builder().userId("user-1").expoToken("token-1").build());
        entityManager.persist(PushToken.builder().userId("user-2").expoToken("token-2").build());
        entityManager.flush();

        pushTokenRepository.deleteByUserId("user-1");
        entityManager.flush();

        assertThat(pushTokenRepository.findByUserId("user-1")).isEmpty();
        assertThat(pushTokenRepository.findByUserId("user-2")).isPresent();
    }

    @Test
    void deleteByUserId_doesNothing_whenNoMatchingToken() {
        entityManager.persist(PushToken.builder().userId("user-1").expoToken("token-1").build());
        entityManager.flush();

        pushTokenRepository.deleteByUserId("unknown");
        entityManager.flush();

        assertThat(pushTokenRepository.findByUserId("user-1")).isPresent();
    }

    @Test
    void enforcesUniqueUserId_constraintOnSave() {
        entityManager.persist(PushToken.builder().userId("user-1").expoToken("token-1").build());
        entityManager.flush();

        PushToken duplicate = PushToken.builder().userId("user-1").expoToken("token-2").build();

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> {
                    entityManager.persist(duplicate);
                    entityManager.flush();
                });
    }
}