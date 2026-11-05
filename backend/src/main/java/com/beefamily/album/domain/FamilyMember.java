package com.beefamily.album.domain;

import com.beefamily.album.domain.enums.Role;
import com.beefamily.album.support.DateTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 家庭成员关系。
 *
 * <p>{@code uk_member_user(user_id)} 唯一索引在 DB 层落地「1 用户 = 1 家庭」（PRD Q1），
 * 同时是每个认证请求解析 {@code familyId} 的索引（ADR-02：familyId 不进 JWT）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "family_member")
public class FamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    void onCreate() {
        if (joinedAt == null) {
            joinedAt = DateTimes.now();
        }
    }
}
