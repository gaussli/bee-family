package com.beefamily.album.domain;

import com.beefamily.album.domain.enums.MediaType;
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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 照片。
 *
 * <p>三个 key 都是相对路径（无前导 {@code /}、无存储根前缀），文件实体在 {@code backend/data/uploads} 下，
 * 只能经 {@code GET /api/files/**} 鉴权访问（§8.1、§8.2）。</p>
 *
 * <p>P1/P2 预留列（{@code latitude} / {@code longitude} / {@code content_hash} / {@code media_type}）必须映射：
 * {@code ddl-auto: validate} 会校验全部列，漏映射会导致启动失败（T02 关键约束⑤）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "photo")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "album_id")
    private Long albumId;

    @Column(name = "title", length = 80)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "thumb_key", length = 255)
    private String thumbKey;

    @Column(name = "preview_key", length = 255)
    private String previewKey;

    @Column(name = "mime_type", nullable = false, length = 40)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "taken_at", nullable = false)
    private LocalDateTime takenAt;

    @Column(name = "location_name", length = 100)
    private String locationName;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    private MediaType mediaType;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = DateTimes.now();
        if (mediaType == null) {
            mediaType = MediaType.photo;
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
