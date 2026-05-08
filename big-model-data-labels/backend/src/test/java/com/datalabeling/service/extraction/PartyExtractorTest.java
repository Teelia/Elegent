package com.datalabeling.service.extraction;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PartyExtractor 单元测试：重点验证“缺失名单展示”的降噪逻辑不再输出明显的非人名短语。
 */
class PartyExtractorTest {

    @Test
    void testCheckCompleteness_ShouldFilterNoiseNamesInMessage() {
        PartyExtractor extractor = new PartyExtractor();

        List<PartyExtractor.Party> parties = Arrays.asList(
            PartyExtractor.Party.builder()
                .name("表示知晓") // 噪声短语，应被过滤
                .isGeneric(false)
                .idCardValid(false)
                .build(),
            PartyExtractor.Party.builder()
                .name("张三") // 合理人名，应保留展示
                .isGeneric(false)
                .idCardValid(false)
                .build()
        );

        PartyExtractor.CompletenessResult r = extractor.checkCompleteness(parties);
        assertFalse(r.isComplete());
        assertTrue(r.getMessage().contains("缺失"));
        assertTrue(r.getMessage().contains("张三"));
        assertFalse(r.getMessage().contains("表示知晓"));
    }

    @Test
    void testCheckCompleteness_AllNamesFiltered_ShouldShowFallback() {
        PartyExtractor extractor = new PartyExtractor();

        List<PartyExtractor.Party> parties = Arrays.asList(
            PartyExtractor.Party.builder()
                .name("向报警人") // 噪声短语，应被过滤
                .isGeneric(false)
                .idCardValid(false)
                .build()
        );

        PartyExtractor.CompletenessResult r = extractor.checkCompleteness(parties);
        assertFalse(r.isComplete());
        assertTrue(r.getMessage().contains("（无法提取姓名）"));
    }

    @Test
    void testExtractDisputeParties_ShouldNotTruncateName() {
        PartyExtractor extractor = new PartyExtractor();
        String text = "报警人：张三（身份证号：340811199901011234），与李四发生纠纷。";
        List<PartyExtractor.Party> parties = extractor.extractParties(text, null);

        assertTrue(parties.stream().anyMatch(p -> "李四".equals(p.getName())));
    }

    @Test
    void testExtractDisputeParties_ShouldSkipNonPersonTokensLikeProperty() {
        PartyExtractor extractor = new PartyExtractor();
        String text = "报警人：张三（身份证号：340811199901011234），与物业发生纠纷。";
        List<PartyExtractor.Party> parties = extractor.extractParties(text, null);

        assertFalse(parties.stream().anyMatch(p -> "物业".equals(p.getName())));
    }

    @Test
    void testExtractParties_ShouldAssociateIdCardsByNamePattern() {
        PartyExtractor extractor = new PartyExtractor();
        String text = "报警人：张三 身份证号：340811199901011234，与李四发生纠纷，李四身份证号340811199901019876。";
        List<PartyExtractor.Party> parties = extractor.extractParties(text, null);

        assertTrue(parties.stream().anyMatch(p -> "张三".equals(p.getName()) && p.isIdCardValid()));
        assertTrue(parties.stream().anyMatch(p -> "李四".equals(p.getName()) && p.isIdCardValid()));

        PartyExtractor.CompletenessResult r = extractor.checkCompleteness(parties);
        assertTrue(r.isComplete(), r.getMessage());
    }

    @Test
    void testExtractOtherParties_GenericOtherWithId_ShouldNotCountAsMissing() {
        PartyExtractor extractor = new PartyExtractor();
        String text = "报警人：张三（身份证号：340811199901011234），对方当事人身份证号：340811199901019876。";
        List<PartyExtractor.Party> parties = extractor.extractParties(text, null);

        assertTrue(parties.stream().anyMatch(p -> "对方当事人".equals(p.getName()) && p.isGeneric() && p.isIdCardValid()));
        assertTrue(extractor.checkCompleteness(parties).isComplete());
    }
}
