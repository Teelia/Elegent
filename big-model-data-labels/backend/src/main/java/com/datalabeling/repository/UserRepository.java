package com.datalabeling.repository;

import com.datalabeling.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * 根据用户名查找用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    Optional<User> findByUsername(String username);

    /**
     * 检查用户名是否存在
     *
     * @param username 用户名
     * @return 是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 根据邮箱查找用户
     *
     * @param email 邮箱
     * @return 用户信息
     */
    Optional<User> findByEmail(String email);

    /**
     * 用户搜索（按用户名/邮箱模糊匹配）
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR u.username LIKE %:keyword% OR COALESCE(u.email,'') LIKE %:keyword%)")
    Page<User> search(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 查询所有管理员用户ID（用于“系统内置资源”可见性计算）
     */
    @Query("SELECT u.id FROM User u WHERE LOWER(u.role) = 'admin'")
    List<Integer> findAdminUserIds();
}
