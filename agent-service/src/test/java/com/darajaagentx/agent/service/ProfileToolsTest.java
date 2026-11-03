package com.darajaagentx.agent.service;

import com.darajaagentx.agent.client.ProfileApi;
import com.darajaagentx.agent.client.ProfileApi.ProfileApiException;
import com.darajaagentx.agent.model.Identity;
import com.darajaagentx.agent.model.Role;
import com.darajaagentx.agent.service.ToolExecutor.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What the assistant can do with a customer's profile, saved addresses and emailed reports, and what it can never do. */
class ProfileToolsTest {

    private static final String PHONE = "254712345678";

    /** Remembers what it was asked and answers with whatever the test sets. */
    static class FakeApi implements ProfileApi {
        Map<String, Object> profile = new LinkedHashMap<>(Map.of("phone", PHONE, "fullName", "Bill Graham", "email", "bill@example.com",
                "emailVerified", true, "notifySignIn", true, "notifyPayments", true, "hasPicture", false, "emailDelivers", true));
        List<Map<String, Object>> recipients = new ArrayList<>();
        Map<String, Object> pasteResult = Map.of("added", 2, "alreadySaved", 0, "invalid", 0);
        Map<String, Object> reportResult = Map.of("delivered", true, "sentTo", List.of("bill@example.com"), "fileName", "report.pdf", "format", "PDF", "rows", 3);
        RuntimeException failure;
        Map<String, Object> lastChanges;
        Map<String, Object> lastReport;
        String codeRequestedFor;
        String codeChecked;
        String pasted;
        String addedEmail;
        String addedName;
        String removed;

        private void maybeFail() {
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public Map<String, Object> profile(String phone) {
            maybeFail();
            return profile;
        }

        @Override
        public Map<String, Object> updateProfile(String phone, Map<String, Object> changes) {
            maybeFail();
            lastChanges = changes;
            return profile;
        }

        @Override
        public Map<String, Object> requestEmailCode(String phone, String email) {
            maybeFail();
            codeRequestedFor = email;
            return Map.of("expiresAt", "2026-11-03T10:00:00Z");
        }

        @Override
        public Map<String, Object> verifyEmail(String phone, String code) {
            maybeFail();
            codeChecked = code;
            return Map.of("email", "new@example.com");
        }

        @Override
        public List<Map<String, Object>> recipients(String phone) {
            return recipients;
        }

        @Override
        public Map<String, Object> addRecipient(String phone, String email, String name) {
            addedEmail = email;
            addedName = name;
            return Map.of("email", email, "name", name);
        }

        @Override
        public Map<String, Object> addRecipients(String phone, String text) {
            pasted = text;
            return pasteResult;
        }

        @Override
        public Map<String, Object> removeRecipient(String phone, String target) {
            removed = target;
            return Map.of("removed", "acc@example.com");
        }

        @Override
        public Map<String, Object> sendReport(String phone, Map<String, Object> request) {
            maybeFail();
            lastReport = request;
            return reportResult;
        }
    }

    private final FakeApi api = new FakeApi();
    private ProfileTools tools;

    @BeforeEach
    void setUp() {
        tools = new ProfileTools(api);
    }

    private static ToolContext ctx() {
        return new ToolContext(new Identity(PHONE, Role.CUSTOMER, null), "wamid.7", "TEXT");
    }

    private static JsonNode json(String text) {
        return JsonMapper.builder().build().readTree(text);
    }

    @Test
    void theToolsAreDefinedAndRecognised() {
        assertThat(ProfileTools.definitions()).extracting(d -> d.get("name")).containsExactly("get_my_profile", "update_my_profile",
                "request_email_verification", "confirm_email_code", "list_email_recipients", "save_email_recipients", "remove_email_recipient", "send_report_email");
        assertThat(tools.handles("send_report_email")).isTrue();
        assertThat(tools.handles("list_business_accounts")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyDefinitionHasASchemaWithItsRequiredFieldsDeclared() {
        for (Map<String, Object> d : ProfileTools.definitions()) {
            Map<String, Object> schema = (Map<String, Object>) d.get("input_schema");
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            for (Object required : (List<?>) schema.get("required")) {
                assertThat(props).containsKey((String) required);
            }
        }
    }

    @Test
    void theProfileIsShownWithoutAnythingSecret() {
        Result r = tools.execute(ctx(), "get_my_profile", json("{}"));
        assertThat(r.error()).isFalse();
        assertThat(r.content()).contains("Bill Graham").contains("bill@example.com (verified)").contains("sign in on, payments on");
    }

    @Test
    void anUnverifiedEmailIsSaidToBlockReports() {
        api.profile = new LinkedHashMap<>(api.profile);
        api.profile.put("emailVerified", false);
        assertThat(tools.execute(ctx(), "get_my_profile", json("{}")).content()).contains("not verified");
    }

    @Test
    void onlyTheFieldsAskedForAreChanged() {
        Result r = tools.execute(ctx(), "update_my_profile", json("{\"first_name\":\"Billy\",\"city\":\"Nairobi\",\"notify_sign_in\":false}"));
        assertThat(r.error()).isFalse();
        assertThat(api.lastChanges).containsOnlyKeys("firstName", "city", "notifySignIn");
        assertThat(api.lastChanges.get("notifySignIn")).isEqualTo(false);
    }

    @Test
    void emptyTextClearsAndLocationNeedsBothNumbers() {
        tools.execute(ctx(), "update_my_profile", json("{\"middle_name\":\"\"}"));
        assertThat(api.lastChanges.get("middleName")).isEqualTo("");
        api.lastChanges = null;
        Result half = tools.execute(ctx(), "update_my_profile", json("{\"latitude\":-1.29}"));
        assertThat(half.error()).isTrue();
        assertThat(api.lastChanges).isNull();
        tools.execute(ctx(), "update_my_profile", json("{\"latitude\":-1.29,\"longitude\":36.82}"));
        assertThat(api.lastChanges).containsKeys("latitude", "longitude");
    }

    @Test
    void anEmptyUpdateIsRefusedAndPointsToTheEmailTool() {
        Result r = tools.execute(ctx(), "update_my_profile", json("{}"));
        assertThat(r.error()).isTrue();
        assertThat(r.content()).contains("request_email_verification");
    }

    @Test
    void theEmailCodeIsRequestedForTheAddressTypedAndNeverShown() {
        Result r = tools.execute(ctx(), "request_email_verification", json("{\"email\":\"new@example.com\"}"));
        assertThat(r.error()).isFalse();
        assertThat(api.codeRequestedFor).isEqualTo("new@example.com");
        assertThat(r.content()).contains("You cannot see the code");
        assertThat(tools.execute(ctx(), "request_email_verification", json("{\"email\":\"not an email\"}")).error()).isTrue();
    }

    @Test
    void ifTheServerCannotSendEmailTheCustomerIsToldTheCodeWillNotArrive() {
        api.profile = new LinkedHashMap<>(api.profile);
        api.profile.put("emailDelivers", false);
        Result r = tools.execute(ctx(), "request_email_verification", json("{\"email\":\"new@example.com\"}"));
        assertThat(r.content()).contains("not set up to send email");
    }

    @Test
    void theCodeIsCheckedWithSpacesRemovedAndBadCodesNeverReachTheServer() {
        Result r = tools.execute(ctx(), "confirm_email_code", json("{\"code\":\"123 456\"}"));
        assertThat(r.error()).isFalse();
        assertThat(api.codeChecked).isEqualTo("123456");
        assertThat(r.content()).contains("new@example.com");
        api.codeChecked = null;
        assertThat(tools.execute(ctx(), "confirm_email_code", json("{\"code\":\"abc\"}")).error()).isTrue();
        assertThat(api.codeChecked).isNull();
    }

    @Test
    void addressesAreListedSavedAndRemoved() {
        assertThat(tools.execute(ctx(), "list_email_recipients", json("{}")).content()).contains("No email addresses");
        api.recipients = List.of(Map.of("email", "acc@example.com", "name", "Accountant"));
        assertThat(tools.execute(ctx(), "list_email_recipients", json("{}")).content()).contains("acc@example.com (Accountant)");

        tools.execute(ctx(), "save_email_recipients", json("{\"text\":\"boss@example.com\",\"name\":\"Boss\"}"));
        assertThat(api.addedEmail).isEqualTo("boss@example.com");
        assertThat(api.addedName).isEqualTo("Boss");

        Result many = tools.execute(ctx(), "save_email_recipients", json("{\"text\":\"a@x.com, b@x.com\"}"));
        assertThat(api.pasted).isEqualTo("a@x.com, b@x.com");
        assertThat(many.content()).contains("2 new address");

        assertThat(tools.execute(ctx(), "remove_email_recipient", json("{\"target\":\"Accountant\"}")).content()).contains("acc@example.com");
        assertThat(api.removed).isEqualTo("Accountant");
    }

    @Test
    void aListWithBadEntriesAndAFullListIsExplained() {
        api.pasteResult = Map.of("added", 1, "alreadySaved", 2, "invalid", 3, "invalidSamples", List.of("hello", "123"), "limitReached", true);
        String text = tools.execute(ctx(), "save_email_recipients", json("{\"text\":\"anything\"}")).content();
        assertThat(text).contains("1 new").contains("2 were already saved").contains("3 entries were not email addresses").contains("hello").contains("list is full");
    }

    @Test
    void aReportWithNoAddressGoesToTheProfileEmailOnly() {
        Result r = tools.execute(ctx(), "send_report_email", json("{\"source\":\"business_requests\",\"format\":\"XLSX\",\"status\":\"SUCCEEDED\"}"));
        assertThat(r.error()).isFalse();
        assertThat(api.lastReport.get("source")).isEqualTo("BUSINESS_REQUESTS");
        assertThat(api.lastReport.get("format")).isEqualTo("xlsx");
        assertThat(api.lastReport.get("toMe")).isEqualTo(true);
        assertThat(api.lastReport).doesNotContainKey("to");
        assertThat(api.lastReport.get("status")).isEqualTo("SUCCEEDED");
        assertThat(r.content()).contains("report.pdf").contains("bill@example.com");
    }

    @Test
    void namedAddressesAreUsedAsGivenAndDoNotAlsoGoToTheProfileEmail() {
        api.reportResult = Map.of("delivered", true, "sentTo", List.of("a@x.com", "b@x.com"), "fileName", "r.csv", "format", "CSV", "rows", 1);
        Result r = tools.execute(ctx(), "send_report_email", json("{\"source\":\"PAYMENTS\",\"format\":\"csv\",\"to\":[\"a@x.com\",\"b@x.com\"]}"));
        assertThat(api.lastReport.get("to")).isEqualTo(List.of("a@x.com", "b@x.com"));
        assertThat(api.lastReport.get("toMe")).isEqualTo(false);
        assertThat(r.content()).contains("also gets a notice");
    }

    @Test
    void savedAddressesCanBeUsedAndDateFiltersArePassedOn() {
        tools.execute(ctx(), "send_report_email", json("{\"source\":\"PAYMENTS\",\"to_saved\":true,\"from\":\"2026-11-01\",\"until\":\"2026-11-30\",\"limit\":50}"));
        assertThat(api.lastReport.get("toSaved")).isEqualTo(true);
        assertThat(api.lastReport.get("toMe")).isEqualTo(false);
        assertThat(api.lastReport.get("from")).isEqualTo("2026-11-01");
        assertThat(api.lastReport.get("limit")).isEqualTo(50);
    }

    @Test
    void aTableFromTheChatIsSentSquareAndFiltersAreLeftOut() {
        tools.execute(ctx(), "send_report_email", json("{\"source\":\"TABLE\",\"title\":\"Sales\",\"columns\":[\"Day\",\"Total\"],\"numeric_columns\":[\"Total\"],"
                + "\"rows\":[[\"Mon\",1200],[\"Tue\"],[\"Wed\",\"300\",\"extra\"]],\"status\":\"SUCCEEDED\"}"));
        assertThat(api.lastReport.get("columns")).isEqualTo(List.of("Day", "Total"));
        assertThat(api.lastReport.get("rows")).isEqualTo(List.of(List.of("Mon", "1200"), List.of("Tue", ""), List.of("Wed", "300")));
        assertThat(api.lastReport.get("numericColumns")).isEqualTo(List.of("Total"));
        assertThat(api.lastReport).doesNotContainKey("status");
    }

    @Test
    void aTableWithoutRowsAndAnUnknownSourceAreRefusedBeforeAnythingIsSent() {
        assertThat(tools.execute(ctx(), "send_report_email", json("{\"source\":\"TABLE\",\"columns\":[\"A\"],\"rows\":[]}")).error()).isTrue();
        assertThat(tools.execute(ctx(), "send_report_email", json("{\"source\":\"SECRETS\"}")).error()).isTrue();
        assertThat(api.lastReport).isNull();
    }

    @Test
    void ifNothingWasReallyEmailedTheAssistantIsToldNotToSayItWas() {
        api.reportResult = Map.of("delivered", false, "sentTo", List.of("bill@example.com"), "fileName", "report.pdf", "format", "PDF", "rows", 3);
        Result r = tools.execute(ctx(), "send_report_email", json("{\"source\":\"PAYMENTS\"}"));
        assertThat(r.error()).isTrue();
        assertThat(r.content()).contains("nothing was emailed").contains("not sent");
    }

    @Test
    void whenAgentXApiIsDownTheMessageIsFriendlyAndWhenItRefusesTheReasonIsShown() {
        api.failure = new ProfileApiException(0, "not reachable");
        assertThat(tools.execute(ctx(), "get_my_profile", json("{}")).content()).contains("not available right now");
        api.failure = new ProfileApiException(400, "Verify your email first. Reports to your own address need a verified email.");
        Result r = tools.execute(ctx(), "send_report_email", json("{\"source\":\"PAYMENTS\"}"));
        assertThat(r.error()).isTrue();
        assertThat(r.content()).contains("Verify your email first");
    }
}
