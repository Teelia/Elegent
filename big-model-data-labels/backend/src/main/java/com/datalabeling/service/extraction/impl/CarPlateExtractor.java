package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;

/**
 * 车牌号提取器
 *
 * 提取功能：
 * - 普通车牌（省份简称+字母+5位数字/字母）
 * - 新能源车牌（8位）
 * - 军警车牌
 * - 识别归属省份
 *
 * 业务场景：
 * - 停车记录整理
 * - 交通数据分析
 * - 车辆信息管理
 */
@Slf4j
@Component
public class CarPlateExtractor extends AbstractEnhancedExtractor {

    // 省份简称映射
    private static final Map<String, String> PROVINCE_MAP = new LinkedHashMap<>();
    static {
        PROVINCE_MAP.put("京", "北京市");
        PROVINCE_MAP.put("津", "天津市");
        PROVINCE_MAP.put("沪", "上海市");
        PROVINCE_MAP.put("渝", "重庆市");
        PROVINCE_MAP.put("冀", "河北省");
        PROVINCE_MAP.put("豫", "河南省");
        PROVINCE_MAP.put("云", "云南省");
        PROVINCE_MAP.put("辽", "辽宁省");
        PROVINCE_MAP.put("黑", "黑龙江省");
        PROVINCE_MAP.put("湘", "湖南省");
        PROVINCE_MAP.put("皖", "安徽省");
        PROVINCE_MAP.put("鲁", "山东省");
        PROVINCE_MAP.put("新", "新疆维吾尔自治区");
        PROVINCE_MAP.put("苏", "江苏省");
        PROVINCE_MAP.put("浙", "浙江省");
        PROVINCE_MAP.put("赣", "江西省");
        PROVINCE_MAP.put("鄂", "湖北省");
        PROVINCE_MAP.put("桂", "广西壮族自治区");
        PROVINCE_MAP.put("甘", "甘肃省");
        PROVINCE_MAP.put("晋", "山西省");
        PROVINCE_MAP.put("蒙", "内蒙古自治区");
        PROVINCE_MAP.put("陕", "陕西省");
        PROVINCE_MAP.put("吉", "吉林省");
        PROVINCE_MAP.put("闽", "福建省");
        PROVINCE_MAP.put("贵", "贵州省");
        PROVINCE_MAP.put("粤", "广东省");
        PROVINCE_MAP.put("青", "青海省");
        PROVINCE_MAP.put("藏", "西藏自治区");
        PROVINCE_MAP.put("川", "四川省");
        PROVINCE_MAP.put("宁", "宁夏回族自治区");
        PROVINCE_MAP.put("琼", "海南省");
    }

    private static final ExtractorMetadata METADATA = ExtractorMetadata.builder()
        .code("car_plate")
        .name("车牌号提取器")
        .description("提取各种类型的中国车牌号，识别归属省份")
        .category("builtin")
        .outputField("车牌号")
        .dataType("string")
        .multiValue(true)
        .accuracy("high")
        .performance("fast")
        .version("1.0.0")
        .author("System")
        .tags(Arrays.asList("交通", "车辆", "车牌"))
        .useCase("停车记录、交通数据、车辆管理")
        .options(Arrays.asList(
            ExtractorMetadata.ExtractorOption.builder()
                .key("include_new_energy")
                .name("包含新能源车牌")
                .description("提取8位新能源车牌")
                .type("boolean")
                .defaultValue(true)
                .build(),
            ExtractorMetadata.ExtractorOption.builder()
                .key("include_military")
                .name("包含军警车牌")
                .description("提取军警车牌")
                .type("boolean")
                .defaultValue(false)
                .build()
        ))
        .build();

    @Override
    public ExtractorMetadata getMetadata() {
        return METADATA;
    }

    @Override
    protected List<ExtractorPattern> getPatterns() {
        return Arrays.asList(
            // 新能源车牌（8位，小汽车/大型车）
            ExtractorPattern.highPriority(
                "new_energy",
                "\\b[京津冀晋蒙辽吉黑沪苏浙皖闽赣鲁豫鄂湘粤桂琼渝川贵云藏陕甘青宁新][A-Z][0-9A-Z]{6}\\b",
                0.95f
            ),
            // 传统车牌（7位）
            ExtractorPattern.highPriority(
                "traditional",
                "\\b[京津冀晋蒙辽吉黑沪苏浙皖闽赣鲁豫鄂湘粤桂琼渝川贵云藏陕甘青宁新][A-Z][0-9A-Z]{5}\\b",
                0.98f
            ),
            // 军警车牌（简化）
            ExtractorPattern.of(
                "military",
                "\\b(?:海|空|沈|兰|济|南|广|成)[0-9A-Z]{5,6}\\b",
                0.85f
            )
        );
    }

    @Override
    protected EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options) {
        String plate = matcher.group();

        // 判断车牌类型
        String plateType = identifyPlateType(plate);

        // 检查是否排除军警车牌
        if ("军警车牌".equals(plateType) && !Boolean.TRUE.equals(options.get("include_military"))) {
            return null;
        }

        // 识别归属省份
        String province = null;
        String provinceCode = plate.substring(0, 1);
        if (PROVINCE_MAP.containsKey(provinceCode)) {
            province = PROVINCE_MAP.get(provinceCode);
        }

        // 构建验证信息
        StringBuilder validation = new StringBuilder();
        validation.append(plateType);
        if (province != null) {
            validation.append("，归属: ").append(province);
        }

        // 构建结果
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("plateType", plateType);
        attributes.put("length", plate.length());
        if (province != null) {
            attributes.put("province", province);
            attributes.put("provinceCode", provinceCode);
        }
        attributes.put("cityCode", plate.length() > 1 ? plate.substring(1, 2) : "");

        return EnhancedExtractedResult.builder()
            .field(getMetadata().getOutputField())
            .value(plate)
            .rawValue(plate)
            .confidence("新能源车牌".equals(plateType) ? 0.95f : 0.98f)
            .validation(validation.toString())
            .validationStatus("valid")
            .businessMeaning("车辆牌照号码，用于车辆识别和管理")
            .dataType("string")
            .attributes(attributes)
            .startIndex(matcher.start())
            .endIndex(matcher.end())
            .build();
    }

    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("include_new_energy", true);
        options.put("include_military", false);
        return options;
    }

    @Override
    public List<ExtractorExample> getExamples() {
        return Arrays.asList(
            ExtractorExample.of(
                "车牌号：京A12345",
                "[\"京A12345\"]",
                "提取传统车牌"
            ),
            ExtractorExample.of(
                "新能源车牌：沪AD12345",
                "[\"沪AD12345\"]",
                "提取新能源车牌"
            ),
            ExtractorExample.of(
                "粤B88888和京A66666",
                "[\"粤B88888\", \"京A66666\"]",
                "提取多个车牌"
            )
        );
    }

    /**
     * 识别车牌类型
     */
    private String identifyPlateType(String plate) {
        int length = plate.length();

        if (length == 8) {
            return "新能源车牌";
        } else if (length == 7) {
            return "传统车牌";
        } else if (plate.startsWith("海") || plate.startsWith("空") ||
                   plate.startsWith("沈") || plate.startsWith("兰") ||
                   plate.startsWith("济") || plate.startsWith("南") ||
                   plate.startsWith("广") || plate.startsWith("成")) {
            return "军警车牌";
        } else {
            return "未知类型";
        }
    }

    @Override
    public String getExtractorType() {
        return "car_plate";
    }
}
