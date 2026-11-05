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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 成长测量记录。
 *
 * <p>{@code uk_growth_baby_date(baby_id, recorded_at)} 保证同一宝宝同一日期只有一条，
 * 服务端 POST 走 UPSERT（存在则覆盖测量值），因此前端不需要处理 409。</p>
 *
 * <p>不冗余 {@code family_id}：单家庭宝宝 ≤3，按家庭过滤时 JOIN baby 的探测成本可忽略（§4.5 结论 4）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "growth_record")
public class GrowthRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "baby_id", nullable = false)
    private Long babyId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDate recordedAt;

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 4, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "head_cm", precision = 4, scale = 1)
    private BigDecimal headCm;

    @Column(name = "note", length = 200)
    private String note;

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
