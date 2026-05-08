package com.datalabeling.repository;

import com.datalabeling.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 大模型配置数据访问层
 */
@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Integer> {

    Optional<ModelConfig> findFirstByProviderOrderByIdDesc(String provider);

    Optional<ModelConfig> findFirstByProviderAndIsActiveTrueOrderByIdDesc(String provider);

    /**
     * 获取所有激活的配置
     */
    List<ModelConfig> findByIsActiveTrueOrderByIsDefaultDescIdAsc();

    /**
     * 获取默认配置
     */
    Optional<ModelConfig> findFirstByIsDefaultTrueAndIsActiveTrue();

    /**
     * 按提供方获取激活的配置列表
     */
    List<ModelConfig> findByProviderAndIsActiveTrueOrderByIdAsc(String provider);

    /**
     * 清除所有默认标记
     */
    @Modifying
    @Query("UPDATE ModelConfig m SET m.isDefault = false WHERE m.isDefault = true")
    void clearAllDefaults();

    /**
     * 检查名称是否已存在
     */
    boolean existsByNameAndIdNot(String name, Integer id);

    /**
     * 检查名称是否已存在（新建时）
     */
    boolean existsByName(String name);
}
