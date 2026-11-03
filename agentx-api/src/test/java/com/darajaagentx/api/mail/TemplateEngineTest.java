package com.darajaagentx.api.mail;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateEngineTest {

    private static String run(String source, Map<String, Object> data) {
        return TemplateEngine.parse("t", source).render(data, false);
    }

    @Test
    void doubleBracesEscapeAndTripleBracesDoNot() {
        Map<String, Object> d = Map.of("x", "<b>&\"'");
        assertThat(run("{{x}}", d)).isEqualTo("&lt;b&gt;&amp;&quot;&#39;");
        assertThat(run("{{{x}}}", d)).isEqualTo("<b>&\"'");
    }

    @Test
    void ifAndUnlessFollowThePresenceOfAValue() {
        Map<String, Object> d = Map.of("yes", "a", "no", "  ", "flag", false, "list", List.of());
        assertThat(run("{{#if yes}}A{{/if}}{{#if no}}B{{/if}}{{#if flag}}C{{/if}}{{#if list}}D{{/if}}", d)).isEqualTo("A");
        assertThat(run("{{#unless yes}}A{{/unless}}{{#unless no}}B{{/unless}}{{#unless flag}}C{{/unless}}{{#unless list}}D{{/unless}}", d)).isEqualTo("BCD");
    }

    @Test
    void eachRepeatsAndReadsTheItemBeforeTheOuterData() {
        Map<String, Object> d = Map.of("unit", "KES", "rows", List.of(Map.of("n", "1"), Map.of("n", "2")), "names", List.of("a<", "b"));
        assertThat(run("{{#each rows}}[{{n}}{{unit}}]{{/each}}", d)).isEqualTo("[1KES][2KES]");
        assertThat(run("{{#each names}}{{.}};{{/each}}", d)).isEqualTo("a&lt;;b;");
    }

    @Test
    void aBlockTagAloneOnItsLineLeavesNoBlankLine() {
        String src = "one\n{{#if yes}}\ntwo\n{{/if}}\n{{#if no}}\nthree\n{{/if}}\nfour\n";
        assertThat(run(src, Map.of("yes", true, "no", false))).isEqualTo("one\ntwo\nfour\n");
        assertThat(run("{{! a note }}\nhello\n", Map.of())).isEqualTo("hello\n");
    }

    @Test
    void aTagInsideALineKeepsTheLine() {
        assertThat(run("a {{#if yes}}b{{/if}} c\n", Map.of("yes", true))).isEqualTo("a b c\n");
        assertThat(run("a {{#if yes}}b{{/if}} c\n", Map.of("yes", false))).isEqualTo("a  c\n");
    }

    @Test
    void strictModeCatchesAnUnknownNameAndNormalModeDoesNot() {
        TemplateEngine t = TemplateEngine.parse("t", "Hi {{who}}{{#if extra}}!{{/if}}");
        assertThat(t.render(Map.of(), false)).isEqualTo("Hi ");
        assertThatThrownBy(() -> t.render(Map.of("who", "A"), true)).hasMessageContaining("extra");
        assertThat(t.render(Map.of("who", "A", "extra", ""), true)).isEqualTo("Hi A");
    }

    @Test
    void badTemplatesAreRefusedWithAClearMessage() {
        assertThatThrownBy(() -> TemplateEngine.parse("t", "{{#if a}}open")).hasMessageContaining("never closed");
        assertThatThrownBy(() -> TemplateEngine.parse("t", "close{{/if}}")).hasMessageContaining("no matching");
        assertThatThrownBy(() -> TemplateEngine.parse("t", "{{oops")).hasMessageContaining("never closed");
        assertThatThrownBy(() -> TemplateEngine.parse("t", "{{#when a}}x{{/when}}")).hasMessageContaining("not understood");
    }

    @Test
    void namesListsEverythingATemplateReads() {
        assertThat(TemplateEngine.parse("t", "{{a}}{{#if b}}{{{c}}}{{/if}}").names()).containsExactly("a", "b", "c");
    }
}