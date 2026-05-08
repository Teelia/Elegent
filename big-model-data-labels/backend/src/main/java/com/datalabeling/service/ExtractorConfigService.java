package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.dto.request.CreateExtractorRequest;
import com.datalabeling.dto.request.UpdateExtractorRequest;
import com.datalabeling.dto.response.ExtractorConfigVO;
import com.datalabeling.entity.ExtractorConfig;
import com.datalabeling.entity.ExtractorOption;
import com.datalabeling.entity.ExtractorPattern;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.ExtractorConfigRepository;
import com.datalabeling.repository.ExtractorOptionRepository;
import com.datalabeling.repository.ExtractorPatternRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 提取器配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractorConfigService {

    private final ExtractorConfigRepository extractorConfigRepository;
    private final ExtractorPatternRepository extractorPatternRepository;
    private final ExtractorOptionRepository extractorOptionRepository;

    /**
     * 获取所有激活的提取器
     */
    public List<ExtractorConfigVO> listAllActive() {
        List<ExtractorConfig> configs = extractorConfigRepository.findAllActiveWithDetails();
        return configs.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有内置提取器
     */
    public List<ExtractorConfigVO> listBuiltin() {
        List<ExtractorConfig> configs = extractorConfigRepository.findBuiltinWithDetails();
        return configs.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有自定义提取器
     */
    public List<ExtractorConfigVO> listCustom() {
        List<ExtractorConfig> configs = extractorConfigRepository.findCustomWithDetails();
        return configs.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID获取提取器
     */
    public ExtractorConfigVO getById(Integer id) {
        ExtractorConfig config = extractorConfigRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "提取器不存在"));
        return toVO(config);
    }

    /**
     * 根据代码获取提取器
     */
    public ExtractorConfigVO getByCode(String code) {
        ExtractorConfig config = extractorConfigRepository.findByCodeWithDetails(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "提取器不存在: " + code));
        return toVO(config);
    }

    /**
     * 创建提取器
     */
    @Transactional
    public ExtractorConfigVO create(Integer userId, CreateExtractorRequest request) {
        // 检查代码是否已存在
        if (extractorConfigRepository.existsByCode(request.getCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "提取器代码已存在: " + request.getCode());
        }

        // 创建提取器配置
        ExtractorConfig config = new ExtractorConfig();
        config.setUserId(userId);
        config.setName(request.getName());
        config.setCode(request.getCode());
        config.setDescription(request.getDescription());
        config.setCategory(ExtractorConfig.Category.CUSTOM);
        config.setIsActive(true);
        config.setIsSystem(false);

        // 保存提取器
        config = extractorConfigRepository.save(config);

        // 创建正则规则
        if (request.getPatterns() != null && !request.getPatterns().isEmpty()) {
            for (CreateExtractorRequest.PatternRequest patternReq : request.getPatterns()) {
                ExtractorPattern pattern = new ExtractorPattern();
                pattern.setExtractor(config);
                pattern.setName(patternReq.getName());
                pattern.setPattern(patternReq.getPattern());
                pattern.setDescription(patternReq.getDescription());
                pattern.setPriority(patternReq.getPriority() != null ? patternReq.getPriority() : 0);
                pattern.setConfidence(patternReq.getConfidence());
                pattern.setValidationType(patternReq.getValidationType());
                pattern.setValidationConfig(patternReq.getValidationConfig());
                pattern.setIsActive(patternReq.getIsActive() != null ? patternReq.getIsActive() : true);
                pattern.setSortOrder(patternReq.getSortOrder() != null ? patternReq.getSortOrder() : 0);
                config.getPatterns().add(pattern);
            }
        }

        // 创建选项配置
        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            for (CreateExtractorRequest.OptionRequest optionReq : request.getOptions()) {
                ExtractorOption option = new ExtractorOption();
                option.setExtractor(config);
                option.setOptionKey(optionReq.getOptionKey());
                option.setOptionName(optionReq.getOptionName());
                option.setOptionType(optionReq.getOptionType() != null ? optionReq.getOptionType() : "boolean");
                option.setDefaultValue(optionReq.getDefaultValue());
                option.setDescription(optionReq.getDescription());
                option.setSelectOptions(optionReq.getSelectOptions());
                option.setSortOrder(optionReq.getSortOrder() != null ? optionReq.getSortOrder() : 0);
                config.getOptions().add(option);
            }
        }

        // 保存关联数据
        config = extractorConfigRepository.save(config);

        log.info("创建提取器成功: id={}, code={}, name={}", config.getId(), config.getCode(), config.getName());
        return toVO(config);
    }

    /**
     * 更新提取器
     */
    @Transactional
    public ExtractorConfigVO update(Integer id, UpdateExtractorRequest request) {
        ExtractorConfig config = extractorConfigRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "提取器不存在"));

        // 系统内置提取器只能修改部分属性
        if (config.getIsSystem()) {
            // 只允许修改正则规则和选项
            if (request.getName() != null || request.getDescription() != null) {
                log.warn("系统内置提取器不允许修改名称和描述");
            }
        } else {
            // 自定义提取器可以修改所有属性
            if (request.getName() != null) {
                config.setName(request.getName());
            }
            if (request.getDescription() != null) {
                config.setDescription(request.getDescription());
            }
            if (request.getIsActive() != null) {
                config.setIsActive(request.getIsActive());
            }
        }

        // 更新正则规则
        if (request.getPatterns() != null) {
            // 清除旧规则
            config.getPatterns().clear();
            
            // 添加新规则
            for (CreateExtractorRequest.PatternRequest patternReq : request.getPatterns()) {
                ExtractorPattern pattern = new ExtractorPattern();
                pattern.setExtractor(config);
                pattern.setName(patternReq.getName());
                pattern.setPattern(patternReq.getPattern());
                pattern.setDescription(patternReq.getDescription());
                pattern.setPriority(patternReq.getPriority() != null ? patternReq.getPriority() : 0);
                pattern.setConfidence(patternReq.getConfidence());
                pattern.setValidationType(patternReq.getValidationType());
                pattern.setValidationConfig(patternReq.getValidationConfig());
                pattern.setIsActive(patternReq.getIsActive() != null ? patternReq.getIsActive() : true);
                pattern.setSortOrder(patternReq.getSortOrder() != null ? patternReq.getSortOrder() : 0);
                config.getPatterns().add(pattern);
            }
        }

        // 更新选项配置
        if (request.getOptions() != null) {
            // 清除旧选项
            config.getOptions().clear();
            
            // 添加新选项
            for (CreateExtractorRequest.OptionRequest optionReq : request.getOptions()) {
                ExtractorOption option = new ExtractorOption();
                option.setExtractor(config);
                option.setOptionKey(optionReq.getOptionKey());
                option.setOptionName(optionReq.getOptionName());
                option.setOptionType(optionReq.getOptionType() != null ? optionReq.getOptionType() : "boolean");
                option.setDefaultValue(optionReq.getDefaultValue());
                option.setDescription(optionReq.getDescription());
                option.setSelectOptions(optionReq.getSelectOptions());
                option.setSortOrder(optionReq.getSortOrder() != null ? optionReq.getSortOrder() : 0);
                config.getOptions().add(option);
            }
        }

        config = extractorConfigRepository.save(config);

        log.info("更新提取器成功: id={}, code={}", config.getId(), config.getCode());
        return toVO(config);
    }

    /**
     * 删除提取器
     */
    @Transactional
    public void delete(Integer id) {
        ExtractorConfig config = extractorConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "提取器不存在"));

        // 系统内置提取器不能删除
        if (config.getIsSystem()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统内置提取器不能删除");
        }

        extractorConfigRepository.delete(config);
        log.info("删除提取器成功: id={}, code={}", id, config.getCode());
    }

    /**
     * 获取提取器配置（用于实际提取）
     */
    public ExtractorConfig getConfigByCode(String code) {
        return extractorConfigRepository.findByCodeWithDetails(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "提取器不存在: " + code));
    }

    /**
     * 转换为VO
     */
    private ExtractorConfigVO toVO(ExtractorConfig config) {
        ExtractorConfigVO vo = new ExtractorConfigVO();
        vo.setId(config.getId());
        vo.setUserId(config.getUserId());
        vo.setName(config.getName());
        vo.setCode(config.getCode());
        vo.setDescription(config.getDescription());
        vo.setCategory(config.getCategory());
        vo.setIsActive(config.getIsActive());
        vo.setIsSystem(config.getIsSystem());
        vo.setCreatedAt(config.getCreatedAt());
        vo.setUpdatedAt(config.getUpdatedAt());

        // 转换正则规则
        if (config.getPatterns() != null) {
            List<ExtractorConfigVO.PatternVO> patternVOs = new ArrayList<>();
            for (ExtractorPattern pattern : config.getPatterns()) {
                ExtractorConfigVO.PatternVO patternVO = new ExtractorConfigVO.PatternVO();
                patternVO.setId(pattern.getId());
                patternVO.setName(pattern.getName());
                patternVO.setPattern(pattern.getPattern());
                patternVO.setDescription(pattern.getDescription());
                patternVO.setPriority(pattern.getPriority());
                patternVO.setConfidence(pattern.getConfidence());
                patternVO.setValidationType(pattern.getValidationType());
                patternVO.setValidationConfig(pattern.getValidationConfig());
                patternVO.setIsActive(pattern.getIsActive());
                patternVO.setSortOrder(pattern.getSortOrder());
                patternVOs.add(patternVO);
            }
            vo.setPatterns(patternVOs);
        }

        // 转换选项配置
        if (config.getOptions() != null) {
            List<ExtractorConfigVO.OptionVO> optionVOs = new ArrayList<>();
            for (ExtractorOption option : config.getOptions()) {
                ExtractorConfigVO.OptionVO optionVO = new ExtractorConfigVO.OptionVO();
                optionVO.setId(option.getId());
                optionVO.setOptionKey(option.getOptionKey());
                optionVO.setOptionName(option.getOptionName());
                optionVO.setOptionType(option.getOptionType());
                optionVO.setDefaultValue(option.getDefaultValue());
                optionVO.setDescription(option.getDescription());
                optionVO.setSelectOptions(option.getSelectOptions());
                optionVO.setSortOrder(option.getSortOrder());
                optionVOs.add(optionVO);
            }
            vo.setOptions(optionVOs);
        }

        return vo;
    }
}