package com.darajaagentx.api.mail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A very small template language for the email files, so the look of an email lives in a file and not in Java.
 *
 * <pre>
 * {{name}}                    the value, escaped for HTML
 * {{{name}}}                  the value as it is (only for markup the program built itself)
 * {{#if name}} ... {{/if}}    shown when the value is present (not empty, not false)
 * {{#unless name}} ... {{/unless}}
 * {{#each rows}} ... {{/each}} repeated for every item of a list. Inside, {{field}} is read from the item first and
 *                             then from the outer data, and {{.}} is the item itself when it is plain text
 * {{! a note }}               a comment, never shown
 * </pre>
 *
 * A block tag that stands alone on its line takes the whole line with it, so text files stay tidy. Anything a person
 * typed must go through {{name}}, never {{{name}}}. In strict mode a name with no value at all is an error, which is how
 * the tests catch a typo in a template; at run time an unknown name is simply empty, so a typo can never stop an email.
 */
final class TemplateEngine {

    private sealed interface Node permits Text, Var, Section {
    }

    private record Text(String text) implements Node {
    }

    private record Var(String key, boolean raw) implements Node {
    }

    private record Section(String kind, String key, List<Node> body) implements Node {
    }

    private record Token(boolean tag, String body, boolean triple) {
    }

    private final List<Node> nodes;
    private final String name;

    private TemplateEngine(String name, List<Node> nodes) {
        this.name = name;
        this.nodes = nodes;
    }

    static TemplateEngine parse(String name, String source) {
        List<Token> tokens = tokenize(name, source);
        stripStandaloneLines(tokens);
        int[] at = {0};
        List<Node> nodes = build(name, tokens, at, null);
        return new TemplateEngine(name, nodes);
    }

    String render(Map<String, ?> data, boolean strict) {
        StringBuilder out = new StringBuilder();
        renderNodes(nodes, new Scope(data, null), strict, out);
        return out.toString();
    }

    /** The names a template reads, for tests that check a template and its data agree. */
    java.util.Set<String> names() {
        java.util.Set<String> found = new java.util.TreeSet<>();
        collect(nodes, found);
        return found;
    }

    // ------------------------------------------------------------------ parsing

    private static List<Token> tokenize(String name, String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int open = s.indexOf("{{", i);
            if (open < 0) {
                out.add(new Token(false, s.substring(i), false));
                break;
            }
            if (open > i) {
                out.add(new Token(false, s.substring(i, open), false));
            }
            boolean triple = s.startsWith("{{{", open);
            String close = triple ? "}}}" : "}}";
            int end = s.indexOf(close, open + (triple ? 3 : 2));
            if (end < 0) {
                throw new IllegalArgumentException("Template " + name + ": a tag opened at character " + open + " is never closed.");
            }
            out.add(new Token(true, s.substring(open + (triple ? 3 : 2), end).trim(), triple));
            i = end + close.length();
        }
        return out;
    }

    private static boolean isBlock(Token t) {
        if (!t.tag() || t.triple()) {
            return false;
        }
        String b = t.body();
        return b.startsWith("#") || b.startsWith("/") || b.startsWith("!");
    }

    /** A block tag or comment alone on its line is removed together with the line break after it. */
    private static void stripStandaloneLines(List<Token> tokens) {
        // Make text and tags alternate so that every tag has a text token on both sides.
        List<Token> alt = new ArrayList<>();
        boolean lastWasText = false;
        for (Token t : tokens) {
            if (!t.tag() && lastWasText) {
                Token prev = alt.remove(alt.size() - 1);
                alt.add(new Token(false, prev.body() + t.body(), false));
                continue;
            }
            if (t.tag() && !lastWasText) {
                alt.add(new Token(false, "", false));
            }
            alt.add(t);
            lastWasText = !t.tag();
        }
        if (!lastWasText) {
            alt.add(new Token(false, "", false));
        }
        boolean lineStartAfterPreviousTag = false;
        for (int i = 1; i < alt.size() - 1; i += 2) {
            Token tag = alt.get(i);
            Token before = alt.get(i - 1);
            Token after = alt.get(i + 1);
            boolean standalone = false;
            if (isBlock(tag)) {
                String b = before.body();
                int nl = b.lastIndexOf('\n');
                boolean startsLine;
                if (nl >= 0) {
                    startsLine = blank(b.substring(nl + 1));
                } else {
                    startsLine = blank(b) && (i == 1 || lineStartAfterPreviousTag);
                }
                String a = after.body();
                int firstBreak = a.indexOf('\n');
                boolean endsLine = firstBreak >= 0 ? blank(a.substring(0, firstBreak)) : (blank(a) && i + 2 >= alt.size());
                if (startsLine && endsLine) {
                    standalone = true;
                    alt.set(i - 1, new Token(false, nl >= 0 ? b.substring(0, nl + 1) : "", false));
                    alt.set(i + 1, new Token(false, firstBreak >= 0 ? a.substring(firstBreak + 1) : "", false));
                }
            }
            lineStartAfterPreviousTag = standalone;
        }
        tokens.clear();
        tokens.addAll(alt);
    }

    private static boolean blank(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r') {
                return false;
            }
        }
        return true;
    }

    private static List<Node> build(String name, List<Token> tokens, int[] at, String closing) {
        List<Node> nodes = new ArrayList<>();
        while (at[0] < tokens.size()) {
            Token t = tokens.get(at[0]++);
            if (!t.tag()) {
                if (!t.body().isEmpty()) {
                    nodes.add(new Text(t.body()));
                }
                continue;
            }
            String b = t.body();
            if (t.triple()) {
                nodes.add(new Var(b, true));
            } else if (b.startsWith("!")) {
                continue;
            } else if (b.startsWith("/")) {
                String kind = b.substring(1).trim();
                if (closing == null || !closing.equals(kind)) {
                    throw new IllegalArgumentException("Template " + name + ": {{" + b + "}} has no matching opening tag.");
                }
                return nodes;
            } else if (b.startsWith("#")) {
                String[] parts = b.substring(1).trim().split("\\s+");
                if (parts.length != 2 || !(parts[0].equals("if") || parts[0].equals("unless") || parts[0].equals("each"))) {
                    throw new IllegalArgumentException("Template " + name + ": {{" + b + "}} is not understood. Use #if, #unless or #each with one name.");
                }
                nodes.add(new Section(parts[0], parts[1], build(name, tokens, at, parts[0])));
            } else {
                nodes.add(new Var(b, false));
            }
        }
        if (closing != null) {
            throw new IllegalArgumentException("Template " + name + ": {{#" + closing + "}} is never closed.");
        }
        return nodes;
    }

    private static void collect(List<Node> nodes, java.util.Set<String> found) {
        for (Node n : nodes) {
            if (n instanceof Var v) {
                found.add(v.key());
            } else if (n instanceof Section s) {
                found.add(s.key());
                collect(s.body(), found);
            }
        }
    }

    // ------------------------------------------------------------------ rendering

    private record Scope(Object value, Scope outer) {
    }

    private void renderNodes(List<Node> list, Scope scope, boolean strict, StringBuilder out) {
        for (Node n : list) {
            if (n instanceof Text t) {
                out.append(t.text());
            } else if (n instanceof Var v) {
                Object value = lookup(scope, v.key(), strict);
                String s = value == null ? "" : String.valueOf(value);
                out.append(v.raw() ? s : MailTemplates.esc(s));
            } else if (n instanceof Section s) {
                Object value = lookup(scope, s.key(), strict);
                switch (s.kind()) {
                    case "if" -> {
                        if (truthy(value)) {
                            renderNodes(s.body(), scope, strict, out);
                        }
                    }
                    case "unless" -> {
                        if (!truthy(value)) {
                            renderNodes(s.body(), scope, strict, out);
                        }
                    }
                    default -> {
                        if (value instanceof Collection<?> items) {
                            for (Object item : items) {
                                renderNodes(s.body(), new Scope(item, scope), strict, out);
                            }
                        }
                    }
                }
            }
        }
    }

    private Object lookup(Scope scope, String key, boolean strict) {
        if (".".equals(key)) {
            return scope.value();
        }
        for (Scope s = scope; s != null; s = s.outer()) {
            if (s.value() instanceof Map<?, ?> map && map.containsKey(key)) {
                return map.get(key);
            }
        }
        if (strict) {
            throw new IllegalStateException("Template " + name + " uses {{" + key + "}} but no value was given for it.");
        }
        return null;
    }

    private static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof CharSequence c) {
            return !c.toString().isBlank();
        }
        if (v instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0;
        }
        return true;
    }
}