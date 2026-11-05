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
 * 标签。
 *
 * <p>{@code uk_tag_family_name(family_id, name)} 让标签在家庭内复用（PRD P0-09）；
 * 该唯一索引同时是「输入即搜索」前缀匹配 {@code LIKE '海%'} 的索引（§4.5 Q10）。</p>
 *
 * <p>标签行**只增不删**（本期）：删除关联只删 {@code photo_tag}，孤儿标签留给 P1 清理任务。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

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
