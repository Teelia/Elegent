package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.mapper.DatasetMapper;
import com.datalabeling.dto.response.DatasetVO;
import com.datalabeling.entity.AnalysisTask;
import com.datalabeling.entity.DataRow;
import com.datalabeling.entity.Dataset;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.AnalysisTaskRepository;
import com.datalabeling.repository.DataRowRepository;
import com.datalabeling.repository.DatasetRepository;
import com.datalabeling.repository.LabelRepository;
import com.datalabeling.service.model.FilePreviewResult;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 数据集服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DataRowRepository dataRowRepository;
    private final AnalysisTaskRepository analysisTaskRepository;
    private final LabelRepository labelRepository;
    private final FileParseService fileParseService;
    private final DatasetMapper datasetMapper;
    private final SecurityUtil securityUtil;
    private final TransactionTemplate transactionTemplate;

    /**
     * 批量插入数据行的批次大小
     */
    private static final int BATCH_SIZE = 100;

    /**
     * 死锁重试最大次数
     */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * 重试延迟基础时间（毫秒）
     */
    private static final long RETRY_DELAY_BASE_MS = 100;

    /**
     * 获取数据集列表（分页）
     */
    public PageResult<DatasetVO> getDatasets(Integer page, Integer size, String status, String search) {
        Integer userId = securityUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Dataset> datasetPage;
        if (status != null && !status.isEmpty() && search != null && !search.isEmpty()) {
            datasetPage = datasetRepository.findByUserIdAndStatusAndNameContaining(userId, status, search, pageable);
        } else if (status != null && !status.isEmpty()) {
            datasetPage = datasetRepository.findByUserIdAndStatus(userId, status, pageable);
        } else if (search != null && !search.isEmpty()) {
            datasetPage = datasetRepository.findByUserIdAndNameContaining(userId, search, pageable);
        } else {
            datasetPage = datasetRepository.findByUserId(userId, pageable);
        }

        List<DatasetVO> voList = datasetPage.getContent().stream()
                .map(this::enrichDatasetVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, datasetPage.getTotalElements(), page, size);
    }
    
    /**
     * 获取数据集详情
     */
    public DatasetVO getDataset(Integer id) {
        Integer userId = securityUtil.getCurrentUserId();
        Dataset dataset = datasetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        return enrichDatasetVO(dataset);
    }

    /**
     * 查询数据集导入进度
     *
     * @param id 数据集ID
     * @return 导入进度信息
     */
    public com.datalabeling.controller.DatasetController.ImportProgressVO getImportProgress(Integer id) {
        Integer userId = securityUtil.getCurrentUserId();
        Dataset dataset = datasetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));

        String status = dataset.getStatus();
        Integer totalRows = dataset.getTotalRows();
        String message;

        switch (status) {
            case Dataset.Status.IMPORTING:
                message = String.format("正在导入数据，已完成 %d 行", totalRows);
                break;
            case Dataset.Status.UPLOADED:
                message = String.format("导入完成，共导入 %d 行数据", totalRows);
                break;
            case Dataset.Status.FAILED:
                message = "导入失败，请检查数据源配置和表名";
                break;
            default:
                message = "未知状态";
                break;
        }

        return new com.datalabeling.controller.DatasetController.ImportProgressVO(
                id, status, totalRows, message
        );
    }
    
    /**
     * 上传文件创建数据集
     * 直接解析文件内容存入数据库，不保存原始文件
     */
    public DatasetVO uploadDataset(MultipartFile file, String name, String description) {
        Integer userId = securityUtil.getCurrentUserId();
        String originalFilename = file.getOriginalFilename();

        // 解析文件获取列信息和所有数据行
        FilePreviewResult parseResult = fileParseService.parseUploadedFile(file, 20);

        // 数据集名称：优先使用用户指定的名称，否则使用原始文件名
        String datasetName = (name != null && !name.trim().isEmpty())
                ? name.trim()
                : (originalFilename != null ? originalFilename : "未命名数据集");

        // 第一步：创建数据集记录
        Dataset dataset = Dataset.builder()
                .userId(userId)
                .name(datasetName)
                .originalFilename(originalFilename)
                .columns(parseResult.getColumnsInfo())
                .totalRows(parseResult.getTotalRows())
                .status(Dataset.Status.UPLOADED)
                .description(description)
                .build();

        Dataset savedDataset = datasetRepository.save(dataset);
        final Integer datasetId = savedDataset.getId();

        // 第二步：分批保存数据行
        List<DataRow> dataRows = parseResult.getDataRows();

        for (int i = 0; i < dataRows.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, dataRows.size());
            List<DataRow> batch = dataRows.subList(i, endIndex);
            List<DataRow> batchCopy = new ArrayList<>(batch.size());

            for (DataRow row : batch) {
                DataRow newRow = DataRow.builder()
                        .taskId(datasetId)
                        .rowIndex(row.getRowIndex())
                        .originalData(row.getOriginalData())
                        .processingStatus(row.getProcessingStatus())
                        .needsReview(row.getNeedsReview())
                        .isModified(row.getIsModified())
                        .build();
                batchCopy.add(newRow);
            }

            dataRowRepository.saveAll(batchCopy);

            // 每处理10批打印一次进度日志
            if ((i / BATCH_SIZE + 1) % 10 == 0) {
                log.info("数据集 {} 保存进度: {}/{} 行", datasetId, endIndex, dataRows.size());
            }
        }

        log.info("用户 {} 上传数据集成功: {}, 共 {} 行", userId, datasetName, savedDataset.getTotalRows());

        return enrichDatasetVO(savedDataset);
    }

    /**
     * 上传文件创建数据集（简化版，不指定名称）
     */
    public DatasetVO uploadDataset(MultipartFile file, String description) {
        return uploadDataset(file, null, description);
    }
    
    /**
     * 更新数据集描述
     */
    @Transactional
    public DatasetVO updateDataset(Integer id, String description) {
        Integer userId = securityUtil.getCurrentUserId();
        Dataset dataset = datasetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        dataset.setDescription(description);
        dataset = datasetRepository.save(dataset);
        
        return enrichDatasetVO(dataset);
    }
    
    /**
     * 删除数据集（包含死锁重试机制）
     */
    public void deleteDataset(Integer id) {
        Integer userId = securityUtil.getCurrentUserId();
        Dataset dataset = datasetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));

        // 检查是否有正在执行的任务
        if (analysisTaskRepository.hasActiveTasksByDatasetId(id)) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "数据集有正在执行的分析任务，无法删除");
        }

        // 使用重试机制删除
        deleteDatasetWithRetry(dataset);
    }

    /**
     * 带重试机制的数据集删除
     */
    private void deleteDatasetWithRetry(Dataset dataset) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                doDeleteDataset(dataset);
                return;
            } catch (CannotAcquireLockException | DeadlockLoserDataAccessException e) {
                lastException = e;
                attempts++;
                log.warn("数据集删除发生死锁，第{}次重试 (最多{}次): {}",
                        attempts, MAX_RETRY_ATTEMPTS, e.getMessage());

                if (attempts < MAX_RETRY_ATTEMPTS) {
                    try {
                        long delay = RETRY_DELAY_BASE_MS * (long) Math.pow(2, attempts - 1);
                        Thread.sleep(delay + (long) (Math.random() * delay));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除被中断");
                    }
                }
            }
        }

        log.error("数据集删除失败，已达到最大重试次数", lastException);
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除失败，请稍后重试");
    }

    /**
     * 实际执行数据集删除（使用编程式事务）
     */
    private void doDeleteDataset(Dataset dataset) {
        transactionTemplate.executeWithoutResult(status -> {
            // 删除关联的数据行
            dataRowRepository.deleteByTaskId(dataset.getId());

            // 删除数据集
            datasetRepository.delete(dataset);

            log.info("用户删除数据集: {}", dataset.getOriginalFilename());
        });
    }
    
    /**
     * 归档数据集
     */
    @Transactional
    public DatasetVO archiveDataset(Integer id) {
        Integer userId = securityUtil.getCurrentUserId();
        Dataset dataset = datasetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        dataset.setStatus(Dataset.Status.ARCHIVED);
        dataset = datasetRepository.save(dataset);
        
        return enrichDatasetVO(dataset);
    }
    
    /**
     * 获取数据集的数据行（分页）
     */
    public PageResult<DataRow> getDataRows(Integer datasetId, Integer page, Integer size) {
        return getDataRows(datasetId, page, size, null);
    }

    /**
     * 获取数据集的数据行（分页，支持关键词搜索）
     *
     * @param datasetId 数据集ID
     * @param page      页码
     * @param size      每页大小
     * @param keyword   搜索关键词（可选，在所有列中搜索）
     * @return 数据行分页结果
     */
    public PageResult<DataRow> getDataRows(Integer datasetId, Integer page, Integer size, String keyword) {
        Integer userId = securityUtil.getCurrentUserId();

        // 验证数据集归属
        datasetRepository.findByIdAndUserId(datasetId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));

        Page<DataRow> rowPage;

        if (keyword != null && !keyword.trim().isEmpty()) {
            // 使用关键词搜索 - native query 已包含 ORDER BY，使用不带排序的 Pageable
            Pageable pageable = PageRequest.of(page - 1, size);
            rowPage = dataRowRepository.searchByTaskIdAndKeyword(datasetId, keyword.trim(), pageable);
        } else {
            // 无关键词，返回所有数据 - JPA 方法会自动转换属性名到列名
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "rowIndex"));
            rowPage = dataRowRepository.findByTaskId(datasetId, pageable);
        }

        return PageResult.of(rowPage.getContent(), rowPage.getTotalElements(), page, size);
    }

    /**
     * 获取数据集的所有数据行（用于导出）
     */
    public List<DataRow> getAllDataRows(Integer datasetId) {
        Integer userId = securityUtil.getCurrentUserId();

        // 验证数据集归属
        Dataset dataset = datasetRepository.findByIdAndUserId(datasetId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));

        return dataRowRepository.findByTaskIdOrderByRowIndex(datasetId);
    }

    /**
     * 获取数据集（用于导出时获取列信息）
     */
    public Dataset getDatasetEntity(Integer datasetId) {
        Integer userId = securityUtil.getCurrentUserId();
        return datasetRepository.findByIdAndUserId(datasetId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
    }

    /**
     * 批量更新数据行
     *
     * @param datasetId 数据集ID
     * @param updates   更新列表
     */
    @Transactional
    public void batchUpdateDataRows(Integer datasetId, java.util.List<com.datalabeling.controller.DatasetController.DataRowUpdateRequest> updates) {
        Integer userId = securityUtil.getCurrentUserId();

        // 验证数据集归属
        datasetRepository.findByIdAndUserId(datasetId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));

        // 批量更新
        for (com.datalabeling.controller.DatasetController.DataRowUpdateRequest update : updates) {
            DataRow dataRow = dataRowRepository.findById(update.getRowId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据行不存在: " + update.getRowId()));

            // 验证数据行属于该数据集
            if (!dataRow.getTaskId().equals(datasetId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "数据行不属于该数据集");
            }

            // 合并更新（只更新提供的字段）
            java.util.Map<String, Object> currentData = dataRow.getOriginalData();
            if (currentData == null) {
                currentData = new java.util.HashMap<>();
            }
            currentData.putAll(update.getOriginalData());
            dataRow.setOriginalData(currentData);

            dataRowRepository.save(dataRow);
        }

        log.info("批量更新数据行完成: datasetId={}, 更新数量={}", datasetId, updates.size());
    }

    /**
     * 丰富数据集VO（添加统计信息）
     */
    private DatasetVO enrichDatasetVO(Dataset dataset) {
        // 获取任务数量
        int taskCount = (int) analysisTaskRepository.countByDatasetId(dataset.getId());
        
        // 获取最新任务状态和进度
        String latestTaskStatus = null;
        Integer latestTaskProgress = null;
        Optional<AnalysisTask> latestTask = analysisTaskRepository.findFirstByDatasetIdOrderByCreatedAtDesc(dataset.getId());
        if (latestTask.isPresent()) {
            AnalysisTask task = latestTask.get();
            latestTaskStatus = task.getStatus();
            latestTaskProgress = task.getPercentage();
        }
        
        // 获取数据集专属标签数量
        int datasetLabelCount = (int) labelRepository.countByDatasetId(dataset.getId());
        
        return datasetMapper.toVO(dataset, taskCount, latestTaskStatus, latestTaskProgress, datasetLabelCount);
    }
}