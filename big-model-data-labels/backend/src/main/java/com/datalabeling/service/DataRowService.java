package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.mapper.DataRowMapper;
import com.datalabeling.dto.request.BatchUpdateDataRowsRequest;
import com.datalabeling.dto.request.UpdateDataRowRequest;
import com.datalabeling.dto.response.DataRowVO;
import com.datalabeling.entity.DataRow;
import com.datalabeling.entity.FileTask;
import com.datalabeling.entity.TaskLabel;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.DataRowRepository;
import com.datalabeling.repository.FileTaskRepository;
import com.datalabeling.repository.TaskLabelRepository;
import com.datalabeling.service.constant.TaskStatus;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据行服务
 */
@Service
@RequiredArgsConstructor
public class DataRowService {

    private final FileTaskRepository fileTaskRepository;
    private final DataRowRepository dataRowRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final DataRowMapper dataRowMapper;
    private final SecurityUtil securityUtil;
    private final AuditService auditService;

    public PageResult<DataRowVO> getRows(Integer taskId, Integer page, Integer size, HttpServletRequest httpRequest) {
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
        auditDetails.put("page", page);
        auditDetails.put("size", size);
        auditService.recordAdminRead(task.getUserId(), "admin_read_task_rows", "task", taskId, auditDetails, httpRequest);

        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.ASC, "rowIndex"));
        Page<DataRow> dataPage = dataRowRepository.findByTaskId(taskId, pageable);

        List<DataRowVO> items = dataPage.map(dataRowMapper::toVO).getContent();
        return PageResult.of(items, dataPage.getTotalElements(), dataPage.getNumber() + 1, dataPage.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRow(Integer taskId, Long rowId, UpdateDataRowRequest request, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (TaskStatus.ARCHIVED.equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID, "任务已归档，禁止修改结果");
        }

        DataRow dataRow = dataRowRepository.findById(rowId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DATA_ROW_NOT_FOUND));
        if (!taskId.equals(dataRow.getTaskId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "数据行不属于该任务");
        }

        Set<String> allowedKeys = buildAllowedLabelKeys(taskId);
        Map<String, Object> next = mergeLabelResults(dataRow.getLabelResults(),
            validateAndNormalizeLabelResults(request.getLabelResults(), allowedKeys));
        dataRow.setLabelResults(next);
        dataRow.setIsModified(true);
        dataRowRepository.save(dataRow);

        Map<String, Object> details = new HashMap<>();
        details.put("taskId", taskId);
        details.put("rowId", rowId);
        auditService.record("update_row", "data_row", null, details, httpRequest);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRowsBatch(Integer taskId, BatchUpdateDataRowsRequest request, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (TaskStatus.ARCHIVED.equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID, "任务已归档，禁止修改结果");
        }

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "更新项不能为空");
        }

        Set<Long> rowIdSet = new HashSet<>();
        Map<Long, Map<String, Object>> updates = new HashMap<>();
        for (BatchUpdateDataRowsRequest.Item item : request.getItems()) {
            if (item == null || item.getRowId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "rowId 不能为空");
            }
            if (!rowIdSet.add(item.getRowId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "rowId 重复: " + item.getRowId());
            }
            updates.put(item.getRowId(), item.getLabelResults());
        }

        List<DataRow> dataRows = dataRowRepository.findAllById(rowIdSet);
        if (dataRows.size() != rowIdSet.size()) {
            Set<Long> found = dataRows.stream().map(DataRow::getId).collect(Collectors.toSet());
            List<Long> missing = new ArrayList<>();
            for (Long id : rowIdSet) {
                if (!found.contains(id)) {
                    missing.add(id);
                }
            }
            throw new BusinessException(ErrorCode.DATA_ROW_NOT_FOUND, "数据行不存在: " + missing);
        }

        Set<String> allowedKeys = buildAllowedLabelKeys(taskId);

        for (DataRow dr : dataRows) {
            if (!Objects.equals(taskId, dr.getTaskId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "数据行不属于该任务: rowId=" + dr.getId());
            }

            Map<String, Object> normalized = validateAndNormalizeLabelResults(updates.get(dr.getId()), allowedKeys);
            dr.setLabelResults(mergeLabelResults(dr.getLabelResults(), normalized));
            dr.setIsModified(true);
        }
        dataRowRepository.saveAll(dataRows);

        List<Long> rowIds = new ArrayList<>(rowIdSet);
        Collections.sort(rowIds);
        Map<String, Object> details = new HashMap<>();
        details.put("taskId", taskId);
        details.put("count", rowIds.size());
        if (rowIds.size() <= 50) {
            details.put("rowIds", rowIds);
        } else {
            details.put("rowIds", rowIds.subList(0, 50));
            details.put("rowIdsTruncated", true);
        }
        auditService.record("update_rows_batch", "task", taskId, details, httpRequest);
    }

    private Set<String> buildAllowedLabelKeys(Integer taskId) {
        Set<String> allowedKeys = new HashSet<>();
        List<TaskLabel> taskLabels = taskLabelRepository.findByTaskIdOrderByIdAsc(taskId);
        for (TaskLabel tl : taskLabels) {
            allowedKeys.add(buildLabelKey(tl.getLabelName(), tl.getLabelVersion()));
        }
        return allowedKeys;
    }

    private Map<String, Object> validateAndNormalizeLabelResults(Map<String, Object> labelResults, Set<String> allowedKeys) {
        if (labelResults == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "标签结果不能为空");
        }

        Map<String, Object> normalized = new HashMap<>();
        for (Map.Entry<String, Object> entry : labelResults.entrySet()) {
            String key = entry.getKey();
            if (!allowedKeys.contains(key)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "非法标签列: " + key);
            }
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()).trim() : "";
            if (!"是".equals(value) && !"否".equals(value)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "标签结果仅允许“是/否”");
            }
            normalized.put(key, value);
        }
        return normalized;
    }

    private Map<String, Object> mergeLabelResults(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> merged = new HashMap<>();
        if (existing != null) {
            merged.putAll(existing);
        }
        if (incoming != null) {
            merged.putAll(incoming);
        }
        return merged;
    }

    private String buildLabelKey(String labelName, Integer version) {
        return labelName + "_v" + version;
    }
}
