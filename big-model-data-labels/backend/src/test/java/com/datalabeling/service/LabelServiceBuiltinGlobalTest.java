package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.dto.mapper.LabelMapper;
import com.datalabeling.dto.request.CreateLabelRequest;
import com.datalabeling.dto.request.UpdateLabelRequest;
import com.datalabeling.entity.Label;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.LabelRepository;
import com.datalabeling.repository.TaskLabelRepository;
import com.datalabeling.repository.UserRepository;
import com.datalabeling.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LabelServiceBuiltinGlobalTest {

    @Mock private LabelRepository labelRepository;
    @Mock private TaskLabelRepository taskLabelRepository;
    @Mock private SecurityUtil securityUtil;
    @Mock private AuditService auditService;
    @Mock private UserRepository userRepository;

    private LabelService newService() {
        return new LabelService(
            labelRepository,
            taskLabelRepository,
            new LabelMapper(),
            securityUtil,
            auditService,
            userRepository
        );
    }

    @Test
    void createLabel_nonAdmin_scopeGlobal_forbidden() {
        when(securityUtil.getCurrentUserId()).thenReturn(2);
        when(securityUtil.isAdmin()).thenReturn(false);

        CreateLabelRequest req = new CreateLabelRequest();
        req.setName("内置标签A");
        req.setDescription("desc");
        req.setScope(Label.Scope.GLOBAL);

        BusinessException ex = assertThrows(BusinessException.class, () -> newService().createLabel(req));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(labelRepository, never()).save(any());
    }

    @Test
    void getActiveLabels_scopeGlobal_returnsAdminCreatedLabels() {
        when(securityUtil.getCurrentUserId()).thenReturn(2);
        when(securityUtil.isAdmin()).thenReturn(false);

        when(userRepository.findAdminUserIds()).thenReturn(Collections.singletonList(1));

        Label label = Label.builder()
            .id(2)
            .userId(1)
            .name("系统内置标签")
            .version(1)
            .scope(Label.Scope.GLOBAL)
            .type(Label.Type.CLASSIFICATION)
            .description("desc")
            .isActive(true)
            .build();

        when(labelRepository.findBuiltinGlobalActiveLatest(eq(Arrays.asList(1))))
            .thenReturn(Collections.singletonList(label));

        assertEquals(1, newService().getActiveLabels(null, "global", null, null).size());
    }

    @Test
    void updateLabel_nonAdmin_ownGlobal_forbidden() {
        when(securityUtil.isAdmin()).thenReturn(false);

        Label label = Label.builder()
            .id(99)
            .userId(2)
            .name("普通用户的global（历史数据）")
            .version(1)
            .scope(Label.Scope.GLOBAL)
            .type(Label.Type.CLASSIFICATION)
            .description("old")
            .isActive(true)
            .build();

        when(labelRepository.findById(eq(99))).thenReturn(Optional.of(label));

        UpdateLabelRequest req = new UpdateLabelRequest();
        req.setDescription("new");

        BusinessException ex = assertThrows(BusinessException.class, () -> newService().updateLabel(99, req));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(labelRepository, never()).save(any());
    }
}
