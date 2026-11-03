# Connecting BusinessTools to the agent-service

The new files (`BusinessTools`, `BusinessApi`, `AgentXBusinessClient`, `InternalNotifyController`) are complete. Four small edits to
existing files wire them in.

**Package.** The new files use `package com.darajaagentx.agent...`, which is what `RenameToAgentX.java` gives your agent-service. The JSON imports are
Jackson 3 (`tools.jackson.databind`). If your agent-service still uses Jackson 2 (`com.fasterxml.jackson.databind`), change the imports in the new files to match.

## 1. ToolExecutor: send the four tools to BusinessTools

Add a field and constructor argument, like `WalletTools`:

```java
private final BusinessTools businessTools;

public ToolExecutor(PaymentClient payments, IntentStore intents, ReplySender sender, AgentProperties props,
                    ServiceProperties.Business business, WalletTools wallet, BusinessTools businessTools) {
    ...
    this.businessTools = businessTools;
}
```

In `execute`, directly after the `wallet.handles(name)` block:

```java
if (businessTools.handles(name)) {
    return businessTools.execute(ctx, name, input);
}
```

`BusinessTools` uses `ToolExecutor.Result` and `ToolExecutor.ToolException`, which are package-private factories in the same package.
Keep it in `...agent.service`.

If a test builds `ToolExecutor` by hand, add a `BusinessTools` argument there too.

## 2. ToolCatalog: show the tools to every role

In `toolsFor`, directly after `walletTools().forEach(tools::add);`:

```java
BusinessTools.definitions().forEach(d -> tools.add(mapper.valueToTree(d)));
```

(`requiredRole` returns CUSTOMER for unknown names, which is what we want: the tools only ever act on the signed in customer's own accounts.)

## 3. SystemPrompt: one paragraph

Add a constant and include it in `rules()` after `WALLET`:

```java
private static final String BUSINESS_ACCOUNTS = """

        THE PERSON'S OWN BUSINESS ACCOUNTS
        - People can save their own Paybill or Till (Buy Goods) shortcodes in the app under Settings, Business accounts. They may write one as @Name in a message.
        - Use list_business_accounts to see their names and what each can do. You never see keys, secrets or passwords.
        - Use prepare_business_request for STK Push, STK query, dynamic QR, pull transactions, balance, transaction status, B2C, Pochi, B2B and reversal.
        - You never move money. STK Push and reads go to Safaricom at once. Anything that sends money out waits for the owner to approve it in the app. Say which happened.
        - If the account, the amount or the receiver is missing, ask for it. Never guess a till, a paybill or a phone number.
        - For a person, shortcode or number the user picked with @ or from their contacts, use exactly what the message says.
        """;

public String rules() {
    String text = INTRO.formatted(business.name()) + SPEAK + WALLET + BUSINESS_ACCOUNTS + SAFETY + FILES;
    ...
```

## 4. application.yml

Nothing new is needed if you already have the `companion:` and `internal:` blocks. The new client reads `agentx.base-url` (falling back to
`companion.base-url`), `agentx.public-url` (falling back to `companion.public-url`) and `internal.key`. `internal.key` must equal `INTERNAL_KEY` in agentx-api.

## Needs a human to check

- The chat reply for a request that waits for approval contains `PUBLIC_URL/b/{id}`. That page and the app link are served by agentx-api.
- `InternalNotifyController` is how agentx-api tells the chat that Safaricom answered. It calls `ReplySender.image` and `ReplySender.notifyText`, which exist in your current code, and `util.Phones`.
