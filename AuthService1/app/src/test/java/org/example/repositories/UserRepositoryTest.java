package org.example.repositories;

import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("testdb")
                    .withUsername("postgres")
                    .withPassword("123");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private UserRepository userRepository;

    private UserInfo user;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
        user = UserInfo.builder()
                .userId("user-1")
                .username("john")
                .password("password")
                .roles(Set.of(UserRole.ROLE_USER))
                .phoneNumber(9999999999L)
                .email("john@test.com")
                .build();
        userRepository.save(user);
    }

    //---------------------------------------------------------
    // findByUsername
    //---------------------------------------------------------

    @Test
    void shouldFindUserByUsername() {

        UserInfo result = userRepository.findByUsername("john");

        assertNotNull(result);
        assertEquals("john", result.getUsername());
        assertEquals("user-1", result.getUserId());
    }

    @Test
    void shouldReturnNullWhenUsernameDoesNotExist() {

        UserInfo result = userRepository.findByUsername("abc");
        assertNull(result);
    }

    //---------------------------------------------------------
    // existsByUsername
    //---------------------------------------------------------

    @Test
    void shouldReturnTrueWhenUsernameExists() {

        assertTrue(userRepository.existsByUsername("john"));
    }

    @Test
    void shouldReturnFalseWhenUsernameDoesNotExist() {

        assertFalse(userRepository.existsByUsername("xyz"));
    }

    //---------------------------------------------------------
    // existsByEmail
    //---------------------------------------------------------

    @Test
    void shouldReturnTrueWhenEmailExists() {

        assertTrue(userRepository.existsByEmail("john@test.com"));
    }

    @Test
    void shouldReturnFalseWhenEmailDoesNotExist() {

        assertFalse(userRepository.existsByEmail("abc@test.com"));
    }

    //---------------------------------------------------------
    // existsByPhoneNumber
    //---------------------------------------------------------

    @Test
    void shouldReturnTrueWhenPhoneExists() {
        assertTrue(userRepository.existsByPhoneNumber(9999999999L));
    }

    @Test
    void shouldReturnFalseWhenPhoneDoesNotExist() {
        assertFalse(userRepository.existsByPhoneNumber(8888888888L));
    }

    //---------------------------------------------------------
    // deleteByUserId
    //---------------------------------------------------------

    @Test
    void shouldDeleteUserByUserId() {
        userRepository.deleteByUserId("user-1");
        assertFalse(userRepository.existsByUsername("john"));
        assertNull(userRepository.findByUsername("john"));
    }

    @Test
    void shouldDoNothingWhenDeletingUnknownUser() {
        userRepository.deleteByUserId("unknown");
        assertTrue(userRepository.existsByUsername("john"));
    }

}