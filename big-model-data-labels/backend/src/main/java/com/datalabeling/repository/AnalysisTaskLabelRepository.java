package com.datalabeling.repository;

import com.datalabeling.entity.AnalysisTaskLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 分析任务标签关联Repository
 */
@Repository
public interface AnalysisTaskLabelRepository extends JpaRepository<AnalysisTaskLabel, Integer> {
    
    /**
     * 根据任务ID查询所有关联的标签
     */
    List<AnalysisTaskLabel> findByAnalysisTaskId(Integer analysisTaskId);
    
    /**
     * 根据任务ID和标签ID查询
     */
    Optional<AnalysisTaskLabel> findByAnalysisTaskIdAndLabelId(Integer analysisTaskId, Integer labelId);
    
    /**
     * 根据标签ID查询所有使用该标签的任务关联
     */
    List<AnalysisTaskLabel> findByLabelId(Integer labelId);
    
    /**
     * 删除任务的所有标签关联
     */
    @Modifying
    @Query("DELETE FROM AnalysisTaskLabel atl WHERE atl.analysisTaskId = :analysisTaskId")
    int deleteByAnalysisTaskId(@Param("analysisTaskId") Integer analysisTaskId);
    
    /**
     * 删除特定的任务-标签关联
     */
    @Modifying
    @Query("DELETE FROM AnalysisTaskLabel atl WHERE atl.analysisTaskId = :analysisTaskId AND atl.labelId = :labelId")
    int deleteByAnalysisTaskIdAndLabelId(@Param("analysisTaskId") Integer analysisTaskId, @Param("labelId") Integer labelId);
    
    /**
     * 统计任务关联的标签数量
     */
    long countByAnalysisTaskId(Integer analysisTaskId);
    
    /**
     * 统计标签被使用的次数
     */
    long countByLabelId(Integer labelId);
    
    /**
     * 检查任务是否关联了指定标签
     */
    boolean existsByAnalysisTaskIdAndLabelId(Integer analysisTaskId, Integer labelId);
    
    /**
     * 批量查询任务的标签关联
     */
    @Query("SELECT atl FROM AnalysisTaskLabel atl WHERE atl.analysisTaskId IN :analysisTaskIds")
    List<AnalysisTaskLabel> findByAnalysisTaskIds(@Param("analysisTaskIds") List<Integer> analysisTaskIds);
    
    /**
     * 查询任务关联的标签ID列表
     */
    @Query("SELECT atl.labelId FROM AnalysisTaskLabel atl WHERE atl.analysisTaskId = :analysisTaskId")
    List<Integer> findLabelIdsByAnalysisTaskId(@Param("analysisTaskId") Integer analysisTaskId);
}