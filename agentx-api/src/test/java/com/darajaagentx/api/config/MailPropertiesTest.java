package com.darajaagentx.api.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** The mail server password and user name are read forgivingly, so they can be pasted as they were given. */
class MailPropertiesTest {

    private static MailProperties.Smtp smtp(String host, String username, String password) {
        return new MailProperties.Smtp(host, 587, username, password, true, false, Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    @Test
    void aGmailAppPasswordWorksWithOrWithoutQuotesAndSpaces() {
        String plain = "abcdefghijklmnop";
        assertThat(smtp("smtp.gmail.com", "a@gmail.com", "abcd efgh ijkl mnop").password()).isEqualTo(plain);
        assertThat(smtp("smtp.gmail.com", "a@gmail.com", "'abcd efgh ijkl mnop'").password()).isEqualTo(plain);
        assertThat(smtp("smtp.gmail.com", "a@gmail.com", "\"abcd efgh ijkl mnop\"").password()).isEqualTo(plain);
        assertThat(smtp("smtp.gmail.com", "a@gmail.com", "  'abcdefghijklmnop'  ").password()).isEqualTo(plain);
        assertThat(smtp("smtp.gmail.com", "a@gmail.com", plain).password()).isEqualTo(plain);
        assertThat(smtp("smtp.gmail.com", "a@gmail.com", "abcd\u00A0efgh ijkl mnop").password()).isEqualTo(plain);
    }

    @Test
    void anAppPasswordWrittenInGroupsLosesItsSpacesOnAnyServer() {
        assertThat(smtp("mail.example.com", "a", "abcd efgh ijkl mnop").password()).isEqualTo("abcdefghijklmnop");
    }

    @Test
    void otherServersKeepSpacesThatAreReallyPartOfThePassword() {
        assertThat(smtp("mail.example.com", "a", "my pass word").password()).isEqualTo("my pass word");
        assertThat(smtp("mail.example.com", "a", "'my pass word'").password()).isEqualTo("my pass word");
        assertThat(smtp("mail.example.com", "a", "it's").password()).isEqualTo("it's");
    }

    @Test
    void theUserNameLosesQuotesAndEdgeSpaces() {
        assertThat(smtp("smtp.gmail.com", " 'billgrahamp@gmail.com' ", "x").username()).isEqualTo("billgrahamp@gmail.com");
        assertThat(smtp("smtp.gmail.com", "\"u\"", "x").username()).isEqualTo("u");
    }

    @Test
    void emptyStaysEmpty() {
        assertThat(smtp("", null, null).password()).isEmpty();
        assertThat(smtp("", null, null).username()).isEmpty();
        assertThat(smtp("smtp.gmail.com", "", "''").password()).isEmpty();
    }
}