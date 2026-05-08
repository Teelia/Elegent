package com.datalabeling.repository;

import com.datalabeling.entity.LabelResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 标签结果Repository
 */
@Repository
public interface LabelResultRepository extends JpaRepository<LabelResult, Long> {
    
    /**
     * 根据任务ID查询所有标签结果
     */
    List<LabelResult> findByAnalysisTaskId(Integer analysisTaskId);
    
    /**
     * 根据任务ID分页查询标签结果
     */
    Page<LabelResult> findByAnalysisTaskId(Integer analysisTaskId, Pageable pageable);
    
    /**
     * 根据数据行ID查询所有标签结果
     */
    List<LabelResult> findByDataRowId(Long dataRowId);
    
    /**
     * 根据任务ID和数据行ID查询
     */
    List<LabelResult> findByAnalysisTaskIdAndDataRowId(Integer analysisTaskId, Long dataRowId);
    
    /**
     * 根据任务ID、数据行ID和标签ID查询
     */
    Optional<LabelResult> findByAnalysisTaskIdAndDataRowIdAndLabelId(Integer analysisTaskId, Long dataRowId, Integer labelId);
    
    /**
     * 根据任务ID和标签ID查询
     */
    List<LabelResult> findByAnalysisTaskIdAndLabelId(Integer analysisTaskId, Integer labelId);
    
    /**
     * 根据任务ID和标签ID分页查询
     */
    Page<LabelResult> findByAnalysisTaskIdAndLabelId(Integer analysisTaskId, Integer labelId, Pageable pageable);
    
    /**
     * 根据任务ID和结果值查询
     */
    Page<LabelResult> findByAnalysisTaskIdAndResult(Integer analysisTaskId, String result, Pageable pageable);
    
    /**
     * 根据任务ID、标签ID和结果值查询
     */
    Page<LabelResult> findByAnalysisTaskIdAndLabelIdAndResult(
            Integer analysisTaskId, Integer labelId, String result, Pageable pageable);
    
    /**
     * 查询需要人工审核的结果
     */
    Page<LabelResult> findByAnalysisTaskIdAndNeedsReviewTrue(Integer analysisTaskId, Pageable pageable);
    
    /**
     * 查询需要人工审核的结果（按标签筛选）
     */
    Page<LabelResult> findByAnalysisTaskIdAndLabelIdAndNeedsReviewTrue(
            Integer analysisTaskId, Integer labelId, Pageable pageable);
    
    /**
     * 查询已人工修改的结果
     */
    Page<LabelResult> findByAnalysisTaskIdAndIsModifiedTrue(Integer analysisTaskId, Pageable pageable);
    
    /**
     * 统计任务的标签结果数量
     */
    long countByAnalysisTaskId(Integer analysisTaskId);
    
    /**
     * 统计任务特定标签的结果数量
     */
    long countByAnalysisTaskIdAndLabelId(Integer analysisTaskId, Integer labelId);
    
    /**
     * 统计任务特定结果值的数量
     */
    long countByAnalysisTaskIdAndLabelIdAndResult(Integer analysisTaskId, Integer labelId, String result);
    
    /**
     * 统计需要审核的数量
     */
    long countByAnalysisTaskIdAndNeedsReviewTrue(Integer analysisTaskId);
    
    /**
     * 统计已修改的数量
     */
    long countByAnalysisTaskIdAndIsModifiedTrue(Integer analysisTaskId);
    
    /**
     * 删除任务的所有标签结果
     */
    @Modifying
    @Query("DELETE FROM LabelResult lr WHERE lr.analysisTaskId = :analysisTaskId")
    int deleteByAnalysisTaskId(@Param("analysisTaskId") Integer analysisTaskId);
    
    /**
     * 删除数据行的所有标签结果
     */
    @Modifying
    @Query("DELETE FROM LabelResult lr WHERE lr.dataRowId = :dataRowId")
    int deleteByDataRowId(@Param("dataRowId") Long dataRowId);
    
    /**
     * 批量更新信心度阈值
     */
    @Modifying
    @Query("UPDATE LabelResult lr SET lr.confidenceThreshold = :threshold, " +
           "lr.needsReview = CASE WHEN lr.aiConfidence < :threshold THEN true ELSE false END " +
           "WHERE lr.analysisTaskId = :analysisTaskId")
    int updateConfidenceThresholdByAnalysisTaskId(@Param("analysisTaskId") Integer analysisTaskId,
                                                   @Param("threshold") BigDecimal threshold);
    
    /**
     * 批量更新特定标签的信心度阈值
     */
    @Modifying
    @Query("UPDATE LabelResult lr SET lr.confidenceThreshold = :threshold, " +
           "lr.needsReview = CASE WHEN lr.aiConfidence < :threshold THEN true ELSE false END " +
           "WHERE lr.analysisTaskId = :analysisTaskId AND lr.labelId = :labelId")
    int updateConfidenceThresholdByAnalysisTaskIdAndLabelId(@Param("analysisTaskId") Integer analysisTaskId,
                                                             @Param("labelId") Integer labelId,
                                                             @Param("threshold") BigDecimal threshold);
    
    /**
     * 统计各结果值的分布
     */
    @Query("SELECT lr.result, COUNT(lr) FROM LabelResult lr " +
           "WHERE lr.analysisTaskId = :analysisTaskId AND lr.labelId = :labelId " +
           "GROUP BY lr.result")
    List<Object[]> countByResult(@Param("analysisTaskId") Integer analysisTaskId, @Param("labelId") Integer labelId);
    
    /**
     * 计算平均信心度
     */
    @Query("SELECT AVG(lr.aiConfidence) FROM LabelResult lr " +
           "WHERE lr.analysisTaskId = :analysisTaskId AND lr.labelId = :labelId")
    BigDecimal calculateAverageConfidence(@Param("analysisTaskId") Integer analysisTaskId, @Param("labelId") Integer labelId);
    
    /**
     * 查询低信心度结果（用于审核）
     */
    @Query("SELECT lr FROM LabelResult lr WHERE lr.analysisTaskId = :analysisTaskId " +
           "AND lr.aiConfidence < lr.confidenceThreshold ORDER BY lr.aiConfidence ASC")
    Page<LabelResult> findLowConfidenceResults(@Param("analysisTaskId") Integer analysisTaskId, Pageable pageable);
    
    /**
     * 批量查询数据行的标签结果
     */
    @Query("SELECT lr FROM LabelResult lr WHERE lr.analysisTaskId = :analysisTaskId AND lr.dataRowId IN :dataRowIds")
    List<LabelResult> findByAnalysisTaskIdAndDataRowIds(@Param("analysisTaskId") Integer analysisTaskId, @Param("dataRowIds") List<Long> dataRowIds);
    
    /**
     * 统计命中数量（结果为"是"的数量）
     */
    @Query("SELECT lr.labelId, COUNT(lr) FROM LabelResult lr " +
           "WHERE lr.analysisTaskId = :analysisTaskId AND lr.result = '是' " +
           "GROUP BY lr.labelId")
    List<Object[]> countHitsByLabelId(@Param("analysisTaskId") Integer analysisTaskId);

    /**
     * 分页查询任务的不重复数据行ID（用于按数据行分页）
     * 注意：native query 返回 BigInteger，使用 Object 接收后在 Service 层转换
     */
    @Query(value = "SELECT DISTINCT lr.data_row_id FROM label_results lr " +
           "WHERE lr.analysis_task_id = :analysisTaskId " +
           "ORDER BY lr.data_row_id",
           countQuery = "SELECT COUNT(DISTINCT lr.data_row_id) FROM label_results lr " +
           "WHERE lr.analysis_task_id = :analysisTaskId",
           nativeQuery = true)
    Page<Object> findDistinctDataRowIdsByAnalysisTaskId(@Param("analysisTaskId") Integer analysisTaskId, Pageable pageable);

    /**
     * 统计任务的不重复数据行数量
     */
    @Query("SELECT COUNT(DISTINCT lr.dataRowId) FROM LabelResult lr WHERE lr.analysisTaskId = :analysisTaskId")
    long countDistinctDataRowsByAnalysisTaskId(@Param("analysisTaskId") Integer analysisTaskId);
}