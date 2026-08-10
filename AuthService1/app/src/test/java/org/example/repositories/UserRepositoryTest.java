package org.example.repositories;

import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UserRepositoryTest {


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
                .phoneNumber("9999999999")
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
        assertTrue(userRepository.existsByPhoneNumber("9999999999"));
    }

    @Test
    void shouldReturnFalseWhenPhoneDoesNotExist() {
        assertFalse(userRepository.existsByPhoneNumber("8888888888"));
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