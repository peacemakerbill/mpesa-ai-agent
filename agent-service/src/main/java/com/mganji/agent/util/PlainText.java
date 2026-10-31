package com.mganji.agent.util;

/** Turns chat Markdown into plain words, for the spoken copy of an answer. */
public final class PlainText {

    private PlainText() {
    }

    public static String strip(String markdown) {
        if (markdown == null) {
            return "";
        }
        return markdown
                .replaceAll("(?m)^\\s*#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*]\\s+", "")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1")
                .replaceAll("[*_`~]", "")
                .replaceAll("\\s+", " ")
                .strip();
    }
}
