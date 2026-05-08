package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.dto.request.SyncToDbRequest;
import com.datalabeling.service.TaskSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 任务同步控制器
 */
@RestController
@RequestMapping("/tasks/{taskId}")
@RequiredArgsConstructor
@Validated
public class TaskSyncController {

    private final TaskSyncService taskSyncService;

    @PostMapping("/sync")
    public ApiResponse<Void> sync(@PathVariable("taskId") Integer taskId,
                                  @Validated @RequestBody SyncToDbRequest request,
                                  HttpServletRequest httpRequest) {
        taskSyncService.sync(taskId, request, httpRequest);
        return ApiResponse.success("同步成功", null);
    }
}

