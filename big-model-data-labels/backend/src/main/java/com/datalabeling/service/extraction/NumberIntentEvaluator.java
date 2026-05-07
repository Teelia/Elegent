package com.datalabeling.service.extraction;

import com.datalabeling.dto.NumberIntentConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 号码类标签意图执行器（规则优先）。
 *
 * <p>输入：原始文本 + 规则证据（可由本类内部生成） + number_intent
 * <p>输出：exists/extract/invalid/masked/invalid_length_masked 的结论与可审计摘要（脱敏策略由 policy 控制）
 */
@Slf4j
public class NumberIntentEvaluator {

    private final NumberEvidenceExtractor evidenceExtractor = new NumberEvidenceExtractor();

    @Data
    public static class EvaluationResult {
        private boolean canHandle;
        private boolean hit;
        private boolean needsReview;
        private int confidence; // 0-100
        private String summary; // 用于 label_results.value
        private String reasoning; // 用于 label_results.reasoning
        private Map<String, Object> extractedData; // 用于 label_results.extracted_data_json
    }

    public EvaluationResult evaluate(String text, NumberIntentConfig intent) {
        log.info("[NumberIntentEvaluator.evaluate] 开始执行: text长度={}, intent={}",
            text != null ? text.length() : 0, intent);

        EvaluationResult out = new EvaluationResult();
        out.setCanHandle(false);
        out.setHit(false);
        out.setNeedsReview(false);
        out.setConfidence(0);
        out.setSummary("");
        out.setReasoning("");

        if (intent == null) {
            log.info("[NumberIntentEvaluator.evaluate] intent为null，返回默认结果");
            return out;
        }

        String entity = normalize(intent.getEntity());
        String task = normalize(intent.getTask());
        log.info("[NumberIntentEvaluator.evaluate] entity={}, task={}", entity, task);

        if (entity.isEmpty() || task.isEmpty()) {
            log.info("[NumberIntentEvaluator.evaluate] entity或task为空，返回默认结果");
            return out;
        }

        out.setCanHandle(true);

        NumberEvidence evidence = evidenceExtractor.extract(text != null ? text : "");
        log.info("[NumberIntentEvaluator.evaluate] 证据提取完成: 总候选数={}", evidence.getNumbers() != null ? evidence.getNumbers().size() : 0);

        List<NumberEvidence.NumberCandidate> candidates = filterByEntity(evidence.getNumbers(), entity);
        log.info("[NumberIntentEvaluator.evaluate] 按entity过滤后: entity={}, 候选数={}", entity, candidates.size());

        // 选择输出集合
        List<String> include = intent.getInclude() != null ? intent.getInclude() : new ArrayList<>();
        Set<String> includeSet = new HashSet<>();
        for (String s : include) {
            String v = normalize(s);
            if (!v.isEmpty()) {
                includeSet.add(v);
            }
        }

        boolean defaultMaskedOutput = intent.getPolicy() == null || intent.getPolicy().getDefaultMaskedOutput() == null
            || intent.getPolicy().getDefaultMaskedOutput();
        boolean requireKeywordForInvalidBank = intent.getPolicy() == null || intent.getPolicy().getRequireKeywordForInvalidBank() == null
            || intent.getPolicy().getRequireKeywordForInvalidBank();

        // 身份证 invalid 口径增强（默认保持历史行为：invalid 仅包含长度错误）
        boolean idChecksumInvalidIsInvalid = intent.getPolicy() != null
            && Boolean.TRUE.equals(intent.getPolicy().getIdChecksumInvalidIsInvalid());
        boolean id18XIsInvalid = intent.getPolicy() != null
            && Boolean.TRUE.equals(intent.getPolicy().getId18XIsInvalid());
        boolean id15IsValid = intent.getPolicy() == null
            || intent.getPolicy().getId15IsValid() == null
            || intent.getPolicy().getId15IsValid();

        int maxItems = 50;
        String joiner = "，";
        if (intent.getOutput() != null) {
            if (intent.getOutput().getMaxItems() != null && intent.getOutput().getMaxItems() > 0) {
                maxItems = intent.getOutput().getMaxItems();
            }
            if (intent.getOutput().getJoiner() != null && !intent.getOutput().getJoiner().trim().isEmpty()) {
                joiner = intent.getOutput().getJoiner();
            }
        }

        // counts 需与 selection 的口径保持一致，避免"统计=0但实际输出=有"的审计困惑
        Counts counts = countByCategory(candidates, entity, idChecksumInvalidIsInvalid, id18XIsInvalid, id15IsValid);
        log.info("[NumberIntentEvaluator.evaluate] 分类统计: {}", counts.format());

        Selection selection = selectCandidates(task, includeSet, candidates, entity,
            requireKeywordForInvalidBank, idChecksumInvalidIsInvalid, id18XIsInvalid, id15IsValid);
        log.info("[NumberIntentEvaluator.evaluate] 候选选择完成: 选中数={}, 需复核={}, 排除数={}",
            selection.selected.size(), selection.needsReview, selection.excludedDueToPolicy.size());

        out.setNeedsReview(selection.needsReview);
        out.setHit(!selection.selected.isEmpty());

        // 组装输出（默认脱敏）
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> flat = new ArrayList<>();
        for (NumberEvidence.NumberCandidate c : selection.selected) {
            if (items.size() >= maxItems) {
                break;
            }
            String display = displayValue(c, defaultMaskedOutput);
            flat.add(display);

            Map<String, Object> item = new HashMap<>();
            item.put("id", c.getId());
            item.put("type", c.getType());
            item.put("value", display);
            item.put("masked", c.getMasked());
            item.put("maskPattern", c.getMaskPattern());
            item.put("keywordHint", c.getKeywordHint());
            item.put("confidenceRule", c.getConfidenceRule());
            items.add(item);
        }

        Map<String, Object> extractedData = new HashMap<>();
        extractedData.put("entity", entity);
        extractedData.put("task", task);
        extractedData.put("counts", counts.toMap());
        extractedData.put("items", items);
        extractedData.put("needs_review", selection.needsReview);
        out.setExtractedData(extractedData);

        String summary;
        if ("exists".equals(task)) {
            summary = out.isHit() ? "是" : "否";
        } else {
            summary = flat.isEmpty() ? "无" : String.join(joiner, flat);
        }
        out.setSummary(summary);

        int confidence = out.isHit() ? 95 : 0;
        if (out.isHit() && out.isNeedsReview()) {
            confidence = 60;
        }
        out.setConfidence(confidence);

        out.setReasoning(buildReasoning(entity, task, counts, flat, selection, joiner));

        log.info("[NumberIntentEvaluator.evaluate] 执行完成: hit={}, needsReview={}, confidence={}, summary={}",
            out.isHit(), out.isNeedsReview(), out.getConfidence(), out.getSummary());

        return out;
    }

    private String buildReasoning(String entity, String task, Counts counts, List<String> flat, Selection selection, String joiner) {
        StringBuilder sb = new StringBuilder();
        sb.append("号码意图规则执行（number_intent）: entity=").append(entity).append(", task=").append(task).append("。\n");
        sb.append("证据统计: ").append(counts.format()).append("。\n");
        if ("exists".equals(task)) {
            sb.append("存在性结论: ").append(!selection.selected.isEmpty() ? "是" : "否").append("。");
        } else {
            if (flat.isEmpty()) {
                sb.append("结论: 无（未选中符合意图的候选）。");
            } else {
                sb.append("结论候选: ").append(String.join(joiner, flat)).append("。");
            }
        }
        if (selection.needsReview) {
            sb.append("\n备注: 存在弱证据候选（例如无效银行卡缺少关键词窗命中），已标记 needs_review=true。");
        }
        if (!selection.excludedDueToPolicy.isEmpty()) {
            sb.append("\n已按策略排除的候选: ").append(String.join(joiner, selection.excludedDueToPolicy)).append("。");
        }
        return sb.toString();
    }

    private static String normalize(String v) {
        if (v == null) {
            return "";
        }
        return v.trim().toLowerCase(Locale.ROOT);
    }

    private static String displayValue(NumberEvidence.NumberCandidate c, boolean defaultMaskedOutput) {
        if (c == null) {
            return "";
        }
        // 遮挡片段：优先使用原值（包含*），更接近“存在性证据”
        if (Boolean.TRUE.equals(c.getMasked()) || (c.getValue() != null && c.getValue().indexOf('*') >= 0)) {
            return c.getValue() != null ? c.getValue() : "";
        }
        if (!defaultMaskedOutput) {
            return c.getValue() != null ? c.getValue() : "";
        }
        if (c.getMaskedValue() != null && !c.getMaskedValue().isEmpty()) {
            return c.getMaskedValue();
        }
        return c.getValue() != null ? c.getValue() : "";
    }

    private static List<NumberEvidence.NumberCandidate> filterByEntity(List<NumberEvidence.NumberCandidate> in, String entity) {
        List<NumberEvidence.NumberCandidate> out = new ArrayList<>();
        if (in == null || in.isEmpty()) {
            return out;
        }
        for (NumberEvidence.NumberCandidate c : in) {
            if (c == null || c.getType() == null) {
                continue;
            }
            String type = c.getType();
            if ("phone".equals(entity) && type.startsWith("PHONE")) {
                out.add(c);
            } else if ("bank_card".equals(entity) && type.startsWith("BANK_CARD")) {
                out.add(c);
            } else if ("id_card".equals(entity) && type.startsWith("ID_")) {
                out.add(c);
            }
        }
        return out;
    }

    private static final class Counts {
        int valid;
        int invalid;
        int masked;

        Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("valid", valid);
            m.put("invalid", invalid);
            m.put("masked", masked);
            m.put("exists", (valid + invalid + masked) > 0);
            return m;
        }

        String format() {
            return "valid=" + valid + ", invalid=" + invalid + ", masked=" + masked;
        }
    }

    private static Counts countByCategory(List<NumberEvidence.NumberCandidate> candidates, String entity,
                                          boolean idChecksumInvalidIsInvalid,
                                          boolean id18XIsInvalid,
                                          boolean id15IsValid) {
        Counts c = new Counts();
        if (candidates == null) {
            return c;
        }
        for (NumberEvidence.NumberCandidate n : candidates) {
            if (n == null || n.getType() == null) {
                continue;
            }
            String type = n.getType();
            if ("phone".equals(entity)) {
                if ("PHONE".equals(type)) {
                    c.valid++;
                } else if ("PHONE_INVALID".equals(type)) {
                    c.invalid++;
                } else if ("PHONE_MASKED".equals(type)) {
                    c.masked++;
                }
            } else if ("bank_card".equals(entity)) {
                if ("BANK_CARD".equals(type)) {
                    c.valid++;
                } else if ("BANK_CARD_INVALID".equals(type)) {
                    c.invalid++;
                } else if ("BANK_CARD_MASKED".equals(type)) {
                    c.masked++;
                }
            } else if ("id_card".equals(entity)) {
                // 注意：ID_INVALID_LENGTH_MASKED 既是遮挡又是无效长度；业务口径把它计入 invalid。
                if (isInvalidType(entity, type, n, idChecksumInvalidIsInvalid, id18XIsInvalid)) {
                    c.invalid++;
                } else if (isMaskedType(entity, type)) {
                    c.masked++;
                } else if (isValidType(entity, type, n, idChecksumInvalidIsInvalid, id15IsValid, id18XIsInvalid)) {
                    c.valid++;
                }
            }
        }
        return c;
    }

    private static final class Selection {
        final List<NumberEvidence.NumberCandidate> selected = new ArrayList<>();
        final List<String> excludedDueToPolicy = new ArrayList<>();
        boolean needsReview;
    }

    private static Selection selectCandidates(String task, Set<String> includeSet,
                                              List<NumberEvidence.NumberCandidate> candidates,
                                              String entity,
                                              boolean requireKeywordForInvalidBank,
                                              boolean idChecksumInvalidIsInvalid,
                                              boolean id18XIsInvalid,
                                              boolean id15IsValid) {
        Selection s = new Selection();
        if (candidates == null || candidates.isEmpty()) {
            return s;
        }

        boolean includeValid = includeSet.contains("valid");
        boolean includeInvalid = includeSet.contains("invalid");
        boolean includeMasked = includeSet.contains("masked");
        boolean onlyIdInvalidLengthMasked = "id_card".equals(entity) && "invalid_length_masked".equals(task);

        if ("extract".equals(task)) {
            // 默认：全包含（更贴近“我都想要”的诉求）
            if (includeSet.isEmpty()) {
                includeValid = true;
                includeInvalid = true;
                includeMasked = true;
            }
        } else if ("exists".equals(task)) {
            includeValid = true;
            includeInvalid = true;
            includeMasked = true;
        } else if ("invalid".equals(task)) {
            includeValid = false;
            includeInvalid = true;
            includeMasked = false;
        } else if ("masked".equals(task)) {
            includeValid = false;
            includeInvalid = false;
            includeMasked = true;
        } else if (onlyIdInvalidLengthMasked) {
            includeValid = false;
            includeInvalid = false;
            includeMasked = true;
        } else {
            // 未知 task：不处理
            return s;
        }

        for (NumberEvidence.NumberCandidate c : candidates) {
            if (c == null || c.getType() == null) {
                continue;
            }
            String type = c.getType();

            if (onlyIdInvalidLengthMasked) {
                if ("ID_INVALID_LENGTH_MASKED".equals(type)) {
                    s.selected.add(c);
                }
                continue;
            }

            boolean isValid = isValidType(entity, type, c, idChecksumInvalidIsInvalid, id15IsValid, id18XIsInvalid);
            boolean isInvalid = isInvalidType(entity, type, c, idChecksumInvalidIsInvalid, id18XIsInvalid);
            boolean isMasked = isMaskedType(entity, type);

            if (isValid && includeValid) {
                s.selected.add(c);
                continue;
            }
            if (isMasked && includeMasked) {
                s.selected.add(c);
                continue;
            }
            if (isInvalid && includeInvalid) {
                // 无效银行卡：弱信号时可要求关键词窗命中以降低误判
                if ("bank_card".equals(entity) && "BANK_CARD_INVALID".equals(type) && requireKeywordForInvalidBank) {
                    if (c.getKeywordHint() == null || c.getKeywordHint().trim().isEmpty()) {
                        // exists 任务：允许以“弱证据”计入存在性，但必须标记 needs_review
                        // 其他任务：为降低误判，默认不输出该候选
                        s.needsReview = true;
                        if ("exists".equals(task)) {
                            s.selected.add(c);
                        } else {
                            s.excludedDueToPolicy.add(displayValue(c, true));
                        }
                        continue;
                    }
                }
                s.selected.add(c);
            }
        }

        return s;
    }

    private static boolean isValidType(String entity, String type, NumberEvidence.NumberCandidate candidate,
                                       boolean idChecksumInvalidIsInvalid,
                                       boolean id15IsValid,
                                       boolean id18XIsInvalid) {
        if ("phone".equals(entity)) {
            return "PHONE".equals(type);
        }
        if ("bank_card".equals(entity)) {
            return "BANK_CARD".equals(type);
        }
        if ("id_card".equals(entity)) {
            if ("ID_VALID_18".equals(type)) {
                // “仅允许数字”的业务口径：末位 X 视为无效，则不计入 valid。
                if (id18XIsInvalid && candidate != null && candidate.getValue() != null) {
                    String v = candidate.getValue();
                    if (!v.isEmpty()) {
                        char last = v.charAt(v.length() - 1);
                        if (last == 'X' || last == 'x') {
                            return false;
                        }
                    }
                }
                return true;
            }
            if ("ID_INVALID_CHECKSUM".equals(type)) {
                // 默认业务口径：校验位不通过仍视为有效格式（计入 valid，且不计入 invalid）
                return !idChecksumInvalidIsInvalid;
            }
            if ("ID_VALID_15".equals(type)) {
                // 可配置：15位老身份证是否视为有效
                return id15IsValid;
            }
            return false;
        }
        return false;
    }

    private static boolean isInvalidType(String entity, String type) {
        if ("phone".equals(entity)) {
            return "PHONE_INVALID".equals(type);
        }
        if ("bank_card".equals(entity)) {
            return "BANK_CARD_INVALID".equals(type);
        }
        if ("id_card".equals(entity)) {
            // 业务口径：18位校验位不通过不视为“错误身份证号”，仅把“长度错误”计为 invalid
            return "ID_INVALID_LENGTH".equals(type);
        }
        return false;
    }

    private static boolean isInvalidType(String entity, String type, NumberEvidence.NumberCandidate candidate,
                                         boolean idChecksumInvalidIsInvalid,
                                         boolean id18XIsInvalid) {
        if (!"id_card".equals(entity)) {
            return isInvalidType(entity, type);
        }

        if ("ID_INVALID_LENGTH_MASKED".equals(type)) {
            return true;
        }
        if ("ID_INVALID_LENGTH".equals(type)) {
            return true;
        }
        if (idChecksumInvalidIsInvalid && "ID_INVALID_CHECKSUM".equals(type)) {
            return true;
        }

        // “仅允许数字”的业务口径：把末位X的18位身份证视为无效
        if (id18XIsInvalid && "ID_VALID_18".equals(type) && candidate != null && candidate.getValue() != null) {
            String v = candidate.getValue();
            if (!v.isEmpty()) {
                char last = v.charAt(v.length() - 1);
                return last == 'X' || last == 'x';
            }
        }

        return false;
    }

    private static boolean isMaskedType(String entity, String type) {
        if ("phone".equals(entity)) {
            return "PHONE_MASKED".equals(type);
        }
        if ("bank_card".equals(entity)) {
            return "BANK_CARD_MASKED".equals(type);
        }
        if ("id_card".equals(entity)) {
            return "ID_MASKED".equals(type) || "ID_INVALID_LENGTH_MASKED".equals(type);
        }
        return false;
    }
}
