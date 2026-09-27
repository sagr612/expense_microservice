package org.example.repository;

import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long>
{

    void deleteByUserInfo(UserInfo userInfo);

    @Modifying
    @Query("""
            DELETE FROM RefreshToken t WHERE t.expiryDate IS NOT NULL AND  t.expiryDate  < :now
            """)
    int deleteByExpiryDateBefore(@Param("now") Instant now);

    List<RefreshToken> findByUserInfo(UserInfo userInfo);

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
