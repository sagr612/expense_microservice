package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entities.UserInfo;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void deleteUserData(String userId) {

        log.info(
                "Deleting data for user {}",
                userId
        );

        Optional<UserInfo> user= userRepository.findById(userId);
        if(user.isEmpty())return;

        refreshTokenRepository.deleteByUserInfo(user.get());

        userRepository.deleteByUserId(userId);

        log.info(
                "Successfully deleted all data for user {}",
                userId
        );
    }
}