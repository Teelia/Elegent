package com.datalabeling.repository;

import com.datalabeling.entity.ExtractorConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 提取器配置Repository
 */
@Repository
public interface ExtractorConfigRepository extends JpaRepository<ExtractorConfig, Integer> {

    /**
     * 根据代码查找提取器
     */
    Optional<ExtractorConfig> findByCode(String code);

    /**
     * 检查代码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 查找所有激活的提取器
     */
    List<ExtractorConfig> findByIsActiveTrueOrderByIdAsc();

    /**
     * 根据分类查找提取器
     */
    List<ExtractorConfig> findByCategoryAndIsActiveTrueOrderByIdAsc(String category);

    /**
     * 查找用户创建的提取器
     */
    List<ExtractorConfig> findByUserIdAndIsActiveTrueOrderByIdAsc(Integer userId);

    /**
     * 查找所有内置提取器
     */
    List<ExtractorConfig> findByIsSystemTrueAndIsActiveTrueOrderByIdAsc();

    /**
     * 查找所有自定义提取器
     */
    List<ExtractorConfig> findByIsSystemFalseAndIsActiveTrueOrderByIdAsc();

    /**
     * 查找所有内置提取器（带关联数据）
     */
    @Query("SELECT DISTINCT e FROM ExtractorConfig e " +
           "LEFT JOIN FETCH e.patterns p " +
           "LEFT JOIN FETCH e.options o " +
           "WHERE e.isSystem = true AND e.isActive = true " +
           "ORDER BY e.id ASC")
    List<ExtractorConfig> findBuiltinWithDetails();

    /**
     * 查找所有自定义提取器（带关联数据）
     */
    @Query("SELECT DISTINCT e FROM ExtractorConfig e " +
           "LEFT JOIN FETCH e.patterns p " +
           "LEFT JOIN FETCH e.options o " +
           "WHERE e.isSystem = false AND e.isActive = true " +
           "ORDER BY e.id ASC")
    List<ExtractorConfig> findCustomWithDetails();

    /**
     * 根据ID查找提取器（带关联数据）
     */
    @Query("SELECT e FROM ExtractorConfig e " +
           "LEFT JOIN FETCH e.patterns p " +
           "LEFT JOIN FETCH e.options o " +
           "WHERE e.id = :id")
    Optional<ExtractorConfig> findByIdWithDetails(@Param("id") Integer id);

    /**
     * 根据代码查找激活的提取器（带关联数据）
     */
    @Query("SELECT e FROM ExtractorConfig e " +
           "LEFT JOIN FETCH e.patterns p " +
           "LEFT JOIN FETCH e.options o " +
           "WHERE e.code = :code AND e.isActive = true")
    Optional<ExtractorConfig> findByCodeWithDetails(@Param("code") String code);

    /**
     * 查找所有激活的提取器（带关联数据）
     */
    @Query("SELECT DISTINCT e FROM ExtractorConfig e " +
           "LEFT JOIN FETCH e.patterns p " +
           "LEFT JOIN FETCH e.options o " +
           "WHERE e.isActive = true " +
           "ORDER BY e.id ASC")
    List<ExtractorConfig> findAllActiveWithDetails();
}