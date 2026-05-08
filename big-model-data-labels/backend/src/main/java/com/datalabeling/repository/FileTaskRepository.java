package com.datalabeling.repository;

import com.datalabeling.entity.FileTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文件任务数据访问层
 */
@Repository
public interface FileTaskRepository extends JpaRepository<FileTask, Integer> {

    /**
     * 根据用户ID查找任务（分页）
     *
     * @param userId   用户ID
     * @param pageable 分页参数
     * @return 任务列表
     */
    Page<FileTask> findByUserId(Integer userId, Pageable pageable);

    /**
     * 根据用户ID和状态查找任务（分页）
     *
     * @param userId   用户ID
     * @param status   状态
     * @param pageable 分页参数
     * @return 任务列表
     */
    Page<FileTask> findByUserIdAndStatus(Integer userId, String status, Pageable pageable);

    /**
     * 根据用户ID和文件哈希查找任务
     *
     * @param userId   用户ID
     * @param fileHash 文件哈希
     * @return 任务信息
     */
    Optional<FileTask> findByUserIdAndFileHash(Integer userId, String fileHash);

    /**
     * 查找指定状态的所有任务
     *
     * @param status 状态
     * @return 任务列表
     */
    List<FileTask> findByStatus(String status);

    /**
     * 查找指定状态的任务（分页）
     */
    org.springframework.data.domain.Page<FileTask> findByStatus(String status, org.springframework.data.domain.Pageable pageable);

    /**
     * 统计用户的任务数量（按状态）
     *
     * @param userId 用户ID
     * @param status 状态
     * @return 任务数量
     */
    long countByUserIdAndStatus(Integer userId, String status);

    /**
     * 查找需要清理的已归档任务
     *
     * @param archivedBefore 归档时间早于此时间
     * @return 任务列表
     */
    @Query("SELECT t FROM FileTask t WHERE t.status = 'archived' AND t.archivedAt < :archivedBefore")
    List<FileTask> findArchivedTasksBeforeDate(@Param("archivedBefore") LocalDateTime archivedBefore);

    /**
     * 更新任务进度（processed/failed）
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(rollbackFor = Exception.class)
    @Query("UPDATE FileTask t SET t.processedRows = :processedRows, t.failedRows = :failedRows WHERE t.id = :taskId")
    int updateProgress(@Param("taskId") Integer taskId,
                       @Param("processedRows") Integer processedRows,
                       @Param("failedRows") Integer failedRows);

    /**
     * 更新任务状态与时间
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(rollbackFor = Exception.class)
    @Query("UPDATE FileTask t SET t.status = :status, t.errorMessage = :errorMessage, " +
           "t.startedAt = :startedAt, t.completedAt = :completedAt, t.archivedAt = :archivedAt " +
           "WHERE t.id = :taskId")
    int updateStatusAndTimes(@Param("taskId") Integer taskId,
                             @Param("status") String status,
                             @Param("errorMessage") String errorMessage,
                             @Param("startedAt") LocalDateTime startedAt,
                             @Param("completedAt") LocalDateTime completedAt,
                             @Param("archivedAt") LocalDateTime archivedAt);
}
