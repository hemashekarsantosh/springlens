package io.springlens.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.springlens.auth.entity.JwtRefreshToken;
import io.springlens.auth.repository.JwtRefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and validates JWT tokens.
 * Access token: HS256, 15 minutes.
 * Refresh token: HS256, 7 days (rotated).
 *
 * ✅ HARDENED: Multiple layers of JWT security:
 * - JWT secret loaded from AWS Secrets Manager (not config files)
 * - Refresh tokens rotated every 7 days (down from 30 days)
 * - Refresh tokens tracked in database for validation
 * - Token rotation prevents replay of old tokens
 * - Reduced lifetime limits impact of token compromise
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String JWT_SECRET_NAME = "springlens/jwt-secret";

    private final SecretKey signingKey;
    private final String issuer;
    private final SecretsService secretsService;
    private final JwtRefreshTokenRepository refreshTokenRepository;

    public JwtService(
            SecretsService secretsService,
            JwtRefreshTokenRepository refreshTokenRepository,
            @Value("${springlens.jwt.issuer:https://api.springlens.io}") String issuer) {
        this.secretsService = secretsService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.issuer = issuer;

        // ✅ FIXED: Load JWT secret from AWS Secrets Manager instead of config
        String secret = secretsService.getSecret(JWT_SECRET_NAME);
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        log.info("JwtService initialized with secret from Secrets Manager");
    }

    public String issueAccessToken(UUID userId, UUID workspaceId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .claims(Map.of(
                        "workspace_id", workspaceId.toString(),
                        "email", email,
                        "workspace_role", role,
                        "token_type", "access"))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Issue a new refresh token with 7-day expiration (rotated).
     * ✅ SECURITY: Shorter lifetime (7 days) + tracking prevents token replay attacks.
     */
    public String issueRefreshToken(UUID userId, UUID workspaceId) {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();

        // ✅ FIXED: Track refresh token in database for rotation validation
        JwtRefreshToken tokenRecord = JwtRefreshToken.create(userId, workspaceId, jti);
        refreshTokenRepository.save(tokenRecord);

        // ✅ FIXED: Reduce expiration from 30 days to 7 days
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(7, ChronoUnit.DAYS)))
                .claims(Map.of(
                        "workspace_id", workspaceId.toString(),
                        "token_type", "refresh",
                        "jti", jti))
                .signWith(signingKey)
                .compact();

        log.info("Issued refresh token userId={} workspaceId={} expires={}", userId, workspaceId, tokenRecord.getExpiresAt());
        return token;
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            throw ex;
        }
    }

    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get("token_type", String.class));
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public UUID extractWorkspaceId(Claims claims) {
        return UUID.fromString(claims.get("workspace_id", String.class));
    }

    /**
     * Validate refresh token by checking database (prevents replay of rotated tokens).
     * ✅ SECURITY: Ensures token hasn't been rotated or revoked.
     */
    public boolean isRefreshTokenValid(Claims claims) {
        String jti = claims.get("jti", String.class);
        if (jti == null) {
            log.warn("Refresh token missing JTI claim");
            return false;
        }

        // Check if token exists and is still valid (not rotated, not revoked, not expired)
        var tokenRecord = refreshTokenRepository.findValidByJti(jti);
        if (tokenRecord.isEmpty()) {
            log.warn("Refresh token is not valid or has been rotated/revoked: jti={}", jti);
            return false;
        }

        return true;
    }

    /**
     * Mark a refresh token as rotated (it's been used to issue a new one).
     * ✅ SECURITY: Prevents replay of the old token.
     */
    public void rotateRefreshToken(Claims claims) {
        String jti = claims.get("jti", String.class);
        if (jti == null) return;

        var tokenRecord = refreshTokenRepository.findByTokenJti(jti);
        if (tokenRecord.isPresent()) {
            tokenRecord.get().markAsRotated(Instant.now());
            refreshTokenRepository.save(tokenRecord.get());
            log.info("Rotated refresh token: jti={}", jti);
        }
    }
}
