#!/usr/bin/env python3
"""Adds the email branding settings to agentx-api's application.yml and links the new doc.

Run from the repository root:  python3 add-email-settings.py
Safe to run twice: it changes nothing the second time.
"""
import pathlib

YML = pathlib.Path("agentx-api/src/main/resources/application.yml")
DOC = pathlib.Path("docs/PROFILE_AND_EMAIL.md")

BLOCK = """  # Details in the footer of every email. All optional, a blank one is left out.
  # The design and wording of each email are files in src/main/resources/mail/templates.
  brand:
    # Where customers write for help. Blank uses MAIL_REPLY_TO, then MAIL_FROM.
    support-email: ${AGENTX_SUPPORT_EMAIL:}
    # Public address of the product. Shown as a link in the footer and on the welcome email's button.
    website: ${AGENTX_WEBSITE:}
    # Where the "Open" button goes: a Google Play or App Store link, or a web address that opens the app. Must start with https://.
    # Blank uses the website. With neither set, the emails have no button.
    app-url: ${AGENTX_APP_URL:}
    tagline: ${AGENTX_TAGLINE:M-Pesa payments and reports, by chat}
    # Name used in the copyright line, for example "Acme Payments Ltd". Blank uses app-name.
    company: ${AGENTX_COMPANY:}
    address: ${AGENTX_COMPANY_ADDRESS:}
    # The two or three letters in the square logo. Blank uses the initials of app-name.
    mark: ${AGENTX_BRAND_MARK:}
    # A folder with edited copies of the email files, so the look can change without a rebuild. Blank uses the built in files.
    templates-dir: ${MAIL_TEMPLATES_DIR:}
"""

if YML.exists():
    text = YML.read_text(encoding="utf-8")
    if "  brand:\n" in text and "app-url" in text:
        print("application.yml already has the brand settings")
    elif "  brand:\n" in text and "templates-dir" in text:
        lines = text.splitlines(keepends=True)
        for i, line in enumerate(lines):
            if line.startswith("    website:") and "AGENTX_WEBSITE" in line:
                lines.insert(i + 1, '    # Where the "Open" button goes: a Google Play or App Store link, or a web address that opens the app. Must start with https://.\n'
                                    '    # Blank uses the website. With neither set, the emails have no button.\n'
                                    '    app-url: ${AGENTX_APP_URL:}\n')
                YML.write_text("".join(lines), encoding="utf-8")
                print("application.yml: app-url added to the brand settings")
                break
    else:
        lines = text.splitlines(keepends=True)
        for i, line in enumerate(lines):
            if line.startswith("  app-name:"):
                lines.insert(i + 1, BLOCK)
                YML.write_text("".join(lines), encoding="utf-8")
                print("application.yml: brand settings added under agentx")
                break
        else:
            print("application.yml: could not find the 'app-name' line under agentx; add the block by hand (see docs/EMAIL_TEMPLATES.md)")
else:
    print("run this from the repository root, application.yml was not found")

if DOC.exists():
    text = DOC.read_text(encoding="utf-8")
    if "EMAIL_TEMPLATES.md" in text:
        print("PROFILE_AND_EMAIL.md already links the email templates doc")
    elif "### The automatic emails\n" in text:
        note = ("The look and wording of each email are files in `agentx-api/src/main/resources/mail/templates`, with the date, time, "
                "copyright, reference and company details filled in automatically. See [EMAIL_TEMPLATES.md](EMAIL_TEMPLATES.md).\n")
        DOC.write_text(text.replace("### The automatic emails\n", "### The automatic emails\n\n" + note, 1), encoding="utf-8")
        print("PROFILE_AND_EMAIL.md: link added")
