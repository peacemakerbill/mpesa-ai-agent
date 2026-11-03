# Email templates

Every automatic email is built from files in one folder, so the look and the wording can change without touching Java. Emails are sent as HTML only. There are no plain text template files.

```
agentx-api/src/main/resources/mail/templates/
  layout.html          the frame around every email: header, date leaf, pattern band, status label, title, stub, footer
  welcome.html
  sign-in.html
  payment.html
  email-code.html
  report.html
  report-notice.html
  partials/
    slip.html          the ledger of label and value rows
    callout.html       the note with a coloured edge
    button.html        the button
  images/
    hero.png           the pattern band under the header (600 by 72, drawn at double size)
    edge.png           the thin band at the bottom of the card (600 by 12)
    hero.svg, edge.svg the same drawings as editable vector files (not used at run time)
```

Each `NAME.html` holds only the words and the pieces inside the card. `layout.html` puts the header, the pattern band, the title and the stub with the reference around it.

## The look

- A deep green header with a bright green logo square, a white calendar leaf with the date, and under it a flat band of overlapping arcs, like radio waves spreading from a phone mast. The pictures use flat colour only: no gradients, no shadows.
- Green first, in the family of the Safaricom and M-Pesa greens, with white. Red is kept for failures and amber for security notices, so a problem is never confused with the brand.
- Square corners, thin green rules and a ledger for the details.
- Fonts: Bricolage Grotesque for titles, Hanken Grotesk for text, DM Mono for codes, labels and the reference. They load from Google Fonts in Apple Mail, iOS Mail, Samsung Mail and Thunderbird. Gmail and Outlook do not load web fonts, so they show the fallbacks named in every style (Trebuchet MS or Segoe UI, Helvetica or Arial, and Courier New). To stop any request to Google Fonts, delete the `<link href="https://fonts.googleapis.com/...">` line in `layout.html`.
- The status label is a solid block: green for completed, red for failed, amber for security notices, deep green for codes and reports.
- The verification code is shown one digit per box.
- Dark mode switches the page, the card, the rules and the stub in clients that support it. The header and pattern stay green.

### The palette

These are my approximation of the brand greens, not official values. If Safaricom's brand guide gives exact ones, find and replace them in `layout.html`, the other template files and `MailRenderer.java` (the `Tone` list), and redraw the two pictures.

| Colour | Hex | Used for |
|---|---|---|
| Deep green | `#0A4A20` | header, pattern background, rules, button |
| Bright green | `#43B02A` | logo square, leaf top, file box, button edge |
| Lime | `#8FD468` | pattern highlights, dark mode accents |
| Mint | `#E8F6DF` | the stub at the bottom of the card |
| Ink | `#0C2414` | titles |
| Text | `#1B2E21` | body text |
| Muted | `#4E6355` | labels and footer |
| Rule | `#CFE0C8` | thin lines |
| Page | `#EEF3EC` | the background around the card |
| Success | `#1A7F2A` | "Completed" label |
| Failure | `#D6001C` | "Failed" label and its note |
| Notice | `#F2A900` | "Security notice" label |

This is a colour choice only. The emails carry no Safaricom or M-Pesa logo or name, and nothing in them says they come from Safaricom.

### The pattern pictures

The two bands are small PNG files, about 3 KB and 1 KB. The mail sender attaches them inside the email wherever the HTML says `src="cid:hero"` or `src="cid:edge"`, so they show without any web address and without a download. The header and bands sit on a green background, so with images switched off the email still looks green. To change a picture, replace the PNG (keep the name, 1200 by 144 and 1200 by 24 pixels for sharp screens), or add a new one and refer to it as `cid:NAME` in a template. With `MAIL_TEMPLATES_DIR` set, pictures are read from its `images` folder first.

## What is in each email

Every email, without anyone asking, carries:

| Piece | Where | Example |
|---|---|---|
| Calendar leaf with month, day, weekday and time | top right of the header | `Oct 2026`, `29`, `Thu 11:30` |
| Greeting by the hour in Nairobi | first line | Good morning, Amina. |
| Status label | above the title | Completed, Failed, Security notice |
| Reference, issue date and time | the mint stub at the bottom of the card | `DA-261029-Z0PN`, `29 Oct 2026`, `11:30 EAT` |
| Copyright line with the year | footer | (c) 2026 Daraja Agent-X Ltd. All rights reserved. |
| Why you got this email | footer | and how to switch it off |
| Support address, website, company, address, tagline | footer | only the ones you have set |

On top of that: the code email says when the code stops working (`expires` is the sending time plus the code lifetime), a report shows the file type, its name, the number of rows and when it was made, a payment shows the M-Pesa code in the mono face, and a failed payment adds a "What to do next" note.

The reference is a label for people to quote when they write to support. It is built from the brand letters, the date and four letters worked out from the email type and the second it was sent, so it is not a secret.

All times are shown in Nairobi time.

## Sent as HTML only

The mail server gets one body, the HTML. Each `MailTemplates.Message` also has a short plain `text()` digest of the same facts. It is never sent. It is what `transport: log` prints, so a developer can read a verification code without a mail server, and what tests read. It is also the whole email, as plain text, if an edited template file is broken.

Some spam filters like a plain text alternative next to the HTML. If your mail ever lands in spam, that is one thing to look at after the usual ones (SPF, DKIM and DMARC for your sending domain).

## Settings

Under `agentx.brand` in `application.yml`. Every one is optional, and a blank one is simply left out of the email.

| Setting | Environment variable | Default | What it does |
|---|---|---|---|
| `support-email` | `AGENTX_SUPPORT_EMAIL` | blank | Footer "Questions? Write to". Blank uses `MAIL_REPLY_TO`, then `MAIL_FROM` (unless that is the placeholder `no-reply@localhost`). |
| `website` | `AGENTX_WEBSITE` | blank | A link in the footer and the button on the welcome email. |
| `tagline` | `AGENTX_TAGLINE` | M-Pesa payments and reports, by chat | The last line of the footer. |
| `company` | `AGENTX_COMPANY` | blank | The name in the copyright line and the footer. Blank uses `agentx.app-name`. |
| `address` | `AGENTX_COMPANY_ADDRESS` | blank | Shown after the company name. |
| `mark` | `AGENTX_BRAND_MARK` | blank | The letters in the square logo. Blank uses the initials of `agentx.app-name` (DA for Daraja Agent-X). |
| `templates-dir` | `MAIL_TEMPLATES_DIR` | blank | A folder of edited template files. See below. |

## Changing the look

Two ways.

1. Edit the files in `src/main/resources/mail/templates` and rebuild.
2. Without a rebuild: copy the `templates` folder somewhere, set `MAIL_TEMPLATES_DIR` to it, edit the copy. The files are read again for every email, so a saved change shows in the next email. A file missing from your folder is taken from the program, so you can keep only the files you changed. A broken edited file never stops an email: the log says what is wrong and the email goes out as plain text.

To see a template without sending anything, keep `MAIL_TRANSPORT=log` and trigger the email; the log shows the words. To see the HTML, a test can write `MailTemplates.welcome(...).html()` to a file and open it in a browser.

## The template language

Small on purpose.

| Write | Meaning |
|---|---|
| `{{name}}` | the value, escaped for HTML. Use this for everything a person typed. |
| `{{{name}}}` | the value as it is. Only for markup the program built. The email body in `layout.html` is the only user of it. |
| `{{#if name}} ... {{/if}}` | shown when the value is present (not empty, not false) |
| `{{#unless name}} ... {{/unless}}` | shown when it is not |
| `{{#each rows}} ... {{/each}}` | repeated for each item of a list. Inside, `{{label}}` is read from the item first, then from the outer data. `{{.}}` is the item itself. |
| `{{> slip}}` | pulls in `partials/slip.html` |
| `{{! note }}` | a comment that is not shown |

A block tag alone on its line takes the whole line with it, so the HTML stays tidy.

Names every template can read: `brand`, `brandMark`, `date`, `dateShort`, `weekday`, `weekdayShort`, `day`, `monthYear`, `time`, `year`, `zone`, `zoneName`, `hello`, `ref`, `holder`, `copyright`, `supportEmail`, `website`, `websiteLabel`, `tagline`, `company`, `address`, `reason`, `subject`, `heading`, `status`, `preheader`, `accent`, `onAccent`, `tone`.

Names each email adds are in `MailTemplates.java`, next to the words of the subject.

## Rules the design follows

- No emojis, no pictographs and no arrow characters, in the files or the data. A test checks every file and every rendered email.
- No gradients, shadows or rounded corners in the code. A test checks that too. The pictures are flat colour.
- Layout is tables with inline styles, which is what mail clients (including Outlook and Gmail) understand.
- On a phone the card fills the width, lists stack, the stub becomes a column and the code boxes shrink.
- The first line shown in the inbox list (the preheader) is set on purpose and never contains the verification code.
- The subject never carries the code or a line break.

## Tests

`TemplateEngineTest` covers the language. `MailTemplateFilesTest` renders every email in strict mode (a name with no data is an error) and checks: nothing unreplaced, no emoji, the date, time, year, copyright and reference are present, the fonts are named, the greeting follows the hour, the pattern pictures exist and are attached, the code expiry time and one box per digit, colours and labels for a failed payment, the footer details appear only when set, and that an edited folder and a broken edited file behave as described above.

When you add a name to a template, give it a value in `MailTemplates.java`, and the strict tests tell you if you forgot.
