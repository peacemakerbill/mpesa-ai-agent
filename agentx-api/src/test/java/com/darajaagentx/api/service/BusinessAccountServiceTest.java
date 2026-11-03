package com.darajaagentx.api.service;

import com.darajaagentx.api.config.DarajaProperties;
import com.darajaagentx.api.dto.BusinessAccountRequest;
import com.darajaagentx.api.dto.BusinessAccountView;
import com.darajaagentx.api.dto.ImportResult;
import com.darajaagentx.api.dto.VerifyResult;
import com.darajaagentx.api.exception.ActionNotFoundException;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.exception.VaultNotConfiguredException;
import com.darajaagentx.api.model.BusinessAccount;
import com.darajaagentx.api.model.DarajaEnvironment;
import com.darajaagentx.api.model.ShortcodeType;
import com.darajaagentx.api.repository.BusinessAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The business account rules, with an in memory store and no network. Same checks as the real database test, just faster. */
class BusinessAccountServiceTest {

    static final String OWNER = "254712345678";
    static final String KEY = "AbCdEfGhIjKlMnOpQrStUvWxYz123456";
    static final String SECRET = "s3cr3tS3cr3tS3cr3t";
    static final String PASS = "Safaricom999!*!";
    static final String PASSKEY = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919";
    static final String HEADER = "label,environment,shortcode_type,shortcode,till_number,account_reference,parent_shortcode,consumer_key,consumer_secret,initiator_name,initiator_password,passkey";

    private final Map<UUID, BusinessAccount> rows = new LinkedHashMap<>();
    private final AtomicReference<TokenProbe.Outcome> probeAnswer = new AtomicReference<>(new TokenProbe.Outcome(TokenProbe.Kind.ACCEPTED, 200));
    private final List<String> probed = new ArrayList<>();
    private SecretVault vault;
    private BusinessAccountService service;

    /** A repository that keeps rows in a map. Only the methods the service uses are answered. */
    private BusinessAccountRepository store() {
        return (BusinessAccountRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{BusinessAccountRepository.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "save" -> {
                    BusinessAccount a = (BusinessAccount) args[0];
                    a.prepareForMongo(Instant.now());
                    rows.put(a.getId(), a);
                    return a;
                }
                case "deleteAll" -> {
                    for (Object a : (Iterable<?>) args[0]) {
                        rows.remove(((BusinessAccount) a).getId());
                    }
                    return null;
                }
                case "findByOwnerPhoneOrderByCreatedAtAsc" -> {
                    return rows.values().stream().filter(a -> a.getOwnerPhone().equals(args[0])).toList();
                }
                case "findByIdAndOwnerPhone" -> {
                    return Optional.ofNullable(rows.get((UUID) args[0])).filter(a -> a.getOwnerPhone().equals(args[1]));
                }
                case "findByOwnerPhoneAndParentId" -> {
                    return rows.values().stream().filter(a -> a.getOwnerPhone().equals(args[0]) && args[1].equals(a.getParentId())).toList();
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    @BeforeEach
    void setUp() throws Exception {
        vault = new SecretVault(Base64.getEncoder().encodeToString(new byte[32]));
        Path certs = Path.of(getClass().getResource("/daraja-certs").toURI());
        SecurityCredentialGenerator certificates = new SecurityCredentialGenerator(new DarajaProperties("a", "b", certs.toString()));
        TokenProbe probe = (env, key, secret) -> {
            probed.add(key + "/" + secret);
            return probeAnswer.get();
        };
        service = new BusinessAccountService(store(), vault, certificates, probe, Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneOffset.UTC));
    }

    private static BusinessAccountRequest head(String label, String env, String type, String code, String till, String key, String secret, String initiator, String initiatorPass, String passkey) {
        return new BusinessAccountRequest(label, env, type, code, till, null, null, null, key, secret, initiator, initiatorPass, passkey);
    }

    private static BusinessAccountRequest branch(String label, UUID parent, String till, String ref) {
        return new BusinessAccountRequest(label, null, null, null, till, ref, parent, null, null, null, null, null, null);
    }

    private BusinessAccountView fullHead() {
        return service.create(OWNER, head("Main shop", "sandbox", "buy_goods", "174379", null, KEY, SECRET, "testapi", PASS, PASSKEY));
    }

    private static byte[] csv(String... lines) {
        return (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- one account

    @Test
    void aHeadAccountIsSavedLockedAndNeverShownBack() {
        BusinessAccountView v = fullHead();
        assertThat(v.shortcodeType()).isEqualTo(ShortcodeType.BUY_GOODS);
        assertThat(v.consumerKeyHint()).isEqualTo("3456");
        assertThat(v.hasConsumerSecret() && v.hasInitiatorPassword() && v.hasPasskey()).isTrue();
        assertThat(v.capabilities()).allMatch(BusinessAccountView.Capability::ready);
        assertThat(v.toString()).doesNotContain(SECRET, PASS, PASSKEY, KEY);

        BusinessAccount stored = rows.get(v.id());
        assertThat(stored.getConsumerSecretEnc()).startsWith("v1:").doesNotContain(SECRET);
        assertThat(stored.getInitiatorPasswordEnc()).startsWith("v1:");
        assertThat(stored.getPasskeyEnc()).startsWith("v1:");
        assertThat(stored.getTillNumber()).isEmpty();
    }

    @Test
    void withoutAnInitiatorOrPasskeyOnlyTheMatchingApisAreReady() {
        BusinessAccountView v = service.create(OWNER, head("Fees", "production", "paybill", "400200", null, KEY, SECRET, null, null, null));
        assertThat(v.capabilities()).filteredOn(c -> c.code().equals("DYNAMIC_QR")).allMatch(BusinessAccountView.Capability::ready);
        assertThat(v.capabilities()).filteredOn(c -> c.code().equals("STK_PUSH")).allMatch(c -> !c.ready() && c.needs().equals("STK passkey"));
        assertThat(v.capabilities()).filteredOn(c -> c.code().equals("B2C")).allMatch(c -> !c.ready() && c.needs().contains("initiator"));
    }

    @Test
    void badInputIsRefusedWithAPlainMessageAndNothingIsSaved() {
        fullHead();
        assertThatThrownBy(() -> service.create(OWNER, head("x", "sandbox", "buy_goods", "174379", null, KEY, SECRET, null, null, null)))
                .isInstanceOf(InvalidActionRequestException.class).hasMessageContaining("already saved");
        assertThatThrownBy(() -> service.create(OWNER, head("x", "sandbox", "buy_goods", "174379", "123456", KEY, SECRET, null, null, null)))
                .hasMessageContaining("add it as a branch");
        assertThatThrownBy(() -> service.create(OWNER, head("x", "sandbox", "paybill", "1234", null, KEY, SECRET, null, null, null))).hasMessageContaining("5 to 7 digits");
        assertThatThrownBy(() -> service.create(OWNER, head("x", "sandbox", "paybill", "400200", null, KEY, null, null, null, null))).hasMessageContaining("consumer secret is required");
        assertThatThrownBy(() -> service.create(OWNER, head("x", "sandbox", "paybill", "400200", null, KEY, SECRET, "admin", null, null))).hasMessageContaining("both the initiator name and the initiator password");
        assertThatThrownBy(() -> service.create(OWNER, head("x", "sandbox", "paybill", "400200", "123456", KEY, SECRET, null, null, null))).hasMessageContaining("belongs to Buy Goods");
        assertThatThrownBy(() -> service.create(OWNER, head("x", null, "paybill", "400200", null, KEY, SECRET, null, null, null))).hasMessageContaining("sandbox or production");
        assertThatThrownBy(() -> service.create(OWNER, head("x", "sandbox", "paybill", "400200", null, "PASTE_CONSUMER_KEY", SECRET, null, null, null))).hasMessageContaining("placeholder");
        assertThatThrownBy(() -> service.create(OWNER, head("x", "sandbox", "paybill", "400200", null, KEY, "has a space inside", null, null, null))).hasMessageContaining("no spaces");
        assertThat(rows).hasSize(1);
    }

    @Test
    void aBlankSecretOnAnEditKeepsTheSavedOneAndANewOneReplacesIt() {
        BusinessAccountView v = fullHead();
        BusinessAccountView renamed = service.update(OWNER, v.id(), head("Main shop (HQ)", "sandbox", "buy_goods", "174379", null, null, null, null, null, null));
        assertThat(renamed.label()).isEqualTo("Main shop (HQ)");
        assertThat(renamed.hasConsumerSecret() && renamed.hasInitiatorPassword() && renamed.hasPasskey()).isTrue();
        assertThat(service.resolve(OWNER, v.id()).consumerSecret()).isEqualTo(SECRET);

        service.update(OWNER, v.id(), head("Main shop (HQ)", "sandbox", "buy_goods", "174379", null, null, "n3wS3cr3tValue", null, null, null));
        ResolvedCredentials after = service.resolve(OWNER, v.id());
        assertThat(after.consumerSecret()).isEqualTo("n3wS3cr3tValue");
        assertThat(after.consumerKey()).isEqualTo(KEY);
        assertThat(after.toString()).doesNotContain("n3wS3cr3tValue", PASS);
    }

    // ---------------------------------------------------------------- branches

    @Test
    void aBranchSharesTheHeadsShortcodeAndCredentials() {
        BusinessAccountView h = fullHead();
        BusinessAccountView till = service.create(OWNER, branch("Till 1", h.id(), "123456", null));
        assertThat(till.shortcode()).isEqualTo("174379");
        assertThat(till.environment()).isEqualTo(DarajaEnvironment.SANDBOX);
        assertThat(till.inheritsCredentials()).isTrue();
        assertThat(till.consumerKeyHint()).isEqualTo("3456");
        assertThat(till.capabilities()).allMatch(BusinessAccountView.Capability::ready);

        ResolvedCredentials resolved = service.resolve(OWNER, till.id());
        assertThat(resolved.partyB()).isEqualTo("123456");
        assertThat(resolved.initiatorPassword()).isEqualTo(PASS);
        assertThat(resolved.passkey()).isEqualTo(PASSKEY);
        assertThat(resolved.stkTransactionType()).isEqualTo("CustomerBuyGoodsOnline");
        assertThat(service.resolve(OWNER, h.id()).partyB()).isEqualTo("174379");
    }

    @Test
    void branchMistakesAreRefused() {
        BusinessAccountView h = fullHead();
        BusinessAccountView till = service.create(OWNER, branch("Till 1", h.id(), "123456", null));
        assertThatThrownBy(() -> service.create(OWNER, branch("T", h.id(), null, null))).hasMessageContaining("needs its till number");
        assertThatThrownBy(() -> service.create(OWNER, branch("T", h.id(), "123456", null))).hasMessageContaining("already uses");
        assertThatThrownBy(() -> service.create(OWNER, branch("T", till.id(), "333333", null))).hasMessageContaining("head account");
        assertThatThrownBy(() -> service.create(OWNER, branch("T", UUID.randomUUID(), "333333", null))).hasMessageContaining("parent account was not found");
        assertThatThrownBy(() -> service.create(OWNER, new BusinessAccountRequest("T", null, null, null, "444444", null, h.id(), null, null, SECRET, null, null, null)))
                .hasMessageContaining("uses its head account's credentials");
        assertThatThrownBy(() -> service.create(OWNER, new BusinessAccountRequest("T", "production", null, null, "444444", null, h.id(), null, null, null, null, null, null)))
                .hasMessageContaining("same environment");
    }

    @Test
    void changingTheHeadShortcodeCarriesToItsBranches() {
        BusinessAccountView h = fullHead();
        BusinessAccountView till = service.create(OWNER, branch("Till 1", h.id(), "123456", null));
        service.update(OWNER, h.id(), head("Main shop", "sandbox", "buy_goods", "174380", null, null, null, null, null, null));
        assertThat(service.get(OWNER, till.id()).shortcode()).isEqualTo("174380");
    }

    @Test
    void deletingAHeadRemovesItsBranches() {
        BusinessAccountView h = fullHead();
        service.create(OWNER, branch("Till 1", h.id(), "123456", null));
        assertThat(service.delete(OWNER, h.id())).isEqualTo(2);
        assertThat(service.list(OWNER)).isEmpty();
    }

    // ---------------------------------------------------------------- owners

    @Test
    void anotherOwnerCannotSeeChangeOrUseAnAccount() {
        BusinessAccountView h = fullHead();
        String other = "254799999999";
        assertThat(service.list(other)).isEmpty();
        assertThatThrownBy(() -> service.get(other, h.id())).isInstanceOf(ActionNotFoundException.class);
        assertThatThrownBy(() -> service.update(other, h.id(), head("x", "sandbox", "paybill", "400200", null, KEY, SECRET, null, null, null))).isInstanceOf(ActionNotFoundException.class);
        assertThatThrownBy(() -> service.delete(other, h.id())).isInstanceOf(ActionNotFoundException.class);
        assertThatThrownBy(() -> service.resolve(other, h.id())).isInstanceOf(ActionNotFoundException.class);
        assertThatThrownBy(() -> vault.decrypt(rows.get(h.id()).getConsumerSecretEnc(), other + "|consumerSecret")).isInstanceOf(IllegalStateException.class);
    }

    // ---------------------------------------------------------------- Safaricom

    @Test
    void verifyAsksSafaricomWithTheHeadsKeyAndRemembersWhenItWorked() {
        BusinessAccountView h = fullHead();
        BusinessAccountView till = service.create(OWNER, branch("Till 1", h.id(), "123456", null));
        VerifyResult ok = service.verify(OWNER, till.id());
        assertThat(ok.ok()).isTrue();
        assertThat(ok.securityCredentialReady()).isTrue();
        assertThat(probed).containsExactly(KEY + "/" + SECRET);
        assertThat(service.get(OWNER, h.id()).verifiedAt()).isEqualTo(Instant.parse("2026-11-03T08:00:00Z"));
        assertThat(service.get(OWNER, till.id()).verifiedAt()).isEqualTo(Instant.parse("2026-11-03T08:00:00Z"));
    }

    @Test
    void verifyExplainsRefusalsAndOutagesAndChangingTheSecretClearsTheCheck() {
        BusinessAccountView h = fullHead();
        probeAnswer.set(new TokenProbe.Outcome(TokenProbe.Kind.REFUSED, 400));
        assertThat(service.verify(OWNER, h.id()).message()).contains("did not accept");
        probeAnswer.set(new TokenProbe.Outcome(TokenProbe.Kind.UNREACHABLE, 0));
        assertThat(service.verify(OWNER, h.id()).message()).contains("Could not reach");
        probeAnswer.set(new TokenProbe.Outcome(TokenProbe.Kind.ACCEPTED, 200));
        service.verify(OWNER, h.id());
        service.update(OWNER, h.id(), head("Main shop", "sandbox", "buy_goods", "174379", null, null, "an0therS3cret", null, null, null));
        assertThat(service.get(OWNER, h.id()).verifiedAt()).isNull();
    }

    @Test
    void theSecurityCredentialIsMadeFromTheSavedInitiatorPassword() {
        BusinessAccountView h = fullHead();
        String credential = service.securityCredential(OWNER, h.id());
        assertThat(credential).isNotBlank().doesNotContain(PASS);
        assertThat(Base64.getDecoder().decode(credential)).hasSize(256);
        BusinessAccountView noInitiator = service.create(OWNER, head("Fees", "sandbox", "paybill", "400200", null, KEY, SECRET, null, null, null));
        assertThatThrownBy(() -> service.securityCredential(OWNER, noInitiator.id())).hasMessageContaining("no initiator");
    }

    // ---------------------------------------------------------------- CSV

    @Test
    void aGoodFileAddsHeadsAndBranchesInAnyOrderAndADryRunSavesNothing() {
        byte[] file = csv(HEADER,
                "Till 1,sandbox,buy_goods,,123456,,174379,,,,,",
                "Main shop,sandbox,buy_goods,174379,,,," + KEY + "," + SECRET + ",testapi," + PASS + "," + PASSKEY,
                "School fees,production,paybill,400200,,,," + KEY + "," + SECRET + ",,,",
                "Nairobi,production,paybill,,,NAIROBI,400200,,,,,");
        ImportResult dry = service.importCsv(OWNER, file, true);
        assertThat(dry.issues()).isEmpty();
        assertThat(dry.saved()).isFalse();
        assertThat(rows).isEmpty();
        assertThat(dry.created()).isEqualTo(4);
        assertThat(dry.rows().get(0).row()).isEqualTo(2);

        ImportResult real = service.importCsv(OWNER, file, false);
        assertThat(real.saved()).isTrue();
        List<BusinessAccountView> all = service.list(OWNER);
        assertThat(all).hasSize(4);
        BusinessAccountView mainShop = all.stream().filter(a -> a.shortcode().equals("174379") && !a.inheritsCredentials()).findFirst().orElseThrow();
        BusinessAccountView till = all.stream().filter(a -> a.tillNumber().equals("123456")).findFirst().orElseThrow();
        assertThat(till.parentId()).isEqualTo(mainShop.id());
        assertThat(till.capabilities()).allMatch(BusinessAccountView.Capability::ready);
        assertThat(all.stream().filter(a -> a.accountReference().equals("NAIROBI")).findFirst().orElseThrow().environment()).isEqualTo(DarajaEnvironment.PRODUCTION);
    }

    @Test
    void importingAgainUpdatesInsteadOfDuplicatingAndBlankSecretsAreKept() {
        service.importCsv(OWNER, csv(HEADER, "Main shop,sandbox,buy_goods,174379,,,," + KEY + "," + SECRET + ",,,"), false);
        BusinessAccountView h = service.list(OWNER).get(0);
        ImportResult again = service.importCsv(OWNER, csv(HEADER, "Renamed,sandbox,buy_goods,174379,,,,,,,,"), false);
        assertThat(again.saved()).isTrue();
        assertThat(again.updated()).isEqualTo(1);
        assertThat(service.list(OWNER)).hasSize(1);
        assertThat(service.get(OWNER, h.id()).label()).isEqualTo("Renamed");
        assertThat(service.resolve(OWNER, h.id()).consumerSecret()).isEqualTo(SECRET);
    }

    @Test
    void aFileWithProblemsReportsEveryRowAndSavesNothing() {
        ImportResult result = service.importCsv(OWNER, csv(HEADER,
                "Bad code,sandbox,paybill,12,,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,",
                "No secret,sandbox,paybill,500500,,,,AAAAAAAAAAAA,,,,",
                "Paybill with till,sandbox,paybill,600600,123456,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,",
                "Orphan,sandbox,buy_goods,,777777,,999999,,,,,",
                "Dup A,sandbox,paybill,700700,,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,",
                "Dup B,sandbox,paybill,700700,,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,",
                "Same code one till,sandbox,buy_goods,810810,111111,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,",
                "Same code other till,sandbox,buy_goods,810810,222222,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,",
                "Weird env,mars,paybill,910910,,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,",
                "Weird type,sandbox,shop,920920,,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,"), false);
        assertThat(result.saved()).isFalse();
        assertThat(rows).isEmpty();
        assertThat(result.issues()).extracting(ImportResult.Issue::row).containsExactlyInAnyOrder(2, 3, 4, 5, 7, 9, 10, 11);
        assertThat(result.issues()).allMatch(i -> i.message().endsWith("."));
    }

    @Test
    void theUneditedTemplateIsRefusedBecauseOfItsPlaceholders() {
        ImportResult result = service.importCsv(OWNER, BusinessAccountCsv.TEMPLATE.getBytes(StandardCharsets.UTF_8), false);
        assertThat(result.saved()).isFalse();
        assertThat(result.issues()).anyMatch(i -> i.message().contains("placeholder"));
    }

    @Test
    void aBranchRowNeedsTheEnvironmentWhenTheHeadExistsInBoth() {
        service.importCsv(OWNER, csv(HEADER,
                "Both S,sandbox,paybill,555000,,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,",
                "Both P,production,paybill,555000,,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,"), false);
        ImportResult ambiguous = service.importCsv(OWNER, csv(HEADER, "Branch,,paybill,,,B1,555000,,,,,"), true);
        assertThat(ambiguous.issues()).anyMatch(i -> i.message().contains("both sandbox and production"));
        assertThat(service.importCsv(OWNER, csv(HEADER, "Branch,production,,,,B1,555000,,,,,"), false).saved()).isTrue();
    }

    @Test
    void withTheVaultOffReadingAndDryRunsWorkButSavingSecretsFailsClearly() {
        service.importCsv(OWNER, csv(HEADER, "Main,sandbox,paybill,400200,,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,"), false);
        BusinessAccountService off = new BusinessAccountService(store(), new SecretVault(""), null, null, Clock.systemUTC());
        assertThat(off.list(OWNER)).hasSize(1);
        assertThat(off.importCsv(OWNER, csv(HEADER, "N,sandbox,paybill,660660,,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,"), true).issues()).isEmpty();
        int before = rows.size();
        assertThatThrownBy(() -> off.create(OWNER, head("x", "sandbox", "paybill", "670670", null, KEY, SECRET, null, null, null))).isInstanceOf(VaultNotConfiguredException.class);
        assertThatThrownBy(() -> off.importCsv(OWNER, csv(HEADER, "N,sandbox,paybill,660660,,,,AAAAAAAAAAAA,BBBBBBBBBBBB,,,"), false)).isInstanceOf(VaultNotConfiguredException.class);
        assertThat(rows).hasSize(before);
    }
}
