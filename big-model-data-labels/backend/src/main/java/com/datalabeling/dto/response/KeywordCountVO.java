package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关键词统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeywordCountVO {

    private String keyword;

    private Integer count;
}

