package com.mpesaagent.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mpesaagent.agent.chat.ChatChannel.Button;
import com.mpesaagent.agent.client.PaymentClient;
import com.mpesaagent.agent.config.AgentProperties;
import com.mpesaagent.agent.dto.OperationView;
import com.mpesaagent.agent.dto.PaymentView;
import com.mpesaagent.agent.exception.CompanionApiException;
import com.mpesaagent.agent.exception.PaymentApiException;
import com.mpesaagent.agent.model.Intent;
import com.mpesaagent.agent.model.IntentType;
import com.mpesaagent.agent.model.Role;
import com.mpesaagent.agent.store.IntentStore;
import com.mpesaagent.agent.util.Money;
import com.mpesaagent.agent.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mpesaagent.agent.service.ToolCatalog.*;

/**
 * Runs the tools the model asks for. Three rules hold everywhere in here:
 * 1. The model never moves money. Tools that would spend money only create a pending confirmation and queue a
 *    button message; ButtonHandler acts when the person taps.
 * 2. The person's identity and role come from ToolContext, never from the tool input.
 * 3. Results are short, factual text. A failure comes back as an error result the model can explain.
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.of("Africa/Nairobi"));

    /** What goes back to the model. */
    public record Result(String content, boolean error) {
        static Result ok(String content) {
            return new Result(content, false);
        }

        static Result error(String content) {
            return new Result(content, true);
        }
    }

    /** A problem the model should be told about in plain words. */
    static final class ToolException extends RuntimeException {
        ToolException(String message) {
            super(message);
        }
    }

    private final PaymentClient payments;
    private final IntentStore intents;
    private final ReplySender sender;
    private final AgentProperties props;
    private final com.mpesaagent.agent.config.ServiceProperties.Business business;
    private final WalletTools wallet;

    public ToolExecutor(PaymentClient payments, IntentStore intents, ReplySender sender, AgentProperties props,
                        com.mpesaagent.agent.config.ServiceProperties.Business business, WalletTools wallet) {
        this.payments = payments;
        this.intents = intents;
        this.sender = sender;
        this.props = props;
        this.business = business;
        this.wallet = wallet;
    }

    public Result execute(ToolContext ctx, String name, JsonNode input) {
        try {
            if (ctx.role().ordinal() < ToolCatalog.requiredRole(name).ordinal()) {
                log.warn("Role {} tried tool {}", ctx.role(), name);
                return Result.error("You do not have permission to do this.");
            }
            if (wallet.handles(name)) {
                return wallet.execute(ctx, name, input);
            }
            if (!props.businessPayments() && ToolCatalog.BUSINESS_TOOLS.contains(name)) {
                log.warn("Tool {} requested but business payments are switched off", name);
                return Result.error("That feature is not available.");
            }
            return switch (name) {
                case PROPOSE_PAYMENT -> proposePayment(ctx, input);
                case CHECK_PAYMENT_STATUS -> checkPaymentStatus(ctx, input);
                case LIST_MY_PAYMENTS -> listPayments(ctx.phone(), limit(input, 5));
                case SEND_RECEIPT -> sendReceipt(ctx, input);
                case REQUEST_REFUND -> requestRefund(ctx, input);
                case GENERATE_PAYMENT_QR -> generateQr(ctx, input);
                case LIST_MY_REQUESTS -> listMyRequests(ctx);
                case ESCALATE -> escalate(ctx, input);
                case LOOKUP_TRANSACTION -> lookupTransaction(ctx, input);
                case LOOKUP_CUSTOMER_PAYMENTS -> lookupCustomerPayments(input);
                case REQUEST_PAYOUT -> requestPayout(ctx, input);
                case LIST_PENDING_APPROVALS -> listPendingApprovals(ctx);
                case REVIEW_APPROVAL -> reviewApproval(ctx, input);
                case ACCOUNT_BALANCE -> accountBalance(ctx);
                case REQUEST_REVERSAL -> requestReversal(ctx, input);
                default -> Result.error("Unknown tool " + name);
            };
        } catch (ToolException e) {
            return Result.error(e.getMessage());
        } catch (PaymentApiException e) {
            return Result.error(friendly(e));
        } catch (CompanionApiException e) {
            return Result.error(friendlyCompanion(e));
        } catch (RuntimeException e) {
            log.error("Tool {} failed", name, e);
            return Result.error("That action failed unexpectedly.");
        }
    }

    // ------------------------------------------------------------ customer tools

    private Result proposePayment(ToolContext ctx, JsonNode input) {
        int amount = positiveInt(input, "amount");
        String reference = reference(input.path("reference").asText(""));
        String phone = ctx.phone();
        if (!Phones.isKenyanMobile(Phones.normalize(phone))) {
            throw new ToolException("M-Pesa payments from this chat work only for Kenyan Safaricom numbers.");
        }
        Intent intent = intents.create(IntentType.PAYMENT, phone,
                Map.of("amount", Integer.toString(amount), "reference", reference), props.intentTtl());
        String body = "Please confirm your payment:\n\n*Pay to:* " + business.name()
                + "\n*Amount:* " + Money.kes(amount)
                + "\n*Reference:* " + reference
                + "\n\nAn M-Pesa prompt will be sent to " + Phones.mask(phone) + ". You will enter your PIN on your phone.";
        ctx.defer(s -> s.buttons(phone, body, "Nothing is charged until you tap Pay",
                List.of(new Button("PAY:" + intent.id(), "Pay " + Money.kes(amount)), new Button("NO:" + intent.id(), "Cancel"))));
        return Result.ok("A confirmation message with Pay and Cancel buttons will be shown to the customer for "
                + Money.kes(amount) + " (reference " + reference + "). Nothing has been charged. "
                + "Tell them in one short sentence to tap Pay to continue. Do not say the payment has started.");
    }

    private Result checkPaymentStatus(ToolContext ctx, JsonNode input) {
        String id = input.path("payment_id").asText("").strip();
        PaymentView payment;
        if (id.isEmpty()) {
            payment = payments.latest(ctx.phone()).orElse(null);
            if (payment == null) {
                return Result.ok("This customer has no payments yet.");
            }
        } else {
            payment = payments.getPayment(uuid(id, "payment_id"), ctx.phone());
        }
        return Result.ok(describe(payment));
    }

    private Result listPayments(String phone, int limit) {
        List<PaymentView> list = payments.list(phone, limit);
        if (list.isEmpty()) {
            return Result.ok("No payments found.");
        }
        return Result.ok(list.stream().map(this::describeShort).collect(Collectors.joining("\n")));
    }

    private Result sendReceipt(ToolContext ctx, JsonNode input) {
        String id = input.path("payment_id").asText("").strip();
        PaymentView payment;
        if (id.isEmpty()) {
            payment = payments.list(ctx.phone(), 10).stream()
                    .filter(p -> "SUCCESS".equals(p.status())).findFirst().orElse(null);
            if (payment == null) {
                return Result.ok("This customer has no successful payments, so there is no receipt to send.");
            }
        } else {
            payment = payments.getPayment(uuid(id, "payment_id"), ctx.phone());
        }
        if (!"SUCCESS".equals(payment.status())) {
            return Result.error("A receipt is only available for successful payments. This one is " + payment.status() + ".");
        }
        byte[] pdf = payments.receipt(payment.id(), ctx.phone());
        String receipt = payment.mpesaReceipt() == null ? payment.id().toString() : payment.mpesaReceipt();
        String phone = ctx.phone();
        ctx.defer(s -> s.document(phone, pdf, "application/pdf", "receipt-" + receipt + ".pdf", "Receipt for " + Money.kes(payment.amount())));
        return Result.ok("The PDF receipt for " + Money.kes(payment.amount()) + " (M-Pesa receipt " + receipt + ") will be sent as a file.");
    }

    private Result requestRefund(ToolContext ctx, JsonNode input) {
        UUID paymentId = uuid(input.path("payment_id").asText(""), "payment_id");
        String reason = text(input, "reason", 200);
        if (reason.isEmpty()) {
            throw new ToolException("A reason for the refund is required.");
        }
        // Customers can only see their own payments. Staff may refund any payment.
        PaymentView payment = payments.getPayment(paymentId, ctx.role().atLeast(Role.STAFF) ? null : ctx.phone());
        if (!"SUCCESS".equals(payment.status())) {
            throw new ToolException("Only successful payments can be refunded. This one is " + payment.status() + ".");
        }
        int amount = input.hasNonNull("amount") ? positiveInt(input, "amount") : payment.amount();
        if (amount > payment.amount()) {
            throw new ToolException("The refund cannot be more than the payment of " + Money.kes(payment.amount()) + ".");
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("paymentId", paymentId.toString());
        data.put("amount", Integer.toString(amount));
        data.put("reason", reason);
        Intent intent = intents.create(IntentType.REFUND, ctx.phone(), data, props.intentTtl());

        String receipt = payment.mpesaReceipt() == null ? "" : " (" + payment.mpesaReceipt() + ")";
        String body = "Please confirm your refund request:\n\n*Payment:* " + Money.kes(payment.amount()) + receipt
                + "\n*Refund amount:* " + Money.kes(amount)
                + "\n*Reason:* " + reason
                + "\n\nOur team reviews every refund. If approved, it is sent back to the M-Pesa number that paid ("
                + Phones.mask(payment.phone()) + ").";
        String phone = ctx.phone();
        ctx.defer(s -> s.buttons(phone, body, null,
                List.of(new Button("OK:" + intent.id(), "Request refund"), new Button("NO:" + intent.id(), "Keep payment"))));
        return Result.ok("A confirmation with Request refund and Keep payment buttons will be shown for a refund of "
                + Money.kes(amount) + ". Nothing has been requested yet. Tell them in one short sentence to tap Request refund to continue.");
    }

    private Result generateQr(ToolContext ctx, JsonNode input) {
        int amount = positiveInt(input, "amount");
        String reference = reference(input.path("reference").asText(""));
        byte[] png = Base64.getDecoder().decode(payments.qr(amount, reference));
        String phone = ctx.phone();
        ctx.defer(s -> s.image(phone, png, "image/png", "Scan with M-Pesa to pay " + Money.kes(amount) + " to " + business.name()));
        return Result.ok("An M-Pesa QR code for " + Money.kes(amount) + " (reference " + reference + ") will be sent as a picture. "
                + "Tell them to open M-Pesa, choose Scan QR and scan it.");
    }

    private Result listMyRequests(ToolContext ctx) {
        List<OperationView> list = payments.myOperations(ctx.phone());
        if (list.isEmpty()) {
            return Result.ok("No requests found.");
        }
        return Result.ok(list.stream().limit(10).map(this::describeOperation).collect(Collectors.joining("\n")));
    }

    private Result escalate(ToolContext ctx, JsonNode input) {
        String reason = text(input, "reason", 500);
        if (reason.isEmpty()) {
            throw new ToolException("Please include a short summary for the team.");
        }
        String who = (ctx.identity().name() == null ? "" : ctx.identity().name() + ", ") + "+" + ctx.phone();
        String message = "*Customer needs help*\n" + who + "\n\n" + reason;
        int sent = 0;
        for (String staffPhone : props.escalationPhones()) {
            try {
                sender.notifyText(Phones.normalize(staffPhone), message);
                sent++;
            } catch (RuntimeException e) {
                log.warn("Could not reach escalation contact {}: {}", Phones.mask(Phones.normalize(staffPhone)), e.getMessage());
            }
        }
        log.info("Escalation from {}: {}", Phones.mask(ctx.phone()), reason);
        if (sent > 0) {
            return Result.ok("The team has been notified and will follow up in this chat. Let the customer know.");
        }
        String support = business.supportPhone() == null || business.supportPhone().isBlank()
                ? "the support team" : business.supportPhone();
        return Result.ok("No staff member could be reached automatically. Apologise and ask the customer to contact " + support + " directly.");
    }

    // ------------------------------------------------------------ staff tools

    private Result lookupTransaction(ToolContext ctx, JsonNode input) {
        String receipt = text(input, "receipt", 30).toUpperCase();
        if (!receipt.matches("^[A-Z0-9]{8,20}$")) {
            throw new ToolException("That does not look like an M-Pesa receipt number (letters and digits, about 10 characters).");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", "tx-" + UUID.randomUUID());
        body.put("type", "TRANSACTION_STATUS");
        body.put("actorPhone", ctx.phone());
        body.put("targetReceipt", receipt);
        body.put("reason", "Lookup requested in the chat");
        OperationView op = payments.createOperation(body);
        return Result.ok("Lookup of " + receipt + " submitted to Safaricom (reference " + shortId(op) + "). "
                + "The result will arrive in this chat in a few seconds. Tell the person to wait for it.");
    }

    private Result lookupCustomerPayments(JsonNode input) {
        String phone = Phones.normalize(text(input, "phone", 20));
        if (!Phones.isKenyanMobile(phone)) {
            throw new ToolException("That is not a valid Kenyan mobile number.");
        }
        return listPayments(phone, limit(input, 5));
    }

    private Result requestPayout(ToolContext ctx, JsonNode input) {
        String recipient = Phones.normalize(text(input, "recipient_phone", 20));
        if (!Phones.isKenyanMobile(recipient)) {
            throw new ToolException("The recipient is not a valid Kenyan mobile number.");
        }
        int amount = positiveInt(input, "amount");
        String reason = text(input, "reason", 200);
        if (reason.isEmpty()) {
            throw new ToolException("A reason for the payout is required.");
        }
        String type = input.path("payout_type").asText("DISBURSEMENT");
        if (!type.equals("WITHDRAWAL") && !type.equals("DISBURSEMENT")) {
            throw new ToolException("payout_type must be WITHDRAWAL or DISBURSEMENT.");
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("recipient", recipient);
        data.put("amount", Integer.toString(amount));
        data.put("reason", reason);
        Intent intent = intents.create(IntentType.PAYOUT, ctx.phone(), data, props.intentTtl());
        String body = "Please confirm this payout request:\n\n*To:* " + Phones.mask(recipient)
                + "\n*Amount:* " + Money.kes(amount)
                + "\n*Reason:* " + reason
                + "\n\nAnother approver must approve it before any money is sent.";
        String phone = ctx.phone();
        ctx.defer(s -> s.buttons(phone, body, null,
                List.of(new Button("OK:" + intent.id(), "Request payout"), new Button("NO:" + intent.id(), "Cancel"))));
        return Result.ok("A confirmation with Request payout and Cancel buttons will be shown for " + Money.kes(amount)
                + " to " + Phones.mask(recipient) + ". Nothing has been requested yet. Tell them in one short sentence to tap Request payout.");
    }

    // ------------------------------------------------------------ approver tools

    private Result listPendingApprovals(ToolContext ctx) {
        List<OperationView> pending = payments.pendingApprovals(ctx.phone());
        if (pending.isEmpty()) {
            return Result.ok("There is nothing waiting for approval.");
        }
        return Result.ok(pending.stream().map(this::describeOperationForApprover).collect(Collectors.joining("\n")));
    }

    private Result reviewApproval(ToolContext ctx, JsonNode input) {
        UUID id = uuid(input.path("operation_id").asText(""), "operation_id");
        OperationView op = payments.getOperation(id, ctx.phone());
        if (!"PENDING_APPROVAL".equals(op.status())) {
            return Result.ok("This request is already " + op.status() + ", so there is nothing to decide.");
        }
        if (ctx.phone().equals(op.requestedBy())) {
            return Result.ok("You made this request yourself, so another approver has to decide it.");
        }
        Intent intent = intents.create(IntentType.APPROVAL, ctx.phone(), Map.of("operationId", id.toString()), props.intentTtl());
        String body = "*Approval needed*\n\n" + describeOperationForApprover(op) + "\n\nApprove this request?";
        String phone = ctx.phone();
        ctx.defer(s -> s.buttons(phone, body, null,
                List.of(new Button("APPROVE:" + intent.id(), "Approve"), new Button("REJECT:" + intent.id(), "Reject"))));
        return Result.ok("Approve and Reject buttons will be shown for this request. Nothing is decided until they tap one. Say that in one short sentence.");
    }

    // ------------------------------------------------------------ admin tools

    private Result accountBalance(ToolContext ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", "bal-" + UUID.randomUUID());
        body.put("type", "ACCOUNT_BALANCE");
        body.put("actorPhone", ctx.phone());
        body.put("reason", "Balance requested in the chat");
        OperationView op = payments.createOperation(body);
        return Result.ok("Balance request submitted to Safaricom (reference " + shortId(op) + "). The balances will arrive in this chat in a few seconds.");
    }

    private Result requestReversal(ToolContext ctx, JsonNode input) {
        String receipt = text(input, "receipt", 30).toUpperCase();
        if (!receipt.matches("^[A-Z0-9]{8,20}$")) {
            throw new ToolException("That does not look like an M-Pesa receipt number.");
        }
        int amount = positiveInt(input, "amount");
        String reason = text(input, "reason", 200);
        if (reason.isEmpty()) {
            throw new ToolException("A reason for the reversal is required.");
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("receipt", receipt);
        data.put("amount", Integer.toString(amount));
        data.put("reason", reason);
        Intent intent = intents.create(IntentType.REVERSAL, ctx.phone(), data, props.intentTtl());
        String body = "Please confirm this reversal request:\n\n*Receipt:* " + receipt
                + "\n*Amount:* " + Money.kes(amount) + "\n*Reason:* " + reason
                + "\n\nAnother approver must approve it before it is sent to M-Pesa.";
        String phone = ctx.phone();
        ctx.defer(s -> s.buttons(phone, body, null,
                List.of(new Button("OK:" + intent.id(), "Request reversal"), new Button("NO:" + intent.id(), "Cancel"))));
        return Result.ok("A confirmation with Request reversal and Cancel buttons will be shown. Nothing has been requested yet.");
    }

    // ------------------------------------------------------------ describing things

    String describe(PaymentView p) {
        StringBuilder sb = new StringBuilder();
        sb.append("Payment ").append(p.id()).append(": ").append(Money.kes(p.amount()))
                .append(", reference ").append(p.accountReference())
                .append(", status ").append(p.status()).append(" (").append(statusMeaning(p)).append(")");
        if (p.mpesaReceipt() != null) {
            sb.append(", M-Pesa receipt ").append(p.mpesaReceipt());
        }
        if (p.createdAt() != null) {
            sb.append(", started ").append(TIME.format(p.createdAt()));
        }
        return sb.toString();
    }

    private String describeShort(PaymentView p) {
        return "- " + (p.createdAt() == null ? "" : TIME.format(p.createdAt()) + ": ") + Money.kes(p.amount()) + ", ref "
                + p.accountReference() + ", " + p.status()
                + (p.mpesaReceipt() == null ? "" : ", receipt " + p.mpesaReceipt()) + ", id " + p.id();
    }

    static String statusMeaning(PaymentView p) {
        return switch (p.status()) {
            case "PENDING" -> "being started";
            case "PUSHED" -> "M-Pesa prompt sent, waiting for the customer to enter their PIN";
            case "SUCCESS" -> "paid";
            case "FAILED" -> "not completed" + (p.resultDesc() == null ? "" : ": " + p.resultDesc());
            case "CANCELLED" -> "the customer cancelled the prompt";
            case "TIMEOUT" -> "the prompt expired without an answer";
            default -> p.status();
        };
    }

    private String describeOperation(OperationView o) {
        return "- " + o.type() + (o.amount() == null ? "" : " " + Money.kes(o.amount())) + ", " + o.status()
                + (o.createdAt() == null ? "" : ", " + TIME.format(o.createdAt())) + ", ref " + shortId(o);
    }

    private String describeOperationForApprover(OperationView o) {
        StringBuilder sb = new StringBuilder();
        sb.append(o.type()).append(" ").append(o.amount() == null ? "" : Money.kes(o.amount()));
        if (o.recipientPhone() != null) {
            sb.append(" to ").append(Phones.mask(o.recipientPhone()));
        }
        if (o.targetReceipt() != null) {
            sb.append(" for receipt ").append(o.targetReceipt());
        }
        sb.append("; requested by ").append(Phones.mask(o.requestedBy()));
        if (o.reason() != null) {
            sb.append("; reason: ").append(o.reason());
        }
        sb.append("; id ").append(o.id());
        return sb.toString();
    }

    private static String shortId(OperationView o) {
        return o.id() == null ? "" : o.id().toString().substring(0, 8);
    }

    // ------------------------------------------------------------ input helpers

    static int positiveInt(JsonNode input, String field) {
        JsonNode node = input.get(field);
        if (node == null || node.isNull() || !(node.isInt() || node.isLong() || node.isTextual() || node.isDouble())) {
            throw new ToolException(field + " is required and must be a whole number of shillings.");
        }
        double value;
        if (node.isTextual()) {
            try {
                value = Double.parseDouble(node.asText().replace(",", "").trim());
            } catch (NumberFormatException e) {
                throw new ToolException(field + " must be a number.");
            }
        } else {
            value = node.asDouble();
        }
        if (value != Math.floor(value) || value < 1 || value > 10_000_000) {
            throw new ToolException(field + " must be a whole number of shillings, at least 1.");
        }
        return (int) value;
    }

    private static int limit(JsonNode input, int fallback) {
        int value = input.path("limit").asInt(fallback);
        return Math.max(1, Math.min(value, 10));
    }

    static String text(JsonNode input, String field, int max) {
        String value = input.path(field).asText("").strip();
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static UUID uuid(String value, String field) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            throw new ToolException(field + " is not a valid id. Use list_my_payments to find it.");
        }
    }

    /** Daraja's AccountReference allows letters, digits, - and _, up to 12 characters. */
    static String reference(String raw) {
        String cleaned = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9_-]", "");
        if (cleaned.isEmpty()) {
            return "CHAT";
        }
        return cleaned.length() <= 12 ? cleaned : cleaned.substring(0, 12);
    }

    private static String friendlyCompanion(CompanionApiException e) {
        if (e.isNotFound()) {
            return "I could not find that request. It may not exist or may belong to someone else.";
        }
        if (e.isUnavailable()) {
            return "The app service is not available right now. Please try again in a moment.";
        }
        if (e.isTooManyRequests()) {
            return e.getMessage();
        }
        return e.getMessage();
    }

    private static String friendly(PaymentApiException e) {
        if (e.isForbidden()) {
            return "You do not have permission to do this.";
        }
        if (e.isNotFound()) {
            return "I could not find that. It may not exist or may belong to someone else.";
        }
        if (e.isUnavailable()) {
            return "The payment system is not available right now. Please try again in a moment.";
        }
        return e.getMessage();
    }
}
