package com.datalabeling.service;

import com.datalabeling.dto.response.TaskProgressVO;
import com.datalabeling.entity.DataRow;
import com.datalabeling.entity.FileTask;
import com.datalabeling.entity.Label;
import com.datalabeling.entity.TaskLabel;
import com.datalabeling.repository.DataRowRepository;
import com.datalabeling.repository.FileTaskRepository;
import com.datalabeling.repository.LabelRepository;
import com.datalabeling.repository.TaskLabelRepository;
import com.datalabeling.service.constant.RowProcessingStatus;
import com.datalabeling.service.constant.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步任务分析执行器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAnalyzeAsyncService {

    private final FileTaskRepository fileTaskRepository;
    private final DataRowRepository dataRowRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final LabelRepository labelRepository;
    private final FileParseService fileParseService;
    private final DeepSeekService deepSeekService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ModelConfigService modelConfigService;

    @Async("taskExecutor")
    public void analyzeAsync(Integer taskId, Integer modelConfigId, boolean includeReasoning) {
        FileTask task = fileTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }

        // 获取模型配置
        ModelConfigService.LLMRuntimeConfig runtimeConfig = null;
        if (modelConfigId != null) {
            try {
                com.datalabeling.dto.response.ModelConfigVO configVO = modelConfigService.getConfigById(modelConfigId);
                runtimeConfig = modelConfigService.toRuntimeConfig(configVO);
            } catch (Exception e) {
                log.error("Failed to load model config", e);
                // Fallback to default or fail? Let's fail to be safe.
                fileTaskRepository.updateStatusAndTimes(taskId, TaskStatus.FAILED, "加载模型配置失败: " + e.getMessage(),
                    task.getStartedAt(), LocalDateTime.now(), task.getArchivedAt());
                return;
            }
        } else {
            runtimeConfig = modelConfigService.getDeepSeekRuntimeConfig();
        }

        int total = task.getTotalRows() != null ? task.getTotalRows() : 0;
        List<TaskLabel> taskLabels = taskLabelRepository.findByTaskIdOrderByIdAsc(taskId);
        if (taskLabels.isEmpty()) {
            fileTaskRepository.updateStatusAndTimes(taskId, TaskStatus.FAILED, "未绑定任何标签",
                task.getStartedAt(), LocalDateTime.now(), task.getArchivedAt());
            pushProgress(taskId, total, 0, 0, 0, 0, 0, TaskStatus.FAILED);
            return;
        }

        Map<Integer, Label> labelById = new HashMap<>();
        for (TaskLabel tl : taskLabels) {
            Label label = labelRepository.findById(tl.getLabelId()).orElse(null);
            if (label != null) {
                labelById.put(tl.getLabelId(), label);
            }
        }

        long startMs = System.currentTimeMillis();
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger lastCurrentRow = new AtomicInteger(0);
        AtomicInteger lastPercentage = new AtomicInteger(0);
        long[] lastPushMs = new long[]{0L};

        try {
            final ModelConfigService.LLMRuntimeConfig finalConfig = runtimeConfig;
            fileParseService.forEachDataRow(task.getFilePath(), (rowIndex, rowData) -> {
                Map<String, Object> labelResults = new HashMap<>();
                Map<String, Object> aiConfidence = new HashMap<>();
                Map<String, Object> aiReasoning = new HashMap<>();
                boolean rowFailed = false;
                StringBuilder rowError = new StringBuilder();

                // 推送实时日志
                pushLog(taskId, "正在分析第 " + rowIndex + " 行...");

                for (TaskLabel tl : taskLabels) {
                    String labelKey = buildLabelKey(tl.getLabelName(), tl.getLabelVersion());
                    Label label = labelById.get(tl.getLabelId());
                    if (label == null) {
                        rowFailed = true;
                        appendError(rowError, "标签不存在: " + labelKey);
                        continue;
                    }
                    try {
                        Object result;
                        // 根据标签类型调用不同的方法
                        if (label.isExtraction()) {
                            // 提取类型标签
                            if (label.getExtractFields() != null && !label.getExtractFields().isEmpty()) {
                                // 有指定提取字段，使用结构化提取
                                result = deepSeekService.extract(label, rowData, finalConfig);
                            } else {
                                // 无指定字段，使用自由提取模式
                                result = deepSeekService.extractFreeForm(label, rowData, finalConfig);
                            }
                            // 提取类型的结果直接存储
                            labelResults.put(labelKey, result);
                        } else {
                            // 分类类型标签
                            result = deepSeekService.judge(label, rowData, finalConfig, includeReasoning);

                            // 判断是否是包含推理结果的Map（includeReasoning=true时）
                            if (includeReasoning && result instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> resultMap = (Map<String, Object>) result;
                                // 分别存储result、confidence、reasoning
                                labelResults.put(labelKey, resultMap.get("result"));
                                if (resultMap.get("confidence") != null) {
                                    aiConfidence.put(labelKey, resultMap.get("confidence"));
                                }
                                if (resultMap.get("reasoning") != null) {
                                    aiReasoning.put(labelKey, resultMap.get("reasoning"));
                                }
                            } else {
                                // 非推理模式，直接存储"是/否"字符串
                                labelResults.put(labelKey, result);
                            }
                        }
                    } catch (Exception e) {
                        rowFailed = true;
                        appendError(rowError, labelKey + " 调用失败: " + e.getMessage());
                        pushLog(taskId, "Row " + rowIndex + " [" + labelKey + "] 失败: " + e.getMessage());
                    }
                }

                DataRow dataRow = DataRow.builder()
                    .taskId(taskId)
                    .rowIndex(rowIndex)
                    .originalData(rowData)
                    .labelResults(labelResults)
                    .aiConfidence(aiConfidence.isEmpty() ? null : aiConfidence)
                    .aiReasoning(aiReasoning.isEmpty() ? null : aiReasoning)
                    .isModified(false)
                    .processingStatus(rowFailed ? RowProcessingStatus.FAILED : RowProcessingStatus.SUCCESS)
                    .errorMessage(rowFailed ? rowError.toString() : null)
                    .build();
                dataRowRepository.save(dataRow);

                int p = processed.incrementAndGet();
                int f = failed.get() + (rowFailed ? 1 : 0);
                failed.set(f);
                fileTaskRepository.updateProgress(taskId, p, f);

                int percentage = total > 0 ? (int) Math.min(100L, Math.round(p * 100.0 / total)) : 0;
                lastCurrentRow.set(rowIndex);
                lastPercentage.set(percentage);

                long now = System.currentTimeMillis();
                if (p >= total || now - lastPushMs[0] >= 500) {
                    lastPushMs[0] = now;
                    int etaSeconds = estimateEtaSeconds(startMs, now, total, p);
                    pushProgress(taskId, total, p, f, percentage, rowIndex, etaSeconds, TaskStatus.PROCESSING);
                }
            });

            fileTaskRepository.updateStatusAndTimes(taskId, TaskStatus.COMPLETED, null,
                task.getStartedAt(), LocalDateTime.now(), task.getArchivedAt());
            pushProgress(taskId, total, processed.get(), failed.get(), 100, lastCurrentRow.get(), 0, TaskStatus.COMPLETED);
        } catch (Exception e) {
            log.error("任务分析失败: taskId={}, {}", taskId, e.getMessage(), e);
            fileTaskRepository.updateStatusAndTimes(taskId, TaskStatus.FAILED, e.getMessage(),
                task.getStartedAt(), LocalDateTime.now(), task.getArchivedAt());
            pushProgress(taskId, total, processed.get(), failed.get(), lastPercentage.get(), lastCurrentRow.get(), 0, TaskStatus.FAILED);
        }
    }

    private void pushProgress(Integer taskId, int total, int processed, int failed, int percentage,
                              int currentRow, int etaSeconds, String status) {
        TaskProgressVO vo = TaskProgressVO.builder()
            .taskId(taskId)
            .total(total)
            .processed(processed)
            .failed(failed)
            .percentage(percentage)
            .currentRow(currentRow)
            .etaSeconds(etaSeconds)
            .status(status)
            .build();
        messagingTemplate.convertAndSend("/topic/tasks/" + taskId + "/progress", vo);
    }

    private int estimateEtaSeconds(long startMs, long nowMs, int total, int processed) {
        if (total <= 0 || processed <= 0) {
            return 0;
        }
        long elapsedMs = nowMs - startMs;
        double avgMs = elapsedMs * 1.0 / processed;
        double remainingMs = avgMs * (total - processed);
        return (int) Math.max(0, Math.round(remainingMs / 1000.0));
    }

    private String buildLabelKey(String labelName, Integer version) {
        return labelName + "_v" + version;
    }

    private void appendError(StringBuilder sb, String error) {
        if (sb.length() > 0) {
            sb.append("; ");
        }
        sb.append(error);
    }

    private void pushLog(Integer taskId, String message) {
        try {
            Map<String, Object> logMsg = new HashMap<>();
            logMsg.put("taskId", taskId);
            logMsg.put("message", message);
            logMsg.put("timestamp", System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/task/" + taskId + "/log", logMsg);
        } catch (Exception e) {
            log.warn("Failed to push log", e);
        }
    }
}
