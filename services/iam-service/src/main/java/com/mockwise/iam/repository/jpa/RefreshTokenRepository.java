package com.mockwise.iam.repository.jpa;

import com.mockwise.iam.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByIdAndRevokedFalse(String id);
    void deleteByUserUserId(String userId); // Delete all refresh tokens for a user when they log out
    @Modifying
    @Query(value = """
      INSERT INTO refresh_tokens(id, session_id, token_hash, issued_at, expires_at, revoked, user_id)
      VALUES (:id, :sessionId, :tokenHash, :issuedAt, :expiresAt, :revoked, :userId)
      """, nativeQuery = true)
    void insertToken(@Param("id") String id,
                     @Param("sessionId") String sessionId,
                     @Param("tokenHash") String tokenHash,
                     @Param("issuedAt") Instant issuedAt,
                     @Param("expiresAt") Instant expiresAt,
                     @Param("revoked") boolean revoked,
                     @Param("userId") String userId);

    boolean existsById(String id);

    List<RefreshToken> findAllByUserUserIdOrderByIssuedAtDesc(String userId);
}
