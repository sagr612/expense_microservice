package org.example.repository;

import org.example.entities.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByToken(String token);

    @Transactional
    @Modifying
    @Query("""
            DELETE FROM PasswordResetToken prt 
            WHERE prt.expiresAt IS NOT NULL
            AND prt.user.userId = :id
            """)
    void deleteByUserId( @Param("id") String id);

    @Transactional
    @Modifying
    @Query("""
            DELETE FROM PasswordResetToken prt 
            WHERE prt.expiresAt IS NOT NULL
            AND prt.expiresAt < :now
            """)
    int deleteExpiredTokens(@Param("now") Instant now);

    Optional<PasswordResetToken> findByUserEmail(String email);
}