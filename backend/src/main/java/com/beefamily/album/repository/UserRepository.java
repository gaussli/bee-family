package com.beefamily.album.repository;

import com.beefamily.album.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户仓储。
 *
 * <p>邮箱一律以全小写入库，查询用 {@code IgnoreCase} 兜住历史脏数据。</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 按邮箱查询（忽略大小写）。
     *
     * @param email 邮箱
     * @return 用户
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * 邮箱是否已注册。
     *
     * @param email 邮箱
     * @return true 表示已存在
     */
    boolean existsByEmailIgnoreCase(String email);
}
