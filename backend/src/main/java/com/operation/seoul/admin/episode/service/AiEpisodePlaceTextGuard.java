package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AiEpisodePlaceTextGuard {
    private AiEpisodePlaceTextGuard() {
    }

    static List<String> forbiddenPlaceNamesFor(AiEpisodeDraftRequest.PlaceInput place) {
        if (place == null || blank(place.getName())) {
            return List.of();
        }
        String name = place.getName().trim();
        List<String> tokens = new ArrayList<>();
        tokens.add(name);
        String normalized = name.replaceAll("[()\\[\\],/|·]", " ").replaceAll("\\s+", " ").trim();
        for (String token : normalized.split("\\s+")) {
            String cleaned = token.trim();
            if (cleaned.length() >= 2) {
                tokens.add(cleaned);
            }
        }
        for (String suffix : List.of("점", "지점", "본점", "분점", "광화문", "충무로", "교보문고")) {
            if (name.contains(suffix)) {
                tokens.add(suffix);
            }
        }
        return tokens.stream()
                .filter(value -> !blank(value))
                .distinct()
                .toList();
    }

    static List<String> resolvePlaceEvidenceAnchors(AiEpisodeDraftRequest.PlaceInput place, int index) {
        List<String> candidates = new ArrayList<>();
        if (place != null) {
            if (place.getVisibleElements() != null) candidates.addAll(place.getVisibleElements());
            if (place.getKeywords() != null) candidates.addAll(place.getKeywords());
            addAnchorWords(candidates, place.getDescription());
            addAnchorWords(candidates, place.getAdminMemo());
        }
        List<String> forbidden = forbiddenPlaceNamesFor(place);
        List<String> anchors = candidates.stream()
                .filter(value -> !blank(value))
                .map(String::trim)
                .filter(value -> value.length() >= 2 && value.length() <= 12)
                .filter(value -> !containsForbiddenPlaceName(value, forbidden))
                .filter(value -> !containsAny(value, "관리자", "검수", "확인 필요", "공식", "TourAPI", "Kakao"))
                .distinct()
                .limit(3)
                .toList();
        if (!anchors.isEmpty()) {
            return anchors;
        }
        return List.of(
                List.of("유리문", "게시판", "벽면 표식").get(Math.floorMod(index, 3)),
                List.of("창가", "기둥 번호", "층 안내판").get(Math.floorMod(index + 1, 3))
        );
    }

    static void addAnchorWords(List<String> candidates, String text) {
        if (blank(text)) {
            return;
        }
        for (String token : text.replaceAll("[^가-힣A-Za-z0-9\\s]", " ").replaceAll("\\s+", " ").trim().split("\\s+")) {
            if (token.length() >= 2 && token.length() <= 8 && containsAny(token,
                    "문", "창", "벽", "기둥", "계단", "표식", "게시판", "안내판", "입구", "통로", "난간", "바닥", "번호")) {
                candidates.add(token);
            }
        }
    }

    static String storyPlaceAliasFor(List<String> anchors, int index) {
        List<String> source = anchors == null || anchors.isEmpty()
                ? List.of("입구", "벽면 표식")
                : anchors;
        String first = source.get(0);
        String second = source.size() > 1 ? source.get(1) : "통로";
        return switch (Math.floorMod(index, 4)) {
            case 0 -> first + " 옆 좁은 통로";
            case 1 -> second + "이 보이는 입구";
            case 2 -> first + " 아래 그늘진 자리";
            default -> second + " 뒤쪽 모서리";
        };
    }

    static boolean containsForbiddenPlaceName(String text, List<String> forbiddenPlaceNames) {
        if (blank(text) || forbiddenPlaceNames == null || forbiddenPlaceNames.isEmpty()) {
            return false;
        }
        return forbiddenPlaceNames.stream()
                .filter(value -> !blank(value) && compact(value).length() >= 2)
                .anyMatch(value -> containsExactAnswerValue(text, value));
    }

    static boolean containsAnyForbiddenPlaceName(String text, AiEpisodeDraftRequest request) {
        if (blank(text) || request == null || request.getPlaces() == null) {
            return false;
        }
        return request.getPlaces().stream()
                .filter(place -> place != null)
                .anyMatch(place -> containsForbiddenPlaceName(text, forbiddenPlaceNamesFor(place)));
    }


    private static boolean containsExactAnswerValue(String text, String value) {
        if (blank(text) || blank(value)) {
            return false;
        }

        String normalizedText = text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String normalizedValue = value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String compactValue = compact(normalizedValue);

        if (compactValue.length() <= 2) {
            if (same(normalizedText, normalizedValue)) {
                return true;
            }
            for (String token : normalizedText.split("[\\s\\p{Punct}·|/]+")) {
                if (same(token, normalizedValue)) {
                    return true;
                }
            }
            return false;
        }

        return normalizedText.contains(normalizedValue)
                || compact(normalizedText).contains(compactValue);
    }


    private static boolean containsAny(String text, String... targets) {
        if (blank(text)) {
            return false;
        }
        String normalized = compact(text);
        for (String target : targets) {
            if (!blank(target) && normalized.contains(compact(target))) {
                return true;
            }
        }
        return false;
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }}
