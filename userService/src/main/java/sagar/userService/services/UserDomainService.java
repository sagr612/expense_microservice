package sagar.userService.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.entities.UserInfo;
import sagar.userService.exceptions.DuplicateResourceException;
import sagar.userService.repository.UserRepository;
import sagar.userService.dtos.request.CreateUserRequest;
import sagar.userService.dtos.request.UpdateUserRequest;


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
        if (dto.getPhoneNumber() == null || dto.getPhoneNumber().isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be blank");
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
        if (userRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
            throw new DuplicateResourceException("Phone number already in use: " + dto.getPhoneNumber());
        }
        return dto.toEntity();
    }

    public UserInfo createNewUser(CreateUserRequest dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("Email already in use: " + dto.getEmail());
        }
        if (userRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
            throw new DuplicateResourceException("Phone no. already in use: " + dto.getPhoneNumber());
        }
        return UserInfo.builder()
                .userId(dto.getUserId())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .phoneNumber(dto.getPhoneNumber())
                .build();
    }

    public UserInfo updateExistingUser(UserInfo existingUser, UpdateUserRequest dto) {

        if (dto.getPhoneNumber() != null && !dto.getPhoneNumber().equals(existingUser.getPhoneNumber())) {
            if(userRepository.existsByPhoneNumberAndUserIdNot(dto.getPhoneNumber(), existingUser.getUserId())) {
                throw  new DuplicateResourceException("Phone number already in use: " + dto.getPhoneNumber());
            }
            existingUser.setPhoneNumber(dto.getPhoneNumber());
        }
        if (dto.getEmail() != null && !dto.getEmail().equals(existingUser.getEmail())) {
            if(userRepository.existsByEmailAndUserIdNot(dto.getEmail(), existingUser.getUserId())) {
                throw  new DuplicateResourceException("Email already in use: " + dto.getEmail());
            }
            existingUser.setEmail(dto.getEmail());
        }

        if (dto.getFirstName() != null) {
            existingUser.setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            existingUser.setLastName(dto.getLastName());
        }
        return existingUser;
    }



}
