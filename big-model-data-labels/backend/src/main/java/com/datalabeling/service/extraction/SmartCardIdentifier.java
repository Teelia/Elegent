package com.datalabeling.service.extraction;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能卡号识别器
 *
 * 基于数据建模的卡号识别服务，确保：
 * 1. 按优先级准确识别各种卡号类型
 * 2. 避免同一串数字被重复识别为多种类型
 * 3. 支持格式验证和错误检测
 * 4. 防止超长输入导致的性能问题（OOM攻击防护）
 *
 * @author Claude Code
 * @since 2025-01-19
 */
@Slf4j
public class SmartCardIdentifier {

    /**
     * 最大输入长度限制（10KB）
     * 防止超长字符串导致内存溢出或性能问题
     */
    private static final int MAX_INPUT_LENGTH = 10000;

    /**
     * 从文本中智能识别并分类所有卡号
     *
     * 策略：
     * 1. 优先处理X结尾的身份证号（18位：17位数字+X）
     * 2. 按优先级从高到低匹配（银行卡 > 身份证 > 手机号 > 其他）
     * 3. 已匹配的号码不会被重复匹配为其他类型
     * 4. 返回每种类型的号码及其格式状态
     * 5. 防止超长输入导致的性能问题
     *
     * @param text 输入文本
     * @return 识别结果列表，按类型分组
     */
    public IdentificationResult identifyAllCards(String text) {
        if (text == null || text.isEmpty()) {
            return new IdentificationResult();
        }

        // 防止超长输入导致性能问题或内存溢出
        if (text.length() > MAX_INPUT_LENGTH) {
            log.warn("输入文本过长: {} 字符，截断至前 {} 字符", text.length(), MAX_INPUT_LENGTH);
            text = text.substring(0, MAX_INPUT_LENGTH);
        }

        log.debug("[SmartCardIdentifier] 开始识别，文本长度: {}", text.length());
        log.debug("[SmartCardIdentifier] 身份证号范围: {}-{}",
            CardType.ID_CARD.getMinLength(), CardType.ID_CARD.getMaxLength());

        // 记录已识别的号码和位置范围，避免重复
        Set<String> extractedNumbers = new HashSet<>();
        Set<ExtractedRange> extractedRanges = new HashSet<>();
        Map<String, Set<CardMatch>> cardsByType = new LinkedHashMap<>();

        // 优先级调整：X结尾身份证号需要最高优先级
        // 因为银行卡号可能会提取其17位数字子串
        Set<CardMatch> idCardMatches = new LinkedHashSet<>();
        extractXEndingIdCards(text, extractedNumbers, extractedRanges, idCardMatches);
        if (!idCardMatches.isEmpty()) {
            log.debug("[SmartCardIdentifier] 识别到 {} 个X结尾身份证号", idCardMatches.size());
            cardsByType.put(CardType.ID_CARD.name(), idCardMatches);
        }

        // 按优先级从高到低进行匹配（跳过已处理的X结尾身份证）
        for (CardType type : CardType.values()) {
            // X结尾身份证已处理，跳过
            if (type == CardType.ID_CARD) {
                // 继续处理非X结尾的身份证号（15位、18位纯数字）
                // 但需要跳过X结尾的17位数字部分
            }
            Set<CardMatch> matches = identifyCardsByType(text, type, extractedNumbers, extractedRanges);
            if (!matches.isEmpty()) {
                log.debug("[SmartCardIdentifier] 识别到 {} 个 {} 号码", matches.size(), type.name());
                // 合并到已有的结果中
                cardsByType.computeIfAbsent(type.name(), k -> new LinkedHashSet<>()).addAll(matches);
            }
        }

        log.debug("[SmartCardIdentifier] 识别完成，总共识别到 {} 个号码", extractedNumbers.size());
        return new IdentificationResult(cardsByType, extractedNumbers.size());
    }

    /**
     * 提取X结尾的身份证号（18位：17位数字+X）
     * 此方法优先执行，避免被其他卡号类型截断
     */
    private void extractXEndingIdCards(String text, Set<String> extractedNumbers,
                                       Set<ExtractedRange> extractedRanges, Set<CardMatch> idCardMatches) {
        Pattern xPattern = Pattern.compile("\\d{17}[Xx]");
        Matcher matcher = xPattern.matcher(text);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String number = matcher.group().toUpperCase();

            // 检查是否与已提取的范围重叠
            boolean overlaps = false;
            for (ExtractedRange range : extractedRanges) {
                if (start < range.getEnd() && end > range.getStart()) {
                    overlaps = true;
                    break;
                }
            }

            if (!overlaps && !extractedNumbers.contains(number)) {
                boolean isValid = validateFormat(number, CardType.ID_CARD);
                CardMatch match = new CardMatch(number, CardType.ID_CARD, isValid, start, end);
                idCardMatches.add(match);
                extractedNumbers.add(number);
                extractedRanges.add(new ExtractedRange(start, end));
            }
        }
    }

    /**
     * 识别指定类型的卡号
     */
    private Set<CardMatch> identifyCardsByType(String text, CardType type, Set<String> extractedNumbers, Set<ExtractedRange> extractedRanges) {
        Set<CardMatch> matches = new LinkedHashSet<>();

        // 查找符合长度范围的所有数字串
        List<NumberMatch> potentialNumbers = extractPotentialNumbers(text, type, extractedRanges);

        for (NumberMatch numberMatch : potentialNumbers) {
            String number = numberMatch.getNumber();

            // 跳过已提取的号码
            if (extractedNumbers.contains(number)) {
                continue;
            }

            // 进行格式验证
            boolean isValid = validateFormat(number, type);

            // 创建匹配结果
            CardMatch match = new CardMatch(
                number,
                type,
                isValid,
                numberMatch.getStart(),
                numberMatch.getEnd()
            );

            matches.add(match);
            extractedNumbers.add(number);
        }

        return matches;
    }

    /**
     * 从文本中提取指定长度范围的潜在号码
     *
     * 修复：使用已提取范围记录，避免子串重复匹配
     * 优化：对于手机号，检查边界避免提取更长号码的子串
     * 性能优化：使用缓存避免重复编译Pattern
     */
    private List<NumberMatch> extractPotentialNumbers(String text, CardType type, Set<ExtractedRange> extractedRanges) {
        List<NumberMatch> results = new ArrayList<>();

        // 不再使用缓存，每次重新构建Pattern以确保使用最新的范围配置
        // 这解决了CardType范围修改后缓存不更新的问题
        String regex = "\\d{" + type.getMinLength() + "," + type.getMaxLength() + "}";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        log.debug("[extractPotentialNumbers] 类型: {}, 正则: {}, 匹配到的候选数: {}",
            type.name(), regex, results.size());

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String number = matcher.group();

            // 检查是否与已提取的范围重叠
            boolean overlaps = false;
            for (ExtractedRange range : extractedRanges) {
                if (start < range.getEnd() && end > range.getStart()) {
                    overlaps = true;
                    break;
                }
            }

            // 对于手机号，需要特别检查边界
            // 不要提取可能是更长号码一部分的11位数字
            if (!overlaps && type == CardType.PHONE_NUMBER) {
                // 如果前面有数字，跳过（是更长号码的后缀）
                if (start > 0 && Character.isDigit(text.charAt(start - 1))) {
                    overlaps = true;
                }
                // 如果后面有数字，跳过（是更长号码的前缀）
                if (!overlaps && end < text.length() && Character.isDigit(text.charAt(end))) {
                    overlaps = true;
                }
            }

            if (!overlaps) {
                results.add(new NumberMatch(number, start, end));
                // 记录已提取的范围
                extractedRanges.add(new ExtractedRange(start, end));
            }
        }

        return results;
    }

    /**
     * 已提取范围记录
     *
     * 修复：实现equals和hashCode确保在HashSet中正确去重
     */
    private static class ExtractedRange {
        private final int start;
        private final int end;

        public ExtractedRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExtractedRange that = (ExtractedRange) o;
            return start == that.start && end == that.end;
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }
    }

    /**
     * 验证号码格式是否正确
     */
    private boolean validateFormat(String number, CardType type) {
        Pattern pattern = type.getPattern();
        Matcher matcher = pattern.matcher(number);

        // 身份证特殊处理：18位或15位都需要验证
        if (type == CardType.ID_CARD) {
            return matcher.matches();
        }

        // 其他类型直接匹配
        return matcher.matches();
    }

    /**
     * 号码匹配信息
     */
    @Data
    public static class NumberMatch {
        private final String number;
        private final int start;
        private final int end;

        public NumberMatch(String number, int start, int end) {
            this.number = number;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * 卡号匹配结果
     */
    @Data
    public static class CardMatch {
        private final String number;
        private final CardType type;
        private final boolean isValidFormat;
        private final int start;
        private final int end;

        public CardMatch(String number, CardType type, boolean isValidFormat, int start, int end) {
            this.number = number;
            this.type = type;
            this.isValidFormat = isValidFormat;
            this.start = start;
            this.end = end;
        }

        public String getStatus() {
            return isValidFormat ? "格式正确" : "格式错误";
        }

        public String getReason() {
            if (type == CardType.BANK_CARD) {
                return isValidFormat ? "银行卡号格式正确" : "银行卡号格式错误（位数或BIN码不匹配）";
            } else if (type == CardType.ID_CARD) {
                return isValidFormat ? "身份证号格式正确" : "身份证号格式错误（位数、地区码或校验位不匹配）";
            } else if (type == CardType.PHONE_NUMBER) {
                return isValidFormat ? "手机号格式正确" : "手机号格式错误（号段不匹配）";
            }
            return isValidFormat ? "格式正确" : "格式错误";
        }

        /**
         * 获取号码长度描述
         */
        public String getLengthDescription() {
            int length = number.length();
            if (type == CardType.ID_CARD) {
                if (length == 18) {
                    return "18位（二代身份证）";
                } else if (length == 15) {
                    return "15位（一代身份证）";
                } else {
                    return length + "位（非标准长度）";
                }
            } else if (type == CardType.BANK_CARD) {
                return length + "位银行卡";
            } else if (type == CardType.PHONE_NUMBER) {
                return "11位手机号";
            }
            return length + "位";
        }
    }

    /**
     * 识别结果总览
     */
    @Data
    public static class IdentificationResult {
        private final Map<String, Set<CardMatch>> cardsByType;
        private final int totalNumbers;

        public IdentificationResult() {
            this.cardsByType = new LinkedHashMap<>();
            this.totalNumbers = 0;
        }

        public IdentificationResult(Map<String, Set<CardMatch>> cardsByType, int totalNumbers) {
            this.cardsByType = cardsByType;
            this.totalNumbers = totalNumbers;
        }

        /**
         * 获取所有有效的卡号
         */
        public List<CardMatch> getAllValidCards() {
            List<CardMatch> result = new ArrayList<>();
            for (Set<CardMatch> matches : cardsByType.values()) {
                for (CardMatch match : matches) {
                    if (match.isValidFormat()) {
                        result.add(match);
                    }
                }
            }
            return result;
        }

        /**
         * 获取所有无效的卡号（格式错误的）
         */
        public List<CardMatch> getAllInvalidCards() {
            List<CardMatch> result = new ArrayList<>();
            for (Set<CardMatch> matches : cardsByType.values()) {
                for (CardMatch match : matches) {
                    if (!match.isValidFormat()) {
                        result.add(match);
                    }
                }
            }
            return result;
        }

        /**
         * 获取指定类型的卡号
         */
        public Set<CardMatch> getCardsByType(CardType type) {
            return cardsByType.getOrDefault(type.name(), new HashSet<>());
        }

        /**
         * 获取指定类型中格式错误的卡号
         */
        public List<CardMatch> getInvalidCardsByType(CardType type) {
            List<CardMatch> result = new ArrayList<>();
            Set<CardMatch> matches = cardsByType.get(type.name());
            if (matches != null) {
                for (CardMatch match : matches) {
                    if (!match.isValidFormat()) {
                        result.add(match);
                    }
                }
            }
            return result;
        }

        /**
         * 生成识别报告
         */
        public String generateReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== 卡号识别报告 ===\n");
            sb.append(String.format("总共识别到 %d 个号码\n\n", totalNumbers));

            for (Map.Entry<String, Set<CardMatch>> entry : cardsByType.entrySet()) {
                String typeName = entry.getKey();
                Set<CardMatch> matches = entry.getValue();

                sb.append(String.format("【%s】共 %d 个：\n", typeName, matches.size()));

                int validCount = 0;
                int invalidCount = 0;

                for (CardMatch match : matches) {
                    if (match.isValidFormat()) {
                        validCount++;
                        sb.append(String.format("  ✓ %s - %s\n", match.getNumber(), match.getLengthDescription()));
                    } else {
                        invalidCount++;
                        sb.append(String.format("  ✗ %s - %s\n", match.getNumber(), match.getReason()));
                    }
                }

                sb.append(String.format("  有效: %d, 无效: %d\n\n", validCount, invalidCount));
            }

            return sb.toString();
        }
    }
}
