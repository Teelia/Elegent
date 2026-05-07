package com.datalabeling.service.extraction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 护照号提取器
 *
 * <p>提取功能：</p>
 * <ul>
 *   <li>中国护照号（常见：E/G/P/S/D 开头，9位）</li>
 *   <li>通用护照号（字母1-2位 + 数字6-9位）</li>
 * </ul>
 */
@Slf4j
@Component
public class PassportExtractor implements INumberExtractor {

    // 中国护照号（常见：E/G/P/S/D 开头，9位）
    // 说明：避免使用 \\b（Unicode 下中文可能被视为“单词字符”，导致边界判定失效）
    private static final Pattern PASSPORT_CN = Pattern.compile("(?<![A-Za-z0-9])[EGDPS]\\d{8}(?![A-Za-z0-9])");

    // 护照号通用格式（字母+数字，6-11位）
    private static final Pattern PASSPORT_GENERIC = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z]{1,2}\\d{6,9}(?![A-Za-z0-9])");

    @Override
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        boolean includeCnOnly = Boolean.TRUE.equals(options != null ? options.get("include_cn_only") : null);

        List<ExtractedNumber> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1) 中国护照号优先
        Matcher mCn = PASSPORT_CN.matcher(text);
        while (mCn.find()) {
            String passport = mCn.group();
            if (!seen.add(passport)) {
                continue;
            }
            results.add(ExtractedNumber.builder()
                .field("护照号")
                .value(passport)
                .confidence(0.90f)
                .validation("中国护照号（9位，" + passport.charAt(0) + "开头）")
                .startIndex(mCn.start())
                .endIndex(mCn.end())
                .build());
        }

        // 2) 通用护照号（可选）
        if (!includeCnOnly) {
            Matcher mGeneric = PASSPORT_GENERIC.matcher(text);
            while (mGeneric.find()) {
                String passport = mGeneric.group();
                if (!seen.add(passport)) {
                    continue;
                }
                // 避免与中国护照号重复标注（已在第一段提取）
                if (PASSPORT_CN.matcher(passport).matches()) {
                    continue;
                }
                results.add(ExtractedNumber.builder()
                    .field("护照号")
                    .value(passport)
                    .confidence(0.75f)
                    .validation("通用护照号格式")
                    .startIndex(mGeneric.start())
                    .endIndex(mGeneric.end())
                    .build());
            }
        }

        log.debug("护照号提取完成，共提取 {} 个", results.size());
        return results;
    }

    @Override
    public String getExtractorType() {
        return "passport";
    }
}
