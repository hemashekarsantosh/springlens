package io.springlens.auth.repository;

import io.springlens.auth.entity.JwtRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for JWT Refresh Token tracking.
 */
@Repository
public interface JwtRefreshTokenRepository extends JpaRepository<JwtRefreshToken, UUID> {

    /**
     * Find a valid refresh token by JTI (JWT ID claim).
     * Valid = not expired, not rotated, not revoked.
     */
    @Query("""
            SELECT t FROM JwtRefreshToken t
            WHERE t.tokenJti = :jti
              AND t.revokedAt IS NULL
              AND t.rotatedAt IS NULL
              AND t.expiresAt > CURRENT_TIMESTAMP
            """)
    Optional<JwtRefreshToken> findValidByJti(@Param("jti") String jti);

    /**
     * Find all valid refresh tokens for a user (for logout/revoke all).
     */
    @Query("""
            SELECT t FROM JwtRefreshToken t
            WHERE t.userId = :userId
              AND t.revokedAt IS NULL
              AND t.rotatedAt IS NULL
              AND t.expiresAt > CURRENT_TIMESTAMP
            """)
    java.util.List<JwtRefreshToken> findValidByUserId(@Param("userId") UUID userId);

    /**
     * Find a specific token by JTI regardless of status (for rotation checks).
     */
    Optional<JwtRefreshToken> findByTokenJti(String tokenJti);
}
