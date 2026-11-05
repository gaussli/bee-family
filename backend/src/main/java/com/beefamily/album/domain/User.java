package com.beefamily.album.domain;

import com.beefamily.album.support.DateTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户。
 *
 * <p>表名 {@code user} 是 MySQL 关键字，实体侧用反引号显式引用，避免 Hibernate 生成未转义 SQL。</p>
 *
 * <p>{@code tokenValidAfter} 是 §12 R-3 的落地列：登出 / 改密码 / 被移除成员时写入 now()，
 * JWT 校验时 {@code iat < tokenValidAfter} 即判失效（返回 40103），补偿「无状态 JWT 无法主动失效」的缺口。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "`user`")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "email", nullable = false, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, length = 32)
    private String nickname;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "token_valid_after")
    private LocalDateTime tokenValidAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = DateTimes.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = DateTimes.now();
    }
}
