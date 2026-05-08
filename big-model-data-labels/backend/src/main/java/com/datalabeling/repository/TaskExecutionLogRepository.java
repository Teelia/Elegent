package com.datalabeling.repository;

import com.datalabeling.entity.TaskExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务执行日志Repository
 */
@Repository
public interface TaskExecutionLogRepository extends JpaRepository<TaskExecutionLog, Long> {
    
    /**
     * 根据任务ID查询所有日志
     */
    List<TaskExecutionLog> findByAnalysisTaskIdOrderByCreatedAtDesc(Integer analysisTaskId);
    
    /**
     * 根据任务ID分页查询日志
     */
    Page<TaskExecutionLog> findByAnalysisTaskId(Integer analysisTaskId, Pageable pageable);
    
    /**
     * 根据任务ID和日志级别查询
     */
    List<TaskExecutionLog> findByAnalysisTaskIdAndLogLevel(Integer analysisTaskId, String logLevel);
    
    /**
     * 根据任务ID和日志级别分页查询
     */
    Page<TaskExecutionLog> findByAnalysisTaskIdAndLogLevel(Integer analysisTaskId, String logLevel, Pageable pageable);
    
    /**
     * 根据任务ID和数据行ID查询
     */
    List<TaskExecutionLog> findByAnalysisTaskIdAndDataRowId(Integer analysisTaskId, Long dataRowId);
    
    /**
     * 查询任务的错误日志
     */
    @Query("SELECT tel FROM TaskExecutionLog tel WHERE tel.analysisTaskId = :analysisTaskId " +
           "AND tel.logLevel = 'ERROR' ORDER BY tel.createdAt DESC")
    List<TaskExecutionLog> findErrorLogsByAnalysisTaskId(@Param("analysisTaskId") Integer analysisTaskId);
    
    /**
     * 查询任务的错误日志（分页）
     */
    @Query("SELECT tel FROM TaskExecutionLog tel WHERE tel.analysisTaskId = :analysisTaskId " +
           "AND tel.logLevel = 'ERROR' ORDER BY tel.createdAt DESC")
    Page<TaskExecutionLog> findErrorLogsByAnalysisTaskId(@Param("analysisTaskId") Integer analysisTaskId, Pageable pageable);
    
    /**
     * 查询任务最近的日志
     */
    @Query("SELECT tel FROM TaskExecutionLog tel WHERE tel.analysisTaskId = :analysisTaskId " +
           "ORDER BY tel.createdAt DESC")
    List<TaskExecutionLog> findRecentLogsByAnalysisTaskId(@Param("analysisTaskId") Integer analysisTaskId, Pageable pageable);
    
    /**
     * 查询指定时间之后的日志（用于实时更新）
     */
    @Query("SELECT tel FROM TaskExecutionLog tel WHERE tel.analysisTaskId = :analysisTaskId " +
           "AND tel.createdAt > :since ORDER BY tel.createdAt ASC")
    List<TaskExecutionLog> findLogsSince(@Param("analysisTaskId") Integer analysisTaskId,
                                          @Param("since") LocalDateTime since);
    
    /**
     * 统计任务的日志数量
     */
    long countByAnalysisTaskId(Integer analysisTaskId);
    
    /**
     * 统计任务特定级别的日志数量
     */
    long countByAnalysisTaskIdAndLogLevel(Integer analysisTaskId, String logLevel);
    
    /**
     * 删除任务的所有日志
     */
    @Modifying
    @Query("DELETE FROM TaskExecutionLog tel WHERE tel.analysisTaskId = :analysisTaskId")
    int deleteByAnalysisTaskId(@Param("analysisTaskId") Integer analysisTaskId);
    
    /**
     * 删除指定时间之前的日志（用于清理旧日志）
     */
    @Modifying
    @Query("DELETE FROM TaskExecutionLog tel WHERE tel.createdAt < :before")
    int deleteLogsBefore(@Param("before") LocalDateTime before);
    
    /**
     * 删除指定任务指定时间之前的日志
     */
    @Modifying
    @Query("DELETE FROM TaskExecutionLog tel WHERE tel.analysisTaskId = :analysisTaskId AND tel.createdAt < :before")
    int deleteByAnalysisTaskIdAndCreatedAtBefore(@Param("analysisTaskId") Integer analysisTaskId,
                                                  @Param("before") LocalDateTime before);
    
    /**
     * 查询包含特定关键词的日志
     */
    @Query("SELECT tel FROM TaskExecutionLog tel WHERE tel.analysisTaskId = :analysisTaskId " +
           "AND tel.message LIKE %:keyword% ORDER BY tel.createdAt DESC")
    Page<TaskExecutionLog> searchByMessage(@Param("analysisTaskId") Integer analysisTaskId,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);
}