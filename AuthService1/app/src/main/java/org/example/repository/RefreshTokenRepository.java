package org.example.repository;

import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long>
{
    Optional<RefreshToken> findByToken(String token);

    void deleteByUserInfo(UserInfo userInfo);

    void deleteByExpiryDateBefore(Instant now);

    Optional<RefreshToken> findByUserInfo(UserInfo userInfo);

}
