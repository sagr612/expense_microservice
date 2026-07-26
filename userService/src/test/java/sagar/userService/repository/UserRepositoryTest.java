package sagar.userService.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import sagar.userService.entities.UserInfo;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUserId_returnsUser_whenUserIdExists() {
        UserInfo user = UserInfo.builder()
                .userId("user-1")
                .firstName("Alice")
                .lastName("Smith")
                .phoneNumber(1000000001L)
                .email("alice@example.com")
                .build();
        entityManager.persist(user);
        entityManager.flush();

        Optional<UserInfo> result = userRepository.findByUserId("user-1");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findByUserId_returnsEmpty_whenUserIdDoesNotExist() {
        Optional<UserInfo> result = userRepository.findByUserId("does-not-exist");

        assertThat(result).isEmpty();
    }

    @Test
    void findByEmail_returnsUser_whenEmailExists() {
        UserInfo user = UserInfo.builder()
                .userId("user-2")
                .firstName("Bob")
                .lastName("Jones")
                .phoneNumber(1000000002L)
                .email("bob@example.com")
                .build();
        entityManager.persist(user);
        entityManager.flush();

        Optional<UserInfo> result = userRepository.findByEmail("bob@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo("user-2");
    }

    @Test
    void findByEmail_returnsEmpty_whenEmailDoesNotExist() {
        Optional<UserInfo> result = userRepository.findByEmail("nobody@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void existsByEmail_returnsTrue_whenEmailExists() {
        UserInfo user = UserInfo.builder()
                .userId("user-3")
                .firstName("Carol")
                .lastName("Davis")
                .phoneNumber(1000000003L)
                .email("carol@example.com")
                .build();
        entityManager.persist(user);
        entityManager.flush();

        Boolean exists = userRepository.existsByEmail("carol@example.com");

        assertThat(exists).isTrue();
    }

    @Test
    void existsByEmail_returnsFalse_whenEmailDoesNotExist() {
        Boolean exists = userRepository.existsByEmail("nobody@example.com");

        assertThat(exists).isFalse();
    }
}