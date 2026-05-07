package com.datalabeling.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 同步配置敏感字段加解密（用于外部数据库密码）
 */
@Slf4j
@Service
public class SyncCryptoService {

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public SyncCryptoService(@Value("${sync.crypto-key:${jwt.secret}}") String keyMaterial) {
        this.keySpec = new SecretKeySpec(deriveKeyBytes(keyMaterial), "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));

            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("加密失败: {}", e.getMessage());
            throw new IllegalStateException("加密失败");
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length <= 16) {
                throw new IllegalArgumentException("密文格式错误");
            }

            byte[] iv = new byte[16];
            byte[] cipherBytes = new byte[combined.length - 16];
            System.arraycopy(combined, 0, iv, 0, 16);
            System.arraycopy(combined, 16, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密失败: {}", e.getMessage());
            throw new IllegalStateException("解密失败");
        }
    }

    private static byte[] deriveKeyBytes(String keyMaterial) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((keyMaterial != null ? keyMaterial : "").getBytes(StandardCharsets.UTF_8));
            byte[] key = new byte[16];
            System.arraycopy(hash, 0, key, 0, 16);
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("初始化加密密钥失败");
        }
    }
}

