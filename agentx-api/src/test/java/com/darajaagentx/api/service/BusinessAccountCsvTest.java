package com.darajaagentx.api.service;

import com.darajaagentx.api.dto.ImportResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessAccountCsvTest {

    @Test
    void theTemplateReadsCleanly() {
        BusinessAccountCsv.Parsed parsed = BusinessAccountCsv.parse(BusinessAccountCsv.TEMPLATE.getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.issues()).isEmpty();
        assertThat(parsed.rows()).hasSize(5);
        assertThat(parsed.rows().get(0).row()).isEqualTo(2);
        assertThat(parsed.rows().get(1).request().parentShortcode()).isEqualTo("174379");
    }

    @Test
    void columnOrderDoesNotMatterAndShortNamesWork() {
        BusinessAccountCsv.Parsed parsed = BusinessAccountCsv.parse(
                "Name,Env,Type,Shortcode,Till,Key,Secret,Mystery\nA,sandbox,paybill,123456,,k,s,x\n".getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.issues()).isEmpty();
        assertThat(parsed.ignoredColumns()).containsExactly("Mystery");
        assertThat(parsed.rows().get(0).request().label()).isEqualTo("A");
        assertThat(parsed.rows().get(0).request().consumerSecret()).isEqualTo("s");
    }

    @Test
    void aMissingShortcodeColumnIsExplained() {
        BusinessAccountCsv.Parsed parsed = BusinessAccountCsv.parse("label,type\nA,paybill\n".getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.issues()).extracting(ImportResult.Issue::field).contains("shortcode");
    }

    @Test
    void emptyHeaderOnlyAndBadEncodingFilesAreRefused() {
        assertThat(BusinessAccountCsv.parse(new byte[0]).issues()).hasSize(1);
        assertThat(BusinessAccountCsv.parse((String.join(",", BusinessAccountCsv.COLUMNS) + "\n").getBytes(StandardCharsets.UTF_8)).issues()).hasSize(1);
        assertThat(BusinessAccountCsv.parse(new byte[]{(byte) 0xc3, 0x28, 'a'}).issues()).isNotEmpty();
    }

    @Test
    void aFileOverTheLimitIsRefused() {
        assertThat(BusinessAccountCsv.parse(new byte[BusinessAccountCsv.MAX_BYTES + 1]).issues()).hasSize(1);
    }
}
