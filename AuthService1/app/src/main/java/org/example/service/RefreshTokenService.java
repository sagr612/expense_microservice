package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.exceptions.TokenExpiredException;
import org.example.model.RefreshTokenResponse;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.refresh-expiration:604800000}")
    private Long refreshTokenExpiration;

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
    @Transactional
    public RefreshTokenResponse createRefreshToken(String username) {
        UserInfo userInfo = userRepository.findByUsername(username);

        if (userInfo == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        refreshTokenRepository.deleteByUserInfo(userInfo);

//        Optional<RefreshToken> existingToken = refreshTokenRepository.findByUserInfo(userInfo);
//        existingToken.ifPresent(refreshTokenRepository::delete);
        refreshTokenRepository.flush();

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hash(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .userInfo(userInfo)
                .tokenHash(tokenHash)
                .expiryDate(Instant.now().plusMillis(refreshTokenExpiration))
                .build();
        RefreshToken saved=refreshTokenRepository.save(refreshToken);


        return  RefreshTokenResponse.builder().token(rawToken).expiryDate(saved.getExpiryDate()).build();
    }




    public Optional<RefreshToken> findByToken(String token) {
        String tokenHash = hash(token);
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }

    public RefreshToken verifyExpirationAndDeleteIfExpired(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new TokenExpiredException("Refresh token expired. Please login again.");
        }
        return token;
    }

    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteByExpiryDateBefore(Instant.now());
    }

    public void delete(RefreshToken oldToken) {
        refreshTokenRepository.delete(oldToken);
    }
}
