package com.datalabeling.service.extraction;

import com.datalabeling.util.PiiMaskingUtil;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * 号码证据提取器（规则侧 SSOT）。
 *
 * <p>核心目标：
 * - 用确定性规则对文本中的号码进行候选抽取、校验与分类
 * - 输出结构化证据 JSON，供标签判断与 LLM 分析引用
 *
 * <p>当前聚焦：身份证/银行卡/手机号（以及"错误身份证号"场景）。
 */
@Slf4j
public class NumberEvidenceExtractor {

    // 号码候选：用于捕获 17/19/22 等“错误身份证号”及超长数字串
    // 注意：避免把“18位身份证(末位X)”拆成 17 位纯数字，导致 invalid_length 误判
    private static final Pattern DIGITS_14_22 = Pattern.compile("(?<!\\d)\\d{14,22}(?![\\dXx])");

    // 手机号（严格 11 位，避免从更长数字中截取）
    private static final Pattern PHONE_11 = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    // 手机号（11位，以1开头，允许无效号段；用于捕获“错误手机号”）
    private static final Pattern PHONE_11_START_1 = Pattern.compile("(?<!\\d)1\\d{10}(?!\\d)");

    // 遮挡手机号（11位，包含*；用于捕获 1553***5598 等）
    private static final Pattern PHONE_11_MASKED = Pattern.compile("(?<![\\d*])1[\\d*]{10}(?![\\d*])");

    // X 结尾身份证（18 位）：前17位数字 + 末位X
    private static final Pattern ID_18_WITH_X = Pattern.compile("(?<!\\d)\\d{17}[Xx](?!\\d)");

    // 14-22位包含X（X可能在任意位置）：用于捕获所有可能的错误身份证号
    // 覆盖范围：14位（太短）、15位（标准）、16-17位（太短）、18位（标准/错误）、19-22位（太长）
    private static final Pattern ID_CONTAINS_X = Pattern.compile("(?<!\\d)[\\dXx]{14,22}(?![\\dXx])");

    // 19 位且末位 X：常见于"多一位 + X校验位"的录入错误（应视为长度错误）
    private static final Pattern ID_19_ENDING_X = Pattern.compile("(?<!\\d)\\d{18}[Xx](?!\\d)");

    // 18 位身份证（结构校验，不含校验位算法）
    private static final Pattern ID_18_STRUCTURE = Pattern.compile(
        "^[1-6]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$"
    );

    // 15 位旧身份证（结构）
    private static final Pattern ID_15_STRUCTURE = Pattern.compile(
        "^[1-9]\\d{5}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}$"
    );

    // 遮挡数字串（用于银行卡遮挡，如 6212***********3290）；限制16-19位，且必须包含*，避免把纯数字误当作“遮挡串”
    private static final Pattern MASKED_16_19 = Pattern.compile("(?<![\\d*])(?=[\\d*]{0,18}\\*)[\\d*]{16,19}(?![\\d*])");

    // 银行卡前缀弱特征（用于无效/遮挡银行卡判定）。
    // 为降低误判，弱特征默认收敛到 62（银联卡常见前缀）；有效银行卡仍以 Luhn 为准，不受此限制。
    private static final Pattern BANK_PREFIX_LIKELY = Pattern.compile("^62\\d+");

    // 18 位身份证校验位权重与映射
    private static final int[] ID_CARD_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CARD_CHECK_CHARS = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    /**
     * 提取并构建证据。
     */
    public NumberEvidence extract(String text) {
        NumberEvidence evidence = new NumberEvidence();
        if (text == null || text.isEmpty()) {
            evidence.getDerived().put("empty_text", true);
            return evidence;
        }

        // 1) 抽取候选（保留位置信息）
        List<CandidateMatch> candidates = new ArrayList<>();

        // 1.1 优先抽取 14-22位包含X的号码（包括X在末尾和X在中间）
        // 注意：必须在 DIGITS_14_22 之前执行，避免被纯数字规则拆分
        addMatches(candidates, ID_CONTAINS_X, text);
        log.info("[ID_CONTAINS_X] 匹配到 {} 个包含X的号码候选", candidates.stream()
            .filter(c -> c.value.contains("X") || c.value.contains("x"))
            .count());
        // 1.1.1 抽取"19位末位X"（避免被纯数字规则拆分或漏检）
        addMatches(candidates, ID_19_ENDING_X, text);
        // 1.2 抽取 14-22 位纯数字候选（覆盖错误身份证与部分银行卡/流水号）
        addMatches(candidates, DIGITS_14_22, text);
        // 1.3 抽取手机号（11 位，含无效号段）
        addMatches(candidates, PHONE_11_START_1, text);
        // 1.4 抽取遮挡手机号（11位，包含*）
        addMatches(candidates, PHONE_11_MASKED, text);
        // 1.5 抽取遮挡数字串（优先覆盖银行卡遮挡）
        addMatches(candidates, MASKED_16_19, text);

        // 1.6 抽取手机号（严格 11 位，作为补充）
        addMatches(candidates, PHONE_11, text);

        // 2) 去重：同位置同值只保留一次
        List<CandidateMatch> unique = dedupeBySpanAndValue(candidates);

        // 3) 分类与证据构建
        int seq = 1;
        Set<String> seenValueWithType = new HashSet<>();
        for (CandidateMatch m : unique) {
            String raw = m.value;
            String normalized = normalize(raw);
            if (normalized.isEmpty()) {
                continue;
            }

            // 对外展示/提示词使用脱敏值
            String masked = PiiMaskingUtil.maskNumber(normalized);
            int len = normalized.length();

            Classification c = classify(normalized, m, text);
            if (c == null) {
                continue;
            }

            // 同类型同值去重（保留首个位置）
            String dedupeKey = c.type + ":" + normalized;
            if (seenValueWithType.contains(dedupeKey)) {
                continue;
            }
            seenValueWithType.add(dedupeKey);

            NumberEvidence.NumberCandidate n = new NumberEvidence.NumberCandidate();
            n.setId("n" + seq++);
            n.setType(c.type);
            n.setValue(normalized);
            n.setMaskedValue(masked);
            n.setLength(len);
            n.setStartIndex(m.start);
            n.setEndIndex(m.end);
            applyMaskInfo(n, normalized);
            n.setKeywordHint(c.keywordHint);
            n.setConfidenceRule(c.confidence);
            n.getValidations().addAll(c.validations);
            n.getConflicts().addAll(c.conflicts);
            evidence.getNumbers().add(n);
        }

        // 4) 派生字段
        derive(evidence);
        return evidence;
    }

    private void addMatches(List<CandidateMatch> out, Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            out.add(new CandidateMatch(matcher.group(), matcher.start(), matcher.end()));
        }
    }

    private List<CandidateMatch> dedupeBySpanAndValue(List<CandidateMatch> in) {
        List<CandidateMatch> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CandidateMatch m : in) {
            String key = m.start + "-" + m.end + ":" + m.value;
            if (seen.add(key)) {
                out.add(m);
            }
        }
        return out;
    }

    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    /**
     * 分类：输出可用于“号码类标签”的证据类型。
     *
     * <p>重要：当出现“银行卡 Luhn 通过”与“身份证结构特征强”冲突时，优先保留身份证侧证据，
     * 以避免否定条件任务（如 Task43）被银行卡规则吞噬导致漏检。
     */
    private Classification classify(String number, CandidateMatch span, String fullText) {
        if (number == null || number.isEmpty()) {
            return null;
        }

        // 0) 遮挡号码（包含*）：先按更强的结构识别
        if (number.indexOf('*') >= 0) {
            // 0.1 遮挡手机号
            if (isPhoneMasked(number)) {
                return Classification.of("PHONE_MASKED", 0.85f)
                    .addValidation("phone_masked", true, "手机号遮挡片段（包含*）");
            }

            // 0.2 遮挡银行卡（默认仅 62 前缀；若命中“银行卡/账号”等关键词窗，则允许放宽前缀限制）
            String bankKw = findKeywordHint(fullText, span.start, span.end, KeywordGroup.BANK);
            if (isBankCardMasked(number) || (bankKw != null && isMaskedDigitsLike(number, 16, 19, 8))) {
                Classification cls = Classification.of("BANK_CARD_MASKED", 0.85f)
                    .addValidation("bank_masked", true, "银行卡遮挡片段（包含*）");
                if (bankKw != null) {
                    cls.addValidation("keyword_hint", true, "命中关键词: " + bankKw);
                    cls.keywordHint = bankKw;
                }
                return cls;
            }

            // 0.3 遮挡身份证：支持 masked / invalid_length_masked。
            Classification idMasked = classifyIdMasked(number, span, fullText);
            if (idMasked != null) {
                return idMasked;
            }

            // 其他遮挡串暂不输出（避免误判）
            return null;
        }

        // 1) 手机号（有效）
        if (isPhone(number)) {
            return Classification.of("PHONE", 0.90f)
                .addValidation("phone_format", true, "11位手机号格式");
        }

        // 1.1) 手机号（无效）：11位且以1开头但号段不匹配
        if (isPhoneInvalid(number)) {
            Classification cls = Classification.of("PHONE_INVALID", 0.80f)
                .addValidation("phone_format", false, "11位但号段不匹配");
            String kw = findKeywordHint(fullText, span.start, span.end, KeywordGroup.PHONE);
            if (kw != null) {
                cls.addValidation("keyword_hint", true, "命中关键词: " + kw);
                cls.keywordHint = kw;
            }
            return cls;
        }

        // 1.5) 14-22位包含X的号码分类（必须在标准检查之前）
        if (number.length() >= 14 && number.length() <= 22 && number.toUpperCase().contains("X")) {
            log.info("[分类] 开始分类包含X的号码: 长度={}, 值={}", number.length(),
                PiiMaskingUtil.maskNumber(number));
            Classification cls = classifyIdWithX(number, fullText, span);
            if (cls != null) {
                log.info("[分类] 分类结果: type={}, confidence={}, validations={}",
                    cls.type, cls.confidence, cls.validations);
                return cls;
            } else {
                log.info("[分类] 号码不满足身份证特征，跳过: {}", PiiMaskingUtil.maskNumber(number));
            }
        }

        // 2) 身份证有效 18
        if (number.length() == 18 && ID_18_STRUCTURE.matcher(number).matches()) {
            boolean checksumOk = validateIdCardCheckBit(number);
            Classification cls = Classification.of(checksumOk ? "ID_VALID_18" : "ID_INVALID_CHECKSUM", checksumOk ? 0.95f : 0.80f)
                .addValidation("id_structure_18", true, "18位结构匹配")
                .addValidation("id_checksum", checksumOk, checksumOk ? "校验位通过" : "校验位不通过");
            return cls;
        }

        // 3) 身份证有效 15
        if (number.length() == 15 && ID_15_STRUCTURE.matcher(number).matches()) {
            return Classification.of("ID_VALID_15", 0.85f)
                .addValidation("id_structure_15", true, "15位结构匹配");
        }

        // 3.1) 19位末位X：明显的身份证位数错误（常见录入多一位）
        if (number.length() == 19 && ID_19_ENDING_X.matcher(number).matches()) {
            return Classification.of("ID_INVALID_LENGTH", 0.85f)
                .addValidation("id_suffix_x", true, "末位为X")
                .addValidation("id_length", false, "长度=19，期望=18");
        }

        // 4) 银行卡（Luhn 强校验）
        boolean bankByLuhn = isBankCardByLuhn(number);

        // 4.1) 19位身份证“近似错误”：删除其中一位可恢复为有效18位身份证（结构+校验位通过）
        // 典型场景：身份证号中间多了一位，导致生日段无法解析，从而被 isIdLikeByAreaAndBirth 漏掉。
        IdNearMiss nearMiss = findValidId18ByRemovingOneDigit(number);
        if (nearMiss != null && number.length() != 18) {
            Classification cls = Classification.of("ID_INVALID_LENGTH", 0.90f)
                .addValidation("id_length", false, "长度=" + number.length() + "，期望=18")
                .addValidation("id_near_miss_remove_one_digit", true, "删除索引=" + nearMiss.removeIndex +
                    " 可恢复为18位有效身份证（结构匹配+校验位通过，masked=" + PiiMaskingUtil.maskNumber(nearMiss.repairedId18) + "）");
            if (bankByLuhn) {
                cls.addConflict("BANK_CARD", "Luhn通过但可恢复为18位有效身份证，按错误身份证处理", "ID_INVALID_LENGTH");
            }
            return cls;
        }

        // 5) 错误身份证（位数不对，但结构特征强）
        boolean idLike = isIdLikeByAreaAndBirth(number);
        if (idLike && number.length() != 18) {
            Classification cls = Classification.of("ID_INVALID_LENGTH", 0.85f)
                .addValidation("id_like", true, "地区码首位1-6且生日位可解析")
                .addValidation("id_length", false, "长度=" + number.length() + "，期望=18");
            if (bankByLuhn) {
                cls.addConflict("BANK_CARD", "Luhn通过但身份证结构特征更强，按错误身份证处理", "ID_INVALID_LENGTH");
            }
            return cls;
        }

        // 6) 银行卡：当不具备强身份证结构特征时才认定为银行卡，避免误吞身份证变体
        if (bankByLuhn) {
            return Classification.of("BANK_CARD", 0.90f)
                .addValidation("bank_luhn", true, "通过Luhn校验");
        }

        // 6.1) 银行卡无效：长度正确但未通过Luhn（需要更强约束避免误判）
        if (isBankCardInvalid(number)) {
            Classification cls = Classification.of("BANK_CARD_INVALID", 0.75f)
                .addValidation("bank_luhn", false, "未通过Luhn校验")
                .addValidation("bank_prefix", true, "满足银行卡前缀弱特征");
            String kw = findKeywordHint(fullText, span.start, span.end, KeywordGroup.BANK);
            if (kw != null) {
                cls.addValidation("keyword_hint", true, "命中关键词: " + kw);
                cls.keywordHint = kw;
                // 上下文命中时适当提升置信度
                cls.confidence = 0.80f;
            }
            return cls;
        }

        // 7) 其他：暂不输出
        return null;
    }

    private boolean isPhone(String number) {
        return number != null && number.length() == 11 && number.startsWith("1") && PHONE_11.matcher(number).matches();
    }

    private boolean isPhoneInvalid(String number) {
        if (number == null || number.length() != 11) {
            return false;
        }
        if (!number.startsWith("1")) {
            return false;
        }
        if (!isAllDigits(number)) {
            return false;
        }
        // 有效手机号已被 isPhone 捕获；此处仅保留“以1开头但号段不合法”的11位
        return !PHONE_11.matcher(number).matches();
    }

    private boolean isPhoneMasked(String number) {
        if (number == null || number.length() != 11) {
            return false;
        }
        if (!number.startsWith("1")) {
            return false;
        }
        if (number.indexOf('*') < 0) {
            return false;
        }
        // 仅允许数字或*，且至少包含一定数量数字，避免纯*误判
        int digit = 0;
        for (int i = 0; i < number.length(); i++) {
            char ch = number.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digit++;
            } else if (ch != '*') {
                return false;
            }
        }
        return digit >= 5;
    }

    private boolean isBankCardByLuhn(String number) {
        if (number == null) {
            return false;
        }
        if (number.length() < 16 || number.length() > 19) {
            return false;
        }
        if (!isAllDigits(number)) {
            return false;
        }
        return luhnCheck(number);
    }

    private boolean isBankCardInvalid(String number) {
        if (number == null) {
            return false;
        }
        if (number.length() < 16 || number.length() > 19) {
            return false;
        }
        if (!isAllDigits(number)) {
            return false;
        }
        // 已通过Luhn的会被认定为BANK_CARD；此处仅保留“前缀像银行卡但Luhn失败”
        if (luhnCheck(number)) {
            return false;
        }
        if (!BANK_PREFIX_LIKELY.matcher(number).matches()) {
            return false;
        }
        // 若具备强身份证结构特征，则不按银行卡无效处理（避免误吞身份证变体）
        return !isIdLikeByAreaAndBirth(number);
    }

    private boolean isBankCardMasked(String number) {
        if (number == null) {
            return false;
        }
        int len = number.length();
        if (len < 16 || len > 19) {
            return false;
        }
        if (number.indexOf('*') < 0) {
            return false;
        }
        int digit = 0;
        for (int i = 0; i < number.length(); i++) {
            char ch = number.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digit++;
            } else if (ch != '*') {
                return false;
            }
        }
        // 银行卡遮挡通常至少暴露 BIN(6) + 尾号(4) 的一部分；这里用 8 作为保守阈值
        if (digit < 8) {
            return false;
        }
        // 前缀弱特征（至少以62/4/5/3开头）
        String normalizedPrefix = number.replace("*", "");
        if (normalizedPrefix.isEmpty()) {
            return false;
        }
        return BANK_PREFIX_LIKELY.matcher(normalizedPrefix).matches();
    }

    private void applyMaskInfo(NumberEvidence.NumberCandidate n, String value) {
        if (n == null || value == null) {
            return;
        }
        int digitCount = 0;
        int maskCount = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digitCount++;
            } else if (ch == '*') {
                maskCount++;
            }
        }
        n.setDigitCount(digitCount);
        n.setMaskCount(maskCount);
        n.setMasked(maskCount > 0);
        if (maskCount > 0) {
            n.setMaskPattern(buildMaskPattern(value));
        }
    }

    private String buildMaskPattern(String value) {
        // 简化：统计前缀连续数字 + 连续* + 后缀连续数字
        int i = 0;
        int prefix = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);
            if (ch >= '0' && ch <= '9') {
                prefix++;
                i++;
            } else {
                break;
            }
        }
        int stars = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);
            if (ch == '*') {
                stars++;
                i++;
            } else {
                break;
            }
        }
        int suffix = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);
            if (ch >= '0' && ch <= '9') {
                suffix++;
            }
            i++;
        }
        return prefix + "+*" + stars + "+" + suffix;
    }

    private enum KeywordGroup {
        PHONE,
        BANK,
        ID
    }

    private String findKeywordHint(String text, int start, int end, KeywordGroup group) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int left = Math.max(0, start - 12);
        int right = Math.min(text.length(), end + 12);
        String window = text.substring(left, right);
        switch (group) {
            case PHONE:
                return firstKeyword(window, new String[]{"手机号", "手机", "电话", "联系电话", "联系方式"});
            case BANK:
                return firstKeyword(window, new String[]{"银行卡", "卡号", "银行卡号", "账号", "账户", "转账", "收款"});
            case ID:
                // 注意：全遮挡（全*）按业务口径不算“存在”，因此这里只作为“部分遮挡身份证”的类型锚定。
                return firstKeyword(window, new String[]{"身份证号", "身份证号码", "证件号", "证件号码", "身份证"});
            default:
                return null;
        }
    }

    private Classification classifyIdMasked(String number, CandidateMatch span, String fullText) {
        if (number == null || number.isEmpty()) {
            return null;
        }
        int len = number.length();
        // 仅考虑身份证可能出现的长度范围（15/18 为有效口径；其它长度作为“错误长度”子类）
        if (len < 15 || len > 20) {
            return null;
        }

        MaskInfo info = MaskInfo.parse(number);
        if (!info.valid || info.maskCount <= 0) {
            return null;
        }
        // 业务口径：全遮挡号码不计存在（当作无用字符）
        if (info.digitCount == 0) {
            return null;
        }
        // 身份证地区码首位：1-6；若首位被遮挡或不在范围内，为降低误判直接忽略。
        char first = number.charAt(0);
        if (first < '1' || first > '6') {
            return null;
        }

        String kw = findKeywordHint(fullText, span.start, span.end, KeywordGroup.ID);
        boolean idKeyword = kw != null;

        // 没有关键词时，仅在“结构证据足够强”时才认定为身份证遮挡，避免把其它遮挡数字串误判为身份证。
        boolean strongStructure = isIdMaskedStrongStructure(number, info);
        if (!idKeyword && !strongStructure) {
            return null;
        }

        String type;
        if (len == 18 || len == 15) {
            type = "ID_MASKED";
        } else {
            // 15位旧身份证视为正确，因此这里的“错误长度”排除 15
            type = "ID_INVALID_LENGTH_MASKED";
        }

        Classification cls = Classification.of(type, 0.80f)
            .addValidation("id_masked", true, "身份证号遮挡片段（包含*）")
            .addValidation("id_length", (len == 18 || len == 15), "长度=" + len + "（15/18为有效口径）");
        if (type.equals("ID_INVALID_LENGTH_MASKED")) {
            cls.addValidation("id_length_invalid", true, "不满足15/18长度要求（遮挡）");
        }
        if (idKeyword) {
            cls.addValidation("keyword_hint", true, "命中关键词: " + kw);
            cls.keywordHint = kw;
            cls.confidence = 0.85f;
        } else if (strongStructure) {
            cls.addValidation("id_like_masked", true, "具备强结构特征（地区码首位+生日段可解析）");
        }
        return cls;
    }

    private boolean isIdMaskedStrongStructure(String number, MaskInfo info) {
        if (number == null || number.isEmpty() || info == null) {
            return false;
        }
        if (number.length() < 14) {
            return false;
        }
        // 地区码首位：1-6
        char first = number.charAt(0);
        if (first < '1' || first > '6') {
            return false;
        }
        // 生日段需要可解析（位置 6-13，要求全为数字）
        if (number.length() < 14) {
            return false;
        }
        for (int i = 6; i < 14; i++) {
            char ch = number.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        String birth = number.substring(6, 14);
        try {
            int year = Integer.parseInt(birth.substring(0, 4));
            int month = Integer.parseInt(birth.substring(4, 6));
            int day = Integer.parseInt(birth.substring(6, 8));
            int currentYear = LocalDate.now().getYear();
            if (year < 1900 || year > currentYear + 1) {
                return false;
            }
            LocalDate.of(year, month, day);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isMaskedDigitsLike(String number, int minLen, int maxLen, int minDigits) {
        if (number == null) {
            return false;
        }
        int len = number.length();
        if (len < minLen || len > maxLen) {
            return false;
        }
        MaskInfo info = MaskInfo.parse(number);
        if (!info.valid) {
            return false;
        }
        if (info.maskCount <= 0) {
            return false;
        }
        // 避免全遮挡误判
        if (info.digitCount < minDigits) {
            return false;
        }
        return true;
    }

    private String firstKeyword(String window, String[] keywords) {
        if (window == null || window.isEmpty() || keywords == null) {
            return null;
        }
        for (String k : keywords) {
            if (k != null && !k.isEmpty() && window.contains(k)) {
                return k;
            }
        }
        return null;
    }

    private boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    private boolean luhnCheck(String cardNumber) {
        int sum = 0;
        boolean alternate = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = cardNumber.charAt(i) - '0';
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    /**
     * 身份证结构特征：地区码首位1-6 + 生日位 YYYYMMDD 可解析（含闰年校验）。
     * 支持X结尾的18位身份证（前17位必须为数字，最后一位可以是X或x）。
     */
    private boolean isIdLikeByAreaAndBirth(String number) {
        if (number == null || number.length() < 14) {
            return false;
        }
        char first = number.charAt(0);
        if (first < '1' || first > '6') {
            return false;
        }
        // 检查是否为有效格式：纯数字 或 18位且最后一位为X/x
        if (!isAllDigitsOrXEnding(number)) {
            return false;
        }
        String birth = number.substring(6, 14);
        if (birth.length() != 8) {
            return false;
        }
        int year;
        int month;
        int day;
        try {
            year = Integer.parseInt(birth.substring(0, 4));
            month = Integer.parseInt(birth.substring(4, 6));
            day = Integer.parseInt(birth.substring(6, 8));
        } catch (NumberFormatException e) {
            return false;
        }
        int currentYear = LocalDate.now().getYear();
        if (year < 1900 || year > currentYear + 1) {
            return false;
        }
        try {
            LocalDate.of(year, month, day);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    /**
     * 检查字符串是否为纯数字，或18位且最后一位为X/x（身份证格式）。
     */
    private boolean isAllDigitsOrXEnding(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        int len = s.length();
        // 检查前17位（或全部，如果长度<18）
        int checkLen = Math.min(len, 17);
        for (int i = 0; i < checkLen; i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        // 如果长度为18，检查最后一位可以是X/x
        if (len == 18) {
            char last = s.charAt(17);
            return (last >= '0' && last <= '9') || last == 'X' || last == 'x';
        }
        // 其他长度必须全部是数字
        if (len > 18) {
            for (int i = 17; i < len; i++) {
                char ch = s.charAt(i);
                if (ch < '0' || ch > '9') {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 分类14-22位包含X的号码。
     *
     * <p>支持识别各种位数的身份证号：
     * <ul>
     *   <li>14位：太短（标准应为15位或18位）</li>
     *   <li>15位：旧版标准（可能含X，格式错误）</li>
     *   <li>16-17位：太短</li>
     *   <li>18位：新版标准（X应在末尾）或格式错误（X不在末尾）</li>
     *   <li>19-22位：太长</li>
     * </ul>
     */
    private Classification classifyIdWithX(String number, String fullText, CandidateMatch span) {
        if (number == null) {
            return null;
        }

        int len = number.length();
        if (len < 14 || len > 22) {
            return null;
        }

        String upper = number.toUpperCase();
        int lastXIndex = upper.lastIndexOf('X');

        log.info("[classifyIdWithX] 输入: 长度={}, 值={}, X位置={}",
            len, PiiMaskingUtil.maskNumber(number), lastXIndex);

        // 统计非数字字符的数量和位置
        List<Integer> nonDigitPositions = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            char ch = number.charAt(i);
            if (ch < '0' || ch > '9') {
                nonDigitPositions.add(i);
            }
        }

        log.info("[classifyIdWithX] 非数字字符统计: 数量={}, 位置={}",
            nonDigitPositions.size(), nonDigitPositions);

        // 检查是否具备身份证特征（地区码+生日）
        boolean hasIdFeatures = hasIdCardFeaturesIgnoringX(number);
        log.info("[classifyIdWithX] 身份证特征检查: {}", hasIdFeatures);

        // 根据位数判断
        if (len == 15) {
            // 15位包含X：格式错误（15位旧版身份证应该是纯数字）
            if (hasIdFeatures) {
                log.info("[classifyIdWithX] 15位含X: type=ID_INVALID_FORMAT, maskedValue={}",
                    PiiMaskingUtil.maskNumber(number));
                return Classification.of("ID_INVALID_FORMAT", 0.75f)
                    .addValidation("id_like", true, "具备身份证特征")
                    .addValidation("id_format", false, "格式错误：15位身份证不应包含X（非数字字符：" + nonDigitPositions + "）");
            }
            log.info("[classifyIdWithX] 15位号码不具备身份证特征，跳过");
            return null;
        }

        if (len == 14 || len == 16 || len == 17) {
            // 太短
            if (hasIdFeatures) {
                Classification result = Classification.of("ID_INVALID_LENGTH", 0.80f)
                    .addValidation("id_like", true, "具备身份证特征")
                    .addValidation("id_length", false, "长度=" + len + "，太短（标准为15位或18位）")
                    .addValidation("id_format", false, "格式错误：包含" + nonDigitPositions.size() + "个非数字字符：" + nonDigitPositions);
                log.info("[classifyIdWithX] {}位号码太短: type=ID_INVALID_LENGTH, maskedValue={}",
                    len, PiiMaskingUtil.maskNumber(number));
                return result;
            }
            log.info("[classifyIdWithX] {}位号码不具备身份证特征，跳过", len);
            return null;
        }

        if (len == 18) {
            // X在末尾（第17位，正常位置）
            if (lastXIndex == 17) {
                // 前17位必须是数字
                boolean first17AreDigits = true;
                for (int i = 0; i < 17; i++) {
                    char ch = number.charAt(i);
                    if (ch < '0' || ch > '9') {
                        first17AreDigits = false;
                        break;
                    }
                }

                if (!first17AreDigits) {
                    log.info("[classifyIdWithX] 18位前17位含非数字: type=ID_INVALID_FORMAT, maskedValue={}",
                        PiiMaskingUtil.maskNumber(number));
                    return Classification.of("ID_INVALID_FORMAT", 0.75f)
                        .addValidation("id_structure_18", false, "18位但前17位包含非数字字符")
                        .addValidation("id_format", false, "格式错误：前17位包含非数字字符：" + nonDigitPositions);
                }

                // 检查是否符合18位身份证结构
                if (ID_18_STRUCTURE.matcher(number).matches()) {
                    boolean checksumOk = validateIdCardCheckBit(number);
                    String type = checksumOk ? "ID_VALID_18" : "ID_INVALID_CHECKSUM";
                    log.info("[classifyIdWithX] 18位末位X结构匹配: type={}, 校验位={}, maskedValue={}",
                        type, checksumOk, PiiMaskingUtil.maskNumber(number));
                    return Classification.of(checksumOk ? "ID_VALID_18" : "ID_INVALID_CHECKSUM", checksumOk ? 0.95f : 0.80f)
                        .addValidation("id_structure_18", true, "18位结构匹配")
                        .addValidation("id_checksum", checksumOk, checksumOk ? "校验位通过" : "校验位不通过");
                }

                log.info("[classifyIdWithX] 18位末位X但结构不匹配: type=ID_INVALID_FORMAT, maskedValue={}",
                    PiiMaskingUtil.maskNumber(number));
                return Classification.of("ID_INVALID_FORMAT", 0.75f)
                    .addValidation("id_structure_18", false, "18位末位X但结构不匹配")
                    .addValidation("id_format", false, "格式错误：不符合身份证结构（地区码/日期格式错误）");
            }

            // X不在末尾
            if (hasIdFeatures) {
                log.info("[classifyIdWithX] 18位X不在末尾: type=ID_INVALID_FORMAT, X位置={}, maskedValue={}",
                    lastXIndex, PiiMaskingUtil.maskNumber(number));
                return Classification.of("ID_INVALID_FORMAT", 0.80f)
                    .addValidation("id_like", true, "具备身份证特征")
                    .addValidation("id_format", false, "格式错误：X应在末尾但实际在位置" + lastXIndex +
                        "（非数字字符：" + nonDigitPositions + "）");
            }
            log.info("[classifyIdWithX] 18位X不在末尾且无身份证特征，跳过");
            return null;
        }

        if (len >= 19 && len <= 22) {
            // 太长
            if (hasIdFeatures) {
                log.info("[classifyIdWithX] {}位号码太长: type=ID_INVALID_LENGTH, maskedValue={}",
                    len, PiiMaskingUtil.maskNumber(number));
                return Classification.of("ID_INVALID_LENGTH", 0.80f)
                    .addValidation("id_like", true, "具备身份证特征")
                    .addValidation("id_length", false, "长度=" + len + "，太长（标准为15位或18位）")
                    .addValidation("id_format", false, "格式错误：包含" + nonDigitPositions.size() + "个非数字字符：" + nonDigitPositions);
            }
            log.info("[classifyIdWithX] {}位号码不具备身份证特征，跳过", len);
            return null;
        }

        log.info("[classifyIdWithX] 未匹配到任何分类规则，返回null");
        return null;
    }

    /**
     * 检查18位号码是否具备身份证特征（忽略X字符的影响）。
     * 只检查地区码首位和生日段的可解析性。
     */
    private boolean hasIdCardFeaturesIgnoringX(String number) {
        if (number == null || number.length() < 14) {
            return false;
        }

        // 地区码首位：1-6
        char first = number.charAt(0);
        if (first < '1' || first > '6') {
            return false;
        }

        // 生日段（位置6-13）必须全是数字且可解析
        StringBuilder birthDigits = new StringBuilder();
        for (int i = 6; i < 14 && i < number.length(); i++) {
            char ch = number.charAt(i);
            if (ch >= '0' && ch <= '9') {
                birthDigits.append(ch);
            } else {
                // 生日段包含非数字字符，无法解析
                return false;
            }
        }

        if (birthDigits.length() != 8) {
            return false;
        }

        try {
            int year = Integer.parseInt(birthDigits.substring(0, 4));
            int month = Integer.parseInt(birthDigits.substring(4, 6));
            int day = Integer.parseInt(birthDigits.substring(6, 8));
            int currentYear = LocalDate.now().getYear();
            if (year < 1900 || year > currentYear + 1) {
                return false;
            }
            LocalDate.of(year, month, day);
            return true;
        } catch (NumberFormatException | DateTimeException e) {
            return false;
        }
    }

    /**
     * 校验18位身份证号的校验位。
     */
    private boolean validateIdCardCheckBit(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return false;
        }
        // 前17位必须为数字
        for (int i = 0; i < 17; i++) {
            char ch = idCard.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idCard.charAt(i) - '0') * ID_CARD_WEIGHTS[i];
        }
        char expected = ID_CARD_CHECK_CHARS[sum % 11];
        char actual = Character.toUpperCase(idCard.charAt(17));
        return expected == actual;
    }

    private static final class IdNearMiss {
        private final int removeIndex;
        private final String repairedId18;

        private IdNearMiss(int removeIndex, String repairedId18) {
            this.removeIndex = removeIndex;
            this.repairedId18 = repairedId18;
        }
    }

    /**
     * 针对 19 位纯数字串：尝试删除其中一位，若能得到结构+校验位均通过的 18 位身份证，则视为“错误长度身份证”强证据。
     */
    private IdNearMiss findValidId18ByRemovingOneDigit(String number) {
        if (number == null) {
            return null;
        }
        if (number.length() != 19) {
            return null;
        }
        if (!isAllDigits(number)) {
            return null;
        }

        for (int i = 0; i < number.length(); i++) {
            String repaired = number.substring(0, i) + number.substring(i + 1);
            if (repaired.length() != 18) {
                continue;
            }
            if (!ID_18_STRUCTURE.matcher(repaired).matches()) {
                continue;
            }
            if (validateIdCardCheckBit(repaired)) {
                return new IdNearMiss(i, repaired);
            }
        }
        return null;
    }

    private void derive(NumberEvidence evidence) {
        int idValid18 = 0;
        int idValid15 = 0;
        int idInvalidLength = 0;
        int idInvalidChecksum = 0;
        int idInvalidFormat = 0;
        int idMasked = 0;
        int idInvalidLengthMasked = 0;
        int bankValid = 0;
        int bankInvalid = 0;
        int bankMasked = 0;
        int phoneValid = 0;
        int phoneInvalid = 0;
        int phoneMasked = 0;

        for (NumberEvidence.NumberCandidate n : evidence.getNumbers()) {
            switch (n.getType()) {
                case "ID_VALID_18":
                    idValid18++;
                    break;
                case "ID_VALID_15":
                    idValid15++;
                    break;
                case "ID_INVALID_LENGTH":
                    idInvalidLength++;
                    break;
                case "ID_INVALID_CHECKSUM":
                    idInvalidChecksum++;
                    break;
                case "ID_INVALID_FORMAT":
                    idInvalidFormat++;
                    break;
                case "ID_MASKED":
                    idMasked++;
                    break;
                case "ID_INVALID_LENGTH_MASKED":
                    idInvalidLengthMasked++;
                    break;
                case "BANK_CARD":
                    bankValid++;
                    break;
                case "BANK_CARD_INVALID":
                    bankInvalid++;
                    break;
                case "BANK_CARD_MASKED":
                    bankMasked++;
                    break;
                case "PHONE":
                    phoneValid++;
                    break;
                case "PHONE_INVALID":
                    phoneInvalid++;
                    break;
                case "PHONE_MASKED":
                    phoneMasked++;
                    break;
                default:
                    break;
            }
        }

        Map<String, Object> d = evidence.getDerived();
        d.put("id_exists", (idValid18 + idValid15 + idInvalidLength + idInvalidChecksum + idInvalidFormat + idMasked + idInvalidLengthMasked) > 0);
        d.put("id_valid_18_count", idValid18);
        d.put("id_valid_15_count", idValid15);
        d.put("id_invalid_length_count", idInvalidLength);
        d.put("id_invalid_checksum_count", idInvalidChecksum);
        d.put("id_invalid_format_count", idInvalidFormat);
        d.put("id_masked_count", idMasked);
        d.put("id_invalid_length_masked_count", idInvalidLengthMasked);

        // 向后兼容：保留旧字段（仅统计 valid）
        d.put("bank_card_count", bankValid);
        d.put("phone_count", phoneValid);

        // 新增：手机号/银行卡派生字段（含 invalid/masked）
        d.put("phone_exists", (phoneValid + phoneInvalid + phoneMasked) > 0);
        d.put("phone_valid_count", phoneValid);
        d.put("phone_invalid_count", phoneInvalid);
        d.put("phone_masked_count", phoneMasked);

        d.put("bank_exists", (bankValid + bankInvalid + bankMasked) > 0);
        d.put("bank_valid_count", bankValid);
        d.put("bank_invalid_count", bankInvalid);
        d.put("bank_masked_count", bankMasked);
    }

    private static final class CandidateMatch {
        private final String value;
        private final int start;
        private final int end;

        private CandidateMatch(String value, int start, int end) {
            this.value = value;
            this.start = start;
            this.end = end;
        }
    }

    private static final class Classification {
        private final String type;
        private float confidence;
        private String keywordHint;
        private final List<NumberEvidence.ValidationItem> validations = new ArrayList<>();
        private final List<NumberEvidence.ConflictItem> conflicts = new ArrayList<>();

        private Classification(String type, float confidence) {
            this.type = type;
            this.confidence = confidence;
        }

        private static Classification of(String type, float confidence) {
            return new Classification(type, confidence);
        }

        private Classification addValidation(String name, boolean pass, String detail) {
            validations.add(NumberEvidence.ValidationItem.of(name, pass, detail));
            return this;
        }

        private Classification addConflict(String withType, String reason, String resolvedAs) {
            conflicts.add(NumberEvidence.ConflictItem.of(withType, reason, resolvedAs));
            return this;
        }
    }

    private static final class MaskInfo {
        final boolean valid;
        final int digitCount;
        final int maskCount;

        private MaskInfo(boolean valid, int digitCount, int maskCount) {
            this.valid = valid;
            this.digitCount = digitCount;
            this.maskCount = maskCount;
        }

        static MaskInfo parse(String value) {
            if (value == null || value.isEmpty()) {
                return new MaskInfo(false, 0, 0);
            }
            int digit = 0;
            int mask = 0;
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if (ch >= '0' && ch <= '9') {
                    digit++;
                } else if (ch == '*') {
                    mask++;
                } else if ((ch == 'X' || ch == 'x') && i == value.length() - 1) {
                    // 允许身份证最后一位为 X（遮挡场景下不做校验位算法）
                } else {
                    return new MaskInfo(false, 0, 0);
                }
            }
            return new MaskInfo(true, digit, mask);
        }
    }
}
