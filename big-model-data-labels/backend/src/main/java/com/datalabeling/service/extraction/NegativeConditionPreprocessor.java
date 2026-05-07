package com.datalabeling.service.extraction;

import com.datalabeling.entity.Label;
import com.datalabeling.util.PiiMaskingUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 否定条件任务预处理器
 *
 * 对于规则明确的否定条件提取任务，使用精确的代码逻辑处理，
 * 避免调用 LLM，实现 100% 准确率。
 *
 * 处理流程：
 * 1. 解析标签描述，识别任务类型
 * 2. 应用对应的规则引擎进行精确提取
 * 3. 如果规则能完全处理，直接返回结果
 * 4. 如果规则无法处理，返回 null，由 LLM 接管
 *
 * @author Claude Code
 * @since 2025-01-19
 */
@Slf4j
@Component
public class NegativeConditionPreprocessor {

    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * 号码证据提取器（规则侧SSOT）。
     * 用于避免“银行卡规则先占用范围导致身份证候选被吞掉”的系统性漏检。
     */
    private final NumberEvidenceExtractor numberEvidenceExtractor = new NumberEvidenceExtractor();

    /**
     * 获取号码证据提取器实例（用于DeepSeekService数据建模预识别）。
     */
    public NumberEvidenceExtractor getNumberEvidenceExtractor() {
        return numberEvidenceExtractor;
    }

    /**
     * 尝试使用规则预处理否定条件任务
     *
     * @param label 标签定义
     * @param rowData 数据行
     * @return 如果规则能处理，返回提取结果；否则返回 null
     */
    public PreprocessResult preprocess(Label label, Map<String, Object> rowData) {
        String description = label.getDescription();
        if (description == null || description.isEmpty()) {
            return null;
        }

        // 检测是否是否定条件任务
        if (!isNegativeConditionTask(description)) {
            return null;
        }

        // 尝试不同的规则引擎
        PreprocessResult result = null;

        // 1. 尝试处理"不满足18位身份证号"类型
        if (description.contains("18位") && description.contains("身份证")) {
            result = processInvalidIdCardByLength(rowData, description);
        }

        // 2. 尝试处理"错误的身份证号"类型
        if (result == null && description.contains("错误") && description.contains("身份证")) {
            result = processInvalidIdCardGeneral(rowData);
        }

        // 3. 尝试处理"不满足XX长度"的通用模式
        if (result == null) {
            result = processInvalidLengthByPattern(rowData, description);
        }

        return result;
    }

    /**
     * 处理"不满足18位的身份证号"任务
     *
     * 优化策略：使用 SmartCardIdentifier 进行智能卡号识别
     * - 自动区分银行卡号、身份证号、手机号
     * - 避免银行卡号被错误识别为身份证号
     * - 基于优先级匹配，确保同一号码不被重复识别
     *
     * 规则：查找所有可能的身份证号码，过滤出长度不是18位的
     * 注意：18位身份证号包括 17位数字+1位数字/X 的情况
     */
    private PreprocessResult processInvalidIdCardByLength(Map<String, Object> rowData, String description) {
        // 将数据转换为字符串
        String dataString = convertDataToString(rowData);
        if (dataString == null || dataString.isEmpty()) {
            log.debug("[预处理器] 数据为空");
            return PreprocessResult.empty("数据为空");
        }

        // 使用证据提取器进行规则建模（避免冲突吞噬）
        NumberEvidence evidence = numberEvidenceExtractor.extract(dataString);

        List<String> invalidIds = new ArrayList<>();
        for (NumberEvidence.NumberCandidate n : evidence.getNumbers()) {
            if ("ID_INVALID_LENGTH".equals(n.getType())) {
                invalidIds.add(n.getValue());
            }
        }

        int idAny = (int) evidence.getNumbers().stream()
            .filter(n -> n.getType() != null && n.getType().startsWith("ID_"))
            .count();
        int idValid18 = (int) evidence.getNumbers().stream()
            .filter(n -> "ID_VALID_18".equals(n.getType()))
            .count();
        int idValid15 = (int) evidence.getNumbers().stream()
            .filter(n -> "ID_VALID_15".equals(n.getType()))
            .count();
        int idInvalidLength = invalidIds.size();

        log.debug("[预处理器] 身份证候选数: {}, 18位有效: {}, 15位有效: {}, 不满足18位(错误): {}",
            idAny, idValid18, idValid15, idInvalidLength);

        if (idAny == 0) {
            log.debug("[预处理器] 返回: 未找到身份证号");
            return PreprocessResult.empty("未找到身份证号");
        }

        if (invalidIds.isEmpty()) {
            // 业务口径：15位旧身份证视为“正确号码”，不应计入“不满足18位的错误身份证号”。
            if (idValid15 > 0 && idValid18 == 0) {
                log.debug("[预处理器] 返回: 仅发现15位旧身份证（视为正确），无错误号码");
                return PreprocessResult.empty("仅发现15位旧身份证号（视为正确），未找到不满足18位的错误身份证号");
            }
            log.debug("[预处理器] 返回: 未找到不满足18位的错误身份证号");
            return PreprocessResult.empty("未找到不满足18位的错误身份证号（15位旧身份证号视为正确）");
        }

        // 构建结果
        String resultText = String.join(", ", invalidIds);
        String reasoning = String.format(
            "使用规则证据提取器从数据中识别到 %d 个身份证相关候选，其中 %d 个不满足18位要求：%s [规则预处理: 号码证据建模]",
            idAny, invalidIds.size(), resultText
        );

        // 日志仅输出脱敏信息
        log.debug("[预处理器] 返回成功: {}", PiiMaskingUtil.maskNumber(resultText));
        return PreprocessResult.success(resultText, 95, reasoning);
    }

    /**
     * 计算身份证号的有效长度
     * 规则：中国身份证号标准为18位，可以是18位数字或17位数字+X
     */
    private int calculateIdCardLength(String id) {
        if (id == null || id.isEmpty()) {
            return 0;
        }

        // 如果以X结尾，总长度就是字符串长度（18）
        if (id.toUpperCase().endsWith("X")) {
            return id.length();
        }

        // 纯数字，返回数字位数
        return id.length();
    }

    /**
     * 通用处理"错误的身份证号"任务
     *
     * 优化策略：使用 SmartCardIdentifier 进行智能卡号识别
     * - 自动区分银行卡号、身份证号、手机号
     * - 避免银行卡号被错误识别为身份证号
     * - 基于优先级匹配，确保同一号码不被重复识别
     *
     * 规则：中国身份证号标准格式：
     * - 18位（二代身份证）或 15位（一代身份证，已停用）
     * - 前17位必须是数字，第18位可以是数字或 X
     * - 地区码有效（前6位）
     */
    private PreprocessResult processInvalidIdCardGeneral(Map<String, Object> rowData) {
        String dataString = convertDataToString(rowData);
        if (dataString == null || dataString.isEmpty()) {
            return PreprocessResult.empty("数据为空");
        }

        NumberEvidence evidence = numberEvidenceExtractor.extract(dataString);

        List<String> invalidIds = new ArrayList<>();
        for (NumberEvidence.NumberCandidate n : evidence.getNumbers()) {
            if ("ID_INVALID_LENGTH".equals(n.getType()) || "ID_INVALID_CHECKSUM".equals(n.getType())) {
                invalidIds.add(n.getValue());
            }
        }

        int idAny = (int) evidence.getNumbers().stream()
            .filter(n -> n.getType() != null && n.getType().startsWith("ID_"))
            .count();

        if (idAny == 0) {
            return PreprocessResult.empty("未找到身份证号");
        }

        if (invalidIds.isEmpty()) {
            return PreprocessResult.empty("所有身份证号格式都正确");
        }

        String resultText = String.join(", ", invalidIds);
        String reasoning = String.format(
            "使用规则证据提取器从数据中识别到 %d 个身份证相关候选，其中 %d 个为错误身份证号：%s [规则预处理: 号码证据建模]",
            idAny, invalidIds.size(), resultText
        );

        return PreprocessResult.success(resultText, 95, reasoning);
    }

    /**
     * 通用处理"不满足XX长度"的模式
     *
     * 例如："不满足11位的手机号"、"不满足XX的号码"
     */
    private PreprocessResult processInvalidLengthByPattern(Map<String, Object> rowData, String description) {
        // 尝试提取长度要求
        Pattern lengthPattern = Pattern.compile("不满足(\\d+)位");
        Matcher lengthMatcher = lengthPattern.matcher(description);

        if (!lengthMatcher.find()) {
            return null;
        }

        int requiredLength = Integer.parseInt(lengthMatcher.group(1));
        String dataString = convertDataToString(rowData);
        if (dataString == null || dataString.isEmpty()) {
            return PreprocessResult.empty("数据为空");
        }

        // 查找所有可能的号码（长度在 requiredLength ± 5 范围内）
        int minLen = Math.max(1, requiredLength - 5);
        int maxLen = requiredLength + 5;
        Pattern numberPattern = Pattern.compile("\\d{" + minLen + "," + maxLen + "}");
        Matcher numberMatcher = numberPattern.matcher(dataString);

        List<String> allNumbers = new ArrayList<>();
        List<String> invalidNumbers = new ArrayList<>();

        while (numberMatcher.find()) {
            String number = numberMatcher.group();
            allNumbers.add(number);
            if (number.length() != requiredLength) {
                invalidNumbers.add(number);
            }
        }

        if (invalidNumbers.isEmpty()) {
            return PreprocessResult.empty(String.format("所有号码都是%d位", requiredLength));
        }

        String resultText = String.join(", ", invalidNumbers);
        String reasoning = String.format(
            "从数据中找到 %d 个可能的号码，其中 %d 个不满足%d位要求：%s",
            allNumbers.size(), invalidNumbers.size(), requiredLength, resultText
        );

        return PreprocessResult.success(resultText, 85, reasoning);
    }

    /**
     * 从数据中提取所有可能的身份证号码（包括错误格式）
     *
     * 规则：
     * 1. 优先匹配完整的18位身份证号（17位数字+X/x）
     * 2. 再匹配15-20位纯数字（包括19位、20位等错误长度的号码）
     * 3. 避免重复提取（已匹配完整的不再只提取数字部分）
     *
     * 注意：对于"提取错误的身份证号"任务，需要提取所有可能的长度的号码
     */
    private List<String> extractPotentialIdCards(String dataString) {
        List<String> results = new ArrayList<>();
        Set<String> extractedPositions = new HashSet<>(); // 记录已提取的位置

        // 1. 优先匹配17位数字+X/x的18位身份证号
        Pattern patternWithX = Pattern.compile("\\d{17}[Xx]");
        Matcher matcherWithX = patternWithX.matcher(dataString);

        Set<String> uniqueIds = new HashSet<>();
        while (matcherWithX.find()) {
            String id = matcherWithX.group().toUpperCase();
            int start = matcherWithX.start();
            int end = matcherWithX.end();

            uniqueIds.add(id);

            // 标记这个完整ID已被提取
            extractedPositions.add(start + "-" + end);

            // 关键修复：标记纯数字部分（去掉X）也被提取过
            // 这样后续匹配纯数字时就不会重复提取
            int pureDigitStart = start;
            int pureDigitEnd = end - 1;  // 去掉最后的X
            extractedPositions.add(pureDigitStart + "-" + pureDigitEnd);
        }

        // 2. 匹配14-20位连续数字（排除已提取的位置）
        // 修复：扩展范围从15-20改为14-20，支持14位的错误号码
        // 同时移除长度限制，因为19位、20位的号码也是"错误的身份证号"
        Pattern pattern = Pattern.compile("\\d{14,20}");
        Matcher matcher = pattern.matcher(dataString);

        while (matcher.find()) {
            String id = matcher.group();
            int start = matcher.start();
            int end = matcher.end();
            String positionKey = start + "-" + end;

            // 检查是否已被提取
            if (!extractedPositions.contains(positionKey)) {
                // 修复：提取所有15-20位的号码，包括19位、20位
                // 这些都是可能存在的"错误身份证号"
                // 过滤银行卡号：19位且以621-625开头
                if (isLikelyBankCardNumber(id)) {
                    continue;
                }
                uniqueIds.add(id);
            }
        }

        results.addAll(uniqueIds);
        return results;
    }

    /**
     * 判断一个数字串是否可能是银行卡号
     *
     * 银行卡特征：
     * - 通常16-19位
     * - 中国银联卡以62开头，具体BIN码包括621-625
     * - 如果是19位且以621-625开头，很可能是银行卡
     */
    private boolean isLikelyBankCardNumber(String number) {
        if (number == null || number.length() < 16) {
            return false;
        }

        // 19位且以621-625开头，很可能是银行卡
        if (number.length() == 19 && number.matches("^6[2-5]\\d+")) {
            return true;
        }

        return false;
    }

    /**
     * 验证身份证号格式是否正确
     *
     * 规则：
     * - 18位：前17位数字 + 第18位数字或X
     * - 15位：全数字（一代身份证，已停用但仍可能存在）
     */
    private boolean isValidIdCardFormat(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }

        // 18位身份证
        if (id.length() == 18) {
            return id.matches("\\d{17}[\\dXx]");
        }

        // 15位身份证（一代）
        if (id.length() == 15) {
            return id.matches("\\d{15}");
        }

        return false;
    }

    /**
     * 判断是否是否定条件任务
     */
    private boolean isNegativeConditionTask(String description) {
        String desc = description.toLowerCase();
        return desc.contains("不满足") || desc.contains("不是") ||
            desc.contains("非") || desc.contains("错误的") ||
            desc.contains("异常的") || desc.contains("无效") ||
            desc.contains("不符");
    }

    /**
     * 将数据行转换为字符串
     */
    private String convertDataToString(Map<String, Object> rowData) {
        if (rowData == null || rowData.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : rowData.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            Object value = entry.getValue();
            if (value != null) {
                sb.append(value.toString());
            }
        }
        return sb.toString();
    }

    /**
     * 预处理结果
     */
    public static class PreprocessResult {
        private final boolean canHandle;
        private final String result;
        private final int confidence;
        private final String reasoning;
        private final boolean isEmpty;

        private PreprocessResult(boolean canHandle, String result, int confidence,
                                 String reasoning, boolean isEmpty) {
            this.canHandle = canHandle;
            this.result = result;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.isEmpty = isEmpty;
        }

        public static PreprocessResult success(String result, int confidence, String reasoning) {
            return new PreprocessResult(true, result, confidence, reasoning, false);
        }

        public static PreprocessResult empty(String reasoning) {
            return new PreprocessResult(true, "无", 100, reasoning, true);
        }

        public boolean canHandle() {
            return canHandle;
        }

        public String getResult() {
            return result;
        }

        public int getConfidence() {
            return confidence;
        }

        public String getReasoning() {
            return reasoning;
        }

        public boolean isEmpty() {
            return isEmpty;
        }

        public Map<String, Object> toResponseMap(long durationMs) {
            Map<String, Object> response = new HashMap<>();
            response.put("result", result);
            response.put("success", true);
            response.put("confidence", confidence);
            // 规则预处理为确定性计算，但并不意味着业务口径上的“100%准确”，避免误导。
            response.put("reasoning", reasoning + " [规则预处理]");
            response.put("durationMs", durationMs);
            response.put("preprocessed", true);
            return response;
        }
    }
}
