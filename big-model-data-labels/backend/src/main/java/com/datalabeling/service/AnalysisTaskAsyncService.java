package com.datalabeling.service;

import com.datalabeling.dto.response.ModelConfigVO;
import com.datalabeling.entity.*;
import com.datalabeling.repository.*;
import com.datalabeling.service.extraction.ExtractionOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分析任务异步执行服务
 * 处理新版 AnalysisTask 的大模型分析逻辑
 * 支持并发处理多行数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisTaskAsyncService {

    private final AnalysisTaskRepository analysisTaskRepository;
    private final AnalysisTaskLabelRepository analysisTaskLabelRepository;
    private final DataRowRepository dataRowRepository;
    private final LabelRepository labelRepository;
    private final LabelResultRepository labelResultRepository;
    private final TaskExecutionLogRepository taskExecutionLogRepository;
    private final DeepSeekService deepSeekService;
    private final ModelConfigService modelConfigService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ExtractionOrchestrator extractionOrchestrator;
    private final PromptTemplateEngine promptTemplateEngine;
    private final EnhancementService enhancementService;

    /**
     * 号码类标签意图执行器（规则优先，避免依赖 description 猜测）。
     */
    private final com.datalabeling.service.extraction.NumberIntentEvaluator numberIntentEvaluator =
        new com.datalabeling.service.extraction.NumberIntentEvaluator();

    /**
     * 号码证据提取器（用于格式化“规则验证结果”，避免模型凭空生成号码）。
     */
    private final com.datalabeling.service.extraction.NumberEvidenceExtractor numberEvidenceExtractor =
        new com.datalabeling.service.extraction.NumberEvidenceExtractor();

    /**
     * 异步执行分析任务（支持并发）
     */
    @Async("taskExecutor")
    public void executeAsync(Integer taskId) {
        log.info("开始异步执行分析任务: {}", taskId);

        AnalysisTask task = analysisTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.error("分析任务不存在: {}", taskId);
            return;
        }

        // 检查任务状态
        if (!AnalysisTask.Status.PROCESSING.equals(task.getStatus())) {
            log.warn("任务状态不是 processing，跳过执行: taskId={}, status={}", taskId, task.getStatus());
            return;
        }

        // 获取并发数
        int concurrency = task.getConcurrency() != null ? task.getConcurrency() : 1;
        concurrency = Math.max(1, Math.min(10, concurrency)); // 限制在 1-10 之间

        log.info("任务 {} 使用并发数: {}", taskId, concurrency);

        try {
            // 获取模型配置
            ModelConfigService.LLMRuntimeConfig runtimeConfig = getRuntimeConfig(task.getModelConfigId());
            if (runtimeConfig == null) {
                failTask(task, "无法获取模型配置");
                return;
            }

            // 获取任务关联的标签
            List<AnalysisTaskLabel> taskLabels = analysisTaskLabelRepository.findByAnalysisTaskId(taskId);
            if (taskLabels.isEmpty()) {
                failTask(task, "任务未绑定任何标签");
                return;
            }

            // 加载标签详情
            Map<Integer, Label> labelMap = new HashMap<>();
            for (AnalysisTaskLabel tl : taskLabels) {
                Label label = labelRepository.findById(tl.getLabelId()).orElse(null);
                if (label != null) {
                    labelMap.put(tl.getLabelId(), label);
                }
            }

            // 获取数据集的所有数据行
            Integer datasetId = task.getDatasetId();
            List<DataRow> dataRows = dataRowRepository.findByTaskIdOrderByRowIndex(datasetId);

            if (dataRows.isEmpty()) {
                failTask(task, "数据集中没有数据行");
                return;
            }

            logTask(taskId, TaskExecutionLog.LogLevel.INFO,
                String.format("开始分析 %d 行数据，使用 %d 个标签，并发数: %d",
                    dataRows.size(), taskLabels.size(), concurrency));

            // 使用并发处理
            if (concurrency > 1) {
                executeConcurrently(task, dataRows, taskLabels, labelMap, runtimeConfig, concurrency);
            } else {
                executeSequentially(task, dataRows, taskLabels, labelMap, runtimeConfig);
            }

        } catch (Exception e) {
            log.error("任务执行异常: taskId={}", taskId, e);
            failTask(task, "执行异常: " + e.getMessage());
        }
    }

    /**
     * 并发执行分析任务
     */
    private void executeConcurrently(AnalysisTask task, List<DataRow> dataRows,
                                      List<AnalysisTaskLabel> taskLabels,
                                      Map<Integer, Label> labelMap,
                                      ModelConfigService.LLMRuntimeConfig runtimeConfig,
                                      int concurrency) {
        Integer taskId = task.getId();
        int totalRows = dataRows.size();

        // 线程安全的计数器
        AtomicInteger processedRows = new AtomicInteger(0);
        AtomicInteger successRows = new AtomicInteger(0);
        AtomicInteger failedRows = new AtomicInteger(0);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        // 创建固定大小的线程池
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        // 使用信号量控制并发数
        Semaphore semaphore = new Semaphore(concurrency);

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (DataRow dataRow : dataRows) {
                // 检查任务是否被取消
                if (cancelled.get()) {
                    break;
                }

                // 获取信号量
                semaphore.acquire();

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // 再次检查取消状态
                        if (cancelled.get()) {
                            return;
                        }

                        // 检查任务是否被取消或暂停
                        AnalysisTask currentTask = analysisTaskRepository.findById(taskId).orElse(null);
                        if (currentTask == null ||
                            AnalysisTask.Status.CANCELLED.equals(currentTask.getStatus()) ||
                            AnalysisTask.Status.PAUSED.equals(currentTask.getStatus())) {
                            log.info("任务已取消或暂停: taskId={}", taskId);
                            cancelled.set(true);
                            return;
                        }

                        // 处理单行数据
                        boolean rowSuccess = processDataRow(taskId, dataRow, taskLabels, labelMap,
                            runtimeConfig, task.getDefaultConfidenceThreshold());

                        // 更新计数
                        int processed = processedRows.incrementAndGet();
                        if (rowSuccess) {
                            successRows.incrementAndGet();
                        } else {
                            failedRows.incrementAndGet();
                        }

                        // 更新任务进度（每处理一行都更新）
                        updateTaskProgress(taskId, processed, successRows.get(), failedRows.get());

                        // 推送进度到前端
                        pushProgress(taskId, totalRows, processed, successRows.get(), failedRows.get(),
                            dataRow.getRowIndex(), task.getStatus());

                        // 记录日志（每10行或完成时）
                        if (processed % 10 == 0 || processed == totalRows) {
                            logTask(taskId, TaskExecutionLog.LogLevel.INFO,
                                String.format("进度: %d/%d 行已处理 (并发)", processed, totalRows));
                        }

                    } finally {
                        semaphore.release();
                    }
                }, executor);

                futures.add(future);
            }

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 检查是否被取消
            if (!cancelled.get()) {
                // 完成任务
                completeTask(taskId, processedRows.get(), successRows.get(), failedRows.get());
                logTask(taskId, TaskExecutionLog.LogLevel.INFO,
                    String.format("任务完成: 成功 %d 行，失败 %d 行 (并发模式)",
                        successRows.get(), failedRows.get()));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("任务被中断: taskId={}", taskId, e);
            failTask(task, "任务被中断");
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 顺序执行分析任务（原有逻辑）
     */
    private void executeSequentially(AnalysisTask task, List<DataRow> dataRows,
                                      List<AnalysisTaskLabel> taskLabels,
                                      Map<Integer, Label> labelMap,
                                      ModelConfigService.LLMRuntimeConfig runtimeConfig) {
        Integer taskId = task.getId();
        int totalRows = dataRows.size();
        int processedRows = 0;
        int successRows = 0;
        int failedRows = 0;

        // 遍历每一行数据
        for (DataRow dataRow : dataRows) {
            // 检查任务是否被取消或暂停
            AnalysisTask currentTask = analysisTaskRepository.findById(taskId).orElse(null);
            if (currentTask == null ||
                AnalysisTask.Status.CANCELLED.equals(currentTask.getStatus()) ||
                AnalysisTask.Status.PAUSED.equals(currentTask.getStatus())) {
                log.info("任务已取消或暂停: taskId={}, status={}", taskId,
                    currentTask != null ? currentTask.getStatus() : "null");
                return;
            }

            // 处理单行数据
            boolean rowSuccess = processDataRow(taskId, dataRow, taskLabels, labelMap,
                runtimeConfig, task.getDefaultConfidenceThreshold());

            processedRows++;
            if (rowSuccess) {
                successRows++;
            } else {
                failedRows++;
            }

            // 更新任务进度
            updateTaskProgress(taskId, processedRows, successRows, failedRows);

            // 推送进度到前端
            pushProgress(taskId, totalRows, processedRows, successRows, failedRows,
                dataRow.getRowIndex(), task.getStatus());

            // 记录日志
            if (processedRows % 10 == 0 || processedRows == totalRows) {
                logTask(taskId, TaskExecutionLog.LogLevel.INFO,
                    String.format("进度: %d/%d 行已处理", processedRows, totalRows));
            }
        }

        // 完成任务
        completeTask(taskId, processedRows, successRows, failedRows);

        logTask(taskId, TaskExecutionLog.LogLevel.INFO,
            String.format("任务完成: 成功 %d 行，失败 %d 行", successRows, failedRows));
    }

    /**
     * 处理单行数据的所有标签
     * @return 是否全部成功
     */
    private boolean processDataRow(Integer taskId, DataRow dataRow,
                                    List<AnalysisTaskLabel> taskLabels,
                                    Map<Integer, Label> labelMap,
                                    ModelConfigService.LLMRuntimeConfig runtimeConfig,
                                    BigDecimal confidenceThreshold) {
        boolean rowSuccess = true;

        log.info("[processDataRow] 开始处理行: taskId={}, rowIndex={}, 标签数={}",
            taskId, dataRow.getRowIndex(), taskLabels.size());

        // 对每个标签进行分析
        for (AnalysisTaskLabel taskLabel : taskLabels) {
            Label label = labelMap.get(taskLabel.getLabelId());
            if (label == null) {
                log.warn("[processDataRow] 标签未找到: labelId={}", taskLabel.getLabelId());
                continue;
            }

            String labelKey = buildLabelKey(taskLabel.getLabelName(), taskLabel.getLabelVersion());
            log.info("[processDataRow] 处理标签: labelKey={}, type={}", labelKey, label.getType());

            try {
                // 检查该标签结果是否已成功处理（断点续传支持）
                LabelResult existing = labelResultRepository
                    .findByAnalysisTaskIdAndDataRowIdAndLabelId(taskId, dataRow.getId(), taskLabel.getLabelId())
                    .orElse(null);

                if (existing != null && LabelResult.ProcessingStatus.SUCCESS.equals(existing.getProcessingStatus())) {
                    // 已成功处理，跳过
                    log.debug("跳过已处理的标签结果: taskId={}, rowIndex={}, labelId={}",
                        taskId, dataRow.getRowIndex(), taskLabel.getLabelId());
                    continue;
                }

                // 调用大模型分析
                log.debug("分析行 {}，标签: {}，类型: {}", dataRow.getRowIndex(), labelKey, label.getType());

                // 根据标签类型选择不同的处理方式
                if (label.isStructuredExtraction()) {
                    // 结构化号码提取（使用规则提取器）
                    processStructuredExtractionLabel(taskId, dataRow, taskLabel, label, labelKey, confidenceThreshold);
                } else if (label.isExtraction()) {
                    // LLM通用提取
                    processExtractionLabel(taskId, dataRow, taskLabel, label, labelKey, runtimeConfig, confidenceThreshold);
                } else {
                    // 分类类型标签（默认）
                    processClassificationLabel(taskId, dataRow, taskLabel, label, labelKey, runtimeConfig, confidenceThreshold);
                }

            } catch (Exception e) {
                log.error("分析失败: taskId={}, rowIndex={}, label={}, error={}",
                    taskId, dataRow.getRowIndex(), labelKey, e.getMessage());

                // 保存失败结果
                saveLabelResultFailed(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey, e.getMessage(), label.getType());
                rowSuccess = false;

                logTask(taskId, TaskExecutionLog.LogLevel.ERROR,
                    String.format("行 %d 标签 %s 分析失败: %s", dataRow.getRowIndex(), labelKey, e.getMessage()));
            }
        }

        return rowSuccess;
    }

    /**
     * 处理分类类型标签
     * 支持预处理模式和二次强化分析
     */
    private void processClassificationLabel(Integer taskId, DataRow dataRow, AnalysisTaskLabel taskLabel,
                                             Label label, String labelKey,
                                             ModelConfigService.LLMRuntimeConfig runtimeConfig,
                                             BigDecimal confidenceThreshold) {
        Map<String, Object> rowData = dataRow.getOriginalData();

        log.info("[processClassificationLabel] 标签类型: {}, ruleOnly={}, hybrid={}",
            label.getType(), label.isRuleOnlyMode(), label.isHybridMode());

        // 根据预处理模式选择处理方式
        if (label.isRuleOnlyMode()) {
            // 纯规则模式：不调用 LLM
            log.info("[processClassificationLabel] 使用纯规则模式处理: {}", labelKey);
            processRuleOnlyLabel(taskId, dataRow, taskLabel, label, labelKey, confidenceThreshold);
            return;
        }

        // 混合模式或普通模式：调用 LLM
        String preprocessorResult = null;
        List<com.datalabeling.service.extraction.ExtractedNumber> extractedNumbers = null;

        // 如果启用了预处理（混合模式），先执行规则提取
        if (label.isHybridMode()) {
            log.info("[processClassificationLabel] 使用混合模式，先执行规则提取");
            try {
                preprocessorResult = executePreprocessing(label, rowData);
                log.info("[processClassificationLabel] 规则提取完成: 结果长度={}",
                    preprocessorResult != null ? preprocessorResult.length() : 0);
            } catch (Exception e) {
                log.warn("预处理失败，继续使用普通模式: {}", e.getMessage());
            }
        }

        // 调用 LLM 判断（传递预处理结果）
        log.info("[processClassificationLabel] 调用LLM进行判断: {}", labelKey);
        Object result = deepSeekService.judge(label, rowData, preprocessorResult, runtimeConfig, true);

        // 解析结果
        String resultValue;
        int confidenceInt = 0;
        String reasoning = null;

        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            resultValue = String.valueOf(resultMap.getOrDefault("result", "否"));
            Object confObj = resultMap.get("confidence");
            if (confObj instanceof Number) {
                confidenceInt = ((Number) confObj).intValue();
            }
            Object reasonObj = resultMap.get("reasoning");
            if (reasonObj != null) {
                reasoning = String.valueOf(reasonObj);
            }

            // 详细日志：LLM返回的原始结果
            log.info("[processClassificationLabel] LLM返回结果解析: result={}, confidence={}, reasoning长度={}, reasoning内容={}",
                resultValue, confidenceInt, reasoning != null ? reasoning.length() : 0, reasoning);
        } else {
            resultValue = String.valueOf(result);
            log.info("[processClassificationLabel] LLM返回非Map结果: result={}, 类型={}",
                resultValue, result != null ? result.getClass().getName() : "null");
        }

        // 如果启用了二次强化分析，执行强化
        if (label.isEnhancementEnabled()) {
            try {
                com.datalabeling.dto.EnhancementConfig enhancementConfig =
                    com.datalabeling.dto.EnhancementConfig.fromJson(label.getEnhancementConfig());

                EnhancementService.EnhancementResult enhancementResult = enhancementService.enhance(
                    label,
                    rowData,
                    resultValue,
                    confidenceInt,
                    reasoning != null ? reasoning : "",
                    preprocessorResult != null ? preprocessorResult : null,
                    enhancementConfig
                );

                // 应用强化结果
                if (enhancementResult.getFinalResult() != null) {
                    resultValue = enhancementResult.getFinalResult();
                }
                confidenceInt = enhancementResult.getFinalConfidence();

                // 合并推理信息
                String enhancedReasoning = reasoning;
                if (enhancementResult.getValidationNotes() != null && !enhancementResult.getValidationNotes().isEmpty()) {
                    enhancedReasoning = reasoning + "\n[二次强化] " + enhancementResult.getValidationNotes();
                }
                reasoning = enhancedReasoning;

            } catch (Exception e) {
                log.warn("强化分析失败，使用原始结果: {}", e.getMessage());
            }
        }

        // 转换置信度
        BigDecimal confidence = new BigDecimal(confidenceInt).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);

        // 详细日志：即将保存的最终判断依据
        log.info("[processClassificationLabel] 准备保存结果: labelKey={}, result={}, confidence={}, reasoning长度={}, reasoning内容={}",
            labelKey, resultValue, confidence, reasoning != null ? reasoning.length() : 0, reasoning);

        // 保存标签结果
        saveLabelResult(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey,
            resultValue, confidence, reasoning, confidenceThreshold, Label.Type.CLASSIFICATION, null);
    }

    /**
     * 纯规则模式处理（不调用 LLM）
     */
    private void processRuleOnlyLabel(Integer taskId, DataRow dataRow, AnalysisTaskLabel taskLabel,
                                       Label label, String labelKey,
                                       BigDecimal confidenceThreshold) {
        Map<String, Object> rowData = dataRow.getOriginalData();

        log.info("[processRuleOnlyLabel] 使用纯规则模式处理: {}", labelKey);

        try {
            // number_intent：显式意图优先（用于手机号/银行卡等"存在/提取/无效/遮挡"任务）
            com.datalabeling.dto.PreprocessorConfig cfg =
                com.datalabeling.dto.PreprocessorConfig.fromJson(label.getPreprocessorConfig());
            if (cfg.getNumberIntent() != null) {
                log.info("[processRuleOnlyLabel] 检测到number_intent配置，使用NumberIntentEvaluator处理");
                String text = getTargetText(label, rowData);
                log.info("[processRuleOnlyLabel] 目标文本长度: {}", text != null ? text.length() : 0);

                com.datalabeling.service.extraction.NumberIntentEvaluator.EvaluationResult r =
                    numberIntentEvaluator.evaluate(text, cfg.getNumberIntent());
                if (r.isCanHandle()) {
                    log.info("[processRuleOnlyLabel] NumberIntentEvaluator处理完成: hit={}, confidence={}",
                        r.isHit(), r.getConfidence());

                    String resultValue = r.isHit() ? "是" : "否";
                    BigDecimal confidence = new BigDecimal(r.getConfidence())
                        .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
                    String reasoning = r.getReasoning();

                    String extractedDataJson = null;
                    try {
                        extractedDataJson = new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(r.getExtractedData());
                    } catch (Exception e) {
                        log.warn("序列化提取数据失败: {}", e.getMessage());
                    }

                    log.info("[processRuleOnlyLabel] 保存结果: result={}, confidence={}, reasoning长度={}",
                        resultValue, confidence, reasoning != null ? reasoning.length() : 0);

                    saveLabelResult(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey,
                        resultValue, confidence, reasoning, confidenceThreshold, Label.Type.CLASSIFICATION, extractedDataJson);
                    return;
                } else {
                    log.info("[processRuleOnlyLabel] NumberIntentEvaluator无法处理该意图");
                }
            }

            // 执行规则提取
            log.info("[processRuleOnlyLabel] 执行常规规则提取");
            String preprocessorResult = executePreprocessing(label, rowData);

            // 根据提取结果判断
            boolean hasResult = preprocessorResult != null && !preprocessorResult.isEmpty()
                && !preprocessorResult.contains("未提取到");

            String resultValue = hasResult ? "是" : "否";
            BigDecimal confidence = hasResult ? new BigDecimal("0.95") : new BigDecimal("0.00");
            String reasoning = hasResult ? preprocessorResult : "规则提取未找到匹配内容";

            // 保存标签结果
            saveLabelResult(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey,
                resultValue, confidence, reasoning, confidenceThreshold, Label.Type.CLASSIFICATION, null);

        } catch (Exception e) {
            log.error("规则模式处理失败: {}", e.getMessage(), e);
            saveLabelResult(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey,
                "否", new BigDecimal("0.00"), "规则提取失败: " + e.getMessage(),
                confidenceThreshold, Label.Type.CLASSIFICATION, null);
        }
    }

    /**
     * 执行预处理（规则提取）
     */
    private String executePreprocessing(Label label, Map<String, Object> rowData) {
        com.datalabeling.dto.PreprocessorConfig config =
            com.datalabeling.dto.PreprocessorConfig.fromJson(label.getPreprocessorConfig());

        // 获取目标文本
        String text = getTargetText(label, rowData);

        StringBuilder output = new StringBuilder();

        // number_intent：先输出“意图规则执行摘要”，再补充“身份证证据详情”（如适用）
        if (config.getNumberIntent() != null) {
            com.datalabeling.service.extraction.NumberIntentEvaluator.EvaluationResult r =
                numberIntentEvaluator.evaluate(text, config.getNumberIntent());
            if (r.isCanHandle() && r.getReasoning() != null && !r.getReasoning().trim().isEmpty()) {
                output.append(r.getReasoning().trim());
            }

            if (config.getNumberIntent().getEntity() != null
                && "id_card".equalsIgnoreCase(config.getNumberIntent().getEntity().trim())) {
                com.datalabeling.service.extraction.NumberEvidence evidence = numberEvidenceExtractor.extract(text);
                String validation = formatValidationResult(evidence);
                if (validation != null && !validation.isEmpty()) {
                    if (output.length() > 0) {
                        output.append("\n\n");
                    }
                    output.append(validation);
                }
            }
        }

        List<com.datalabeling.service.extraction.ExtractedNumber> allResults = new ArrayList<>();

        // 根据配置的提取器执行提取
        if (config.isExtractorEnabled("id_card")) {
            allResults.addAll(extractIdCards(text, config));
        }
        if (config.isExtractorEnabled("phone")) {
            allResults.addAll(extractPhones(text, config));
        }
        if (config.isExtractorEnabled("bank_card")) {
            allResults.addAll(extractBankCards(text, config));
        }
        if (config.isExtractorEnabled("passport")) {
            allResults.addAll(extractPassports(text, config));
        }
        if (config.isExtractorEnabled("keyword_match")) {
            allResults.addAll(extractKeywordMatches(text, config));
        }
        if (config.isExtractorEnabled("school_info")) {
            allResults.addAll(extractSchoolInfo(text, config));
        }

        // 格式化其他提取器结果
        if (!allResults.isEmpty()) {
            if (output.length() > 0) {
                output.append("\n\n");
            }
            for (com.datalabeling.service.extraction.ExtractedNumber result : allResults) {
                if (output.length() > 0 && (output.charAt(output.length() - 1) != '\n')) {
                    output.append("\n");
                }
            String field = result.getField() != null ? result.getField() : "提取结果";
            String validation = result.getValidation() != null ? result.getValidation() : "";
                output.append("- 检测到").append(field).append(": ")
                    .append(result.getValue())
                    .append(" (").append(validation).append(")");
            }
        }

        if (output.length() == 0) {
            return "未提取到相关信息";
        }
        return output.toString();
    }

    /**
     * 将身份证规则证据（NumberEvidence）转换为易读文本，供 LLM 与二次强化审计使用。
     *
     * <p>注意：仅输出 maskedValue，避免在提示词/日志中泄露完整号码。</p>
     */
    private String formatValidationResult(com.datalabeling.service.extraction.NumberEvidence evidence) {
        if (evidence == null || evidence.getNumbers() == null || evidence.getNumbers().isEmpty()) {
            return "未提取到身份证号";
        }

        List<com.datalabeling.service.extraction.NumberEvidence.NumberCandidate> idCandidates = new ArrayList<>();
        for (com.datalabeling.service.extraction.NumberEvidence.NumberCandidate n : evidence.getNumbers()) {
            if (n != null && n.getType() != null && n.getType().startsWith("ID_")) {
                idCandidates.add(n);
            }
        }
        if (idCandidates.isEmpty()) {
            return "未提取到身份证号";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 身份证号提取证据 ===\n");

        Map<String, Object> derived = evidence.getDerived() != null ? evidence.getDerived() : new HashMap<>();
        sb.append("统计：\n");
        sb.append("  - 有效18位：").append(derived.get("id_valid_18_count")).append("个\n");
        sb.append("  - 有效15位：").append(derived.get("id_valid_15_count")).append("个\n");
        sb.append("  - 长度错误：").append(derived.get("id_invalid_length_count")).append("个\n");
        sb.append("  - 遮挡长度错误：").append(derived.get("id_invalid_length_masked_count")).append("个\n");
        sb.append("  - 校验位错误：").append(derived.get("id_invalid_checksum_count")).append("个（不计入无效）\n");
        sb.append("  - 遮挡：").append(derived.get("id_masked_count")).append("个\n");
        sb.append("\n");

        sb.append("详细列表：\n");
        for (com.datalabeling.service.extraction.NumberEvidence.NumberCandidate n : idCandidates) {
            sb.append("  ").append(n.getId()).append(". ");
            sb.append("[").append(n.getType()).append("] ");
            sb.append(n.getMaskedValue() != null ? n.getMaskedValue() : "");
            sb.append(" (").append(n.getLength()).append("位)");

            if ("ID_INVALID_LENGTH".equals(n.getType())) {
                sb.append(" 【长度错误】");
            } else if ("ID_INVALID_LENGTH_MASKED".equals(n.getType())) {
                sb.append(" 【遮挡且长度错误】");
            } else if ("ID_INVALID_CHECKSUM".equals(n.getType())) {
                sb.append(" 【校验位错误，但仍视为有效格式】");
            } else if ("ID_MASKED".equals(n.getType())) {
                sb.append(" 【遮挡】");
            }

            if (n.getKeywordHint() != null && !n.getKeywordHint().isEmpty()) {
                sb.append("（命中关键词：").append(n.getKeywordHint()).append("）");
            }
            sb.append("\n");

            if (n.getValidations() != null) {
                for (com.datalabeling.service.extraction.NumberEvidence.ValidationItem v : n.getValidations()) {
                    if (v == null) {
                        continue;
                    }
                    sb.append("      - ").append(v.getName()).append(": ");
                    sb.append(v.isPass() ? "✓" : "✗");
                    sb.append(" ").append(v.getDetail()).append("\n");
                }
            }

            if (n.getConflicts() != null && !n.getConflicts().isEmpty()) {
                for (com.datalabeling.service.extraction.NumberEvidence.ConflictItem c : n.getConflicts()) {
                    if (c == null) {
                        continue;
                    }
                    sb.append("      - 冲突: 与").append(c.getWithType())
                        .append("（").append(c.getReason()).append("），最终按").append(c.getResolvedAs()).append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * 提取身份证号
     */
    private List<com.datalabeling.service.extraction.ExtractedNumber> extractIdCards(
            String text, com.datalabeling.dto.PreprocessorConfig config) {
        com.datalabeling.dto.PreprocessorConfig.IdCardOptions legacy =
            config.getIdCardOptions() != null ? config.getIdCardOptions() : new com.datalabeling.dto.PreprocessorConfig.IdCardOptions();
        Map<String, Object> dynamic = config.getExtractorOptions("id_card");
        Map<String, Object> optionMap = new HashMap<>();
        optionMap.put("include18Digit", dynamic.containsKey("include18Digit") ? dynamic.get("include18Digit") : legacy.isInclude18Digit());
        optionMap.put("include15Digit", dynamic.containsKey("include15Digit") ? dynamic.get("include15Digit") : legacy.isInclude15Digit());
        optionMap.put("includeLoose", dynamic.containsKey("includeLoose") ? dynamic.get("includeLoose") : legacy.isIncludeLoose());

        List<com.datalabeling.service.extraction.ExtractedNumber> results =
            extractionOrchestrator.getIdCardExtractor().extract(text, optionMap);
        // 补齐字段名（基础提取器不设置 field）
        for (com.datalabeling.service.extraction.ExtractedNumber r : results) {
            if (r.getField() == null) {
                r.setField("身份证号");
            }
        }
        return results;
    }

    /**
     * 提取手机号
     */
    private List<com.datalabeling.service.extraction.ExtractedNumber> extractPhones(
            String text, com.datalabeling.dto.PreprocessorConfig config) {
        com.datalabeling.dto.PreprocessorConfig.PhoneOptions legacy =
            config.getPhoneOptions() != null ? config.getPhoneOptions() : new com.datalabeling.dto.PreprocessorConfig.PhoneOptions();
        Map<String, Object> dynamic = config.getExtractorOptions("phone");
        Map<String, Object> optionMap = new HashMap<>();
        optionMap.put("includeChinaMobile", dynamic.containsKey("includeChinaMobile") ? dynamic.get("includeChinaMobile") : legacy.isIncludeChinaMobile());
        optionMap.put("includeChinaUnicom", dynamic.containsKey("includeChinaUnicom") ? dynamic.get("includeChinaUnicom") : legacy.isIncludeChinaUnicom());
        optionMap.put("includeChinaTelecom", dynamic.containsKey("includeChinaTelecom") ? dynamic.get("includeChinaTelecom") : legacy.isIncludeChinaTelecom());
        optionMap.put("includeLoose", dynamic.containsKey("includeLoose") ? dynamic.get("includeLoose") : legacy.isIncludeLoose());

        List<com.datalabeling.service.extraction.ExtractedNumber> results =
            extractionOrchestrator.getPhoneExtractor().extract(text, optionMap);
        for (com.datalabeling.service.extraction.ExtractedNumber r : results) {
            if (r.getField() == null) {
                r.setField("手机号");
            }
        }
        return results;
    }

    /**
     * 提取银行卡号
     */
    private List<com.datalabeling.service.extraction.ExtractedNumber> extractBankCards(
            String text, com.datalabeling.dto.PreprocessorConfig config) {
        com.datalabeling.dto.PreprocessorConfig.BankCardOptions legacy =
            config.getBankCardOptions() != null ? config.getBankCardOptions() : new com.datalabeling.dto.PreprocessorConfig.BankCardOptions();
        Map<String, Object> dynamic = config.getExtractorOptions("bank_card");
        Map<String, Object> optionMap = new HashMap<>();
        optionMap.put("include16Digit", dynamic.containsKey("include16Digit") ? dynamic.get("include16Digit") : legacy.isInclude16Digit());
        optionMap.put("includeOtherLength", dynamic.containsKey("includeOtherLength") ? dynamic.get("includeOtherLength") : legacy.isIncludeOtherLength());
        optionMap.put("validateBin", dynamic.containsKey("validateBin") ? dynamic.get("validateBin") : legacy.isValidateBin());
        optionMap.put("useLuhnValidation", dynamic.containsKey("useLuhnValidation") ? dynamic.get("useLuhnValidation") : legacy.isUseLuhnValidation());

        List<com.datalabeling.service.extraction.ExtractedNumber> results =
            extractionOrchestrator.getBankCardExtractor().extract(text, optionMap);
        for (com.datalabeling.service.extraction.ExtractedNumber r : results) {
            if (r.getField() == null) {
                r.setField("银行卡号");
            }
        }
        return results;
    }

    /**
     * 提取护照号
     */
    private List<com.datalabeling.service.extraction.ExtractedNumber> extractPassports(
            String text, com.datalabeling.dto.PreprocessorConfig config) {
        Map<String, Object> optionMap = new HashMap<>(config.getExtractorOptions("passport"));
        List<com.datalabeling.service.extraction.ExtractedNumber> results =
            extractionOrchestrator.getPassportExtractor().extract(text, optionMap);
        for (com.datalabeling.service.extraction.ExtractedNumber r : results) {
            if (r.getField() == null) {
                r.setField("护照号");
            }
        }
        return results;
    }

    /**
     * 关键词匹配（规则证据）
     */
    private List<com.datalabeling.service.extraction.ExtractedNumber> extractKeywordMatches(
            String text, com.datalabeling.dto.PreprocessorConfig config) {
        Map<String, Object> optionMap = new HashMap<>(config.getExtractorOptions("keyword_match"));
        List<com.datalabeling.service.extraction.ExtractedNumber> results =
            extractionOrchestrator.getKeywordMatcherExtractor().extract(text, optionMap);
        for (com.datalabeling.service.extraction.ExtractedNumber r : results) {
            if (r.getField() == null) {
                r.setField("关键词匹配");
            }
        }
        return results;
    }

    /**
     * 学校信息提取（规则证据）
     */
    private List<com.datalabeling.service.extraction.ExtractedNumber> extractSchoolInfo(
            String text, com.datalabeling.dto.PreprocessorConfig config) {
        Map<String, Object> optionMap = new HashMap<>(config.getExtractorOptions("school_info"));
        List<com.datalabeling.service.extraction.ExtractedNumber> results =
            extractionOrchestrator.getSchoolInfoExtractor().extract(text, optionMap);
        for (com.datalabeling.service.extraction.ExtractedNumber r : results) {
            if (r.getField() == null) {
                r.setField("学校信息");
            }
        }
        return results;
    }

    /**
     * 获取目标文本
     */
    private String getTargetText(Label label, Map<String, Object> rowData) {
        List<String> focusColumns = label.getFocusColumns();

        if (focusColumns != null && !focusColumns.isEmpty()) {
            // 兼容：大小写不敏感 + 去空格匹配；若全部未命中则回退到全部数据，避免空文本。
            Map<String, String> normalizedKeyMap = new HashMap<>();
            for (String key : rowData.keySet()) {
                normalizedKeyMap.put(normalizeColumnName(key), key);
            }

            StringBuilder sb = new StringBuilder();
            for (String column : focusColumns) {
                Object value = null;
                if (rowData.containsKey(column)) {
                    value = rowData.get(column);
                } else {
                    String actualKey = normalizedKeyMap.get(normalizeColumnName(column));
                    if (actualKey != null) {
                        value = rowData.get(actualKey);
                    }
                }
                if (value != null) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(value.toString());
                }
            }

            if (sb.length() > 0) {
                return sb.toString();
            }
            // 回退：关注列未命中，使用全部数据
            StringBuilder fallback = new StringBuilder();
            for (Object value : rowData.values()) {
                if (value != null) {
                    if (fallback.length() > 0) fallback.append("\n");
                    fallback.append(value.toString());
                }
            }
            return fallback.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            for (Object value : rowData.values()) {
                if (value != null) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(value.toString());
                }
            }
            return sb.toString();
        }
    }

    private String normalizeColumnName(String columnName) {
        if (columnName == null) {
            return "";
        }
        return columnName.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * 处理提取类型标签
     * 支持预处理模式和二次强化分析
     * 如果没有定义 extractFields，使用自由提取模式
     */
    private void processExtractionLabel(Integer taskId, DataRow dataRow, AnalysisTaskLabel taskLabel,
                                         Label label, String labelKey,
                                         ModelConfigService.LLMRuntimeConfig runtimeConfig,
                                         BigDecimal confidenceThreshold) {
        Map<String, Object> rowData = dataRow.getOriginalData();

        // 检查是否为纯规则模式
        if (label.isRuleOnlyMode()) {
            // 纯规则模式：使用规则提取器提取，不调用 LLM
            processExtractionRuleOnlyMode(taskId, dataRow, taskLabel, label, labelKey, confidenceThreshold);
            return;
        }

        boolean isStructuredExtraction = label.getExtractFields() != null && !label.getExtractFields().isEmpty();

        // 预处理：如果启用了混合模式，先执行规则提取来丰富 LLM 上下文
        String preprocessorResult = null;
        List<com.datalabeling.service.extraction.ExtractedNumber> extractedNumbers = null;

        if (label.isHybridMode()) {
            try {
                preprocessorResult = executePreprocessing(label, rowData);
            } catch (Exception e) {
                log.warn("预处理失败，继续使用普通模式: {}", e.getMessage());
            }
        }

        // 调用 LLM 提取
        Map<String, Object> extractResult;

        if (isStructuredExtraction) {
            // 有定义提取字段，使用结构化提取
            extractResult = deepSeekService.extract(label, rowData, runtimeConfig);
        } else {
            // 没有定义提取字段，使用自由提取模式
            extractResult = deepSeekService.extractFreeForm(label, rowData, runtimeConfig);
        }

        Boolean success = (Boolean) extractResult.get("success");

        // 处理结果：结构化提取和自由提取返回格式不同
        String summary;
        String extractedDataJson = null;

        if (isStructuredExtraction) {
            // 结构化提取返回 summary 和 extractedData
            summary = (String) extractResult.get("summary");
            @SuppressWarnings("unchecked")
            Map<String, Object> extractedData = (Map<String, Object>) extractResult.get("extractedData");
            if (extractedData != null) {
                try {
                    extractedDataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extractedData);
                } catch (Exception e) {
                    log.warn("序列化提取数据失败: {}", e.getMessage());
                }
            }
        } else {
            // 自由提取返回 result
            summary = (String) extractResult.get("result");
        }

        int confidenceInt = 0;
        Object confObj = extractResult.get("confidence");
        if (confObj instanceof Number) {
            confidenceInt = ((Number) confObj).intValue();
        }

        // 提取 AI 分析原因
        String reasoning = (String) extractResult.get("reasoning");

        // 二次强化分析
        if (label.isEnhancementEnabled() && Boolean.TRUE.equals(success)) {
            try {
                com.datalabeling.dto.EnhancementConfig enhancementConfig =
                    com.datalabeling.dto.EnhancementConfig.fromJson(label.getEnhancementConfig());

                EnhancementService.EnhancementResult enhancementResult = enhancementService.enhance(
                    label,
                    rowData,
                    summary,  // 对于提取类型，使用 summary 作为初始结果
                    confidenceInt,
                    reasoning != null ? reasoning : "",
                    preprocessorResult != null ? preprocessorResult : null,
                    enhancementConfig
                );

                // 应用强化结果（调整置信度和推理）
                confidenceInt = enhancementResult.getFinalConfidence();

                // 合并推理信息
                String enhancedReasoning = reasoning;
                if (enhancementResult.getValidationNotes() != null && !enhancementResult.getValidationNotes().isEmpty()) {
                    enhancedReasoning = reasoning + "\n[二次强化] " + enhancementResult.getValidationNotes();
                }
                reasoning = enhancedReasoning;

            } catch (Exception e) {
                log.warn("强化分析失败，使用原始结果: {}", e.getMessage());
            }
        }

        BigDecimal confidence = new BigDecimal(confidenceInt).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);

        if (Boolean.TRUE.equals(success)) {
            // 保存成功结果（包含 reasoning）
            saveLabelResult(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey,
                summary, confidence, reasoning, confidenceThreshold, Label.Type.EXTRACTION, extractedDataJson);
        } else {
            // 保存失败结果
            String error = (String) extractResult.get("error");
            saveLabelResultFailed(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey,
                error != null ? error : "提取失败", Label.Type.EXTRACTION);
        }
    }

    /**
     * 纯规则模式处理提取标签（不调用 LLM）
     * 使用规则提取器直接提取数据
     */
    private void processExtractionRuleOnlyMode(Integer taskId, DataRow dataRow, AnalysisTaskLabel taskLabel,
                                               Label label, String labelKey,
                                               BigDecimal confidenceThreshold) {
        Map<String, Object> rowData = dataRow.getOriginalData();

        try {
            // number_intent：显式意图优先（规则闭环输出）
            com.datalabeling.dto.PreprocessorConfig cfg =
                com.datalabeling.dto.PreprocessorConfig.fromJson(label.getPreprocessorConfig());
            if (cfg.getNumberIntent() != null) {
                String text = getTargetText(label, rowData);
                com.datalabeling.service.extraction.NumberIntentEvaluator.EvaluationResult r =
                    numberIntentEvaluator.evaluate(text, cfg.getNumberIntent());
                if (r.isCanHandle()) {
                    String summary = r.getSummary();
                    BigDecimal confidence = new BigDecimal(r.getConfidence())
                        .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
                    String reasoning = r.getReasoning();

                    String extractedDataJson = null;
                    try {
                        extractedDataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(r.getExtractedData());
                    } catch (Exception e) {
                        log.warn("序列化提取数据失败: {}", e.getMessage());
                    }

                    saveLabelResult(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey,
                        summary, confidence, reasoning, confidenceThreshold, Label.Type.EXTRACTION, extractedDataJson);
                    return;
                }
            }

            // 执行规则提取
            String preprocessorResult = executePreprocessing(label, rowData);

            // 将提取结果转换为提取数据格式
            boolean hasResult = preprocessorResult != null && !preprocessorResult.isEmpty()
                && !preprocessorResult.contains("未提取到");

            Map<String, Object> extractedData = new java.util.HashMap<>();
            extractedData.put("extraction_result", preprocessorResult != null ? preprocessorResult : "未提取到内容");

            String summary = hasResult ? preprocessorResult : "未提取到匹配内容";
            BigDecimal confidence = hasResult ? new BigDecimal("0.95") : new BigDecimal("0.00");
            String reasoning = hasResult ? preprocessorResult : "规则提取未找到匹配内容";

            // 序列化提取数据
            String extractedDataJson = null;
            try {
                extractedDataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extractedData);
            } catch (Exception e) {
                log.warn("序列化提取数据失败: {}", e.getMessage());
            }

            // 保存标签结果
            saveLabelResult(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey,
                summary, confidence, reasoning, confidenceThreshold, Label.Type.EXTRACTION, extractedDataJson);

        } catch (Exception e) {
            log.error("规则模式处理失败: {}", e.getMessage(), e);
            saveLabelResultFailed(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey,
                "处理失败: " + e.getMessage(), Label.Type.EXTRACTION);
        }
    }

    /**
     * 处理结构化提取类型标签（使用规则提取器）
     * 支持身份证号、银行卡号、手机号的结构化提取
     * 支持二次强化分析（用 LLM 验证规则提取的质量）
     */
    private void processStructuredExtractionLabel(Integer taskId, DataRow dataRow, AnalysisTaskLabel taskLabel,
                                                  Label label, String labelKey, BigDecimal confidenceThreshold) {
        try {
            Map<String, Object> rowData = dataRow.getOriginalData();

            // 调用提取器协调服务（传入用户设置的信心度阈值）
            float threshold = confidenceThreshold != null ? confidenceThreshold.floatValue() : 0.80f;
            Map<String, Object> extractResult = extractionOrchestrator.extract(label, rowData, threshold);

            Boolean success = (Boolean) extractResult.get("success");

            if (Boolean.TRUE.equals(success)) {
                // 提取成功
                @SuppressWarnings("unchecked")
                Map<String, Object> extractedData = (Map<String, Object>) extractResult.get("extractedData");

                // 生成摘要
                String summary = generateExtractionSummary(extractedData);

                // 转换为JSON
                String extractedDataJson = null;
                if (extractedData != null) {
                    try {
                        extractedDataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extractedData);
                    } catch (Exception e) {
                        log.warn("序列化提取数据失败: {}", e.getMessage());
                    }
                }

                // 获取置信度和推理过程
                int confidenceInt = 0;
                Object confObj = extractResult.get("confidence");
                if (confObj instanceof Number) {
                    confidenceInt = ((Number) confObj).intValue();
                }
                String reasoning = (String) extractResult.get("reasoning");

                // 二次强化分析：用 LLM 验证规则提取的质量
                if (label.isEnhancementEnabled()) {
                    try {
                        com.datalabeling.dto.EnhancementConfig enhancementConfig =
                            com.datalabeling.dto.EnhancementConfig.fromJson(label.getEnhancementConfig());

                        EnhancementService.EnhancementResult enhancementResult = enhancementService.enhance(
                            label,
                            rowData,
                            summary,  // 使用规则提取的摘要作为初始结果
                            confidenceInt,
                            reasoning != null ? reasoning : "",
                            summary,  // 对于结构化提取，将提取结果作为验证上下文
                            enhancementConfig
                        );

                        // 应用强化结果（调整置信度和推理）
                        confidenceInt = enhancementResult.getFinalConfidence();

                        // 合并推理信息
                        String enhancedReasoning = reasoning;
                        if (enhancementResult.getValidationNotes() != null && !enhancementResult.getValidationNotes().isEmpty()) {
                            enhancedReasoning = reasoning + "\n[二次强化] " + enhancementResult.getValidationNotes();
                        }
                        reasoning = enhancedReasoning;

                    } catch (Exception e) {
                        log.warn("强化分析失败，使用原始结果: {}", e.getMessage());
                    }
                }

                BigDecimal confidence = new BigDecimal(confidenceInt).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);

                // 保存成功结果
                saveLabelResult(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey,
                    summary, confidence, reasoning, confidenceThreshold, Label.Type.STRUCTURED_EXTRACTION, extractedDataJson);

            } else {
                // 保存失败结果
                String error = (String) extractResult.get("error");
                saveLabelResultFailed(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey,
                    error != null ? error : "提取失败", Label.Type.STRUCTURED_EXTRACTION);
            }
        } catch (Exception e) {
            log.error("结构化提取失败: taskId={}, rowIndex={}, label={}, error={}",
                taskId, dataRow.getRowIndex(), labelKey, e.getMessage());
            saveLabelResultFailed(taskId, dataRow.getId(), taskLabel.getLabelId(), labelKey,
                "提取异常: " + e.getMessage(), Label.Type.STRUCTURED_EXTRACTION);
        }
    }

    /**
     * 生成提取结果摘要
     */
    private String generateExtractionSummary(Map<String, Object> extractedData) {
        if (extractedData == null || extractedData.isEmpty()) {
            return "未提取到信息";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;

        for (Map.Entry<String, Object> entry : extractedData.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> values = (List<String>) value;
                if (!values.isEmpty()) {
                    if (count > 0) {
                        sb.append("; ");
                    }
                    sb.append(entry.getKey()).append(": ").append(String.join(", ", values));
                    count++;
                }
            }
        }

        return sb.length() > 0 ? sb.toString() : "未提取到信息";
    }

    /**
     * 获取运行时模型配置
     */
    private ModelConfigService.LLMRuntimeConfig getRuntimeConfig(Integer modelConfigId) {
        try {
            if (modelConfigId != null) {
                ModelConfigVO configVO = modelConfigService.getConfigById(modelConfigId);
                return modelConfigService.toRuntimeConfig(configVO);
            } else {
                return modelConfigService.getDeepSeekRuntimeConfig();
            }
        } catch (Exception e) {
            log.error("获取模型配置失败", e);
            return null;
        }
    }

    /**
     * 保存标签分析结果（先查后存，避免重复插入）
     */
    @Transactional
    public synchronized void saveLabelResult(Integer taskId, Long dataRowId, Integer labelId, String labelKey,
                                 String result, BigDecimal confidence, String reasoning,
                                 BigDecimal confidenceThreshold, String labelType, String extractedData) {
        // 截断过长的 reasoning（数据库字段限制为500字符）
        String truncatedReasoning = truncateReasoning(reasoning);

        // 先检查是否已存在
        LabelResult existing = labelResultRepository
            .findByAnalysisTaskIdAndDataRowIdAndLabelId(taskId, dataRowId, labelId)
            .orElse(null);

        LabelResult labelResult;
        if (existing != null) {
            // 已存在，更新记录
            labelResult = existing;
            labelResult.setLabelKey(labelKey);
            labelResult.setResult(truncateResult(result));
            labelResult.setAiConfidence(confidence);
            labelResult.setAiReasoning(truncatedReasoning);
            labelResult.setConfidenceThreshold(confidenceThreshold);
            labelResult.setLabelType(labelType);
            labelResult.setExtractedData(extractedData);
            labelResult.setProcessingStatus(LabelResult.ProcessingStatus.SUCCESS);
            labelResult.setErrorMessage(null);
            log.debug("更新已存在的标签结果: taskId={}, dataRowId={}, labelId={}", taskId, dataRowId, labelId);
        } else {
            // 不存在，创建新记录
            labelResult = LabelResult.builder()
                .analysisTaskId(taskId)
                .dataRowId(dataRowId)
                .labelId(labelId)
                .labelKey(labelKey)
                .result(truncateResult(result))
                .aiConfidence(confidence)
                .aiReasoning(truncatedReasoning)
                .confidenceThreshold(confidenceThreshold)
                .labelType(labelType)
                .extractedData(extractedData)
                .processingStatus(LabelResult.ProcessingStatus.SUCCESS)
                .build();
        }

        // 判断是否需要审核
        labelResult.updateNeedsReview();

        // 使用 saveAndFlush 确保数据立即写入数据库（因为@Transactional在内部调用时不生效）
        labelResultRepository.saveAndFlush(labelResult);
    }

    /**
     * 截断 reasoning 到最大长度（数据库字段限制为500字符）
     */
    private String truncateReasoning(String reasoning) {
        if (reasoning == null) {
            return null;
        }
        // 限制为495字符，保留5字符给截断标记（如果需要）
        if (reasoning.length() > 495) {
            return reasoning.substring(0, 495) + "...[截断]";
        }
        return reasoning;
    }

    /**
     * 截断 result 到最大长度（数据库字段限制为500字符）
     */
    private String truncateResult(String result) {
        if (result == null) {
            return null;
        }
        if (result.length() > 495) {
            return result.substring(0, 495) + "...[截断]";
        }
        return result;
    }

    /**
     * 保存失败的标签结果（先查后存，避免重复插入）
     */
    @Transactional
    public synchronized void saveLabelResultFailed(Integer taskId, Long dataRowId, Integer labelId,
                                       String labelKey, String errorMessage, String labelType) {
        // 先检查是否已存在
        LabelResult existing = labelResultRepository
            .findByAnalysisTaskIdAndDataRowIdAndLabelId(taskId, dataRowId, labelId)
            .orElse(null);

        // 失败结果：分类标签为"否"，提取标签为"提取失败"
        String failResult = Label.Type.EXTRACTION.equals(labelType) ? "提取失败" : "否";

        LabelResult labelResult;
        if (existing != null) {
            // 已存在，更新记录
            labelResult = existing;
            labelResult.setLabelKey(labelKey);
            labelResult.setResult(failResult);
            labelResult.setLabelType(labelType);
            labelResult.setProcessingStatus(LabelResult.ProcessingStatus.FAILED);
            labelResult.setErrorMessage(errorMessage);
            labelResult.setNeedsReview(true);
            log.debug("更新已存在的失败标签结果: taskId={}, dataRowId={}, labelId={}", taskId, dataRowId, labelId);
        } else {
            // 不存在，创建新记录
            labelResult = LabelResult.builder()
                .analysisTaskId(taskId)
                .dataRowId(dataRowId)
                .labelId(labelId)
                .labelKey(labelKey)
                .result(failResult)
                .labelType(labelType)
                .processingStatus(LabelResult.ProcessingStatus.FAILED)
                .errorMessage(errorMessage)
                .needsReview(true)
                .build();
        }

        // 使用 saveAndFlush 确保数据立即写入数据库
        labelResultRepository.saveAndFlush(labelResult);
    }

    /**
     * 更新任务进度
     */
    @Transactional
    public synchronized void updateTaskProgress(Integer taskId, int processedRows, int successRows, int failedRows) {
        analysisTaskRepository.findById(taskId).ifPresent(task -> {
            task.setProcessedRows(processedRows);
            task.setSuccessRows(successRows);
            task.setFailedRows(failedRows);
            task.setLastActivityAt(LocalDateTime.now());
            analysisTaskRepository.saveAndFlush(task);
        });
    }

    /**
     * 完成任务
     */
    @Transactional
    public synchronized void completeTask(Integer taskId, int processedRows, int successRows, int failedRows) {
        analysisTaskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(AnalysisTask.Status.COMPLETED);
            task.setProcessedRows(processedRows);
            task.setSuccessRows(successRows);
            task.setFailedRows(failedRows);
            task.setCompletedAt(LocalDateTime.now());
            task.setLastActivityAt(LocalDateTime.now());
            analysisTaskRepository.saveAndFlush(task);
        });

        pushProgress(taskId, processedRows, processedRows, successRows, failedRows,
            processedRows, AnalysisTask.Status.COMPLETED);
    }

    /**
     * 失败任务
     */
    @Transactional
    public void failTask(AnalysisTask task, String errorMessage) {
        task.setStatus(AnalysisTask.Status.FAILED);
        task.setErrorMessage(errorMessage);
        task.setCompletedAt(LocalDateTime.now());
        task.setLastActivityAt(LocalDateTime.now());
        analysisTaskRepository.saveAndFlush(task);

        logTask(task.getId(), TaskExecutionLog.LogLevel.ERROR, "任务失败: " + errorMessage);

        pushProgress(task.getId(), task.getTotalRows(), task.getProcessedRows(),
            task.getSuccessRows(), task.getFailedRows(), 0, AnalysisTask.Status.FAILED);
    }

    /**
     * 记录任务日志
     */
    private void logTask(Integer taskId, String level, String message) {
        try {
            TaskExecutionLog logEntry = TaskExecutionLog.taskLog(taskId, level, message);
            taskExecutionLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("记录任务日志失败", e);
        }
    }

    /**
     * 推送进度到前端
     */
    private void pushProgress(Integer taskId, int totalRows, int processedRows,
                               int successRows, int failedRows, int currentRow, String status) {
        try {
            Map<String, Object> progress = new HashMap<>();
            progress.put("taskId", taskId);
            progress.put("total", totalRows);
            progress.put("processed", processedRows);
            progress.put("success", successRows);
            progress.put("failed", failedRows);
            progress.put("percentage", totalRows > 0 ? (int) Math.round(processedRows * 100.0 / totalRows) : 0);
            progress.put("currentRow", currentRow);
            progress.put("status", status);
            progress.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/analysis-tasks/" + taskId + "/progress", progress);
        } catch (Exception e) {
            log.warn("推送进度失败", e);
        }
    }

    /**
     * 构建标签键
     */
    private String buildLabelKey(String labelName, Integer version) {
        return labelName + "_v" + version;
    }
}
