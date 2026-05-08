package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.dto.request.CreateSyncConfigRequest;
import com.datalabeling.dto.request.UpdateSyncConfigRequest;
import com.datalabeling.dto.response.SyncConfigVO;
import com.datalabeling.dto.response.TableSchemaVO;
import com.datalabeling.service.AuditService;
import com.datalabeling.service.SyncConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;

/**
 * 同步配置控制器
 */
@RestController
@RequestMapping("/sync-configs")
@RequiredArgsConstructor
@Validated
public class SyncConfigController {

    private final SyncConfigService syncConfigService;
    private final AuditService auditService;

    @GetMapping
    public ApiResponse<List<SyncConfigVO>> list(@RequestParam(required = false) Integer userId, HttpServletRequest httpRequest) {
        return ApiResponse.success(syncConfigService.list(userId, httpRequest));
    }

    @PostMapping
    public ApiResponse<SyncConfigVO> create(@Validated @RequestBody CreateSyncConfigRequest request,
                                           HttpServletRequest httpRequest) {
        SyncConfigVO vo = syncConfigService.create(request);
        HashMap<String, Object> details = new HashMap<>();
        details.put("name", vo.getName());
        details.put("dbType", vo.getDbType());
        auditService.record("create_sync_config", "sync_config", vo.getId(), details, httpRequest);
        return ApiResponse.success("创建成功", vo);
    }

    @PutMapping("/{id}")
    public ApiResponse<SyncConfigVO> update(@PathVariable("id") Integer id,
                                           @Validated @RequestBody UpdateSyncConfigRequest request,
                                           HttpServletRequest httpRequest) {
        SyncConfigVO vo = syncConfigService.update(id, request);
        HashMap<String, Object> details = new HashMap<>();
        details.put("name", vo.getName());
        details.put("dbType", vo.getDbType());
        auditService.record("update_sync_config", "sync_config", vo.getId(), details, httpRequest);
        return ApiResponse.success("更新成功", vo);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Integer id, HttpServletRequest httpRequest) {
        syncConfigService.delete(id);
        auditService.record("delete_sync_config", "sync_config", id, null, httpRequest);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/{id}/table-schema")
    public ApiResponse<TableSchemaVO> tableSchema(@PathVariable("id") Integer id, HttpServletRequest httpRequest) {
        TableSchemaVO vo = syncConfigService.getTableSchema(id);
        auditService.record("get_table_schema", "sync_config", id, null, httpRequest);
        return ApiResponse.success(vo);
    }
}

