package com.datalabeling.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 字符编码检测工具类
 * 用于自动检测CSV/Excel文件的字符编码
 */
@Slf4j
public class CharsetDetectorUtil {

    /**
     * 常见的中文字符编码（按优先级排序）
     */
    private static final List<Charset> COMMON_CHARSETS = new ArrayList<>();

    /**
     * 中文字符正则（用于验证解码是否成功）
     */
    private static final Pattern CHINESE_PATTERN = Pattern.compile(
        "[\\u4e00-\\u9fa5]"  // 基本汉字
    );

    /**
     * 乱码检测模式（常见的UTF-8误读为GBK的乱码字符）
     */
    private static final Pattern GARBAGE_PATTERN = Pattern.compile(
        "[ï¿½Â¦§¨©ª²³µ¶·¸¹ºÂ¼½¾¿ÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ]+"
    );

    static {
        // 按优先级添加常见编码
        COMMON_CHARSETS.add(StandardCharsets.UTF_8);
        COMMON_CHARSETS.add(Charset.forName("GBK"));
        COMMON_CHARSETS.add(Charset.forName("GB2312"));
        COMMON_CHARSETS.add(Charset.forName("GB18030"));
    }

    /**
     * 检测输入流的字符编码
     *
     * @param inputStream 输入流（不会被关闭）
     * @return 检测到的字符集
     */
    public static Charset detectCharset(InputStream inputStream) {
        byte[] buffer = readSampleBytes(inputStream, 8192);
        return detectCharset(buffer);
    }

    /**
     * 检测字节数组的字符编码
     *
     * @param buffer 字节数组
     * @return 检测到的字符集
     */
    public static Charset detectCharset(byte[] buffer) {
        if (buffer == null || buffer.length == 0) {
            return StandardCharsets.UTF_8;  // 默认UTF-8
        }

        // 1. 检查 BOM 标记
        Charset bomCharset = detectByBOM(buffer);
        if (bomCharset != null) {
            log.debug("通过BOM检测到编码: {}", bomCharset);
            return bomCharset;
        }

        // 2. 尝试各种编码，找出最合适的
        for (Charset charset : COMMON_CHARSETS) {
            if (isValidEncoding(buffer, charset)) {
                log.debug("检测到文件编码: {}", charset);
                return charset;
            }
        }

        // 3. 默认返回UTF-8
        log.debug("无法检测编码，使用默认UTF-8");
        return StandardCharsets.UTF_8;
    }

    /**
     * 通过 BOM（字节顺序标记）检测编码
     */
    private static Charset detectByBOM(byte[] buffer) {
        if (buffer.length >= 3 && buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB && buffer[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (buffer.length >= 2 && buffer[0] == (byte) 0xFE && buffer[1] == (byte) 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (buffer.length >= 2 && buffer[0] == (byte) 0xFF && buffer[1] == (byte) 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        return null;
    }

    /**
     * 验证是否是有效的编码
     *
     * @param buffer  字节数组
     * @param charset 字符集
     * @return 是否有效
     */
    private static boolean isValidEncoding(byte[] buffer, Charset charset) {
        try {
            String decoded = new String(buffer, charset);

            // 检查是否包含乱码特征
            if (containsGarbageCharacters(decoded)) {
                return false;
            }

            // 检查是否包含有效的中文字符
            int chineseCount = countChineseCharacters(decoded);
            if (chineseCount > 0) {
                // 包含中文，认为是有效编码
                return true;
            }

            // 如果没有中文，检查是否都是可打印字符
            if (isPrintableAscii(decoded)) {
                return true;
            }

            return false;
        } catch (Exception e) {
            log.trace("编码 {} 验证失败: {}", charset, e.getMessage());
            return false;
        }
    }

    /**
     * 检查是否包含乱码字符
     */
    private static boolean containsGarbageCharacters(String text) {
        // 检查是否包含常见的乱码字符序列
        if (GARBAGE_PATTERN.matcher(text).find()) {
            return true;
        }

        // 检查是否包含过多的替换字符（）
        int replacementCount = 0;
        for (int i = 0; i < Math.min(text.length(), 1000); i++) {
            if (text.charAt(i) == '\uFFFD') {
                replacementCount++;
            }
        }
        return replacementCount > 5;  // 超过5个替换字符认为有乱码
    }

    /**
     * 统计中文字符数量
     */
    private static int countChineseCharacters(String text) {
        int count = 0;
        for (int i = 0; i < Math.min(text.length(), 2000); i++) {
            char c = text.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fa5') {
                count++;
            }
        }
        return count;
    }

    /**
     * 检查是否是可打印的ASCII字符
     */
    private static boolean isPrintableAscii(String text) {
        int printableCount = 0;
        int total = Math.min(text.length(), 500);

        for (int i = 0; i < total; i++) {
            char c = text.charAt(i);
            // 可打印字符：空格、制表符、换行、或者可打印ASCII
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || (c >= 32 && c <= 126)) {
                printableCount++;
            }
        }

        return printableCount >= total * 0.8;  // 80%以上是可打印字符
    }

    /**
     * 从输入流读取采样字节
     */
    private static byte[] readSampleBytes(InputStream inputStream, int sampleSize) {
        try {
            byte[] buffer = new byte[sampleSize];
            int bytesRead = inputStream.read(buffer);
            if (bytesRead <= 0) {
                return new byte[0];
            }
            if (bytesRead < sampleSize) {
                byte[] result = new byte[bytesRead];
                System.arraycopy(buffer, 0, result, 0, bytesRead);
                return result;
            }
            return buffer;
        } catch (IOException e) {
            log.error("读取输入流失败: {}", e.getMessage());
            return new byte[0];
        } finally {
            // 不要关闭输入流，由调用者负责
        }
    }

    /**
     * 获取所有支持的字符集
     */
    public static List<Charset> getSupportedCharsets() {
        return new ArrayList<>(COMMON_CHARSETS);
    }
}
