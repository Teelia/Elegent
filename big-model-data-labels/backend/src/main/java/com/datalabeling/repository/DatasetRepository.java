package com.datalabeling.repository;

import com.datalabeling.entity.Dataset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据集Repository
 */
@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Integer> {

    /**
     * 根据用户ID查询数据集列表
     */
    List<Dataset> findByUserIdOrderByCreatedAtDesc(Integer userId);

    /**
     * 根据用户ID分页查询数据集
     */
    Page<Dataset> findByUserId(Integer userId, Pageable pageable);

    /**
     * 根据用户ID和状态查询数据集
     */
    Page<Dataset> findByUserIdAndStatus(Integer userId, String status, Pageable pageable);

    /**
     * 根据用户ID和名称模糊查询
     */
    Page<Dataset> findByUserIdAndNameContaining(Integer userId, String name, Pageable pageable);

    /**
     * 根据用户ID、状态和名称模糊查询
     */
    Page<Dataset> findByUserIdAndStatusAndNameContaining(
            Integer userId, String status, String name, Pageable pageable);

    /**
     * 根据ID和用户ID查询（确保用户只能访问自己的数据集）
     */
    Optional<Dataset> findByIdAndUserId(Integer id, Integer userId);

    /**
     * 统计用户的数据集数量
     */
    long countByUserId(Integer userId);

    /**
     * 统计用户特定状态的数据集数量
     */
    long countByUserIdAndStatus(Integer userId, String status);

    /**
     * 查询用户最近的数据集
     */
    @Query("SELECT d FROM Dataset d WHERE d.userId = :userId ORDER BY d.createdAt DESC")
    List<Dataset> findRecentByUserId(@Param("userId") Integer userId, Pageable pageable);

    /**
     * 批量更新状态
     */
    @Modifying
    @Query("UPDATE Dataset d SET d.status = :status WHERE d.id IN :ids AND d.userId = :userId")
    int updateStatusByIdsAndUserId(@Param("ids") List<Integer> ids,
                                    @Param("userId") Integer userId,
                                    @Param("status") String status);
}