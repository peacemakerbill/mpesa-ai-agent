package com.mganji.agent.service;

import com.mganji.agent.config.AgentProperties;
import com.mganji.agent.model.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

/**
 * The tools the model may use, by role. A customer's model never even sees the staff tools. This is a
 * convenience and a way to keep the model on task: the real permission checks run again in ToolExecutor and
 * once more in payment-service.
 *
 * Everyone gets the wallet tools (prepare a request for their own M-Pesa) and escalate. The tools that use
 * payment-service (business payments, refunds, payouts, staff work) exist only when agent.business-payments is on.
 */
@Component
public class ToolCatalog {

    public static final String PREPARE_SEND_MONEY = "prepare_send_money";
    public static final String PREPARE_PAY_BILL = "prepare_pay_bill";
    public static final String PREPARE_PAY_TILL = "prepare_pay_till";
    public static final String PREPARE_PAY_POCHI = "prepare_pay_pochi";
    public static final String PREPARE_BUY_AIRTIME = "prepare_buy_airtime";
    public static final String PREPARE_RATIBA = "prepare_ratiba";
    public static final String LIST_WALLET_REQUESTS = "list_wallet_requests";
    public static final String CANCEL_WALLET_REQUEST = "cancel_wallet_request";
    public static final String RECORD_CONFIRMATION = "record_mpesa_confirmation";
    public static final String FIND_CONTACT = "find_contact";

    /** Tools that need payment-service. Refused when business payments are switched off. */
    public static final Set<String> BUSINESS_TOOLS = Set.of("propose_payment", "check_payment_status", "list_my_payments",
            "send_receipt", "request_refund", "generate_payment_qr", "list_my_requests", "lookup_transaction",
            "lookup_customer_payments", "request_payout", "list_pending_approvals", "review_approval", "account_balance",
            "request_reversal");

    public static final String PROPOSE_PAYMENT = "propose_payment";
    public static final String CHECK_PAYMENT_STATUS = "check_payment_status";
    public static final String LIST_MY_PAYMENTS = "list_my_payments";
    public static final String SEND_RECEIPT = "send_receipt";
    public static final String REQUEST_REFUND = "request_refund";
    public static final String GENERATE_PAYMENT_QR = "generate_payment_qr";
    public static final String LIST_MY_REQUESTS = "list_my_requests";
    public static final String ESCALATE = "escalate_to_human";
    public static final String LOOKUP_TRANSACTION = "lookup_transaction";
    public static final String LOOKUP_CUSTOMER_PAYMENTS = "lookup_customer_payments";
    public static final String REQUEST_PAYOUT = "request_payout";
    public static final String LIST_PENDING_APPROVALS = "list_pending_approvals";
    public static final String REVIEW_APPROVAL = "review_approval";
    public static final String ACCOUNT_BALANCE = "account_balance";
    public static final String REQUEST_REVERSAL = "request_reversal";

    private final ObjectMapper mapper;
    private final boolean businessPayments;

    @Autowired
    public ToolCatalog(ObjectMapper mapper, AgentProperties props) {
        this(mapper, props.businessPayments());
    }

    public ToolCatalog(ObjectMapper mapper, boolean businessPayments) {
        this.mapper = mapper;
        this.businessPayments = businessPayments;
    }

    /** With every tool switched on. */
    public ToolCatalog(ObjectMapper mapper) {
        this(mapper, true);
    }

    public ArrayNode toolsFor(Role role) {
        ArrayNode tools = mapper.createArrayNode();
        walletTools().forEach(tools::add);
        tools.add(escalateTool());
        if (!businessPayments) {
            return tools;
        }
        customerTools().forEach(tools::add);
        if (role.atLeast(Role.STAFF)) {
            staffTools().forEach(tools::add);
        }
        if (role.atLeast(Role.APPROVER)) {
            approverTools().forEach(tools::add);
        }
        if (role.atLeast(Role.ADMIN)) {
            adminTools().forEach(tools::add);
        }
        return tools;
    }

    /** Which role is needed for a tool. Used by ToolExecutor to double check. */
    public static Role requiredRole(String tool) {
        return switch (tool) {
            case LOOKUP_TRANSACTION, LOOKUP_CUSTOMER_PAYMENTS, REQUEST_PAYOUT -> Role.STAFF;
            case LIST_PENDING_APPROVALS, REVIEW_APPROVAL -> Role.APPROVER;
            case ACCOUNT_BALANCE, REQUEST_REVERSAL -> Role.ADMIN;
            default -> Role.CUSTOMER;
        };
    }

    // ------------------------------------------------------------ definitions

    private List<ObjectNode> customerTools() {
        return List.of(
                tool(PROPOSE_PAYMENT,
                        "Ask the customer to confirm a payment. This sends them a confirmation message with Pay and Cancel buttons; "
                                + "nothing is charged until they tap Pay. The M-Pesa prompt always goes to the number they are chatting from. "
                                + "Call it once they have told you the amount.",
                        props(integer("amount", "Amount in whole Kenyan shillings, at least 1"),
                                string("reference", "Short reference such as an order or invoice number. Letters, digits, - and _ only, at most 12 characters. Optional.")),
                        "amount"),
                tool(CHECK_PAYMENT_STATUS,
                        "Look up the status of the customer's own payment. Without payment_id it checks their most recent payment.",
                        props(string("payment_id", "Payment id from an earlier answer. Optional.")), new String[0]),
                tool(LIST_MY_PAYMENTS,
                        "List the customer's recent payments, newest first.",
                        props(integer("limit", "How many to show, 1 to 10. Default 5.")), new String[0]),
                tool(SEND_RECEIPT,
                        "Send the customer a PDF receipt for one of their successful payments. Without payment_id it uses their most recent successful payment.",
                        props(string("payment_id", "Payment id. Optional.")), new String[0]),
                tool(REQUEST_REFUND,
                        "Ask the customer to confirm a refund request for one of their own successful payments. Refunds are reviewed and approved by staff before any money moves.",
                        props(string("payment_id", "Id of the payment to refund"),
                                integer("amount", "Part of the payment to refund, in KES. Leave out for the full amount."),
                                string("reason", "Why the customer wants the refund, in a few words")),
                        "payment_id", "reason"),
                tool(GENERATE_PAYMENT_QR,
                        "Create an M-Pesa QR code the customer can scan in their M-Pesa app to pay a fixed amount, and send it to them as a picture.",
                        props(integer("amount", "Amount in whole KES"),
                                string("reference", "Reference, letters and digits, at most 12 characters")),
                        "amount", "reference"),
                tool(LIST_MY_REQUESTS,
                        "Show the status of the customer's own refund and other requests.",
                        props(), new String[0]));
    }

    private ObjectNode escalateTool() {
        return tool(ESCALATE,
                "Hand the conversation to a human. Use for disputes, suspected fraud, wrong payments, complaints, or anything you cannot resolve.",
                props(string("reason", "A clear summary for the staff member: what happened and what the customer wants")),
                "reason");
    }

    private List<ObjectNode> walletTools() {
        return List.of(
                tool(PREPARE_SEND_MONEY,
                        "Prepare a Send Money request from the customer's own M-Pesa to a person. This does not send anything: the customer gets a link, "
                                + "reviews it in the M-Ganji app, approves with fingerprint or face, and enters their M-Pesa PIN inside M-Pesa. "
                                + "Give recipient_phone for a number, or contact_name when the customer names someone in their phone contacts (for example \"Mama\"), "
                                + "never both.",
                        withCommon(string("recipient_phone", "Kenyan phone number to send to. Not the customer's own."),
                                string("contact_name", "Name of a contact on the customer's phone. The app looks it up on the phone, you never see contacts."),
                                string("recipient_name", "The recipient's name if the customer said it. Optional."),
                                integer("amount", "Amount in whole Kenyan shillings, at least 1")),
                        "amount"),
                tool(PREPARE_PAY_BILL,
                        "Prepare a Lipa na M-Pesa Paybill payment from the customer's own M-Pesa. Needs the Paybill number and the account number.",
                        withCommon(string("paybill", "Paybill business number, 5 to 7 digits"),
                                string("account", "Account number or reference the customer must enter, for example a meter or student number"),
                                string("business_name", "Name of the business if known, optional"),
                                integer("amount", "Amount in whole Kenyan shillings")),
                        "amount", "paybill", "account"),
                tool(PREPARE_PAY_TILL,
                        "Prepare a Buy Goods payment to a Till number from the customer's own M-Pesa.",
                        withCommon(string("till", "Till number, 5 to 7 digits"),
                                string("business_name", "Name of the business if known, optional"),
                                integer("amount", "Amount in whole Kenyan shillings")),
                        "amount", "till"),
                tool(PREPARE_PAY_POCHI,
                        "Prepare a Pochi la Biashara payment, which is paid to the business owner's phone number.",
                        withCommon(string("pochi_phone", "Phone number of the Pochi la Biashara owner"),
                                string("business_name", "Name of the business if known, optional"),
                                integer("amount", "Amount in whole Kenyan shillings")),
                        "amount", "pochi_phone"),
                tool(PREPARE_BUY_AIRTIME,
                        "Prepare an airtime purchase from the customer's own M-Pesa. Leave for_phone out to top up their own number.",
                        withCommon(string("for_phone", "Phone number to top up. Optional, defaults to the customer's own number."),
                                integer("amount", "Amount of airtime in whole Kenyan shillings")),
                        "amount"),
                tool(PREPARE_RATIBA,
                        "Prepare a Ratiba (scheduled, repeating M-Pesa payment). The customer finishes setting it up in M-Pesa. "
                                + "Give the target and the details that target needs.",
                        withCommon(enumString("target", "What the Ratiba pays", "SEND_MONEY", "PAY_BILL", "PAY_TILL"),
                                enumString("frequency", "How often", "DAILY", "WEEKLY", "MONTHLY"),
                                string("start_date", "First payment date, like 2026-11-01"),
                                string("end_date", "Last payment date, optional"),
                                string("recipient_phone", "For SEND_MONEY: a phone number"),
                                string("contact_name", "For SEND_MONEY: a contact name on the customer's phone"),
                                string("recipient_name", "For SEND_MONEY: the recipient's name, optional"),
                                string("paybill", "For PAY_BILL: the Paybill number"),
                                string("account", "For PAY_BILL: the account number"),
                                string("till", "For PAY_TILL: the Till number"),
                                string("business_name", "Name of the business, optional"),
                                integer("amount", "Amount of each payment in whole Kenyan shillings")),
                        "amount", "target", "frequency", "start_date"),
                tool(LIST_WALLET_REQUESTS,
                        "List the customer's recent requests prepared for their own M-Pesa and what happened to each.",
                        props(integer("limit", "How many, 1 to 10. Default 5.")), new String[0]),
                tool(CANCEL_WALLET_REQUEST,
                        "Cancel a prepared request that the customer has not yet taken to M-Pesa. Without request_id it cancels the newest waiting one.",
                        props(string("request_id", "Id of the request. Optional.")), new String[0]),
                tool(RECORD_CONFIRMATION,
                        "Keep the M-Pesa confirmation code the customer reports (from a forwarded SMS or screenshot) against the matching request. "
                                + "It is recorded as reported by the customer and is not verified with Safaricom.",
                        props(string("receipt", "The transaction code printed at the start of the M-Pesa message, 10 letters and digits"),
                                string("request_id", "Id of the request, optional"),
                                integer("amount", "Amount shown in the message, used to find the matching request. Optional.")),
                        "receipt"),
                tool(FIND_CONTACT,
                        "Open the contact finder in the customer's app for a name, so they can pick the right contact (and number) from the contacts they have "
                                + "connected. Use it when they name a person instead of giving a number. It sends nothing and prepares nothing: the person they pick "
                                + "arrives in the chat as a shared contact, and then you can prepare the request.",
                        props(string("name", "The name the customer used, for example Mama or John Kamau")), "name"));
    }

    private ObjectNode withCommon(ObjectNode... fields) {
        ObjectNode[] all = java.util.Arrays.copyOf(fields, fields.length + 2);
        all[fields.length] = string("note", "What the payment is for, a few words. Optional.");
        all[fields.length + 1] = enumString("details_from",
                "Where the numbers and names came from if not typed: VOICE, IMAGE, DOCUMENT or CONTACT. Optional.",
                "TEXT", "VOICE", "IMAGE", "DOCUMENT", "CONTACT");
        return props(all);
    }

    private List<ObjectNode> staffTools() {
        return List.of(
                tool(LOOKUP_TRANSACTION,
                        "Look up an M-Pesa transaction by its receipt number with Safaricom. The result arrives in this chat a few seconds later.",
                        props(string("receipt", "M-Pesa receipt number such as QGH7ABC123")), "receipt"),
                tool(LOOKUP_CUSTOMER_PAYMENTS,
                        "List the recent payments of a customer, by their phone number.",
                        props(string("phone", "Customer phone number"), integer("limit", "How many, 1 to 10. Default 5.")), "phone"),
                tool(REQUEST_PAYOUT,
                        "Ask the staff member to confirm a payout request (money sent from the business account to a phone number). "
                                + "Another approver must approve it before any money moves.",
                        props(string("recipient_phone", "Phone number to pay"),
                                integer("amount", "Amount in whole KES"),
                                string("reason", "Purpose of the payout"),
                                enumString("payout_type", "WITHDRAWAL for business withdrawals, DISBURSEMENT for payments to people. Default DISBURSEMENT.",
                                        "WITHDRAWAL", "DISBURSEMENT")),
                        "recipient_phone", "amount", "reason"));
    }

    private List<ObjectNode> approverTools() {
        return List.of(
                tool(LIST_PENDING_APPROVALS, "List the payout and refund requests that are waiting for a decision.", props(), new String[0]),
                tool(REVIEW_APPROVAL,
                        "Show one waiting request and send the approver Approve and Reject buttons. Nothing is decided until they tap a button.",
                        props(string("operation_id", "Id of the request, from list_pending_approvals")), "operation_id"));
    }

    private List<ObjectNode> adminTools() {
        return List.of(
                tool(ACCOUNT_BALANCE, "Ask Safaricom for the business account balance. The answer arrives in this chat a few seconds later.", props(), new String[0]),
                tool(REQUEST_REVERSAL,
                        "Ask the admin to confirm a reversal of a customer payment by its M-Pesa receipt number.",
                        props(string("receipt", "M-Pesa receipt number of the payment to reverse"),
                                integer("amount", "Amount to reverse in whole KES"),
                                string("reason", "Why it is being reversed")),
                        "receipt", "amount", "reason"));
    }

    // ------------------------------------------------------------ schema helpers

    private ObjectNode tool(String name, String description, ObjectNode properties, String... required) {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        ObjectNode schema = tool.putObject("input_schema");
        schema.put("type", "object");
        schema.set("properties", properties);
        ArrayNode req = schema.putArray("required");
        for (String r : required) {
            req.add(r);
        }
        return tool;
    }

    private ObjectNode props(ObjectNode... fields) {
        ObjectNode properties = mapper.createObjectNode();
        for (ObjectNode field : fields) {
            String name = field.get("_name").asString();
            ObjectNode copy = field.deepCopy();
            copy.remove("_name");
            properties.set(name, copy);
        }
        return properties;
    }

    private ObjectNode string(String name, String description) {
        ObjectNode n = mapper.createObjectNode();
        n.put("_name", name);
        n.put("type", "string");
        n.put("description", description);
        return n;
    }

    private ObjectNode integer(String name, String description) {
        ObjectNode n = mapper.createObjectNode();
        n.put("_name", name);
        n.put("type", "integer");
        n.put("description", description);
        return n;
    }

    private ObjectNode enumString(String name, String description, String... values) {
        ObjectNode n = string(name, description);
        ArrayNode e = n.putArray("enum");
        for (String v : values) {
            e.add(v);
        }
        return n;
    }
}