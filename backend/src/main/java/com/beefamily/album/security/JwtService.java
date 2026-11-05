package com.beefamily.album.security;

import com.beefamily.album.config.JwtProperties;
import com.beefamily.album.support.DateTimes;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * JWT 签发与解析。
 *
 * <p>算法 HS256；claims 仅 {@code sub/email/iat/exp}，**不放 familyId / role / nickname**（ADR-02）。
 * 解析失败一律抛出 jjwt 原生异常，由过滤器分类映射为 40100 / 40103，本类不做 HTTP 语义判断。</p>
 */
@Service
public class JwtService {

    /** 密钥最小长度（256 bit）。 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final Duration ttl;

    /**
     * 构造并校验密钥长度。
     *
     * <p>密钥长度可变（开发默认值、环境变量长度不一），因此先做一次 SHA-256 派生，得到固定 32 字节的
     * HMAC 密钥，再用 {@code Jwts.SIG.HS256} 显式签发 —— 保证算法恒为 HS256（docs §7.2）。
     * 若直接用 {@code Keys.hmacShaKeyFor(secret.getBytes())}，密钥长度 &gt; 48 字节时 jjwt 会
     * 自动升级为 HS384/HS512，与文档约定的算法不一致。</p>
     *
     * @param properties JWT 配置
     */
    public JwtService(JwtProperties properties) {
        byte[] secretBytes = properties.getSecret() == null
                ? new byte[0]
                : properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            // 宁可启动失败，也不要静默使用弱密钥
            throw new IllegalStateException("app.jwt.secret 长度不足 " + MIN_SECRET_BYTES + " 字节，请通过 JWT_SECRET 覆盖");
        }
        this.signingKey = Keys.hmacShaKeyFor(sha256(secretBytes));
        this.ttl = properties.getTtl() == null ? Duration.ofDays(7) : properties.getTtl();
    }

    /**
     * SHA-256 派生固定长度密钥。
     *
     * @param secret 原始密钥字节
     * @return 32 字节摘要
     */
    private static byte[] sha256(byte[] secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 不支持 SHA-256", ex);
        }
    }

    /**
     * 签发 token。
     *
     * @param userId 用户 id
     * @param email  邮箱
     * @return 签发结果（token + 过期时间）
     */
    public IssuedToken issue(Long userId, String email) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, DateTimes.fromInstant(expiresAt));
    }

    /**
     * 解析并校验签名 / 过期。
     *
     * @param token 原始 JWT
     * @return 解析结果
     * @throws io.jsonwebtoken.ExpiredJwtException token 已过期
     * @throws JwtException                        签名不符 / 结构畸形
     */
    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get("email", String.class);
        return new ParsedToken(userId, email,
                DateTimes.fromInstant(claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant()),
                DateTimes.fromInstant(claims.getExpiration() == null ? null : claims.getExpiration().toInstant()));
    }

    /**
     * 签发结果。
     *
     * @param token     JWT 字符串
     * @param expiresAt 过期时间（本地时区）
     */
    public record IssuedToken(String token, LocalDateTime expiresAt) {
    }

    /**
     * 解析结果。
     *
     * @param userId    用户 id
     * @param email     邮箱
     * @param issuedAt  签发时间
     * @param expiresAt 过期时间
     */
    public record ParsedToken(Long userId, String email, LocalDateTime issuedAt, LocalDateTime expiresAt) {
    }
}
