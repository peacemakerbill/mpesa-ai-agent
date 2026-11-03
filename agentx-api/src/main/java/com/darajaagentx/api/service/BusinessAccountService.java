package com.darajaagentx.api.service;

import com.darajaagentx.api.dto.BusinessAccountRequest;
import com.darajaagentx.api.dto.BusinessAccountView;
import com.darajaagentx.api.dto.ImportResult;
import com.darajaagentx.api.dto.VerifyResult;
import com.darajaagentx.api.exception.ActionNotFoundException;
import com.darajaagentx.api.exception.CertificateMissingException;
import com.darajaagentx.api.exception.InvalidActionRequestException;
import com.darajaagentx.api.exception.VaultNotConfiguredException;
import com.darajaagentx.api.model.BusinessAccount;
import com.darajaagentx.api.model.DarajaEnvironment;
import com.darajaagentx.api.model.ShortcodeType;
import com.darajaagentx.api.repository.BusinessAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A customer's business shortcodes and the Daraja credentials for them.
 *
 * <p>A head office holds its own shortcode and credentials, when it has gone live. A child belongs to a head office and
 * is one of two things:
 * <ul>
 *   <li>a till or branch code that shares the head office's shortcode and credentials and only adds its own till number
 *       or account reference;</li>
 *   <li>a child shortcode (a store number) with its own number. A Buy Goods store number has the till number linked to
 *       it, a Paybill child has none. It uses the head office's credentials, or its own when it went live on its own, and
 *       any group of those it leaves empty comes from the head office, except the passkey, which belongs to one shortcode.</li>
 * </ul>
 * Everything is scoped to the signed-in owner. Secrets are locked with {@link SecretVault}, never returned,
 * and a blank secret on an edit means "keep what is saved".
 */
@Service
public class BusinessAccountService {

    public static final int MAX_ACCOUNTS = 500;

    private static final Pattern CODE = Pattern.compile("\\d{5,7}");
    private static final Pattern ACCOUNT_REF = Pattern.compile("[A-Za-z0-9]{1,12}");
    private static final Pattern NO_SPACE = Pattern.compile("\\S+");

    private final BusinessAccountRepository accounts;
    private final SecretVault vault;
    private final SecurityCredentialGenerator certificates;
    private final TokenProbe probe;
    private final Clock clock;

    public BusinessAccountService(BusinessAccountRepository accounts, SecretVault vault,
                                  SecurityCredentialGenerator certificates, TokenProbe probe, Clock clock) {
        this.accounts = accounts;
        this.vault = vault;
        this.certificates = certificates;
        this.probe = probe;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reading

    public List<BusinessAccountView> list(String owner) {
        List<BusinessAccount> all = accounts.findByOwnerPhoneOrderByCreatedAtAsc(owner);
        Map<UUID, BusinessAccount> byId = index(all);
        return all.stream().map(a -> view(a, byId)).toList();
    }

    public BusinessAccountView get(String owner, UUID id) {
        BusinessAccount a = require(owner, id);
        return view(a, null);
    }

    private BusinessAccountView view(BusinessAccount a, Map<UUID, BusinessAccount> byId) {
        BusinessAccount head = null;
        if (a.isBranch()) {
            head = byId != null ? byId.get(a.getParentId()) : accounts.findByIdAndOwnerPhone(a.getParentId(), a.getOwnerPhone()).orElse(null);
        }
        return BusinessAccountView.of(a, head);
    }

    // ------------------------------------------------------------------ writing one account

    @Transactional
    public BusinessAccountView create(String owner, BusinessAccountRequest request) {
        List<BusinessAccount> all = accounts.findByOwnerPhoneOrderByCreatedAtAsc(owner);
        Draft draft = new Draft(0, request);
        Context ctx = new Context(owner, all, false);
        if (request.parentId() != null) {
            BusinessAccount head = ctx.byId.get(request.parentId());
            if (head == null || head.isBranch()) {
                throw new InvalidActionRequestException("The parent account was not found. A branch can only be added under a head account.");
            }
            draft.headAccount = head;
        }
        check(draft, ctx, null);
        failIfAny(List.of(draft));
        persist(owner, List.of(draft), ctx);
        return view(draft.saved, null);
    }

    @Transactional
    public BusinessAccountView update(String owner, UUID id, BusinessAccountRequest request) {
        List<BusinessAccount> all = accounts.findByOwnerPhoneOrderByCreatedAtAsc(owner);
        Context ctx = new Context(owner, all, false);
        BusinessAccount existing = ctx.byId.get(id);
        if (existing == null) {
            throw new ActionNotFoundException("That business account was not found.");
        }
        Draft draft = new Draft(0, request);
        draft.existing = existing;
        if (existing.isBranch()) {
            draft.headAccount = ctx.byId.get(existing.getParentId());
        }
        check(draft, ctx, null);
        failIfAny(List.of(draft));
        persist(owner, List.of(draft), ctx);
        return view(draft.saved, null);
    }

    /** Removes the account. A head account takes its branches with it. Returns how many were removed. */
    @Transactional
    public int delete(String owner, UUID id) {
        BusinessAccount a = require(owner, id);
        List<BusinessAccount> doomed = new ArrayList<>();
        if (!a.isBranch()) {
            doomed.addAll(accounts.findByOwnerPhoneAndParentId(owner, a.getId()));
        }
        doomed.add(a);
        accounts.deleteAll(doomed);
        return doomed.size();
    }

    // ------------------------------------------------------------------ CSV

    @Transactional
    public ImportResult importCsv(String owner, byte[] file, boolean dryRun) {
        BusinessAccountCsv.Parsed parsed = BusinessAccountCsv.parse(file);
        if (!parsed.issues().isEmpty()) {
            return new ImportResult(dryRun, false, 0, 0, 0, List.of(), parsed.issues(), parsed.ignoredColumns());
        }
        List<BusinessAccount> all = accounts.findByOwnerPhoneOrderByCreatedAtAsc(owner);
        Context ctx = new Context(owner, all, true);

        List<Draft> drafts = new ArrayList<>();
        for (BusinessAccountCsv.Row r : parsed.rows()) {
            drafts.add(new Draft(r.row(), r.request()));
        }

        // Heads first, because a branch row may name a head that is further down the file.
        Map<String, Draft> seenKeys = new HashMap<>();
        List<Draft> heads = new ArrayList<>();
        List<Draft> branches = new ArrayList<>();
        for (Draft d : drafts) {
            (blank(d.in.parentShortcode()) ? heads : branches).add(d);
        }
        for (Draft d : heads) {
            check(d, ctx, seenKeys);
            if (d.env != null && !d.shortcode.isEmpty()) {
                ctx.newHeads.add(d);
            }
        }
        for (Draft d : branches) {
            resolveHead(d, ctx);
            check(d, ctx, seenKeys);
        }

        int newAccounts = (int) drafts.stream().filter(d -> d.existing == null).count();
        List<ImportResult.Issue> issues = new ArrayList<>();
        if (all.size() + newAccounts > MAX_ACCOUNTS) {
            issues.add(new ImportResult.Issue(0, "file", "That would make more than " + MAX_ACCOUNTS + " accounts. Remove some first."));
        }
        for (Draft d : drafts) {
            for (ImportResult.Issue i : d.issues) {
                issues.add(i);
            }
        }
        issues.sort((a, b) -> Integer.compare(a.row(), b.row()));

        int created = (int) drafts.stream().filter(d -> d.existing == null).count();
        int updated = drafts.size() - created;
        List<ImportResult.Row> preview = new ArrayList<>();
        for (Draft d : drafts) {
            preview.add(new ImportResult.Row(d.row, d.label, d.env == null ? "" : d.env.name().toLowerCase(Locale.ROOT),
                    d.type == null ? "" : d.type.name().toLowerCase(Locale.ROOT), d.shortcode, d.till, d.accountRef,
                    blank(d.in.parentShortcode()) ? "" : d.in.parentShortcode().trim(), d.existing == null ? "create" : "update"));
        }

        boolean saved = false;
        if (issues.isEmpty() && !dryRun) {
            persist(owner, drafts, ctx);
            saved = true;
        }
        return new ImportResult(dryRun, saved, drafts.size(), created, updated, preview, issues, parsed.ignoredColumns());
    }

    // ------------------------------------------------------------------ checking Safaricom

    /** Asks Daraja for an access token with the saved key and secret, and checks the certificate can lock the initiator password. */
    public VerifyResult verify(String owner, UUID id) {
        BusinessAccount a = require(owner, id);
        BusinessAccount head = a.isBranch() ? require(owner, a.getParentId()) : null;
        BusinessAccount source = pick(a, head, BusinessAccount::hasConsumerCredentials);
        if (source == null) {
            return new VerifyResult(false, "Add the consumer key and secret first.", false, "", a.getVerifiedAt());
        }
        String key = vault.decrypt(source.getConsumerKeyEnc(), ctx(source.getOwnerPhone(), "consumerKey"));
        String secret = vault.decrypt(source.getConsumerSecretEnc(), ctx(source.getOwnerPhone(), "consumerSecret"));
        String env = a.getEnvironment() == DarajaEnvironment.PRODUCTION ? "production" : "sandbox";

        TokenProbe.Outcome outcome = probe.probe(a.getEnvironment(), key, secret);
        boolean ok = outcome.kind() == TokenProbe.Kind.ACCEPTED;
        String message = switch (outcome.kind()) {
            case ACCEPTED -> "Safaricom accepted this key and secret on " + env + ".";
            case REFUSED -> "Safaricom did not accept this key and secret on " + env
                    + ". Check they belong to the app you made for " + env + ".";
            case ERROR -> "Safaricom answered with an error" + (outcome.status() > 0 ? " (" + outcome.status() + ")" : "")
                    + ". Try again in a moment.";
            case UNREACHABLE -> "Could not reach Safaricom (" + env + "). Try again in a moment.";
        };

        boolean credentialReady = false;
        String credentialMessage = "";
        BusinessAccount initiator = pick(a, head, BusinessAccount::hasInitiator);
        if (initiator != null) {
            try {
                certificates.generate(a.getEnvironment(), vault.decrypt(initiator.getInitiatorPasswordEnc(), ctx(initiator.getOwnerPhone(), "initiatorPassword")));
                credentialReady = true;
                credentialMessage = "The initiator password can be locked with the " + env + " certificate.";
            } catch (CertificateMissingException e) {
                credentialMessage = e.getMessage();
            }
        }
        if (ok) {
            source.setVerifiedAt(Instant.now(clock));
            accounts.save(source);
        }
        return new VerifyResult(ok, message, credentialReady, credentialMessage, source.getVerifiedAt());
    }

    // ------------------------------------------------------------------ for the services that call Daraja

    /** The unlocked credentials for one account, branch values filled in from the parent. */
    public ResolvedCredentials resolve(String owner, UUID id) {
        BusinessAccount a = require(owner, id);
        BusinessAccount head = a.isBranch() ? require(owner, a.getParentId()) : null;
        BusinessAccount app = pick(a, head, BusinessAccount::hasConsumerCredentials);
        if (app == null) {
            throw new InvalidActionRequestException("This business account has no consumer key and secret saved.");
        }
        BusinessAccount initiator = pick(a, head, BusinessAccount::hasInitiator);
        // A passkey belongs to one shortcode, so a child with its own shortcode never borrows the head office's.
        BusinessAccount passkey = a.hasPasskey() ? a : (head != null && head.getShortcode().equals(a.getShortcode()) && head.hasPasskey() ? head : null);
        String o = a.getOwnerPhone();
        return new ResolvedCredentials(a.getId(), a.getEnvironment(), a.getShortcodeType(), a.getShortcode(), a.getTillNumber(),
                a.getAccountReference(),
                vault.decrypt(app.getConsumerKeyEnc(), ctx(o, "consumerKey")),
                vault.decrypt(app.getConsumerSecretEnc(), ctx(o, "consumerSecret")),
                initiator == null ? null : initiator.getInitiatorName(),
                initiator == null ? null : vault.decrypt(initiator.getInitiatorPasswordEnc(), ctx(o, "initiatorPassword")),
                passkey == null ? null : vault.decrypt(passkey.getPasskeyEnc(), ctx(o, "passkey")));
    }

    /** The account itself when it holds the group, otherwise the head office when it does, otherwise null. */
    private static BusinessAccount pick(BusinessAccount a, BusinessAccount head, java.util.function.Predicate<BusinessAccount> holds) {
        if (holds.test(a)) {
            return a;
        }
        return head != null && holds.test(head) ? head : null;
    }

    /** The value Daraja wants in SecurityCredential for this account, made fresh from the saved initiator password. */
    public String securityCredential(String owner, UUID id) {
        ResolvedCredentials c = resolve(owner, id);
        if (c.initiatorPassword() == null) {
            throw new InvalidActionRequestException("This business account has no initiator name and password saved.");
        }
        return certificates.generate(c.environment(), c.initiatorPassword());
    }

    // ------------------------------------------------------------------ checking one draft

    /** What we know about one row or request while it is being checked. */
    private static final class Draft {
        final int row;
        final BusinessAccountRequest in;
        DarajaEnvironment env;
        ShortcodeType type;
        String shortcode = "";
        String till = "";
        String accountRef = "";
        String label;
        String key;
        String secret;
        String initName;
        String initPass;
        String passkey;
        boolean ownCode;
        boolean ownCredentials;
        boolean clearOwn;
        BusinessAccount headAccount;
        Draft headDraft;
        BusinessAccount existing;
        BusinessAccount saved;
        final List<ImportResult.Issue> issues = new ArrayList<>();

        Draft(int row, BusinessAccountRequest in) {
            this.row = row;
            this.in = in;
        }

        boolean branch() {
            return headAccount != null || headDraft != null;
        }

        void problem(String field, String message) {
            issues.add(new ImportResult.Issue(row, field, message));
        }

        boolean issuesHave(String field) {
            return issues.stream().anyMatch(i -> i.field().equals(field));
        }
    }

    /** The owner's saved accounts, indexed, plus the heads that are being added in the same upload. */
    private static final class Context {
        final String owner;
        final boolean importMode;
        final Map<UUID, BusinessAccount> byId;
        final Map<String, BusinessAccount> byKey = new HashMap<>();
        final Map<String, BusinessAccount> headByCode = new HashMap<>();
        final Map<String, List<BusinessAccount>> byCode = new HashMap<>();
        final List<Draft> newHeads = new ArrayList<>();

        Context(String owner, List<BusinessAccount> all, boolean importMode) {
            this.owner = owner;
            this.importMode = importMode;
            this.byId = index(all);
            for (BusinessAccount a : all) {
                byKey.put(keyOf(a.getEnvironment(), a.getShortcode(), a.getTillNumber(), a.getAccountReference()), a);
                byCode.computeIfAbsent(codeKey(a.getEnvironment(), a.getShortcode()), k -> new ArrayList<>()).add(a);
                if (!a.isBranch()) {
                    headByCode.put(codeKey(a.getEnvironment(), a.getShortcode()), a);
                }
            }
        }
    }

    private static Map<UUID, BusinessAccount> index(List<BusinessAccount> all) {
        Map<UUID, BusinessAccount> map = new LinkedHashMap<>();
        for (BusinessAccount a : all) {
            map.put(a.getId(), a);
        }
        return map;
    }

    private static String keyOf(DarajaEnvironment env, String shortcode, String till, String ref) {
        return env + "|" + shortcode + "|" + till + "|" + ref;
    }

    private static String codeKey(DarajaEnvironment env, String shortcode) {
        return env + "|" + shortcode;
    }

    /** Finds the head for a CSV branch row: another row in the same file first, then an account that is already saved. */
    private void resolveHead(Draft d, Context ctx) {
        String code = digits(d.in.parentShortcode());
        DarajaEnvironment wanted = blank(d.in.environment()) ? null : DarajaEnvironment.parse(d.in.environment());
        List<Draft> inFile = new ArrayList<>();
        for (Draft h : ctx.newHeads) {
            if (h.shortcode.equals(code) && (wanted == null || h.env == wanted)) {
                inFile.add(h);
            }
        }
        List<BusinessAccount> saved = new ArrayList<>();
        for (DarajaEnvironment e : DarajaEnvironment.values()) {
            BusinessAccount a = ctx.headByCode.get(codeKey(e, code));
            if (a != null && (wanted == null || a.getEnvironment() == wanted)) {
                saved.add(a);
            }
        }
        // A saved head that is also in the file is the same account, so count it once.
        saved.removeIf(a -> inFile.stream().anyMatch(h -> h.env == a.getEnvironment() && h.shortcode.equals(a.getShortcode())));
        int matches = inFile.size() + saved.size();
        if (matches == 0) {
            d.problem("parent_shortcode", "No head account has the shortcode " + d.in.parentShortcode().trim()
                    + ". Add the head account in this file or save it first.");
        } else if (matches > 1) {
            d.problem("environment", "The shortcode " + code + " exists in both sandbox and production. Fill the environment column for this row.");
        } else if (!inFile.isEmpty()) {
            d.headDraft = inFile.get(0);
        } else {
            d.headAccount = saved.get(0);
        }
    }

    /**
     * Validates one draft and finds the saved account it will change, if any.
     *
     * @param seenKeys when not null, used to spot two rows of one file describing the same account
     */
    private void check(Draft d, Context ctx, Map<String, Draft> seenKeys) {
        BusinessAccountRequest in = d.in;
        d.label = clean(in.label());
        if (d.label != null && d.label.length() > 80) {
            d.problem("label", "The name can be at most 80 characters.");
        }

        boolean branch = d.branch();
        boolean branchRow = branch || !blank(in.parentShortcode()) || in.parentId() != null;
        if (branchRow && !branch) {
            return; // the head could not be found, and that problem is already recorded
        }

        if (branch) {
            DarajaEnvironment headEnv = d.headAccount != null ? d.headAccount.getEnvironment() : d.headDraft.env;
            ShortcodeType headType = d.headAccount != null ? d.headAccount.getShortcodeType() : d.headDraft.type;
            String headCode = d.headAccount != null ? d.headAccount.getShortcode() : d.headDraft.shortcode;
            if (headEnv == null || headType == null || headCode == null || headCode.isEmpty()) {
                return; // the head itself has problems, they are shown on its row
            }
            d.env = headEnv;
            d.type = headType;
            d.shortcode = headCode;
            if (!blank(in.environment()) && DarajaEnvironment.parse(in.environment()) != headEnv) {
                d.problem("environment", "A branch is always in the same environment as its head account.");
            }
            if (!blank(in.shortcodeType()) && ShortcodeType.parse(in.shortcodeType()) != headType) {
                d.problem("shortcode_type", "A branch has the same type as its head account. Leave the type empty.");
            }
            // A child shares the head office's shortcode, or has its own (a store number that went live on its own).
            String given = digits(in.shortcode());
            if (!given.isEmpty() && !CODE.matcher(given).matches()) {
                d.problem("shortcode", "The shortcode must be 5 to 7 digits.");
                given = "";
            }
            d.shortcode = !given.isEmpty() ? given : (d.existing != null ? d.existing.getShortcode() : headCode);
            d.ownCode = !d.shortcode.equals(headCode);

            boolean sent = !blank(in.consumerKey()) || !blank(in.consumerSecret()) || !blank(in.initiatorName())
                    || !blank(in.initiatorPassword()) || !blank(in.passkey());
            String mode = blank(in.credentials()) ? null : in.credentials().trim().toLowerCase(Locale.ROOT);
            if (mode != null && !mode.equals("head") && !mode.equals("own")) {
                d.problem("credentials", "Choose the head office's credentials or this account's own.");
                mode = null;
            }
            boolean heldOwn = d.existing != null && d.existing.holdsOwnCredentials();
            if (!d.ownCode) {
                // Same shortcode as the head office, so the same passkey and credentials.
                if (sent || "own".equals(mode)) {
                    d.problem("consumer_key", "This one uses the head office's shortcode, so it uses the head office's credentials. "
                            + "If it has its own credentials, give it its own shortcode (store number).");
                }
                d.ownCredentials = false;
                d.clearOwn = heldOwn;
            } else {
                d.ownCredentials = mode != null ? mode.equals("own") : (sent || heldOwn);
                if (!d.ownCredentials && sent) {
                    d.problem("consumer_key", "You chose the head office's credentials, so leave the key, secret, initiator and passkey empty. "
                            + "If this shortcode has its own, choose its own credentials.");
                }
                if (d.ownCredentials && !sent && !heldOwn) {
                    d.problem("consumer_key", "Add the consumer key and secret, passkey or initiator from this shortcode's own Daraja app, "
                            + "or choose the head office's credentials.");
                }
                d.clearOwn = !d.ownCredentials && heldOwn;
            }
        } else {
            if (blank(in.environment())) {
                if (ctx.importMode) {
                    d.env = DarajaEnvironment.SANDBOX;
                } else if (d.existing != null) {
                    d.env = d.existing.getEnvironment();
                } else {
                    d.problem("environment", "Choose sandbox or production.");
                }
            } else {
                d.env = DarajaEnvironment.parse(in.environment());
                if (d.env == null) {
                    d.problem("environment", "The environment must be sandbox or production.");
                }
            }
            d.type = ShortcodeType.parse(in.shortcodeType());
            if (blank(in.shortcodeType()) && d.existing != null) {
                d.type = d.existing.getShortcodeType();
            } else if (d.type == null) {
                d.problem("shortcode_type", blank(in.shortcodeType()) ? "Choose Paybill or Buy Goods."
                        : "The type must be paybill or buy_goods.");
            }
            String code = digits(in.shortcode());
            if (blank(in.shortcode()) && d.existing != null) {
                code = d.existing.getShortcode();
            }
            if (!CODE.matcher(code).matches()) {
                d.problem("shortcode", "The shortcode must be 5 to 7 digits.");
            } else {
                d.shortcode = code;
            }
        }

        // till number and account reference
        String till = digits(in.tillNumber());
        String ref = clean(in.accountReference()) == null ? "" : clean(in.accountReference());
        if (!till.isEmpty() && !CODE.matcher(till).matches()) {
            d.problem("till_number", "The till number must be 5 to 7 digits.");
            till = "";
        }
        if (!ref.isEmpty() && !ACCOUNT_REF.matcher(ref).matches()) {
            d.problem("account_reference", "The account reference can only have letters and numbers, up to 12 characters.");
            ref = "";
        }
        if (d.type == ShortcodeType.PAYBILL) {
            if (!till.isEmpty()) {
                d.problem("till_number", branch && d.ownCode
                        ? "A child of a Paybill has no till number. Leave it empty."
                        : "A till number belongs to Buy Goods, not to a Paybill.");
            }
            if (branch && !d.ownCode && ref.isEmpty() && !d.issuesHave("account_reference")) {
                d.problem("account_reference", "A Paybill branch needs an account reference, for example the branch code.");
            }
        } else if (d.type == ShortcodeType.BUY_GOODS) {
            if (!ref.isEmpty()) {
                d.problem("account_reference", "An account reference belongs to a Paybill, not to Buy Goods.");
            }
            if (branch && till.isEmpty() && !d.issuesHave("till_number")) {
                d.problem("till_number", d.ownCode
                        ? "A Buy Goods child shortcode (store number) needs the till number linked to it."
                        : "A Buy Goods branch needs its till number.");
            }
        }
        d.till = till;
        d.accountRef = ref;

        // credentials: on a head office, and on a child that has its own
        if (!branch || d.ownCredentials) {
            d.key = secret(d, "consumer_key", in.consumerKey(), 8, 100);
            d.secret = secret(d, "consumer_secret", in.consumerSecret(), 8, 100);
            d.initName = clean(in.initiatorName());
            if (d.initName != null && (d.initName.length() < 2 || d.initName.length() > 50 || !NO_SPACE.matcher(d.initName).matches())) {
                d.problem("initiator_name", "The initiator name must be 2 to 50 characters with no spaces.");
                d.initName = null;
            }
            d.initPass = secret(d, "initiator_password", in.initiatorPassword(), 6, 100);
            d.passkey = secret(d, "passkey", in.passkey(), 20, 200);
        }

        // find the saved account this draft changes, and compare with what already exists
        String key = keyOf(d.env, d.shortcode, d.till, d.accountRef);
        if (d.env != null && d.type != null && !d.shortcode.isEmpty()) {
            BusinessAccount sameKey = ctx.byKey.get(key);
            if (d.existing == null) {
                if (sameKey != null) {
                    if (ctx.importMode) {
                        d.existing = sameKey;
                    } else {
                        d.problem("shortcode", "This shortcode is already saved as \"" + sameKey.getLabel() + "\". Edit that account instead.");
                    }
                }
            } else if (sameKey != null && !sameKey.getId().equals(d.existing.getId())) {
                d.problem("shortcode", "Another account (\"" + sameKey.getLabel() + "\") already uses these numbers.");
            }
            if (d.existing != null && branch != d.existing.isBranch()) {
                d.problem("parent_shortcode", branch ? "This account is saved as a head account, so it cannot become a branch."
                        : "This account is saved as a branch, so it cannot become a head account.");
            }
            if (!branch) {
                BusinessAccount otherHead = ctx.headByCode.get(codeKey(d.env, d.shortcode));
                boolean same = d.existing != null && otherHead != null && otherHead.getId().equals(d.existing.getId());
                if (otherHead != null && !same && d.existing == null) {
                    d.problem("shortcode", "The shortcode " + d.shortcode + " is already saved as \"" + otherHead.getLabel()
                            + "\". To add another till, add it as a branch of that account.");
                }
            }
            if (branch && d.ownCode) {
                for (BusinessAccount o : ctx.byCode.getOrDefault(codeKey(d.env, d.shortcode), List.of())) {
                    if (d.existing == null || !o.getId().equals(d.existing.getId())) {
                        d.problem("shortcode", "The shortcode " + d.shortcode + " is already saved as \"" + o.getLabel()
                                + "\". A child shortcode (store number) must be a number of its own.");
                        break;
                    }
                }
            } else if (!branch) {
                for (BusinessAccount o : ctx.byCode.getOrDefault(codeKey(d.env, d.shortcode), List.of())) {
                    BusinessAccount oHead = o.isBranch() ? ctx.byId.get(o.getParentId()) : null;
                    boolean ownedByChild = oHead != null && !oHead.getShortcode().equals(o.getShortcode());
                    boolean mine = d.existing != null && (o.getId().equals(d.existing.getId()) || d.existing.getId().equals(o.getParentId()));
                    if (ownedByChild && !mine) {
                        d.problem("shortcode", "The shortcode " + d.shortcode + " is already saved as \"" + o.getLabel()
                                + "\", a child shortcode of another account.");
                        break;
                    }
                }
            }
            if (seenKeys != null) {
                Draft earlier = seenKeys.putIfAbsent(key, d);
                if (earlier != null && earlier != d) {
                    d.problem("shortcode", "This repeats row " + earlier.row + ".");
                } else if ((!branch || d.ownCode) && !d.issuesHave("shortcode")) {
                    Draft sameCode = seenKeys.putIfAbsent("HEAD|" + codeKey(d.env, d.shortcode), d);
                    if (sameCode != null && sameCode != d) {
                        d.problem("shortcode", "Row " + sameCode.row + " already uses the shortcode " + d.shortcode
                                + ". To add another till, add it as a branch of that account.");
                    }
                }
            }
        }

        // The key and the secret go together. An account may have neither, for example a head office whose
        // stores each went live on their own.
        if (!branch || d.ownCredentials) {
            boolean hasKey = d.key != null || (d.existing != null && d.existing.getConsumerKeyEnc() != null);
            boolean hasSecret = d.secret != null || (d.existing != null && d.existing.getConsumerSecretEnc() != null);
            if ((d.key != null || d.secret != null) && hasKey != hasSecret) {
                if (!hasKey && !d.issuesHave("consumer_key")) {
                    d.problem("consumer_key", "The consumer key is required.");
                }
                if (!hasSecret && !d.issuesHave("consumer_secret")) {
                    d.problem("consumer_secret", "The consumer secret is required.");
                }
            }
        }
        if (!branch || d.ownCredentials) {
            boolean hasName = d.initName != null || (d.existing != null && d.existing.getInitiatorName() != null && !d.existing.getInitiatorName().isBlank());
            boolean hasPass = d.initPass != null || (d.existing != null && d.existing.getInitiatorPasswordEnc() != null);
            if (hasName != hasPass && (d.initName != null || d.initPass != null)) {
                d.problem(hasName ? "initiator_password" : "initiator_name",
                        "Give both the initiator name and the initiator password, or leave both empty.");
            }
        }

        if (d.label == null) {
            d.label = defaultLabel(d);
        }
    }

    private static String defaultLabel(Draft d) {
        if (d.type == null || d.shortcode.isEmpty()) {
            return "Business account";
        }
        String kind = d.type == ShortcodeType.PAYBILL ? "Paybill " : "Buy Goods ";
        String tail = !d.till.isEmpty() ? " till " + d.till : (!d.accountRef.isEmpty() ? " " + d.accountRef : "");
        return kind + d.shortcode + tail;
    }

    /** Checks one secret value. Returns the cleaned value, or null when it was left empty or was not acceptable. */
    private String secret(Draft d, String field, String value, int min, int max) {
        String v = clean(value);
        if (v == null) {
            return null;
        }
        if (v.startsWith("PASTE_")) {
            d.problem(field, "Replace the placeholder " + v + " with the real value.");
            return null;
        }
        if (v.length() < min || v.length() > max || !NO_SPACE.matcher(v).matches()) {
            d.problem(field, "This value should be " + min + " to " + max + " characters with no spaces. Check you copied all of it.");
            return null;
        }
        return v;
    }

    private void failIfAny(List<Draft> drafts) {
        String message = drafts.stream().flatMap(d -> d.issues.stream()).map(ImportResult.Issue::message)
                .distinct().collect(Collectors.joining(" "));
        if (!message.isEmpty()) {
            throw new InvalidActionRequestException(message);
        }
    }

    // ------------------------------------------------------------------ saving

    private void persist(String owner, List<Draft> drafts, Context ctx) {
        boolean needsVault = drafts.stream().anyMatch(d -> d.key != null || d.secret != null || d.initPass != null || d.passkey != null);
        if (needsVault && !vault.configured()) {
            throw new VaultNotConfiguredException();
        }
        List<Draft> ordered = new ArrayList<>();
        for (Draft d : drafts) {
            if (!d.branch()) {
                ordered.add(d);
            }
        }
        for (Draft d : drafts) {
            if (d.branch()) {
                ordered.add(d);
            }
        }
        for (Draft d : ordered) {
            BusinessAccount e = d.existing != null ? d.existing : new BusinessAccount(owner);
            String oldCode = d.existing != null ? d.existing.getShortcode() : null;
            e.setLabel(d.label);
            e.setEnvironment(d.env);
            e.setShortcodeType(d.type);
            e.setShortcode(d.shortcode);
            e.setTillNumber(d.till);
            e.setAccountReference(d.accountRef);
            if (d.branch()) {
                e.setParentId(d.headAccount != null ? d.headAccount.getId() : d.headDraft.saved.getId());
            }
            if (d.clearOwn) {
                e.clearCredentials();
            } else if (!d.branch() || d.ownCredentials) {
                if (d.key != null) {
                    e.setConsumerKeyEnc(vault.encrypt(d.key, ctx(owner, "consumerKey")));
                    e.setConsumerKeyHint(d.key.substring(Math.max(0, d.key.length() - 4)));
                    e.setVerifiedAt(null);
                }
                if (d.secret != null) {
                    e.setConsumerSecretEnc(vault.encrypt(d.secret, ctx(owner, "consumerSecret")));
                    e.setVerifiedAt(null);
                }
                if (d.initName != null) {
                    e.setInitiatorName(d.initName);
                }
                if (d.initPass != null) {
                    e.setInitiatorPasswordEnc(vault.encrypt(d.initPass, ctx(owner, "initiatorPassword")));
                }
                if (d.passkey != null) {
                    e.setPasskeyEnc(vault.encrypt(d.passkey, ctx(owner, "passkey")));
                }
            }
            d.saved = accounts.save(e);
            if (!d.branch() && d.existing != null) {
                carryToBranches(owner, d.saved, oldCode);
            }
        }
    }

    /**
     * A head office's type and environment are shared with all its children, and its shortcode with the children that
     * share it, so changes are copied to them. A child with its own shortcode keeps it.
     */
    private void carryToBranches(String owner, BusinessAccount head, String oldCode) {
        for (BusinessAccount b : accounts.findByOwnerPhoneAndParentId(owner, head.getId())) {
            boolean sharesCode = oldCode != null && b.getShortcode().equals(oldCode);
            if (b.getEnvironment() != head.getEnvironment() || b.getShortcodeType() != head.getShortcodeType()
                    || (sharesCode && !b.getShortcode().equals(head.getShortcode()))) {
                b.setEnvironment(head.getEnvironment());
                b.setShortcodeType(head.getShortcodeType());
                if (sharesCode) {
                    b.setShortcode(head.getShortcode());
                }
                accounts.save(b);
            }
        }
    }

    // ------------------------------------------------------------------ small helpers

    private BusinessAccount require(String owner, UUID id) {
        return accounts.findByIdAndOwnerPhone(id, owner)
                .orElseThrow(() -> new ActionNotFoundException("That business account was not found."));
    }

    /** What a locked value is tied to: its owner and field. */
    private static String ctx(String owner, String field) {
        return owner + "|" + field;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** Trims, and removes line breaks that a copy and paste can bring along. Empty becomes null. */
    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String v = s.replace("\r", "").replace("\n", "").replace(" ", " ").replace("​", "").trim();
        return v.isEmpty() ? null : v;
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\-]", "");
    }
}
