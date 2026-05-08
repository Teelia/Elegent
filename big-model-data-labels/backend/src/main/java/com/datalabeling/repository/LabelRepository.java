package com.datalabeling.repository;

import com.datalabeling.entity.Label;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 标签数据访问层
 */
@Repository
public interface LabelRepository extends JpaRepository<Label, Integer> {

    /**
     * 根据用户ID查找所有标签（分页）
     *
     * @param userId   用户ID
     * @param pageable 分页参数
     * @return 标签列表
     */
    Page<Label> findByUserId(Integer userId, Pageable pageable);

    /**
     * 查找用户的标签列表（仅最新版本，分页）
     */
    @Query("SELECT l FROM Label l WHERE l.userId = :userId " +
           "AND l.version = (SELECT MAX(l2.version) FROM Label l2 WHERE l2.userId = l.userId AND l2.name = l.name)")
    Page<Label> findLatestByUserId(@Param("userId") Integer userId, Pageable pageable);

    /**
     * 查找所有标签列表（仅最新版本，分页，管理员使用）
     */
    @Query("SELECT l FROM Label l WHERE l.version = (SELECT MAX(l2.version) FROM Label l2 " +
           "WHERE l2.userId = l.userId AND l2.name = l.name)")
    Page<Label> findLatestAll(Pageable pageable);

    /**
     * 根据用户ID和激活状态查找标签
     *
     * @param userId   用户ID
     * @param isActive 是否激活
     * @return 标签列表
     */
    List<Label> findByUserIdAndIsActive(Integer userId, Boolean isActive);

    /**
     * 查找用户的所有激活标签（最新版本）
     *
     * @param userId 用户ID
     * @return 最新版本的激活标签列表
     */
    @Query("SELECT l FROM Label l WHERE l.userId = :userId AND COALESCE(l.isActive, true) = true " +
           "AND l.version = (SELECT MAX(l2.version) FROM Label l2 " +
           "WHERE l2.userId = l.userId AND l2.name = l.name)")
    List<Label> findLatestActiveByUserId(@Param("userId") Integer userId);

    /**
     * 根据用户ID、标签名称和版本查找标签
     *
     * @param userId  用户ID
     * @param name    标签名称
     * @param version 版本号
     * @return 标签信息
     */
    Optional<Label> findByUserIdAndNameAndVersion(Integer userId, String name, Integer version);

    /**
     * 查找指定标签的最大版本号
     *
     * @param userId 用户ID
     * @param name   标签名称
     * @return 最大版本号
     */
    @Query("SELECT MAX(l.version) FROM Label l WHERE l.userId = :userId AND l.name = :name")
    Integer findMaxVersionByUserIdAndName(@Param("userId") Integer userId, @Param("name") String name);

    /**
     * 根据用户ID和标签名称查找所有版本
     *
     * @param userId 用户ID
     * @param name   标签名称
     * @return 标签版本列表
     */
    List<Label> findByUserIdAndNameOrderByVersionDesc(Integer userId, String name);
    
    /**
     * 统计数据集专属标签数量
     *
     * @param datasetId 数据集ID
     * @return 标签数量
     */
    long countByDatasetId(Integer datasetId);
    
    /**
     * 根据数据集ID查找专属标签
     *
     * @param datasetId 数据集ID
     * @return 标签列表
     */
    List<Label> findByDatasetId(Integer datasetId);
    
    /**
     * 查找用户的全局标签（最新版本）
     *
     * @param userId 用户ID
     * @return 全局标签列表
     */
    @Query("SELECT l FROM Label l WHERE l.userId = :userId AND l.scope = 'global' AND COALESCE(l.isActive, true) = true " +
           "AND l.version = (SELECT MAX(l2.version) FROM Label l2 " +
           "WHERE l2.userId = l.userId AND l2.name = l.name)")
    List<Label> findGlobalActiveByUserId(@Param("userId") Integer userId);

    /**
     * 查找“系统内置全局标签”（管理员创建的 global，最新版本）
     * 说明：由 service 先获取管理员 userId 列表，再用 IN 查询避免跨实体 JOIN。
     */
    @Query("SELECT l FROM Label l WHERE l.userId IN :adminUserIds AND l.scope = 'global' " +
           "AND COALESCE(l.builtinLevel, 'custom') = 'system' AND COALESCE(l.isActive, true) = true " +
           "AND l.version = (SELECT MAX(l2.version) FROM Label l2 " +
           "WHERE l2.userId = l.userId AND l2.name = l.name)")
    List<Label> findBuiltinGlobalActiveLatest(@Param("adminUserIds") List<Integer> adminUserIds);

    /**
     * 分页查询“系统内置全局标签”（管理员创建的 global，最新版本）
     */
    @Query("SELECT l FROM Label l WHERE l.userId IN :adminUserIds AND l.scope = 'global' " +
           "AND COALESCE(l.builtinLevel, 'custom') = 'system' AND COALESCE(l.isActive, true) = true " +
           "AND l.version = (SELECT MAX(l2.version) FROM Label l2 " +
           "WHERE l2.userId = l.userId AND l2.name = l.name)")
    Page<Label> findBuiltinGlobalActiveLatest(@Param("adminUserIds") List<Integer> adminUserIds, Pageable pageable);

    /**
     * 按分类分页查询“系统内置全局标签”
     */
    @Query("SELECT l FROM Label l WHERE l.userId IN :adminUserIds AND l.scope = 'global' " +
           "AND COALESCE(l.builtinLevel, 'custom') = 'system' AND l.builtinCategory = :builtinCategory " +
           "AND COALESCE(l.isActive, true) = true " +
           "AND l.version = (SELECT MAX(l2.version) FROM Label l2 " +
           "WHERE l2.userId = l.userId AND l2.name = l.name)")
    Page<Label> findBuiltinGlobalActiveLatestByCategory(@Param("adminUserIds") List<Integer> adminUserIds,
                                                       @Param("builtinCategory") String builtinCategory,
                                                       Pageable pageable);

    /**
     * 查找数据集专属激活标签（最新版本）
     */
    @Query("SELECT l FROM Label l WHERE l.scope = 'dataset' AND l.datasetId = :datasetId AND COALESCE(l.isActive, true) = true " +
           "AND l.version = (SELECT MAX(l2.version) FROM Label l2 " +
           "WHERE l2.userId = l.userId AND l2.name = l.name)")
    List<Label> findDatasetActiveLatestByDatasetId(@Param("datasetId") Integer datasetId);
    
    /**
     * 查找数据集可用的标签（全局标签 + 数据集专属标签）
     *
     * @param userId 用户ID
     * @param datasetId 数据集ID
     * @return 可用标签列表
     */
    @Query("SELECT l FROM Label l WHERE COALESCE(l.isActive, true) = true " +
           "AND ((l.userId = :userId AND l.scope = 'global') OR (l.datasetId = :datasetId AND l.scope = 'dataset')) " +
           "AND l.version = (SELECT MAX(l2.version) FROM Label l2 " +
           "WHERE l2.userId = l.userId AND l2.name = l.name)")
    List<Label> findAvailableLabelsForDataset(@Param("userId") Integer userId, @Param("datasetId") Integer datasetId);
    
    /**
     * 根据ID列表查找标签
     *
     * @param ids 标签ID列表
     * @return 标签列表
     */
    List<Label> findByIdIn(List<Integer> ids);
    
    /**
     * 根据任务ID和作用域查找标签
     *
     * @param taskId 任务ID
     * @param scope 作用域
     * @return 标签列表
     */
    List<Label> findByTaskIdAndScope(Integer taskId, String scope);
    
    /**
     * 根据用户ID和作用域查找标签
     *
     * @param userId 用户ID
     * @param scope 作用域
     * @return 标签列表
     */
    List<Label> findByUserIdAndScope(Integer userId, String scope);
    
    /**
     * 查找任务可用的标签（全局标签 + 任务临时标签）
     *
     * @param userId 用户ID
     * @param taskId 任务ID
     * @return 可用标签列表
     */
    @Query("SELECT l FROM Label l WHERE COALESCE(l.isActive, true) = true " +
           "AND ((l.userId = :userId AND l.scope = 'global') OR (l.taskId = :taskId AND l.scope = 'task')) " +
           "AND l.version = (SELECT MAX(l2.version) FROM Label l2 " +
           "WHERE l2.userId = l.userId AND l2.name = l.name)")
    List<Label> findAvailableLabelsForTask(@Param("userId") Integer userId, @Param("taskId") Integer taskId);
}
