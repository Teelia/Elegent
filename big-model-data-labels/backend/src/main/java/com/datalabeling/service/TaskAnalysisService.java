package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.dto.response.KeywordCountVO;
import com.datalabeling.dto.response.TaskStatisticsVO;
import com.datalabeling.entity.DataRow;
import com.datalabeling.entity.FileTask;
import com.datalabeling.entity.TaskLabel;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.DataRowRepository;
import com.datalabeling.repository.FileTaskRepository;
import com.datalabeling.repository.TaskLabelRepository;
import com.datalabeling.util.SecurityUtil;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 任务统计与关键词分析服务
 */
@Service
@RequiredArgsConstructor
public class TaskAnalysisService {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "的", "了", "和", "是", "在", "我", "有", "也", "就", "不", "都", "一个", "上", "中", "到", "说"
    ));

    private final FileTaskRepository fileTaskRepository;
    private final DataRowRepository dataRowRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final SecurityUtil securityUtil;
    private final AuditService auditService;

    public TaskStatisticsVO getStatistics(Integer taskId, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        HashMap<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("taskId", taskId);
        auditService.recordAdminRead(task.getUserId(), "admin_read_task_statistics", "task", taskId, auditDetails, httpRequest);

        List<TaskLabel> taskLabels = taskLabelRepository.findByTaskIdOrderByIdAsc(taskId);
        List<String> labelKeys = new ArrayList<>();
        for (TaskLabel tl : taskLabels) {
            labelKeys.add(buildLabelKey(tl.getLabelName(), tl.getLabelVersion()));
        }

        Map<String, Map<String, Integer>> labelStatistics = new LinkedHashMap<>();
        for (String key : labelKeys) {
            Map<String, Integer> yesNo = new HashMap<>();
            yesNo.put("是", 0);
            yesNo.put("否", 0);
            labelStatistics.put(key, yesNo);
        }

        int totalRows = task.getTotalRows() != null ? task.getTotalRows() : 0;
        int page = 0;
        int size = 500;
        while (true) {
            Page<DataRow> dataPage = dataRowRepository.findByTaskId(
                taskId, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "rowIndex"))
            );
            for (DataRow row : dataPage.getContent()) {
                Map<String, Object> results = row.getLabelResults();
                for (String key : labelKeys) {
                    String v = results != null && results.get(key) != null ? String.valueOf(results.get(key)) : "否";
                    if ("是".equals(v)) {
                        labelStatistics.get(key).put("是", labelStatistics.get(key).get("是") + 1);
                    } else {
                        labelStatistics.get(key).put("否", labelStatistics.get(key).get("否") + 1);
                    }
                }
            }
            if (!dataPage.hasNext()) {
                break;
            }
            page++;
        }

        List<TaskStatisticsVO.LabelDistribution> distributions = new ArrayList<>();
        for (String key : labelKeys) {
            int yes = labelStatistics.get(key).get("是");
            int no = labelStatistics.get(key).get("否");
            distributions.add(TaskStatisticsVO.LabelDistribution.builder()
                .labelName(key)
                .labelValue("是")
                .count(yes)
                .percentage(totalRows > 0 ? yes * 100.0 / totalRows : 0.0)
                .build());
            distributions.add(TaskStatisticsVO.LabelDistribution.builder()
                .labelName(key)
                .labelValue("否")
                .count(no)
                .percentage(totalRows > 0 ? no * 100.0 / totalRows : 0.0)
                .build());
        }

        return TaskStatisticsVO.builder()
            .taskId(taskId)
            .totalRows(totalRows)
            .processedRows(task.getProcessedRows())
            .failedRows(task.getFailedRows())
            .labelStatistics(labelStatistics)
            .labelDistributions(distributions)
            .build();
    }

    public List<KeywordCountVO> getKeywords(Integer taskId, String labelKey, List<String> columns, Integer topN, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        HashMap<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("taskId", taskId);
        auditDetails.put("labelKey", labelKey);
        auditDetails.put("columns", columns);
        auditDetails.put("top", topN);
        auditService.recordAdminRead(task.getUserId(), "admin_read_task_keywords", "task", taskId, auditDetails, httpRequest);

        if (labelKey == null || labelKey.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "labelKey 不能为空");
        }
        if (columns == null || columns.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "columns 不能为空");
        }

        // 校验 labelKey 属于当前任务
        Set<String> allowedLabelKeys = new HashSet<>();
        for (TaskLabel tl : taskLabelRepository.findByTaskIdOrderByIdAsc(taskId)) {
            allowedLabelKeys.add(buildLabelKey(tl.getLabelName(), tl.getLabelVersion()));
        }
        if (!allowedLabelKeys.contains(labelKey)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "labelKey 不属于该任务");
        }

        int top = topN != null && topN > 0 ? topN : 50;

        Map<String, Integer> freq = new HashMap<>();
        int page = 0;
        int size = 500;
        while (true) {
            Page<DataRow> dataPage = dataRowRepository.findByTaskId(
                taskId, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "rowIndex"))
            );

            for (DataRow row : dataPage.getContent()) {
                Map<String, Object> results = row.getLabelResults();
                String v = results != null && results.get(labelKey) != null ? String.valueOf(results.get(labelKey)) : "否";
                if (!"是".equals(v)) {
                    continue;
                }

                Map<String, Object> original = row.getOriginalData();
                if (original == null) {
                    continue;
                }

                for (String col : columns) {
                    Object raw = original.get(col);
                    if (raw == null) {
                        continue;
                    }
                    countTokens(freq, String.valueOf(raw));
                }
            }

            if (!dataPage.hasNext()) {
                break;
            }
            page++;
        }

        return freq.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(top)
            .map(e -> KeywordCountVO.builder().keyword(e.getKey()).count(e.getValue()).build())
            .collect(java.util.stream.Collectors.toList());
    }

    private void countTokens(Map<String, Integer> freq, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        List<Term> terms = HanLP.segment(text);
        for (Term t : terms) {
            String w = t.word != null ? t.word.trim() : "";
            if (!isValidToken(w)) {
                continue;
            }
            freq.put(w, freq.getOrDefault(w, 0) + 1);
        }
    }

    private boolean isValidToken(String w) {
        if (w.isEmpty() || w.length() < 2) {
            return false;
        }
        if (STOP_WORDS.contains(w)) {
            return false;
        }
        if (w.matches("^\\d+$")) {
            return false;
        }
        if (w.matches("^[\\p{Punct}\\s]+$")) {
            return false;
        }
        return true;
    }

    private String buildLabelKey(String labelName, Integer version) {
        return labelName + "_v" + version;
    }
}

