package com.datalabeling.repository;

import com.datalabeling.entity.TaskLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 任务标签关联数据访问层
 */
@Repository
public interface TaskLabelRepository extends JpaRepository<TaskLabel, Integer> {

    /**
     * 根据任务ID查找所有关联标签
     *
     * @param taskId 任务ID
     * @return 关联标签列表
     */
    List<TaskLabel> findByTaskId(Integer taskId);

    /**
     * 根据任务ID查找所有关联标签（按创建顺序）
     */
    List<TaskLabel> findByTaskIdOrderByIdAsc(Integer taskId);

    /**
     * 根据标签ID查找所有关联任务
     *
     * @param labelId 标签ID
     * @return 关联任务列表
     */
    List<TaskLabel> findByLabelId(Integer labelId);

    /**
     * 检查任务和标签是否已关联
     *
     * @param taskId  任务ID
     * @param labelId 标签ID
     * @return 是否存在
     */
    boolean existsByTaskIdAndLabelId(Integer taskId, Integer labelId);

    /**
     * 删除任务的所有标签关联
     *
     * @param taskId 任务ID
     */
    void deleteByTaskId(Integer taskId);

    /**
     * 删除任务的指定标签关联
     *
     * @param taskId 任务ID
     * @param labelId 标签ID
     */
    void deleteByTaskIdAndLabelId(Integer taskId, Integer labelId);

    /**
     * 统计使用该标签的任务数量
     *
     * @param labelId 标签ID
     * @return 任务数量
     */
    long countByLabelId(Integer labelId);

    /**
     * 统计使用这些标签版本的任务数量
     */
    long countByLabelIdIn(List<Integer> labelIds);
}
