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
 * 相册。
 *
 * <p>{@code photoCount} 是冗余计数，由 {@code PhotoService} 在**同一事务**内维护（+1 / -1 / ±N），
 * {@code AlbumService} 只读不写（docs T09 关键约束①），避免列表页每次 COUNT 扫描。</p>
 *
 * <p>{@code coverPhotoId} 无外键（§4.3 方案 B）：为 NULL 时前端按 PRD P0-06③ 取「相册内最新照片」兜底。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "album")
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "cover_photo_id")
    private Long coverPhotoId;

    @Column(name = "photo_count", nullable = false)
    private Integer photoCount;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = DateTimes.now();
        if (photoCount == null) {
            photoCount = 0;
        }
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
