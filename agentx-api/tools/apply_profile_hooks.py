#!/usr/bin/env python3
"""
Connects the profile and email feature to three files of agentx-api that were not replaced by the update:

  1. config/DeviceAuthFilter.java   sign up is public, like sign in
  2. controller/AuthController.java a sign in alert email after a device signs in
  3. pom.xml                        spring-boot-starter-mail (needed for agentx.mail.transport: smtp)

Usage:  python3 apply_profile_hooks.py [REPO_ROOT]       (default: the current folder)

Safe to run twice. A file whose text does not match what this tool expects is left alone and the exact edit is printed,
so it can be done by hand. Nothing here touches the database or any other file.
"""
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
api = root / "agentx-api"
java = api / "src/main/java/com/darajaagentx/api"
done, manual = [], []


def read(p):
    return p.read_text(encoding="utf-8")


def write(p, s):
    p.write_text(s, encoding="utf-8")


def note_manual(name, text):
    manual.append((name, text))


# ---------------------------------------------------------------- 1. DeviceAuthFilter
f = java / "config/DeviceAuthFilter.java"
if not f.exists():
    note_manual("DeviceAuthFilter.java", "File not found at " + str(f))
else:
    s = read(f)
    if "/api/auth/signup/" in s:
        done.append("DeviceAuthFilter: already allows sign up")
    else:
        new = s.replace('path.equals("/api/auth/request-code")', 'path.equals("/api/auth/request-code") || path.startsWith("/api/auth/signup/")', 1)
        if new == s:
            note_manual("DeviceAuthFilter.java",
                        'In shouldNotFilter, also return true for paths that start with "/api/auth/signup/":\n'
                        '    return !path.startsWith("/api/") || path.equals("/api/auth/verify") || path.equals("/api/auth/request-code")\n'
                        '            || path.startsWith("/api/auth/signup/");')
        else:
            write(f, new)
            done.append("DeviceAuthFilter: sign up is now public")

# ---------------------------------------------------------------- 2. AuthController
f = java / "controller/AuthController.java"
if not f.exists():
    note_manual("AuthController.java", "File not found at " + str(f))
else:
    s = read(f)
    if "CustomerMailer" in s:
        done.append("AuthController: already sends the sign in alert")
    else:
        new = s
        ok = True

        def sub(pattern, repl, text):
            global ok
            out, n = re.subn(pattern, repl, text, count=1, flags=re.S)
            if n != 1:
                ok = False
            return out

        new = sub(r"(import com\.darajaagentx\.api\.service\.CodeDelivery;)", r"\1\nimport com.darajaagentx.api.service.CustomerMailer;", new)
        new = sub(r"(\n(\s*)private final [A-Za-z]*Properties props;)", r"\1\n\2private final CustomerMailer mailer;", new)
        new = sub(r"(public AuthController\([^)]*?)\)\s*\{", r"\1, CustomerMailer mailer) {", new)
        new = sub(r"(\n(\s*)this\.props = props;)", r"\1\n\2this.mailer = mailer;", new)
        new = sub(r"return links\.verify\(request\);",
                  "TokenResponse token = links.verify(request);\n"
                  "        // An email to the customer's verified address, at most once in a while. It never blocks or fails the sign in.\n"
                  "        mailer.signedIn(Phones.normalize(request.phone()), request.deviceName());\n"
                  "        return token;", new)
        if ok:
            write(f, new)
            done.append("AuthController: sends the sign in alert after a sign in")
        else:
            note_manual("AuthController.java",
                        "1. import com.darajaagentx.api.service.CustomerMailer;\n"
                        "2. add a field `private final CustomerMailer mailer;` and a constructor argument `CustomerMailer mailer` (assign it)\n"
                        "3. in verify(...) replace `return links.verify(request);` with:\n"
                        "       TokenResponse token = links.verify(request);\n"
                        "       mailer.signedIn(Phones.normalize(request.phone()), request.deviceName());\n"
                        "       return token;")

# ---------------------------------------------------------------- 3. pom.xml
f = api / "pom.xml"
if not f.exists():
    note_manual("pom.xml", "File not found at " + str(f))
else:
    s = read(f)
    if "spring-boot-starter-mail" in s:
        done.append("pom.xml: already has spring-boot-starter-mail")
    else:
        m = re.search(r"<dependency>\s*<groupId>org\.springframework\.boot</groupId>\s*<artifactId>spring-boot-starter(?:-web|-webmvc)?</artifactId>.*?</dependency>", s, re.S)
        block = ("\n        <dependency>\n            <groupId>org.springframework.boot</groupId>\n"
                 "            <artifactId>spring-boot-starter-mail</artifactId>\n        </dependency>")
        if m:
            write(f, s[:m.end()] + block + s[m.end():])
            done.append("pom.xml: added spring-boot-starter-mail")
        else:
            note_manual("pom.xml", "Add inside <dependencies>:\n" + block.strip("\n"))

print("Profile and email hooks:")
for line in done:
    print("  done   " + line)
for name, text in manual:
    print("  MANUAL " + name)
    for l in text.splitlines():
        print("         " + l)
sys.exit(0)
