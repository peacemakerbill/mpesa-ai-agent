package com.darajaagentx.api.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonNamesTest {

    @Test
    void namesFromAroundTheWorldAreAccepted() {
        for (String ok : new String[]{"Wanjiru", "Otieno-Ochieng'", "Mary Anne", "José", "Zoë", "Nguyễn", "Ólafur", "Dr. Kamau", "O’Neil", "Åsa", "محمد", "李"}) {
            assertThat(PersonNames.clean(ok, "name")).isEqualTo(ok);
        }
    }

    @Test
    void thingsThatAreNotNamesAreRefused() {
        for (String bad : new String[]{"Am1na", "<b>x</b>", "a;b", "Robert'); DROP TABLE", "x@y", "-dash", "$$$", "12"}) {
            assertThatThrownBy(() -> PersonNames.clean(bad, "first name")).hasMessageContaining("first name");
        }
        assertThat(PersonNames.clean("   ", "x")).isNull();
        assertThat(PersonNames.clean(null, "x")).isNull();
        assertThatThrownBy(() -> PersonNames.required(" ", "last name")).hasMessageContaining("Enter your last name");
    }

    @Test
    void fullNameAndInitials() {
        assertThat(PersonNames.full("Amina", null, "Otieno")).isEqualTo("Amina Otieno");
        assertThat(PersonNames.full("Amina", "Wanjiru", "Otieno")).isEqualTo("Amina Wanjiru Otieno");
        assertThat(PersonNames.full(null, null, null)).isEqualTo("");
        assertThat(PersonNames.initials("amina", "otieno")).isEqualTo("AO");
        assertThat(PersonNames.initials(null, "Otieno")).isEqualTo("O");
    }
}
