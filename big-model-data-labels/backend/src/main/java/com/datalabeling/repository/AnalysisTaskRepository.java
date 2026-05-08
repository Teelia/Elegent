package com.datalabeling.repository;

import com.datalabeling.entity.AnalysisTask;
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
 * 分析任务Repository
 */
@Repository
public interface AnalysisTaskRepository extends JpaRepository<AnalysisTask, Integer> {
    
    /**
     * 根据数据集ID查询所有分析任务
     */
    List<AnalysisTask> findByDatasetIdOrderByCreatedAtDesc(Integer datasetId);
    
    /**
     * 根据数据集ID分页查询分析任务
     */
    Page<AnalysisTask> findByDatasetId(Integer datasetId, Pageable pageable);
    
    /**
     * 根据数据集ID和状态查询
     */
    List<AnalysisTask> findByDatasetIdAndStatus(Integer datasetId, String status);
    
    /**
     * 根据状态查询所有任务（用于后台任务调度）
     */
    List<AnalysisTask> findByStatus(String status);
    
    /**
     * 根据状态列表查询任务
     */
    List<AnalysisTask> findByStatusIn(List<String> statuses);
    
    /**
     * 根据ID和数据集ID查询（确保任务属于指定数据集）
     */
    Optional<AnalysisTask> findByIdAndDatasetId(Integer id, Integer datasetId);
    
    /**
     * 统计数据集的任务数量
     */
    long countByDatasetId(Integer datasetId);
    
    /**
     * 统计数据集特定状态的任务数量
     */
    long countByDatasetIdAndStatus(Integer datasetId, String status);
    
    /**
     * 查询正在执行的任务
     */
    @Query("SELECT t FROM AnalysisTask t WHERE t.status IN ('processing', 'paused') ORDER BY t.createdAt ASC")
    List<AnalysisTask> findActiveTasks();
    
    /**
     * 查询待执行的任务（按创建时间排序）
     */
    @Query("SELECT t FROM AnalysisTask t WHERE t.status = 'pending' ORDER BY t.createdAt ASC")
    List<AnalysisTask> findPendingTasks(Pageable pageable);
    
    /**
     * 更新任务进度
     */
    @Modifying
    @Query("UPDATE AnalysisTask t SET t.processedRows = :processedRows, " +
           "t.successRows = :successRows, t.failedRows = :failedRows WHERE t.id = :taskId")
    int updateProgress(@Param("taskId") Integer taskId, 
                       @Param("processedRows") Integer processedRows,
                       @Param("successRows") Integer successRows, 
                       @Param("failedRows") Integer failedRows);
    
    /**
     * 更新任务状态
     */
    @Modifying
    @Query("UPDATE AnalysisTask t SET t.status = :status WHERE t.id = :taskId")
    int updateStatus(@Param("taskId") Integer taskId, @Param("status") String status);
    
    /**
     * 查询用户的所有分析任务（通过数据集关联）
     */
    @Query("SELECT t FROM AnalysisTask t JOIN Dataset d ON t.datasetId = d.id " +
           "WHERE d.userId = :userId ORDER BY t.createdAt DESC")
    Page<AnalysisTask> findByUserId(@Param("userId") Integer userId, Pageable pageable);
    
    /**
     * 查询用户特定状态的分析任务
     */
    @Query("SELECT t FROM AnalysisTask t JOIN Dataset d ON t.datasetId = d.id " +
           "WHERE d.userId = :userId AND t.status = :status ORDER BY t.createdAt DESC")
    Page<AnalysisTask> findByUserIdAndStatus(@Param("userId") Integer userId, 
                                              @Param("status") String status, 
                                              Pageable pageable);
    
    /**
     * 检查数据集是否有正在执行的任务
     */
    @Query("SELECT COUNT(t) > 0 FROM AnalysisTask t WHERE t.datasetId = :datasetId " +
           "AND t.status IN ('pending', 'processing')")
    boolean hasActiveTasksByDatasetId(@Param("datasetId") Integer datasetId);
    
    /**
     * 获取数据集最新的分析任务
     */
    Optional<AnalysisTask> findFirstByDatasetIdOrderByCreatedAtDesc(Integer datasetId);
}