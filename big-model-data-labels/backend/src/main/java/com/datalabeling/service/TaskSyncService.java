package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.dto.request.SyncToDbRequest;
import com.datalabeling.entity.DataRow;
import com.datalabeling.entity.FileTask;
import com.datalabeling.entity.SyncConfig;
import com.datalabeling.entity.TaskLabel;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.DataRowRepository;
import com.datalabeling.repository.FileTaskRepository;
import com.datalabeling.repository.SyncConfigRepository;
import com.datalabeling.repository.TaskLabelRepository;
import com.datalabeling.service.constant.TaskStatus;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.util.*;

/**
 * 任务同步到外部数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSyncService {

    private final FileTaskRepository fileTaskRepository;
    private final DataRowRepository dataRowRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final SyncConfigRepository syncConfigRepository;
    private final ExternalDbService externalDbService;
    private final SecurityUtil securityUtil;
    private final AuditService auditService;

    @Transactional(rollbackFor = Exception.class)
    public void sync(Integer taskId, SyncToDbRequest request, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (!TaskStatus.ARCHIVED.equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID, "仅归档后允许同步到外部数据库");
        }

        SyncConfig config = syncConfigRepository.findById(request.getSyncConfigId())
            .orElseThrow(() -> new BusinessException(ErrorCode.SYNC_CONFIG_NOT_FOUND));
        if (!securityUtil.hasPermission(config.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Map<String, String> fieldMappings = request.getFieldMappings();
        if (fieldMappings == null || fieldMappings.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "字段映射不能为空");
        }

        // 构建可用的文件列（原始列 + 标签列）
        Set<String> fileColumns = new HashSet<>();
        dataRowRepository.findFirstByTaskIdOrderByRowIndexAsc(taskId)
            .ifPresent(first -> {
                if (first.getOriginalData() != null) {
                    fileColumns.addAll(first.getOriginalData().keySet());
                }
            });
        List<TaskLabel> taskLabels = taskLabelRepository.findByTaskIdOrderByIdAsc(taskId);
        for (TaskLabel tl : taskLabels) {
            fileColumns.add(buildLabelKey(tl.getLabelName(), tl.getLabelVersion()));
        }

        // 读取目标表字段
        Set<String> dbColumns = new HashSet<>();
        externalDbService.getTableSchema(config).forEach(c -> dbColumns.add(c.getName()));

        // 校验映射合法性：file列存在、db字段存在、db字段不重复
        Set<String> usedDbColumns = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : fieldMappings.entrySet()) {
            String fileCol = e.getKey();
            String dbCol = e.getValue();
            if (fileCol == null || fileCol.trim().isEmpty() || dbCol == null || dbCol.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "字段映射存在空值");
            }
            if (!fileColumns.contains(fileCol)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "文件列不存在: " + fileCol);
            }
            if (!dbColumns.contains(dbCol)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "目标表字段不存在: " + dbCol);
            }
            if (!usedDbColumns.add(dbCol)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "目标表字段重复映射: " + dbCol);
            }
        }

        // 持久化本次使用的映射（便于复用）
        Map<String, Object> mappingsToSave = new HashMap<>();
        mappingsToSave.putAll(fieldMappings);
        config.setFieldMappings(mappingsToSave);
        syncConfigRepository.save(config);

        // 同步策略：MVP 仅支持 insert
        String strategy = request.getStrategy() != null ? request.getStrategy().trim().toLowerCase(Locale.ROOT) : "insert";
        if (!"insert".equals(strategy)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "暂仅支持 insert 同步策略");
        }

        // 构建 SQL
        List<String> targetColumns = new ArrayList<>(usedDbColumns);
        try (Connection conn = externalDbService.openConnection(config)) {
            conn.setAutoCommit(false);

            DatabaseMetaData meta = conn.getMetaData();
            String quote = meta.getIdentifierQuoteString();
            String quotedTable = quoteQualified(quote, config.getTableName());

            StringBuilder sb = new StringBuilder();
            sb.append("INSERT INTO ").append(quotedTable).append(" (");
            for (int i = 0; i < targetColumns.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(quoteIdentifier(quote, targetColumns.get(i)));
            }
            sb.append(") VALUES (");
            for (int i = 0; i < targetColumns.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append("?");
            }
            sb.append(")");

            Map<String, String> dbToFile = invertMapping(fieldMappings);

            try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                int page = 0;
                int size = 500;
                int batch = 0;
                while (true) {
                    Page<DataRow> dataPage = dataRowRepository.findByTaskId(
                        taskId, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "rowIndex"))
                    );
                    for (DataRow row : dataPage.getContent()) {
                        Map<String, Object> original = row.getOriginalData();
                        Map<String, Object> labels = row.getLabelResults();

                        for (int i = 0; i < targetColumns.size(); i++) {
                            String dbCol = targetColumns.get(i);
                            String fileCol = dbToFile.get(dbCol);
                            String value = extractValue(fileCol, original, labels);
                            ps.setString(i + 1, value);
                        }
                        ps.addBatch();
                        batch++;
                        if (batch >= 500) {
                            ps.executeBatch();
                            conn.commit();
                            batch = 0;
                        }
                    }
                    if (!dataPage.hasNext()) {
                        break;
                    }
                    page++;
                }
                if (batch > 0) {
                    ps.executeBatch();
                    conn.commit();
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("同步失败: taskId={}, {}", taskId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYNC_FAILED, "同步失败: " + e.getMessage());
        }

        Map<String, Object> details = new HashMap<>();
        details.put("syncConfigId", request.getSyncConfigId());
        details.put("strategy", strategy);
        auditService.record("sync_to_db", "task", taskId, details, httpRequest);
    }

    private Map<String, String> invertMapping(Map<String, String> fileToDb) {
        Map<String, String> dbToFile = new HashMap<>();
        for (Map.Entry<String, String> e : fileToDb.entrySet()) {
            dbToFile.put(e.getValue(), e.getKey());
        }
        return dbToFile;
    }

    private String extractValue(String fileCol, Map<String, Object> original, Map<String, Object> labels) {
        if (fileCol == null) {
            return "";
        }
        if (original != null && original.containsKey(fileCol) && original.get(fileCol) != null) {
            return String.valueOf(original.get(fileCol));
        }
        if (labels != null && labels.containsKey(fileCol) && labels.get(fileCol) != null) {
            return String.valueOf(labels.get(fileCol));
        }
        return "";
    }

    private String buildLabelKey(String labelName, Integer version) {
        return labelName + "_v" + version;
    }

    private String quoteIdentifier(String quote, String identifier) {
        String q = quote != null ? quote.trim() : "";
        if (q.isEmpty()) {
            return identifier;
        }
        String trimmed = identifier.trim();
        return q + trimmed + q;
    }

    private String quoteQualified(String quote, String qualified) {
        String[] parts = qualified.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(".");
            }
            sb.append(quoteIdentifier(quote, parts[i]));
        }
        return sb.toString();
    }
}
