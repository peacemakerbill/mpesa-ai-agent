package com.darajaagentx.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeVisibilityTest {

    @Test
    void theSettingIsReadLeniently() {
        assertThat(CodeVisibility.parse(null)).isEqualTo(CodeVisibility.AUTO);
        assertThat(CodeVisibility.parse("")).isEqualTo(CodeVisibility.AUTO);
        assertThat(CodeVisibility.parse("auto")).isEqualTo(CodeVisibility.AUTO);
        assertThat(CodeVisibility.parse("maybe")).isEqualTo(CodeVisibility.AUTO);
        for (String on : new String[]{"true", "TRUE", " on ", "yes", "1", "show"}) {
            assertThat(CodeVisibility.parse(on)).as(on).isEqualTo(CodeVisibility.ON);
        }
        for (String off : new String[]{"false", "False", "off", "no", "0", "hide"}) {
            assertThat(CodeVisibility.parse(off)).as(off).isEqualTo(CodeVisibility.OFF);
        }
    }

    @Test
    void onlyDevelopmentAndStagingEverShowACode() {
        assertThat(CodeVisibility.AUTO.shownIn(AppEnvironment.DEVELOPMENT)).isTrue();
        assertThat(CodeVisibility.AUTO.shownIn(AppEnvironment.STAGING)).isTrue();
        assertThat(CodeVisibility.AUTO.shownIn(AppEnvironment.PRODUCTION)).isFalse();
        assertThat(CodeVisibility.ON.shownIn(AppEnvironment.STAGING)).isTrue();
        assertThat(CodeVisibility.ON.shownIn(AppEnvironment.PRODUCTION)).isFalse();
        assertThat(CodeVisibility.OFF.shownIn(AppEnvironment.DEVELOPMENT)).isFalse();
        assertThat(CodeVisibility.OFF.shownIn(AppEnvironment.STAGING)).isFalse();
    }
}
