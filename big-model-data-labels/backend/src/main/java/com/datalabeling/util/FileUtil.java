package com.datalabeling.util;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.config.properties.FileProperties;
import com.datalabeling.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * 文件工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUtil {

    private final FileProperties fileProperties;

    /**
     * 保存上传文件
     *
     * @param file   上传的文件
     * @param userId 用户ID
     * @return 文件保存路径
     */
    public String saveUploadFile(MultipartFile file, Integer userId) {
        // 验证文件
        validateFile(file);

        // 创建用户目录
        String userDir = fileProperties.getUploadDir() + File.separator + userId;
        File directory = new File(userDir);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + extension;
        String filePath = userDir + File.separator + uniqueFilename;

        try {
            // 保存文件
            Path path = Paths.get(filePath);
            Files.write(path, file.getBytes());
            log.info("文件保存成功: {}", filePath);
            return filePath;
        } catch (IOException e) {
            log.error("文件保存失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "文件保存失败");
        }
    }

    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件不能为空");
        }

        // 检查文件大小
        if (file.getSize() > fileProperties.getMaxSize()) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEED);
        }

        // 检查文件扩展名
        String filename = file.getOriginalFilename();
        String extension = getFileExtension(filename);
        if (!fileProperties.getAllowedExtensions().contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORT,
                "不支持的文件类型: " + extension);
        }
    }

    /**
     * 获取文件扩展名
     */
    public String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex);
    }

    /**
     * 计算文件SHA256哈希
     */
    public String calculateFileHash(byte[] fileBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fileBytes);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("计算文件哈希失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "计算文件哈希失败");
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * 删除文件
     */
    public boolean deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("删除文件失败: {} - {}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    /**
     * 获取文件大小
     */
    public long getFileSize(String filePath) {
        try {
            return Files.size(Paths.get(filePath));
        } catch (IOException e) {
            log.error("获取文件大小失败: {}", e.getMessage());
            return 0;
        }
    }
}
