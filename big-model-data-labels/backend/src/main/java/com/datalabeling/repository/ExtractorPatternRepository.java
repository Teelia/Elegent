package com.datalabeling.repository;

import com.datalabeling.entity.ExtractorPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 提取器正则规则Repository
 */
@Repository
public interface ExtractorPatternRepository extends JpaRepository<ExtractorPattern, Integer> {

    /**
     * 根据提取器ID查找所有规则
     */
    List<ExtractorPattern> findByExtractorIdOrderBySortOrderAscPriorityDesc(Integer extractorId);

    /**
     * 根据提取器ID查找激活的规则
     */
    List<ExtractorPattern> findByExtractorIdAndIsActiveTrueOrderBySortOrderAscPriorityDesc(Integer extractorId);

    /**
     * 删除提取器的所有规则
     */
    void deleteByExtractorId(Integer extractorId);
}