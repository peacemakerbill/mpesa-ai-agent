package com.darajaagentx.api.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailsTest {

    @Test
    void goodAddressesAreLowerCasedAndTrimmed() {
        assertThat(Emails.clean("  Amina.Otieno+tax@Example.CO.KE ")).isEqualTo("amina.otieno+tax@example.co.ke");
        assertThat(Emails.clean("<a@b.co>")).isEqualTo("a@b.co");
        assertThat(Emails.clean("o'brien@example.com")).isEqualTo("o'brien@example.com");
    }

    @Test
    void badAddressesAreRefused() {
        for (String bad : new String[]{null, "", "plain", "a@b", "@b.co", "a@@b.co", "a b@c.co", "a@b..co", "a..b@c.co", "a@-b.co", "a@b.co\r\nBcc: x@y.co", "a@b.c o",
                "x".repeat(65) + "@b.co", "a@" + "x".repeat(250) + ".co"}) {
            assertThat(Emails.clean(bad)).isNull();
        }
        assertThatThrownBy(() -> Emails.require("nope")).hasMessageContaining("\"nope\" is not a valid email");
    }

    @Test
    void listsCanBeSeparatedAnyWay() {
        Emails.Found f = Emails.findAll("A@x.com, b@x.com; c@x.com\nD <d@x.com>\t\"e@x.com\" (f@x.com). a@x.com bad@@x.com no-at-sign");
        assertThat(f.emails()).containsExactly("a@x.com", "b@x.com", "c@x.com", "d@x.com", "e@x.com", "f@x.com");
        assertThat(f.invalid()).containsExactly("bad@@x.com");
    }

    @Test
    void extractFindsAddressesInASentence() {
        assertThat(Emails.extract("please send it to tax@firm.co.ke and cc Wanjiku <w@x.com>, thanks")).containsExactly("tax@firm.co.ke", "w@x.com");
        assertThat(Emails.extract(null)).isEmpty();
    }

    @Test
    void maskingHidesMostOfTheAddress() {
        assertThat(Emails.mask("amina@example.com")).isEqualTo("a***@example.com");
        assertThat(Emails.mask("nonsense")).isEqualTo("***");
        assertThat(Emails.domain("a@Example.COM")).isEqualTo("example.com");
    }
}
