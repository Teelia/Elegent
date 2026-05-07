package com.datalabeling.repository;

import com.datalabeling.entity.SyncConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据库同步配置数据访问层
 */
@Repository
public interface SyncConfigRepository extends JpaRepository<SyncConfig, Integer> {

    /**
     * 根据用户ID查找所有同步配置
     *
     * @param userId 用户ID
     * @return 同步配置列表
     */
    List<SyncConfig> findByUserId(Integer userId);

    /**
     * 根据用户ID和激活状态查找同步配置
     *
     * @param userId   用户ID
     * @param isActive 是否激活
     * @return 同步配置列表
     */
    List<SyncConfig> findByUserIdAndIsActive(Integer userId, Boolean isActive);

    /**
     * 根据用户ID和配置名称查找同步配置
     *
     * @param userId 用户ID
     * @param name   配置名称
     * @return 同步配置列表
     */
    List<SyncConfig> findByUserIdAndName(Integer userId, String name);

    /**
     * 根据用户ID和方向查找同步配置
     *
     * @param userId    用户ID
     * @param direction 数据方向
     * @return 同步配置列表
     */
    List<SyncConfig> findByUserIdAndDirection(Integer userId, String direction);

    /**
     * 根据ID和用户ID查找同步配置
     *
     * @param id     配置ID
     * @param userId 用户ID
     * @return 同步配置
     */
    Optional<SyncConfig> findByIdAndUserId(Integer id, Integer userId);
}
