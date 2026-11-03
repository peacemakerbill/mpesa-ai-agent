<div align="center">

<img src="../docs/assets/banner-hooks.svg" alt="" width="100%"/>

<br/>

<img src="../docs/assets/badges-hooks.svg" alt="Spring Boot agent-service, edits to existing files, new files are complete and copied in, package com.darajaagentx.agent." width="880"/>

<br/><br/>

Three small edits connect the profile and email tools to the agent-service.

<sub><a href="README.md">Back to agent-service</a> &nbsp;|&nbsp; <a href="../README.md">Project overview</a> &nbsp;|&nbsp; <a href="../docs/PROFILE_AND_EMAIL.md">Profile and email</a></sub>

<img src="../docs/assets/divider.svg" alt="" width="100%"/>

</div>

# Connecting ProfileTools to the agent-service

The new files (`ProfileTools`, `ProfileApi`, `AgentXProfileClient`) are complete. They give the assistant eight tools:

| Tool | What the customer says | What happens |
| --- | --- | --- |
| `get_my_profile` | "show my profile" | Name, email (verified or not), address, location, picture yes or no, alert switches |
| `update_my_profile` | "change my last name to Otieno", "my city is Kisumu", "stop emailing me when I sign in" | Only the fields named are changed |
| `request_email_verification` | "use bill@example.com as my email" | A 6 digit code is emailed. The assistant never sees it |
| `confirm_email_code` | "the code is 123456" | The address becomes the verified profile email |
| `list_email_recipients` | "who can you send reports to?" | Shows the saved addresses |
| `save_email_recipients` | "save these emails" (typed, pasted or an attached list) | Finds every address in the text and saves it |
| `remove_email_recipient` | "remove the accountant" | Removes by address or by name |
| `send_report_email` | "send this report to my email as a PDF", "email the successful payments as Excel to a@x.com and b@x.com" | The assistant picks the source and filters, agentx-api builds the file and sends it |

The phone number is the signed in customer's, never one the model chose. The package is `com.darajaagentx.agent`, and the JSON imports are Jackson 3
(`tools.jackson.databind`), like the business tools. For Jackson 2 change the imports.

## 1. ToolExecutor: send the eight tools to ProfileTools

Add a field and constructor argument next to `BusinessTools`:

```java
private final ProfileTools profileTools;

public ToolExecutor(..., BusinessTools businessTools, ProfileTools profileTools) {
    ...
    this.profileTools = profileTools;
}
```

In `execute`, directly after the `businessTools.handles(name)` block:

```java
if (profileTools.handles(name)) {
    return profileTools.execute(ctx, name, input);
}
```

If a test builds `ToolExecutor` by hand, add a `ProfileTools` argument there too (a mock is enough).

## 2. ToolCatalog: show the tools to every role

In `toolsFor`, directly after the line that adds the business tools:

```java
ProfileTools.definitions().forEach(d -> tools.add(mapper.valueToTree(d)));
```

## 3. SystemPrompt: one paragraph

Add a constant and include it in `rules()` after `BUSINESS_ACCOUNTS`:

```java
private static final String PROFILE_AND_EMAIL = """

        THE PERSON'S PROFILE AND EMAIL
        - The person can ask to see or change their profile: first, middle and last name, address, city, county, country and location. Use get_my_profile and update_my_profile, and change only what they asked.
        - Their email address is set in two steps. request_email_verification emails a 6 digit code. They read it in their inbox and type it here, then you call confirm_email_code. You never see the code and you never guess it.
        - The profile picture is changed in the app, not in the chat.
        - They can ask for a report by email: "send this report to my email as a PDF", "email me the successful payments as Excel", "send this as CSV to a@x.com and b@x.com". Use send_report_email.
        - Pick the source that matches what they mean: BUSINESS_REQUESTS, BUSINESS_ACCOUNTS or PAYMENTS. When they mean a table you just showed in the chat, use TABLE with the same columns and rows. Put in any filter they gave (status, type, account, from, until).
        - Format: pdf unless they say csv, or Excel / xlsx / spreadsheet.
        - "my email", "my address", "me", or no address at all means the email on their profile. Do not ask for it. If it is not verified yet, tell them and offer to verify it.
        - If they name addresses, send to exactly those. If they say a list of people but none are named or saved, ask who. Never invent or complete an address. Saved addresses can be used with to_saved, or by name.
        - If they paste addresses or attach a list, call save_email_recipients with the text. Then ask whether to send the report to them now.
        - Say plainly where the report went. If the tool says it was not sent, say that it was not sent.
        - A report never contains keys, secrets, passwords or passkeys.
        """;

public String rules() {
    String text = INTRO.formatted(business.name()) + SPEAK + WALLET + BUSINESS_ACCOUNTS + PROFILE_AND_EMAIL + SAFETY + FILES;
    ...
```

## Attached files

If your chat code puts the text of an attached `.csv` or `.txt` list into the conversation, the assistant passes that text to `save_email_recipients` and every address in it is saved.
I could not check how your agent-service hands attachments to the model, so confirm that a plain text file reaches the model as text. If it does not, the customer can
paste the addresses into the chat, or upload the list in the app under Profile, Email recipients (that route reads `.csv` and `.txt` on the server). Spreadsheet files (`.xlsx`)
are not read for addresses: ask the customer to save them as CSV.

## application.yml

Nothing new in agent-service. The profile client reads the same `agentx.base-url` (falling back to `companion.base-url`) and `internal.key` as the
business client. Email itself is configured in agentx-api (`agentx.mail`), see [Profile and email](../docs/PROFILE_AND_EMAIL.md).

## Needs a human to check

- The tools call `/internal/profile` and `/internal/email/...` on agentx-api. Those need the same `INTERNAL_KEY` in both services.
- A retried model call can send the same report twice. Reports have no idempotency key yet.
