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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 家庭空间。
 *
 * <p>{@code coverPhotoId} 刻意不建立外键（§4.3 方案 B）：与 {@code photo.family_id} 互指会成环，
 * 一致性由 service 显式维护（删照片时把引用置 NULL，读不到时前端降级为空态）。</p>
 *
 * <p>{@code inviteCode} 是 {@code CHAR(8)}，用 {@code @JdbcTypeCode(SqlTypes.CHAR)} 让
 * {@code ddl-auto: validate} 的类型比对通过（String 默认期望 VARCHAR）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "family")
public class Family {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "invite_code", length = 8)
    private String inviteCode;

    @Column(name = "invite_code_expires_at")
    private LocalDateTime inviteCodeExpiresAt;

    @Column(name = "cover_photo_id")
    private Long coverPhotoId;

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
