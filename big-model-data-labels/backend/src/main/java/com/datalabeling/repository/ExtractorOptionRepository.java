package com.datalabeling.repository;

import com.datalabeling.entity.ExtractorOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 提取器选项配置Repository
 */
@Repository
public interface ExtractorOptionRepository extends JpaRepository<ExtractorOption, Integer> {

    /**
     * 根据提取器ID查找所有选项
     */
    List<ExtractorOption> findByExtractorIdOrderBySortOrderAsc(Integer extractorId);

    /**
     * 删除提取器的所有选项
     */
    void deleteByExtractorId(Integer extractorId);
}