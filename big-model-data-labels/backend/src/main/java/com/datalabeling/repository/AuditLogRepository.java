package com.datalabeling.repository;

import com.datalabeling.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志数据访问层
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * 根据用户ID查找操作日志（分页）
     *
     * @param userId   用户ID
     * @param pageable 分页参数
     * @return 日志列表
     */
    Page<AuditLog> findByUserId(Integer userId, Pageable pageable);

    /**
     * 根据操作类型查找日志（分页）
     *
     * @param action   操作类型
     * @param pageable 分页参数
     * @return 日志列表
     */
    Page<AuditLog> findByAction(String action, Pageable pageable);

    /**
     * 根据用户ID和操作类型查找日志
     *
     * @param userId   用户ID
     * @param action   操作类型
     * @param pageable 分页参数
     * @return 日志列表
     */
    Page<AuditLog> findByUserIdAndAction(Integer userId, String action, Pageable pageable);

    /**
     * 根据资源类型和资源ID查找日志
     *
     * @param resourceType 资源类型
     * @param resourceId   资源ID
     * @return 日志列表
     */
    List<AuditLog> findByResourceTypeAndResourceId(String resourceType, Integer resourceId);

    /**
     * 查找指定时间范围的日志
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param pageable  分页参数
     * @return 日志列表
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt >= :startTime AND a.createdAt <= :endTime")
    Page<AuditLog> findByCreatedAtBetween(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable
    );

    /**
     * 删除指定时间之前的日志
     *
     * @param beforeTime 时间点
     */
    void deleteByCreatedAtBefore(LocalDateTime beforeTime);
}
