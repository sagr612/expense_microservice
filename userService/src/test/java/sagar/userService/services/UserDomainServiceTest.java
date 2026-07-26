package sagar.userService.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.entities.UserInfo;
import sagar.userService.exceptions.DuplicateResourceException;
import sagar.userService.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDomainServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDomainService userDomainService;

    private UserInfoDto validDto;

    @BeforeEach
    void setUp() {
        validDto = UserInfoDto.builder()
                .userId("user-1")
                .firstName("Sagar")
                .lastName("Sharma")
                .phoneNumber(9999999999L)
                .email("sagar@example.com")
                .build();
    }

    @Nested
    class ValidateUserDto {

        @Test
        void doesNotThrow_whenDtoIsValid() {
            assertThatCode(() -> userDomainService.validateUserDto(validDto))
                    .doesNotThrowAnyException();
        }

        @Test
        void throwsIllegalArgument_whenUserIdIsNull() {
            validDto.setUserId(null);

            assertThatThrownBy(() -> userDomainService.validateUserDto(validDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User ID");
        }

        @Test
        void throwsIllegalArgument_whenUserIdIsBlank() {
            validDto.setUserId("   ");

            assertThatThrownBy(() -> userDomainService.validateUserDto(validDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User ID");
        }

        @Test
        void throwsIllegalArgument_whenEmailIsNull() {
            validDto.setEmail(null);

            assertThatThrownBy(() -> userDomainService.validateUserDto(validDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email");
        }

        @Test
        void throwsIllegalArgument_whenPhoneNumberIsNull() {
            validDto.setPhoneNumber(null);

            assertThatThrownBy(() -> userDomainService.validateUserDto(validDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Phone number");
        }
    }

    @Nested
    class UpdateExistingUser {

        @Test
        void updatesAllFields_whenAllFieldsProvided() {
            UserInfo existing = UserInfo.builder()
                    .userId("user-1")
                    .firstName("Old")
                    .lastName("Name")
                    .phoneNumber(1111111111L)
                    .email("old@example.com")
                    .profilePic("old-pic-url")
                    .build();

            UserInfoDto updateDto = UserInfoDto.builder()
                    .userId("user-1")
                    .firstName("New")
                    .lastName("Name2")
                    .phoneNumber(2222222222L)
                    .email("new@example.com")
                    .profilePic("new-pic-url")
                    .build();

            UserInfo result = userDomainService.updateExistingUser(existing, updateDto);

            assertThat(result.getFirstName()).isEqualTo("New");
            assertThat(result.getLastName()).isEqualTo("Name2");
            assertThat(result.getPhoneNumber()).isEqualTo(2222222222L);
            assertThat(result.getEmail()).isEqualTo("new@example.com");
            assertThat(result.getProfilePic()).isEqualTo("new-pic-url");
        }

        @Test
        void keepsExistingValues_whenIncomingFieldsAreNull() {
            // This is the regression test for the null-overwrite bug we discussed —
            // a partial DTO (e.g. from a Kafka event) must not wipe out real data.
            UserInfo existing = UserInfo.builder()
                    .userId("user-1")
                    .firstName("Sagar")
                    .lastName("Sharma")
                    .phoneNumber(9999999999L)
                    .email("sagar@example.com")
                    .profilePic("existing-pic-url")
                    .build();

            UserInfoDto partialDto = UserInfoDto.builder()
                    .userId("user-1")
                    .firstName(null)
                    .lastName(null)
                    .phoneNumber(null)
                    .email("updated@example.com") // only email changes
                    .profilePic(null)
                    .build();

            UserInfo result = userDomainService.updateExistingUser(existing, partialDto);

            assertThat(result.getFirstName()).isEqualTo("Sagar");
            assertThat(result.getLastName()).isEqualTo("Sharma");
            assertThat(result.getPhoneNumber()).isEqualTo(9999999999L);
            assertThat(result.getEmail()).isEqualTo("updated@example.com");
            assertThat(result.getProfilePic()).isEqualTo("existing-pic-url");
        }
    }

    @Nested
    class CreateNewUser {

        @Test
        void createsUser_whenEmailNotTaken() {
            when(userRepository.existsByEmail(validDto.getEmail())).thenReturn(false);

            UserInfo result = userDomainService.createNewUser(validDto);

            assertThat(result.getUserId()).isEqualTo("user-1");
            assertThat(result.getEmail()).isEqualTo("sagar@example.com");
        }

        @Test
        void throwsDuplicateResource_whenEmailAlreadyExists() {
            when(userRepository.existsByEmail(validDto.getEmail())).thenReturn(true);

            assertThatThrownBy(() -> userDomainService.createNewUser(validDto))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining(validDto.getEmail());
        }
    }
}
