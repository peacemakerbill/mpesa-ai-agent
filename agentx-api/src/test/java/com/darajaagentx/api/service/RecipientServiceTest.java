package com.darajaagentx.api.service;

import com.darajaagentx.api.dto.RecipientImportResult;
import com.darajaagentx.api.dto.RecipientView;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipientServiceTest {

    static final String OWNER = "254712345678";
    static final String OTHER = "254799999999";

    private final Fakes.Recipients store = new Fakes.Recipients();
    private final RecipientService service = new RecipientService(store.repo(), 5);

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void addsRenamesAndRemoves() {
        RecipientView a = service.add(OWNER, " Accountant@Firm.CO.KE ", "  Accountant ");
        assertThat(a.email()).isEqualTo("accountant@firm.co.ke");
        assertThat(a.name()).isEqualTo("Accountant");
        service.add(OWNER, "accountant@firm.co.ke", "My accountant");
        assertThat(service.list(OWNER)).hasSize(1);
        assertThat(service.list(OWNER).get(0).name()).isEqualTo("My accountant");
        service.remove(OWNER, a.id());
        assertThat(service.list(OWNER)).isEmpty();
        assertThatThrownBy(() -> service.remove(OWNER, a.id())).hasMessageContaining("not on your list");
    }

    @Test
    void lookupWorksByAddressOrNameAndNeverAcrossCustomers() {
        service.add(OWNER, "tax@firm.co.ke", "Accountant");
        service.add(OTHER, "other@x.com", "Accountant");
        assertThat(service.find(OWNER, "my accountant").orElseThrow().getEmail()).isEqualTo("tax@firm.co.ke");
        assertThat(service.find(OWNER, "ACCOUNTANT").orElseThrow().getEmail()).isEqualTo("tax@firm.co.ke");
        assertThat(service.find(OWNER, "TAX@firm.co.ke").orElseThrow().getEmail()).isEqualTo("tax@firm.co.ke");
        assertThat(service.find(OWNER, "other@x.com").isPresent()).isFalse();
        assertThat(service.find(OWNER, "boss").isPresent()).isFalse();
        assertThat(service.removeBy(OWNER, "the accountant").isPresent()).isTrue();
        assertThat(service.list(OWNER)).isEmpty();
        assertThat(service.list(OTHER)).hasSize(1);
    }

    @Test
    void theListHasALimit() {
        for (int i = 0; i < 5; i++) {
            service.add(OWNER, "p" + i + "@x.com", null);
        }
        assertThatThrownBy(() -> service.add(OWNER, "p9@x.com", null)).hasMessageContaining("list is full");
        service.add(OWNER, "p0@x.com", "Zero");     // an address already there can still be renamed
        RecipientImportResult r = service.addMany(OWNER, "p1@x.com, p7@x.com");
        assertThat(r.alreadySaved()).isEqualTo(1);
        assertThat(r.added()).isEqualTo(0);
        assertThat(r.limitReached()).isTrue();
    }

    @Test
    void pastedTextCanBeSeparatedAnyWayAndBadOnesAreReported() {
        RecipientImportResult r = service.addMany(OWNER, "a@x.com; b@x.com\nC@X.com  d@x.com, not-an-address, e@@x.com, <f@x.com>");
        assertThat(r.added()).isEqualTo(5);
        assertThat(r.invalid()).isEqualTo(1);
        assertThat(r.invalidSamples()).contains("e@@x.com");
        assertThat(r.recipients()).hasSize(5);
        RecipientImportResult again = service.addMany(OWNER, "a@x.com");
        assertThat(again.added()).isEqualTo(0);
        assertThat(again.alreadySaved()).isEqualTo(1);
    }

    @Test
    void aCsvWithAHeaderUsesTheNameColumn() {
        String csv = "Name,E-mail\r\nAccountant,tax@firm.co.ke\r\nLawyer,law@firm.co.ke\r\n,plain@x.com\r\n";
        RecipientImportResult r = service.importFile(OWNER, bytes(csv));
        assertThat(r.added()).isEqualTo(3);
        assertThat(service.find(OWNER, "lawyer").orElseThrow().getEmail()).isEqualTo("law@firm.co.ke");
        assertThat(service.find(OWNER, "plain@x.com").orElseThrow().getName()).isNull();
    }

    @Test
    void aCsvWithoutAHeaderFindsTheAddressAndTheName() {
        RecipientImportResult r = service.importFile(OWNER, bytes("Wanjiku,wanjiku@x.com\nbrian@x.com,Brian\nsolo@x.com\n"));
        assertThat(r.added()).isEqualTo(3);
        assertThat(service.find(OWNER, "wanjiku").orElseThrow().getEmail()).isEqualTo("wanjiku@x.com");
        assertThat(service.find(OWNER, "brian").orElseThrow().getEmail()).isEqualTo("brian@x.com");
    }

    @Test
    void aPlainTextFileAndAByteOrderMarkWork() {
        RecipientImportResult r = service.importFile(OWNER, bytes("﻿a@x.com\nb@x.com\n\nc@x.com\n"));
        assertThat(r.added()).isEqualTo(3);
        RecipientImportResult s = service.importFile(OWNER, bytes("email\n\"d@x.com, e@x.com\"\n"));
        assertThat(s.added()).isEqualTo(2);       // a header called email, and two addresses in one cell
    }

    @Test
    void filesAreCheckedForSizeAndEncoding() {
        assertThatThrownBy(() -> service.importFile(OWNER, new byte[0])).hasMessageContaining("empty");
        assertThatThrownBy(() -> service.importFile(OWNER, new byte[300_000])).hasMessageContaining("256 KB");
        assertThatThrownBy(() -> service.importFile(OWNER, new byte[]{(byte) 0xff, (byte) 0xfe, 'a'})).hasMessageContaining("UTF-8");
        RecipientImportResult junk = service.importFile(OWNER, bytes("hello\nworld\n"));
        assertThat(junk.added()).isEqualTo(0);
        assertThat(junk.invalid()).isEqualTo(2);
    }
}
