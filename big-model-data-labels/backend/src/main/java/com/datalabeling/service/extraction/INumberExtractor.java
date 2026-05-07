package com.datalabeling.service.extraction;

import java.util.List;
import java.util.Map;

/**
 * 号码提取器接口
 * 所有结构化号码提取器必须实现此接口
 */
public interface INumberExtractor {

    /**
     * 从文本中提取号码
     *
     * @param text 输入文本
     * @param options 提取选项（提取器特定的配置）
     * @return 提取结果列表
     */
    List<ExtractedNumber> extract(String text, Map<String, Object> options);

    /**
     * 获取提取器类型标识
     *
     * @return 提取器类型（如：id_card, bank_card, phone）
     */
    String getExtractorType();
}
