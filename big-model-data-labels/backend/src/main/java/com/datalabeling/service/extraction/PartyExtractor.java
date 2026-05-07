package com.datalabeling.service.extraction;

import com.datalabeling.util.IdCardLengthValidator;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 涉警当事人提取器
 * <p>
 * 从文本中提取所有涉警当事人，并关联其身份信息
 * </p>
 *
 * @author Data Labeling Team
 * @since 2026-01-28
 */
@Slf4j
@Component
public class PartyExtractor {

    /**
     * 报警人关键词
     */
    private static final List<String> REPORTER_PATTERNS = Arrays.asList(
            "报警人", "报案人", "报称人", "当事人", "报警人称", "当事人称"
    );

    /**
     * 身份信息关键词
     */
    private static final List<String> ID_CARD_PATTERNS = Arrays.asList(
            "身份证号", "身份证", "身份证号码"
    );

    /**
     * 匹配姓名+身份证号的模式
     * 例如："张三（身份证号：340811199901011234）"
     */
    private static final Pattern PERSON_WITH_ID_PATTERN =
            Pattern.compile("([\\u4e00-\\u9fa5]{2,4})[（(]\\s*(" +
                    String.join("|", ID_CARD_PATTERNS) +
                    ")[：:\\s]*([\\dXx]+)");

    /**
     * 匹配“姓名 + 身份证号”非括号写法
     * 例如："张三 身份证号340811199901011234"、"张三身份证:340811..."
     */
    private static final Pattern PERSON_WITH_ID_INLINE_PATTERN =
            Pattern.compile("([\\u4e00-\\u9fa5]{2,4})[^\\u4e00-\\u9fa5]{0,6}(?:" +
                    String.join("|", ID_CARD_PATTERNS) +
                    ")[：:\\s]*([0-9Xx]{17}[0-9Xx])");

    /**
     * 匹配“报警人/报案人 + 姓名 + 身份证号”
     */
    private static final Pattern REPORTER_WITH_ID_PATTERN =
            Pattern.compile("(?:报警人|报案人|报称人|当事人)[：:\\s]*([\\u4e00-\\u9fa5]{2,4})[^\\u4e00-\\u9fa5]{0,12}(?:" +
                    String.join("|", ID_CARD_PATTERNS) +
                    ")[：:\\s]*([0-9Xx]{17}[0-9Xx])");

    /**
     * 匹配“对方当事人/对方人员 + 身份证号”（无姓名场景）
     */
    private static final Pattern GENERIC_OTHER_WITH_ID_PATTERN =
            Pattern.compile("(?:对方当事人|对方人员)[^0-9Xx]{0,12}(?:" +
                    String.join("|", ID_CARD_PATTERNS) +
                    ")[：:\\s]*([0-9Xx]{17}[0-9Xx])");

    /**
     * 提取所有涉警当事人
     *
     * @param text    原始文本
     * @param rowData 行数据（可选，用于关联身份证号）
     * @return 提取的当事人列表
     */
    public List<Party> extractParties(String text, java.util.Map<String, Object> rowData) {
        List<Party> parties = new ArrayList<>();

        // 1. 提取报警人
        parties.addAll(extractReporter(text));

        // 2. 提取对方当事人
        parties.addAll(extractOtherParties(text));

        // 3. 提取纠纷相关当事人
        parties.addAll(extractDisputeParties(text));

        // 4. ✅ 提取隐含当事人（v3新增）
        parties.addAll(extractImpliedParties(text));

        // 4.1 先基于“姓名-身份证”强绑定模式做关联，避免后续“按顺序分配”导致错配
        Map<String, String> nameToId = extractNameToId(text);
        for (Party p : parties) {
            if (p == null || p.isGeneric()) {
                continue;
            }
            String id = nameToId.get(p.getName());
            if (id != null && IdCardLengthValidator.quickValidate(id)) {
                p.setIdCard(id);
                p.setIdCardValid(true);
            }
        }

        // 5. 提取所有身份证号并关联到当事人
        List<IdCardLengthValidator.ExtractedItem> validIdCards =
                IdCardLengthValidator.extractAndValidate(text, false).getItems();

        // 关联身份证号到当事人
        associateIdCardsToParties(parties, validIdCards);

        // 6. 去除重复当事人（同一姓名多次出现）
        parties = deduplicateParties(parties);

        log.debug("提取到{}个涉警当事人，有效身份证号{}个",
                parties.size(), validIdCards.size());

        return parties;
    }

    /**
     * 提取报警人
     */
    private List<Party> extractReporter(String text) {
        List<Party> parties = new ArrayList<>();

        for (String pattern : REPORTER_PATTERNS) {
            // 匹配模式："报警人：张三" 或 "报警人张三"
            Pattern p = Pattern.compile(pattern + "[：:\\s]*([\\u4e00-\\u9fa5]{2,4})");
            Matcher m = p.matcher(text);

            while (m.find()) {
                String name = m.group(1);
                if (!containsParty(parties, name)) {
                    parties.add(Party.builder()
                            .name(name)
                            .type(PartyType.REPORTER)
                            .build());
                }
            }
        }

        return parties;
    }

    /**
     * 提取对方当事人
     */
    private List<Party> extractOtherParties(String text) {
        List<Party> parties = new ArrayList<>();

        // 1. 提取具体人名（在纠纷描述中出现）
        // 模式：姓名 +（身份证号/手机号/年龄）
        Matcher personMatcher = PERSON_WITH_ID_PATTERN.matcher(text);
        while (personMatcher.find()) {
            String name = personMatcher.group(1);
            String idCard = personMatcher.group(3);

            if (!containsParty(parties, name)) {
                Party party = Party.builder()
                        .name(name)
                        .type(PartyType.OTHER)
                        .build();

                // 检查身份证号是否有效
                if (IdCardLengthValidator.quickValidate(idCard)) {
                    party.setIdCard(idCard);
                    party.setIdCardValid(true);
                }

                parties.add(party);
            }
        }

        // 2. 提取"对方"泛指
        if (text.contains("对方当事人") || text.contains("对方人员")) {
            Party party = Party.builder()
                    .name("对方当事人")
                    .type(PartyType.OTHER)
                    .isGeneric(true)  // 泛指
                    .build();

            // 尝试从“对方当事人/对方人员”附近提取身份证号，避免“有证件但无姓名”误判为缺失
            Matcher gm = GENERIC_OTHER_WITH_ID_PATTERN.matcher(text);
            if (gm.find()) {
                String id = gm.group(1);
                if (IdCardLengthValidator.quickValidate(id)) {
                    party.setIdCard(id);
                    party.setIdCardValid(true);
                }
            }

            if (!containsParty(parties, "对方当事人")) {
                parties.add(party);
            }
        }

        return parties;
    }

    /**
     * 提取纠纷相关当事人
     */
    private List<Party> extractDisputeParties(String text) {
        List<Party> parties = new ArrayList<>();

        // 纠纷关键词列表
        List<String> disputeKeywords = Arrays.asList(
                "发生纠纷", "发生争执", "发生冲突",
                "发生交通事故", "发生车祸", "发生刮蹭",
                "发生盗窃", "发生抢劫", "发生诈骗");

        // 检查是否涉及纠纷
        boolean hasDispute = disputeKeywords.stream()
                .anyMatch(text::contains);

        if (!hasDispute) {
            return parties;
        }

        // 在纠纷上下文中提取姓名
        // 模式："与XXX发生纠纷"、"被XXX打"、"被XXX撞" 等
        // 注意：必须用捕获组取人名，不能用 substring（会因“发生/打/撞”长度不同导致截断误判）
        Pattern disputePattern = Pattern.compile("(?:与|被)([\\u4e00-\\u9fa5]{2,4})(?:发生|打|撞|骗|偷)");
        Matcher m = disputePattern.matcher(text);

        while (m.find()) {
            String name = m.group(1);
            if (name == null) {
                continue;
            }
            // 避免把“物业/商家/中介/网友”等泛化实体当成“姓名”导致强制缺失
            if (isNonPersonToken(name)) {
                continue;
            }
            if (name.length() >= 2 && name.length() <= 4) {
                Party party = Party.builder()
                    .name(name)
                    .type(PartyType.OTHER)
                    .build();

                if (!containsParty(parties, name)) {
                    parties.add(party);
                }
            }
        }

        return parties;
    }

    /**
     * 纠纷语境中常见的“非姓名”泛化实体/角色词。
     * 这些词本身不足以指代“可校验身份信息的个人”，直接纳入会引入大量误判。
     */
    private boolean isNonPersonToken(String token) {
        if (token == null) {
            return true;
        }
        String t = token.trim();
        if (t.isEmpty()) {
            return true;
        }
        // 常见实体/角色词（可按数据回归继续扩展）
        return Arrays.asList(
            "物业", "商家", "店家", "店主", "商户", "商铺",
            "中介", "网友",
            "房东", "房客", "租客", "租户",
            "邻居", "楼上", "楼下"
        ).contains(t);
    }

    /**
     * 提取隐含当事人（v3新增）
     * <p>
     * 检测从上下文推断的涉警当事人：
     * - 小孩/儿童（被咬伤、被打等）
     * - 中介（中介纠纷、中介介绍工作等）
     * - 收费方/店家/物业/商家
     * - 网友（网友见面、微信网友等）
     * - 犯罪嫌疑人/侵权人
     * </p>
     */
    private List<Party> extractImpliedParties(String text) {
        List<Party> parties = new ArrayList<>();

        // 1. 小孩/儿童检测（仅在明显涉及伤害/冲突时才推断，避免把无关提及误判为当事人缺失）
        boolean hasChildToken = text.contains("小孩") || text.contains("儿童") || text.contains("孩子");
        boolean hasHarmToken = text.contains("被") || text.contains("咬") || text.contains("打") || text.contains("伤") || text.contains("推搡");
        if (hasChildToken && hasHarmToken) {
            // 检查是否有孩子的身份证号
            Pattern childIdPattern = Pattern.compile(
                    "(?:小孩|儿童|孩子)[^（\\(]*[（(][^）)]*?(?:身份证|身份码)[^：:]*[：:][^0-9]*([0-9Xx]{17}[0-9Xx])");
            Matcher m = childIdPattern.matcher(text);
            if (!m.find()) {
                // 没有找到孩子的身份证号，添加缺失
                parties.add(Party.builder()
                        .name("小孩/儿童")
                        .type(PartyType.VICTIM)
                        .isGeneric(true)
                        .impliedType("小孩")
                        .reason("涉及小孩/儿童但未提取到身份证号")
                        .build());
            }
        }

        // 2~4. 中介/商家物业/网友等“泛化隐含当事人”推断在实践中易引入误判
        // 《检测规则.docx》未要求对这些泛化主体进行身份信息强制校验（除非明确为具体当事人且可识别）。
        // 因此这里不再自动追加，避免把“物业/商家/网友”等词当作必然缺失主体。

        // 5. 犯罪嫌疑人/侵权人（未明确身份）
        if (text.contains("犯罪嫌疑人") || text.contains("嫌疑人")) {
            // 检查是否提供了身份信息
            Pattern suspectPattern = Pattern.compile(
                    "(?:犯罪嫌疑人|嫌疑人)[^（\\(]*[（(][^）)]*?(?:身份证|身份码)[^：:]*[：:][^0-9]*([0-9Xx]{17}[0-9Xx])");
            if (!suspectPattern.matcher(text).find()) {
                parties.add(Party.builder()
                        .name("犯罪嫌疑人/侵权人")
                        .type(PartyType.SUSPECT)
                        .isGeneric(true)
                        .impliedType("犯罪嫌疑人")
                        .reason("提及犯罪嫌疑人/侵权人但未提取到身份信息")
                        .build());
            }
        }

        // 6. 对方/对方当事人/纠纷对方（泛指）
        if ((text.contains("对方") || text.contains("对方当事人") || text.contains("纠纷对方"))
                && !hasIdCardForKeyword(text, "对方")) {
            // 检查是否有明确的"对方：XXX"格式
            if (!text.matches(".*对方[：:\\s]+[\\u4e00-\\u9fa5]{2,4}.*")) {
                parties.add(Party.builder()
                        .name("对方/纠纷对方")
                        .type(PartyType.OTHER)
                        .isGeneric(true)
                        .impliedType("对方")
                        .reason("提及对方但未提取到身份信息")
                        .build());
            }
        }

        return parties;
    }

    /**
     * 检查关键词后是否有身份证号
     */
    private boolean hasIdCardForKeyword(String text, String keyword) {
        // 查找关键词位置后的50个字符内是否有身份证号
        int index = text.indexOf(keyword);
        if (index == -1) {
            return false;
        }

        int end = Math.min(text.length(), index + keyword.length() + 50);
        String afterKeyword = text.substring(index, end);

        // 检查是否有18位身份证号
        Pattern idPattern = Pattern.compile("[0-9Xx]{17}[0-9Xx]");
        return idPattern.matcher(afterKeyword).find();
    }

    /**
     * 将身份证号关联到当事人
     */
    private void associateIdCardsToParties(List<Party> parties,
                                           List<IdCardLengthValidator.ExtractedItem> idCards) {
        if (parties == null || parties.isEmpty() || idCards == null || idCards.isEmpty()) {
            return;
        }

        // 1) 先收集“有效身份证号集合”（保持出现顺序，去重）
        Set<String> allValid = new LinkedHashSet<>();
        for (IdCardLengthValidator.ExtractedItem it : idCards) {
            if (it != null && it.isValid() && it.getValue() != null) {
                allValid.add(it.getValue());
            }
        }

        // 2) 剔除已被“姓名-身份证强绑定”或其他逻辑赋值的身份证号
        Set<String> assigned = new LinkedHashSet<>();
        for (Party p : parties) {
            if (p != null && p.isIdCardValid() && p.getIdCard() != null) {
                assigned.add(p.getIdCard());
            }
        }
        List<String> remaining = allValid.stream()
                .filter(v -> !assigned.contains(v))
                .collect(Collectors.toList());

        // 3) 将剩余身份证号优先分配给“非泛指且缺身份证”的当事人，再分配给泛指当事人
        int idx = 0;
        for (Party p : parties) {
            if (p == null || p.isGeneric() || p.isIdCardValid()) {
                continue;
            }
            if (idx >= remaining.size()) {
                break;
            }
            String id = remaining.get(idx++);
            p.setIdCard(id);
            p.setIdCardValid(true);
        }
        for (Party p : parties) {
            if (p == null || !p.isGeneric() || p.isIdCardValid()) {
                continue;
            }
            if (idx >= remaining.size()) {
                break;
            }
            String id = remaining.get(idx++);
            p.setIdCard(id);
            p.setIdCardValid(true);
        }

        int unassigned = remaining.size() - idx;
        if (unassigned > 0) {
            log.debug("发现{}个未分配到当事人的身份证号", unassigned);
        }
    }

    /**
     * 去除重复当事人（同一姓名多次出现）
     */
    private List<Party> deduplicateParties(List<Party> parties) {
        // 按姓名去重，保留第一个出现的
        return parties.stream()
                .collect(Collectors.toMap(
                        Party::getName,
                        p -> p,
                        (existing, replacement) -> {
                            // 合并：尽量保留“有有效身份证号”的版本，避免因抽取顺序导致丢失证据
                            if (existing == null) {
                                return replacement;
                            }
                            if (replacement == null) {
                                return existing;
                            }
                            if (!existing.isIdCardValid() && replacement.isIdCardValid()) {
                                return replacement;
                            }
                            if (existing.isIdCardValid() && !replacement.isIdCardValid()) {
                                return existing;
                            }
                            // 二者都没证件或都有证件：保留 existing
                            return existing;
                        }
                ))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    private Map<String, String> extractNameToId(String text) {
        Map<String, String> map = new HashMap<>();
        if (text == null || text.isEmpty()) {
            return map;
        }

        // 1) 报警人强绑定
        Matcher rm = REPORTER_WITH_ID_PATTERN.matcher(text);
        while (rm.find()) {
            String name = rm.group(1);
            String id = rm.group(2);
            if (name != null && id != null && IdCardLengthValidator.quickValidate(id)) {
                map.putIfAbsent(name, id);
            }
        }

        // 2) 括号写法强绑定
        Matcher pm = PERSON_WITH_ID_PATTERN.matcher(text);
        while (pm.find()) {
            String name = pm.group(1);
            String id = pm.group(3);
            if (name != null && id != null && IdCardLengthValidator.quickValidate(id)) {
                map.putIfAbsent(name, id);
            }
        }

        // 3) 非括号写法强绑定
        Matcher im = PERSON_WITH_ID_INLINE_PATTERN.matcher(text);
        while (im.find()) {
            String name = im.group(1);
            String id = im.group(2);
            if (name != null && id != null && IdCardLengthValidator.quickValidate(id)) {
                map.putIfAbsent(name, id);
            }
        }

        return map;
    }

    /**
     * 检查是否已存在同名当事人
     */
    private boolean containsParty(List<Party> parties, String name) {
        return parties.stream().anyMatch(p -> p.getName().equals(name));
    }

    /**
     * 检查当事人信息是否完整
     */
    public CompletenessResult checkCompleteness(List<Party> parties) {
        if (parties.isEmpty()) {
            return new CompletenessResult(true, 0, 0, "未能识别到涉警当事人");
        }

        int totalParties = parties.size();
        int partiesWithId = 0;
        int partiesWithoutId = 0;
        List<String> withoutIdNames = new ArrayList<>();

        for (Party party : parties) {
            if (party.isGeneric()) {
                // 泛指当事人：若已关联到有效身份证号，则视为信息完整；否则计入缺失
                if (party.isIdCardValid()) {
                    partiesWithId++;
                } else {
                    partiesWithoutId++;
                    addNameForReport(withoutIdNames, party.getName());
                }
                continue;
            }

            if (party.isIdCardValid()) {
                partiesWithId++;
            } else {
                partiesWithoutId++;
                addNameForReport(withoutIdNames, party.getName());
            }
        }

        boolean isComplete = partiesWithoutId == 0;
        String message;

        if (isComplete) {
            message = String.format("涉警当事人共%d人，身份信息完整", totalParties);
        } else {
            String sampleNames;
            if (withoutIdNames.isEmpty()) {
                sampleNames = "（无法提取姓名）";
            } else {
                sampleNames = String.join("、", withoutIdNames.stream().limit(5).collect(Collectors.toList()));
            }
            message = String.format("涉警当事人共%d人，仅%d人提供有效身份信息，缺失%d人：%s",
                    totalParties, partiesWithId, partiesWithoutId,
                    sampleNames);
        }

        return new CompletenessResult(isComplete, totalParties, partiesWithoutId, message);
    }

    /**
     * 将姓名加入“缺失名单展示”前做降噪处理：
     * - 仅保留更像“姓名/人称”的片段，避免把“表示知晓/向报警人...”等叙述性短语当作姓名展示，造成误导。
     *
     * 注意：该过滤仅影响“展示内容”，不改变 partiesWithoutId 的计数口径（仍以真实缺失为准）。
     */
    private void addNameForReport(List<String> out, String rawName) {
        String cleaned = sanitizeNameForReport(rawName);
        if (cleaned == null || cleaned.isEmpty()) {
            return;
        }
        out.add(cleaned);
    }

    private String sanitizeNameForReport(String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = rawName.trim();
        if (name.isEmpty()) {
            return null;
        }
        // 常见噪声：把叙述性短语/提示语误当成当事人“姓名”
        String[] noiseTokens = new String[] {
            "表示", "告知", "要求", "无法", "不在现场", "不要", "不要影响", "对民警", "向报警人", "对方表示",
            "未发现", "已知", "知晓", "可以通过", "通过司法", "排除案件", "建议", "已协商"
        };
        for (String t : noiseTokens) {
            if (name.contains(t)) {
                return null;
            }
        }
        // 过滤明显非姓名的连接符/结构片段
        if (name.contains("/") || name.contains("\\") || name.contains("：") || name.contains(":") || name.contains("，") || name.contains(",")) {
            return null;
        }
        // 典型姓名：2-4 个中文字符；或包含“某某/某”这类匿名人称
        if (name.matches("^[\\u4e00-\\u9fa5]{2,4}$")) {
            return name;
        }
        if (name.contains("某") && name.length() <= 6) {
            return name;
        }
        // 其余情况不展示，避免误导
        return null;
    }

    /**
     * 当事人
     */
    @Data
    @Builder
    public static class Party {
        private String name;           // 姓名
        private PartyType type;        // 类型
        private boolean isGeneric;     // 是否泛指（如"对方当事人"）
        private String idCard;         // 身份证号
        private boolean idCardValid;   // 身份证是否有效
        private String impliedType;    // ✅ 新增：隐含类型（小孩、中介、网友等）
        private String reason;         // ✅ 新增：信息缺失原因
    }

    /**
     * 当事人类型
     */
    public enum PartyType {
        REPORTER,    // 报警人
        OTHER,       // 对方当事人
        VICTIM,      // ✅ 新增：受害人
        SUSPECT      // ✅ 新增：嫌疑人/侵权人
    }

    /**
     * 完整性检查结果
     */
    @Data
    @lombok.AllArgsConstructor
    public static class CompletenessResult {
        private boolean complete;      // 是否完整
        private int totalParties;      // 总人数
        private int missingCount;      // 缺失人数
        private String message;        // 消息
    }
}
