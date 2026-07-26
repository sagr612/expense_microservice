package sagar.userService.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.*;
import lombok.*;
import sagar.userService.entities.UserInfo;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserInfoDto {

    @NotBlank(message = "User ID is required")
    @Size(max = 100, message = "User ID must not exceed 100 characters")
    private String userId;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @NotNull(message = "Phone number is required")
    @Min(value = 1000000000L, message = "Phone number must be at least 10 digits")
    @Max(value = 9999999999L, message = "Phone number must not exceed 10 digits")
    private Long phoneNumber;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 500, message = "Profile picture URL must not exceed 500 characters")
    private String profilePic;
    private String profileImagePublicId;

    // Convert DTO to Entity
    public UserInfo toEntity() {
        return UserInfo.builder()
                .userId(userId)
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber(phoneNumber)
                .email(email)
                .profilePic(profilePic)
                .profileImagePublicId(profileImagePublicId)
                .build();
    }

    // Convert Entity to DTO
    public static UserInfoDto fromEntity(UserInfo userInfo) {
        return UserInfoDto.builder()
                .userId(userInfo.getUserId())
                .firstName(userInfo.getFirstName())
                .lastName(userInfo.getLastName())
                .phoneNumber(userInfo.getPhoneNumber())
                .email(userInfo.getEmail())
                .profilePic(userInfo.getProfilePic())
                .profileImagePublicId(userInfo.getProfileImagePublicId())
                .build();
    }
}