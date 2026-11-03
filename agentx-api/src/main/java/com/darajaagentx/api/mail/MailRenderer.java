package com.darajaagentx.api.mail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a template name and some data into the HTML of one email. The files are in
 * {@code src/main/resources/mail/templates}: {@code layout.html} frames every email, and {@code NAME.html} holds the words
 * of one email. Small shared pieces (the ledger of details, the note, the button) live in {@code partials}
 * and are pulled in with {@code {{> name}}}. Emails are sent as HTML only, there are no plain text files.
 *
 * Besides the data a caller gives, every template can read the details of the moment and of the product: date, time,
 * year, a greeting for the time of day, a reference number, the copyright line, the support address and so on. See
 * {@link #globals} for the full list.
 */
final class MailRenderer {

    static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH).withZone(NAIROBI);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(NAIROBI);
    private static final DateTimeFormatter WEEKDAY_SHORT = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH).withZone(NAIROBI);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH).withZone(NAIROBI);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d", Locale.ENGLISH).withZone(NAIROBI);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH).withZone(NAIROBI);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(NAIROBI);
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy", Locale.ENGLISH).withZone(NAIROBI);
    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.ofPattern("yyMMdd", Locale.ENGLISH).withZone(NAIROBI);
    private static final Pattern CID = Pattern.compile("src=\"cid:([A-Za-z0-9_-]+)\"");
    private static final Pattern PARTIAL = Pattern.compile("\\{\\{>\\s*([a-z0-9-]+)\\s*}}");

    /** The colour of the status label and of the edge of a note, and the colour of the words on it. */
    enum Tone {
        OK("ok", "#1A7F2A", "#FFFFFF"),
        BAD("bad", "#D6001C", "#FFFFFF"),
        NOTICE("notice", "#F2A900", "#0C2414"),
        INFO("info", "#0A4A20", "#FFFFFF");

        final String css;
        final String accent;
        final String on;

        Tone(String css, String accent, String on) {
            this.css = css;
            this.accent = accent;
            this.on = on;
        }
    }

    private static volatile MailBranding branding = MailBranding.DEFAULT;
    private static final Map<String, TemplateEngine> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> SOURCES = new ConcurrentHashMap<>();

    private MailRenderer() {
    }

    static void install(MailBranding b) {
        branding = b == null ? MailBranding.DEFAULT : b;
        CACHE.clear();
        SOURCES.clear();
    }

    static MailBranding branding() {
        return branding;
    }

    /**
     * @param name  the email, for example "welcome"
     * @param data  what this email says. Values may be text, true or false, or a list of maps for {{#each}}
     * @param strict true in tests: a name that no data answers is an error, not an empty space
     */
    static String render(String name, String brand, Instant at, Tone tone, String subject, String heading, String status, String preheader,
                         String reason, Map<String, Object> data, boolean strict) {
        Map<String, Object> all = globals(name, brand, at);
        all.put("tone", tone.css);
        all.put("accent", tone.accent);
        all.put("onAccent", tone.on);
        all.put("subject", subject);
        all.put("heading", heading);
        all.put("status", status == null ? "" : status);
        all.put("preheader", preheader == null ? "" : preheader);
        all.put("reason", reason == null || reason.isBlank() ? defaultReason(brand) : reason);
        all.putAll(data);

        all.put("content", template(name + ".html").render(all, strict));
        return template("layout.html").render(all, strict).trim() + "\n";
    }

    /**
     * Everything every email knows about the moment and the product.
     * <ul>
     * <li>brand, brandMark: the product name and the letters of its square logo</li>
     * <li>date (Thursday, 29 October 2026), dateShort (29 Oct 2026), weekday, weekdayShort (Thu), day, monthYear (Oct 2026), time (11:30), year</li>
     * <li>zone (EAT) and zoneName (Nairobi time)</li>
     * <li>hello: Good morning, Good afternoon or Good evening, by the Nairobi hour</li>
     * <li>ref: a reference for this email, such as DA-261029-7F3K</li>
     * <li>copyright: the full line, holder: the name in it</li>
     * <li>supportEmail, website, websiteLabel, tagline, company, address</li>
     * </ul>
     */
    static Map<String, Object> globals(String name, String brand, Instant at) {
        MailBranding b = branding;
        Map<String, Object> g = new LinkedHashMap<>();
        String mark = b.mark().isBlank() ? initials(brand) : b.mark();
        String holder = b.company().isBlank() ? brand : b.company();
        int hour = Integer.parseInt(DateTimeFormatter.ofPattern("H", Locale.ENGLISH).withZone(NAIROBI).format(at));
        g.put("brand", brand);
        g.put("brandMark", mark);
        g.put("date", LONG_DATE.format(at));
        g.put("dateShort", SHORT_DATE.format(at));
        g.put("weekday", WEEKDAY.format(at));
        g.put("weekdayShort", WEEKDAY_SHORT.format(at));
        g.put("day", DAY.format(at));
        g.put("monthYear", MONTH_YEAR.format(at));
        g.put("time", CLOCK.format(at));
        g.put("year", YEAR.format(at));
        g.put("zone", "EAT");
        g.put("zoneName", "Nairobi time");
        g.put("hello", hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening");
        g.put("ref", reference(name, brand, mark, at));
        g.put("holder", holder);
        g.put("copyright", "© " + YEAR.format(at) + " " + holder + ". All rights reserved.");
        g.put("supportEmail", b.supportEmail());
        g.put("website", b.website());
        g.put("websiteLabel", websiteLabel(b.website()));
        g.put("tagline", b.tagline());
        g.put("company", b.company());
        g.put("address", b.address());
        return g;
    }

    static String defaultReason(String brand) {
        return "You are getting this email because of activity on your " + brand + " account. You can switch sign in and payment emails off under Profile in the app.";
    }

    /** "Good afternoon, Amina." or "Good afternoon." when there is no name. */
    static String greeting(Instant at, String name) {
        String hello = (String) globals("x", "x", at).get("hello");
        return name == null || name.isBlank() ? hello + "." : hello + ", " + name.trim() + ".";
    }

    static String initials(String brand) {
        StringBuilder sb = new StringBuilder();
        for (String word : (brand == null ? "" : brand).trim().split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty() && sb.length() < 2) {
                sb.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return sb.length() == 0 ? "M" : sb.toString();
    }

    static String reference(String name, String brand, String mark, Instant at) {
        int hash = (name + "|" + brand + "|" + at.getEpochSecond()).hashCode();
        String code = Integer.toString(Math.floorMod(hash, 36 * 36 * 36 * 36), 36).toUpperCase(Locale.ROOT);
        return mark.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "") + "-" + REF_DATE.format(at) + "-" + "0".repeat(4 - code.length()) + code;
    }

    static String websiteLabel(String url) {
        String s = url == null ? "" : url.trim();
        s = s.replaceFirst("^(?i)https?://", "");
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // ------------------------------------------------------------------ template files

    static TemplateEngine template(String file) {
        String dir = branding.templatesDir();
        if (!dir.isEmpty()) {
            return TemplateEngine.parse(file, expand(file, source(file), 0));
        }
        return CACHE.computeIfAbsent(file, f -> TemplateEngine.parse(f, expand(f, source(f), 0)));
    }

    /** The full source of a template with its partials in place. Used by tests. */
    static String sourceOf(String file) {
        return expand(file, source(file), 0);
    }

    private static String expand(String file, String src, int depth) {
        if (depth > 5) {
            throw new IllegalStateException("Template " + file + ": partials are nested too deep.");
        }
        Matcher m = PARTIAL.matcher(src);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String part = "partials/" + m.group(1) + ".html";
            m.appendReplacement(out, Matcher.quoteReplacement(expand(part, source(part), depth + 1)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * A picture used by the emails, such as images/hero.png, read from the edited folder first and then from the program.
     * Returns null when there is no such picture. The mail sender attaches it wherever the HTML says cid:NAME.
     */
    static byte[] image(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        String dir = branding.templatesDir();
        if (!dir.isEmpty()) {
            Path p = Path.of(dir).resolve("images").resolve(name + ".png").normalize();
            if (Files.isRegularFile(p)) {
                try {
                    return Files.readAllBytes(p);
                } catch (IOException e) {
                    return null;
                }
            }
        }
        try (InputStream in = MailRenderer.class.getResourceAsStream("/mail/templates/images/" + name + ".png")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** The picture names an HTML email refers to with cid:NAME, in order, each once. */
    static java.util.List<String> imagesIn(String html) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        if (html != null) {
            Matcher m = CID.matcher(html);
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        return new java.util.ArrayList<>(names);
    }

    private static String source(String file) {
        String dir = branding.templatesDir();
        if (!dir.isEmpty()) {
            Path p = Path.of(dir).resolve(file).normalize();
            if (p.startsWith(Path.of(dir).normalize()) && Files.isRegularFile(p)) {
                try {
                    return Files.readString(p, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read the email template " + p + ": " + e.getMessage(), e);
                }
            }
        }
        return SOURCES.computeIfAbsent(file, f -> {
            try (InputStream in = MailRenderer.class.getResourceAsStream("/mail/templates/" + f)) {
                if (in == null) {
                    throw new IllegalStateException("The email template mail/templates/" + f + " is missing.");
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read the email template " + f + ": " + e.getMessage(), e);
            }
        });
    }
}