package com.mganji.agent.service;

import com.mganji.agent.chat.ChatChannel.ListRow;
import com.mganji.agent.config.AgentProperties;
import com.mganji.agent.config.ServiceProperties;
import com.mganji.agent.model.Identity;
import com.mganji.agent.model.Role;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** The welcome message and tap-to-choose menu. Choosing a row is turned into a normal request to the agent. */
@Component
public class MenuService {

    private static final Pattern TRIGGER = Pattern.compile(
            "^\\s*(hi+|hello+|hey+|hallo|habari|hujambo|jambo|mambo|niaje|sasa|start|menu|help|msaada|anza|salaam|good (morning|afternoon|evening))[\\s!.?]*$",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> REQUESTS = Map.ofEntries(
            Map.entry("MENU:pay", "I want to make a payment."),
            Map.entry("MENU:send", "I want to send money."),
            Map.entry("MENU:paybill", "I want to pay a Paybill."),
            Map.entry("MENU:till", "I want to pay a Till."),
            Map.entry("MENU:pochi", "I want to pay a Pochi la Biashara."),
            Map.entry("MENU:airtime", "I want to buy airtime."),
            Map.entry("MENU:ratiba", "I want to set up a Ratiba."),
            Map.entry("MENU:requests", "Show my recent requests."),
            Map.entry("MENU:contacts", "Help me find a contact to pay."),
            Map.entry("MENU:status", "What is the status of my last payment?"),
            Map.entry("MENU:receipt", "Please send me the receipt for my last payment."),
            Map.entry("MENU:history", "Show me my recent payments."),
            Map.entry("MENU:refund", "I want to request a refund."),
            Map.entry("MENU:qr", "I want an M-Pesa QR code to pay with."),
            Map.entry("MENU:human", "I need to speak to a person."),
            Map.entry("MENU:lookup", "I want to look up a transaction."),
            Map.entry("MENU:payout", "I want to request a payout."),
            Map.entry("MENU:approvals", "Show my pending approvals."),
            Map.entry("MENU:balance", "What is the account balance?"));

    private final ServiceProperties.Business business;
    private final boolean businessPayments;

    public MenuService(ServiceProperties.Business business, AgentProperties props) {
        this.business = business;
        this.businessPayments = props.businessPayments();
    }

    public boolean isTrigger(String text) {
        return text != null && TRIGGER.matcher(text).matches();
    }

    /** The sentence a menu choice stands for, or empty when the id is not a menu id. */
    public Optional<String> requestFor(String actionId) {
        return Optional.ofNullable(actionId).map(REQUESTS::get);
    }

    public String welcomeText(Identity who) {
        String greeting = who.name() != null ? "Hello " + who.name().split("\\s+")[0] + "." : "Hello.";
        return greeting + " Welcome to " + business.name() + ". I can help you send money, pay Paybills and Tills, buy airtime and more from your own M-Pesa. "
                + "You can type or send a voice note, a photo or a PDF.\n\nWhat would you like to do?";
    }

    /** The menu shows at most ten rows. */
    public List<ListRow> rowsFor(Role role) {
        List<ListRow> rows = new ArrayList<>();
        rows.add(new ListRow("MENU:send", "Send money", "To a number or a contact"));
        rows.add(new ListRow("MENU:paybill", "Pay a Paybill", "Bills, school fees, rent"));
        rows.add(new ListRow("MENU:till", "Pay a Till", "Buy goods and services"));
        rows.add(new ListRow("MENU:pochi", "Pochi la Biashara", "Pay a small business"));
        rows.add(new ListRow("MENU:airtime", "Buy airtime", "For you or someone else"));
        rows.add(new ListRow("MENU:ratiba", "Set up a Ratiba", "Scheduled payments"));
        rows.add(new ListRow("MENU:requests", "My requests", "What you have asked for"));
        rows.add(new ListRow("MENU:contacts", "Find a contact", "Search your connected contacts"));
        if (businessPayments) {
            rows.add(new ListRow("MENU:pay", "Pay the business", "With an M-Pesa prompt"));
            if (role.atLeast(Role.APPROVER)) {
                rows.add(new ListRow("MENU:approvals", "Pending approvals", "Review payout requests"));
            } else if (role.atLeast(Role.STAFF)) {
                rows.add(new ListRow("MENU:payout", "Request a payout", "Needs a second approver"));
            } else {
                rows.add(new ListRow("MENU:human", "Talk to a person", "We will follow up"));
            }
        } else {
            rows.add(new ListRow("MENU:human", "Talk to a person", "We will follow up"));
        }
        return rows;
    }
}
