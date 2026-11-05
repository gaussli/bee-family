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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 里程碑。
 *
 * <p>{@code familyId} 冗余列：{@code babyId} 允许为 NULL（家庭级里程碑），时间轴与搜索必须能只按家庭过滤，
 * 否则需要 LEFT JOIN baby 且无法命中 {@code idx_milestone_family_achieved}。</p>
 *
 * <p>{@code photoId} 有 {@code ON DELETE SET NULL}：删除被引用的照片后 DB 自动置 NULL，
 * 前端「里程碑配图消失」是预期行为（§4.3 实测证据）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "milestone")
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "baby_id")
    private Long babyId;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "title", nullable = false, length = 60)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "achieved_at", nullable = false)
    private LocalDate achievedAt;

    @Column(name = "photo_id")
    private Long photoId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

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
