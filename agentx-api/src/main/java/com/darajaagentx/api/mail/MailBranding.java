package com.darajaagentx.api.mail;

/**
 * The company details shown in the footer of every email. All of them are optional: a blank one is left out of the
 * email. Set them under {@code agentx.brand} in application.yml. The name of the product itself comes from
 * {@code agentx.app-name}.
 *
 * @param supportEmail where customers write for help. Blank falls back to the reply-to address, then to the sender
 * @param website      the public address of the product, shown as a link. Blank for none
 * @param tagline      one short line under the footer
 * @param company      the legal or trading name used in the copyright line and the footer, blank to use the product name
 * @param address      the postal or office address, shown after the company name
 * @param mark         the two or three letters in the square logo, blank to use the initials of the product name
 * @param templatesDir a folder with edited copies of the template files, blank to use the ones built into the program
 */
public record MailBranding(String supportEmail, String website, String tagline, String company, String address, String mark, String templatesDir) {

    public static final MailBranding DEFAULT = new MailBranding("", "", "M-Pesa payments and reports, by chat", "", "", "", "");

    public MailBranding {
        supportEmail = clean(supportEmail);
        website = clean(website);
        tagline = clean(tagline);
        company = clean(company);
        address = clean(address);
        mark = clean(mark);
        templatesDir = clean(templatesDir);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}