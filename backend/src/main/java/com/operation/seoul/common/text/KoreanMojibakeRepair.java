package com.operation.seoul.common.text;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class KoreanMojibakeRepair {
    private static final Charset WINDOWS_949 = Charset.forName("windows-949");

    private KoreanMojibakeRepair() {
    }

    public static String repair(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String repaired = new String(value.getBytes(WINDOWS_949), StandardCharsets.UTF_8);
        return cjkCount(repaired) == 0 && koreanTextScore(repaired) > koreanTextScore(value) + 6 ? repaired : value;
    }

    public static String repairOrFallback(String value, String fallback) {
        String repaired = repair(value);
        return isLikelyMojibake(repaired) ? fallback : repaired;
    }

    public static List<String> repairList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(KoreanMojibakeRepair::repair).toList();
    }

    public static List<String> repairListOrFallback(List<String> values, String fallback) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value -> repairOrFallback(value, fallback)).toList();
    }

    private static boolean isLikelyMojibake(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.indexOf('\uFFFD') >= 0
                || value.indexOf('\u0080') >= 0
                || value.indexOf('\u0094') >= 0
                || value.indexOf('\u009C') >= 0
                || value.contains("占")
                || value.contains("珥")
                || value.contains("?섏")
                || value.contains("?쒖")
                || value.contains("理")
                || value.contains("異")
                || (cjkCount(value) > 0 && hangulCount(value) > 0 && value.contains("?"));
    }

    private static int koreanTextScore(String value) {
        int score = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 0xAC00 && ch <= 0xD7A3) {
                score += 3;
            } else if (ch >= 0x3130 && ch <= 0x318F) {
                score -= 1;
            } else if (ch >= 0xF900 && ch <= 0xFAFF) {
                score -= 4;
            } else if (ch == '\uFFFD') {
                score -= 8;
            } else if ((ch >= 0x4E00 && ch <= 0x9FFF) || ch == '?' || ch == '\u0080' || ch == '\u0094' || ch == '\u009C') {
                score -= 2;
            } else if (Character.isLetterOrDigit(ch) || Character.isWhitespace(ch) || ".,!?/:-_()[]".indexOf(ch) >= 0) {
                score += 1;
            }
        }
        return score;
    }

    private static int cjkCount(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                count++;
            }
        }
        return count;
    }

    private static int hangulCount(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 0xAC00 && ch <= 0xD7A3) {
                count++;
            }
        }
        return count;
    }
}
