package com.operation.seoul.admin.episode.service;

final class FinalAnswerKeywordValidator {
    private FinalAnswerKeywordValidator() {
    }

    static boolean weakFinalAnswerKeyword(String slot, String value) {
        return blank(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
