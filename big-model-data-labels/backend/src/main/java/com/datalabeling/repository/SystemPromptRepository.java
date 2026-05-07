package com.datalabeling.repository;

import com.datalabeling.entity.SystemPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 系统提示词数据访问接口
 */
@Repository
public interface SystemPromptRepository extends JpaRepository<SystemPrompt, Integer> {

    /**
     * 根据提示词代码查询
     */
    Optional<SystemPrompt> findByCode(String code);

    /**
     * 根据类型查询启用的提示词
     */
    List<SystemPrompt> findByPromptTypeAndIsActiveTrue(String promptType);

    /**
     * 根据类型查询默认提示词
     */
    Optional<SystemPrompt> findByPromptTypeAndIsSystemDefaultTrueAndIsActiveTrue(String promptType);

    /**
     * 查询用户的所有提示词（包括全局）
     */
    @Query("SELECT p FROM SystemPrompt p WHERE p.userId = :userId OR p.isSystemDefault = true ORDER BY p.isSystemDefault DESC, p.createdAt DESC")
    List<SystemPrompt> findByUserOrGlobal(@Param("userId") Integer userId);

    /**
     * 查询用户指定类型的提示词（包括全局）
     */
    @Query("SELECT p FROM SystemPrompt p WHERE (p.userId = :userId OR p.isSystemDefault = true) AND p.promptType = :promptType ORDER BY p.isSystemDefault DESC, p.createdAt DESC")
    List<SystemPrompt> findByUserOrGlobalAndType(@Param("userId") Integer userId, @Param("promptType") String promptType);

    /**
     * 检查代码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 检查代码是否存在（排除指定ID）
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM SystemPrompt p WHERE p.code = :code AND p.id != :id")
    boolean existsByCodeAndIdNot(@Param("code") String code, @Param("id") Integer id);
}
