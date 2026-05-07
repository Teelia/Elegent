package com.datalabeling.util;

/**
 * 敏感信息脱敏工具。
 *
 * <p>说明：
 * - 警情/接警数据通常包含身份证号、银行卡号、手机号等敏感信息
 * - 日志/提示词/导出应尽量避免输出完整号码
 */
public final class PiiMaskingUtil {

    private PiiMaskingUtil() {
    }

    /**
     * 对号码类字符串进行脱敏：保留前4后4，中间用*替代。
     * <p>适用于身份证号/银行卡号/手机号等（长度不足时不处理）。
     */
    public static String maskNumber(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        if (s.length() <= 8) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(s, 0, 4);
        for (int i = 0; i < s.length() - 8; i++) {
            sb.append('*');
        }
        sb.append(s.substring(s.length() - 4));
        return sb.toString();
    }
}

