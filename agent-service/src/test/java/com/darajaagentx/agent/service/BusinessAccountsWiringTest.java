package com.darajaagentx.agent.service;

import com.darajaagentx.agent.TestSupport;
import com.darajaagentx.agent.client.AgentXClient;
import com.darajaagentx.agent.client.PaymentClient;
import com.darajaagentx.agent.config.AgentProperties;
import com.darajaagentx.agent.model.Identity;
import com.darajaagentx.agent.model.Role;
import com.darajaagentx.agent.service.ToolExecutor.Result;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The customer's saved business accounts are reachable from the chat: the model is shown the tools and the executor runs them. */
class BusinessAccountsWiringTest {

    private static final String PHONE = "254712345678";

    private static JsonNode json(String text) {
        return JsonMapper.builder().build().readTree(text);
    }

    @Test
    void everyCustomerIsOfferedTheFourBusinessAccountToolsWithoutDuplicates() {
        for (AgentProperties props : List.of(TestSupport.walletProps(), TestSupport.props())) {
            ToolCatalog catalog = new ToolCatalog(TestSupport.mapper(), props);
            List<String> names = new ArrayList<>();
            catalog.toolsFor(Role.CUSTOMER).forEach(t -> names.add(t.path("name").asString()));

            assertThat(names).contains("list_business_accounts", "prepare_business_request", "list_business_requests", "cancel_business_request");
            assertThat(names).doesNotHaveDuplicates();
        }
    }

    @Test
    void theExecutorRunsThemForTheSignedInCustomer() {
        BusinessToolsTest.FakeApi api = new BusinessToolsTest.FakeApi();
        api.accounts = List.of(Map.of("label", "Daraja STK", "environment", "SANDBOX", "shortcodeType", "PAYBILL", "shortcode", "174379",
                "accountReference", "STK01", "capabilities", List.of()));
        ToolExecutor executor = new ToolExecutor(mock(PaymentClient.class), null, mock(ReplySender.class), TestSupport.walletProps(),
                TestSupport.business(), new WalletTools(mock(AgentXClient.class)), new BusinessTools(api), new ProfileTools(null));


        Result r = executor.execute(new ToolContext(new Identity(PHONE, Role.CUSTOMER, null), "wamid.1", "TEXT"), "list_business_accounts", json("{}"));

        assertThat(r.error()).isFalse();
        assertThat(r.content()).contains("Daraja STK").contains("paybill 174379").contains("account STK01");
    }

    @Test
    void theAssistantIsToldToReadSavedPaybillsInsteadOfAskingForThem() {
        String rules = new SystemPrompt(TestSupport.business(), TestSupport.walletProps()).rules();

        assertThat(rules).contains("list_business_accounts").contains("prepare_pay_bill").contains("Daraja STK")
                .contains("Never say you cannot see their saved details");
    }

    @Test
    void theProfileToolsAreOfferedAndReadTheSignedInCustomersProfile() {
        for (AgentProperties props : List.of(TestSupport.walletProps(), TestSupport.props())) {
            List<String> names = new ArrayList<>();
            new ToolCatalog(TestSupport.mapper(), props).toolsFor(Role.CUSTOMER).forEach(t -> names.add(t.path("name").asString()));
            assertThat(names).contains("get_my_profile", "update_my_profile", "send_report_email");
            assertThat(names).doesNotHaveDuplicates();
        }

        ToolExecutor executor = new ToolExecutor(mock(PaymentClient.class), null, mock(ReplySender.class), TestSupport.walletProps(),
                TestSupport.business(), new WalletTools(mock(AgentXClient.class)), new BusinessTools(null), new ProfileTools(new ProfileToolsTest.FakeApi()));

        Result r = executor.execute(new ToolContext(new Identity(PHONE, Role.CUSTOMER, null), "wamid.1", "TEXT"), "get_my_profile", json("{}"));

        assertThat(r.error()).isFalse();
        assertThat(r.content()).contains("Bill Graham").contains("bill@example.com");
    }

    @Test
    void thePromptSaysToReadTheProfileAndToUseMyNumberForAnAccount() {
        String rules = new SystemPrompt(TestSupport.business(), TestSupport.walletProps()).rules();

        assertThat(rules).contains("get_my_profile").contains("never say you cannot see their profile").contains("use_my_number");
    }
}
