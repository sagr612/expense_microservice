package sagar.userService.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.entities.UserInfo;
import sagar.userService.exceptions.DuplicateResourceException;
import sagar.userService.repository.UserRepository;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserDomainService {
    private final UserRepository userRepository;

    public void validateUserDto(UserInfoDto dto) {
        if (dto.getUserId() == null || dto.getUserId().isBlank()) {
            throw new IllegalArgumentException("User ID cannot be blank");
        }
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }
        if (dto.getPhoneNumber() == null) {
            throw new IllegalArgumentException("Phone number cannot be null");
        }
    }


    public UserInfo updateExistingUser(UserInfo existingUser, UserInfoDto dto) {
        log.info("Updating existing user: {}", existingUser.getUserId());

        if (dto.getFirstName() != null) {
            existingUser.setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            existingUser.setLastName(dto.getLastName());
        }
        if (dto.getPhoneNumber() != null) {
            existingUser.setPhoneNumber(dto.getPhoneNumber());
        }
        if (dto.getEmail() != null) {
            existingUser.setEmail(dto.getEmail());
        }
        if (dto.getProfilePic() != null) {
            existingUser.setProfilePic(dto.getProfilePic());
        }
        return existingUser;
    }

    UserInfo createNewUser(UserInfoDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("Email already in use: " + dto.getEmail());
        }
        return dto.toEntity();
    }

}
