package com.beefamily.album.domain;

import com.beefamily.album.domain.enums.Gender;
import com.beefamily.album.support.DateTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 宝宝档案。
 *
 * <p>数量上限（3 个）由 service 校验，DB 不建唯一约束：PRD 未要求昵称唯一，兄弟可重名。</p>
 *
 * <p>{@code birthHeightCm}/{@code birthWeightKg} 用 {@link BigDecimal} 承载，保留 DB scale，
 * 序列化为 JSON 数字（§11.3），禁止 {@code stripTrailingZeros}。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "baby")
public class Baby {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "name", nullable = false, length = 32)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 8)
    private Gender gender;

    @Column(name = "birthday", nullable = false)
    private LocalDate birthday;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "birth_height_cm", precision = 5, scale = 1)
    private BigDecimal birthHeightCm;

    @Column(name = "birth_weight_kg", precision = 4, scale = 2)
    private BigDecimal birthWeightKg;

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
