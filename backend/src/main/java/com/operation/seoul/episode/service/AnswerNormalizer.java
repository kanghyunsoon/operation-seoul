package com.operation.seoul.episode.service;

import java.text.Normalizer;
import java.util.Locale;

public final class AnswerNormalizer {
    private AnswerNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }
}
