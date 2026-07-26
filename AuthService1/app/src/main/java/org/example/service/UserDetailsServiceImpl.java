package org.example.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.eventProducer.UserInfoEvent;
import org.example.eventProducer.UserInfoProducer;
import org.example.exceptions.UserAlreadyExistsException;
import org.example.model.PasswordDto;
import org.example.model.SignUpResponse;
import org.example.model.UserInfoDto;
import org.example.repository.UserRepository;
import org.example.response.JwtResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@AllArgsConstructor
@Data
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private final UserRepository userRepository;

    @Autowired
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private final UserInfoProducer userInfoProducer;

    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserInfo user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found...");
        }
        return new CustomUserDetails(user);
    }

    public UserInfo checkIfUserAlreadyExist(String username) {
        return userRepository.findByUsername(username);
    }

    public UserInfo signupUser(UserInfoDto userInfoDto) {
        //        ValidationUtil.validateUserAttributes(userInfoDto);
        userInfoDto.setPassword(passwordEncoder.encode(userInfoDto.getPassword()));
        if (Objects.nonNull(checkIfUserAlreadyExist(userInfoDto.getUsername()))) {
            throw new UserAlreadyExistsException("User with this username already exists");
        }
        String userId = UUID.randomUUID().toString();


        UserInfo userInfo = UserInfo.builder().userId(userId).username(userInfoDto.getUsername()).password(userInfoDto.getPassword()).roles(Set.of(UserRole.ROLE_USER)).phoneNumber(userInfoDto.getPhoneNumber()).email(userInfoDto.getEmail()).build();

        if (userRepository.existsByPhoneNumber(userInfoDto.getPhoneNumber())) {
            throw new UserAlreadyExistsException("User with this phone number already exists");
        }

        if (userRepository.existsByEmail(userInfoDto.getEmail())) {
            throw new UserAlreadyExistsException("User with this email  already exists");
        }

        UserInfo user=userRepository.save(userInfo);
        userInfoProducer.sendEventToKafka(userInfoEventToPublish(userInfoDto, userId));
        return user;
    }

    @Transactional
    public JwtResponseDTO changePassword(PasswordDto passwordDto) {

        String userId = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        UserInfo user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!passwordEncoder.matches(passwordDto.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("Incorrect old password");
        }

        if (passwordDto.getOldPassword().equals(passwordDto.getNewPassword())) {
            throw new IllegalArgumentException("New password cannot be same as old password");
        }

        user.setPassword(passwordEncoder.encode(passwordDto.getNewPassword()));


        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
        return JwtResponseDTO.builder()
                .accessToken(jwtService.GenerateToken(user.getUsername(), user.getUserId(), user.getRoles().stream().map(UserRole::name).toList()))
                .token(refreshToken.getToken())
                .build();

    }

    private UserInfoEvent userInfoEventToPublish(UserInfoDto userInfoDto, String userId) {
        return UserInfoEvent.builder().userId(userId).firstName(userInfoDto.getFirstName()).lastName(userInfoDto.getLastName()).email(userInfoDto.getEmail()).phoneNumber(userInfoDto.getPhoneNumber()).build();
    }

//    public String getUserByUsername(String name) {
//
//        return Optional.of(userRepository.findByUsername(name)).map(UserInfo::getUserId).orElse(null);
//    }
    public String getUserByUsername(String name) {

        return Optional.ofNullable(userRepository.findByUsername(name))
                .map(UserInfo::getUserId)
                .orElse(null);
    }

    public JwtResponseDTO changeUsername(String newUsername, String currentUsername) {
        if (userRepository.existsByUsername(newUsername)) {
            throw new RuntimeException("Username already taken");
        }
        UserInfo user = userRepository.findByUsername(currentUsername);
        if (user == null) {
            throw new RuntimeException("User not found.");
        }

        user.setUsername(newUsername);
        userRepository.save(user);

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
        return JwtResponseDTO.builder()
                .accessToken(jwtService.GenerateToken(user.getUsername(), user.getUserId(), user.getRoles().stream().map(UserRole::name).toList()))
                .token(refreshToken.getToken())
                .build();
    }
}
