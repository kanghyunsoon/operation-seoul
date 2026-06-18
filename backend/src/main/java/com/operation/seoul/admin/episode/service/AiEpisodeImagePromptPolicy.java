package com.operation.seoul.admin.episode.service;

final class AiEpisodeImagePromptPolicy {
    private static final String TEXT_BAN_SUFFIX =
            "no readable text, no Korean letters, no numbers, no labels, no handwriting, no sign text, no legible document text";

    private AiEpisodeImagePromptPolicy() {
    }

    static boolean hasTextFreeImageConstraints(String prompt) {
        if (blank(prompt)) {
            return false;
        }
        String normalized = prompt.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("no readable text")
                && normalized.contains("no korean letters")
                && normalized.contains("no numbers")
                && normalized.contains("no labels")
                && normalized.contains("no handwriting");
    }

    static String ensureTextFreeImagePrompt(String prompt) {
        String base = blank(prompt) ? "" : prompt.trim();
        if (hasTextFreeImageConstraints(base)) {
            return base;
        }
        return blank(base) ? TEXT_BAN_SUFFIX : base + ", " + TEXT_BAN_SUFFIX;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
