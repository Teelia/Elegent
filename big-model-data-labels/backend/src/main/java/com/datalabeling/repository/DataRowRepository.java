package com.datalabeling.repository;

import com.datalabeling.entity.DataRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 数据行数据访问层
 */
@Repository
public interface DataRowRepository extends JpaRepository<DataRow, Long>, JpaSpecificationExecutor<DataRow> {

    /**
     * 根据任务ID查找所有数据行（分页）
     *
     * @param taskId   任务ID
     * @param pageable 分页参数
     * @return 数据行列表
     */
    Page<DataRow> findByTaskId(Integer taskId, Pageable pageable);

    /**
     * 统计任务数据行数量
     */
    long countByTaskId(Integer taskId);

    /**
     * 根据任务ID查找所有数据行
     *
     * @param taskId 任务ID
     * @return 数据行列表
     */
    List<DataRow> findByTaskIdOrderByRowIndex(Integer taskId);

    /**
     * 查找任务的首行数据（用于列信息推导）
     */
    Optional<DataRow> findFirstByTaskIdOrderByRowIndexAsc(Integer taskId);

    /**
     * 根据任务ID和行索引查找数据行
     *
     * @param taskId   任务ID
     * @param rowIndex 行索引
     * @return 数据行信息
     */
    Optional<DataRow> findByTaskIdAndRowIndex(Integer taskId, Integer rowIndex);

    /**
     * 根据任务ID和处理状态查找数据行
     *
     * @param taskId           任务ID
     * @param processingStatus 处理状态
     * @return 数据行列表
     */
    List<DataRow> findByTaskIdAndProcessingStatus(Integer taskId, String processingStatus);

    /**
     * 统计任务的数据行数量（按状态）
     *
     * @param taskId           任务ID
     * @param processingStatus 处理状态
     * @return 数据行数量
     */
    long countByTaskIdAndProcessingStatus(Integer taskId, String processingStatus);

    /**
     * 查找任务中被手动修改的数据行
     *
     * @param taskId 任务ID
     * @return 数据行列表
     */
    List<DataRow> findByTaskIdAndIsModified(Integer taskId, Boolean isModified);

    /**
     * 删除任务的所有数据行
     *
     * @param taskId 任务ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM DataRow d WHERE d.taskId = :taskId")
    void deleteByTaskId(@Param("taskId") Integer taskId);

    /**
     * 查找任务的最大行索引（用于增量追加模式）
     *
     * @param taskId 任务ID
     * @return 最大行索引，如果没有数据则返回 0
     */
    @Query("SELECT COALESCE(MAX(d.rowIndex), 0) FROM DataRow d WHERE d.taskId = :taskId")
    int findMaxRowIndexByTaskId(@Param("taskId") Integer taskId);

    /**
     * 批量查询指定行索引范围的数据
     *
     * @param taskId       任务ID
     * @param startIndex   开始索引
     * @param endIndex     结束索引
     * @return 数据行列表
     */
    @Query("SELECT d FROM DataRow d WHERE d.taskId = :taskId " +
           "AND d.rowIndex >= :startIndex AND d.rowIndex <= :endIndex " +
           "ORDER BY d.rowIndex")
    List<DataRow> findByTaskIdAndRowIndexBetween(
        @Param("taskId") Integer taskId,
        @Param("startIndex") Integer startIndex,
        @Param("endIndex") Integer endIndex
    );

    /**
     * 根据任务ID和关键词搜索数据行（在JSON字段中搜索）
     * 使用MySQL的JSON_SEARCH函数进行模糊匹配
     *
     * @param taskId   任务ID/数据集ID
     * @param keyword  搜索关键词
     * @param pageable 分页参数
     * @return 匹配的数据行
     */
    @Query(value = "SELECT * FROM data_rows d WHERE d.task_id = :taskId " +
           "AND JSON_SEARCH(LOWER(d.original_data), 'one', LOWER(CONCAT('%', :keyword, '%'))) IS NOT NULL " +
           "ORDER BY d.row_index",
           countQuery = "SELECT COUNT(*) FROM data_rows d WHERE d.task_id = :taskId " +
           "AND JSON_SEARCH(LOWER(d.original_data), 'one', LOWER(CONCAT('%', :keyword, '%'))) IS NOT NULL",
           nativeQuery = true)
    Page<DataRow> searchByTaskIdAndKeyword(
        @Param("taskId") Integer taskId,
        @Param("keyword") String keyword,
        Pageable pageable
    );
}
