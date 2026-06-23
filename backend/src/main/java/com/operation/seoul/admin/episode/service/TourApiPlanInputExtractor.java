package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

final class TourApiPlanInputExtractor {
    private TourApiPlanInputExtractor() {
    }

    static TourApiPlanContext extract(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null) return TourApiPlanContext.empty();
        return new TourApiPlanContext(
                storyAnchors(request),
                includedInputs(request),
                excludedInputs(request),
                historicalContext(request),
                answerSeedContext(request)
        );
    }

    private static List<String> storyAnchors(AiEpisodeDraftRequest request) {
        LinkedHashSet<String> anchors = new LinkedHashSet<>();
        for (AiEpisodeDraftRequest.PlaceInput place : request.getPlaces()) {
            if (place == null) continue;
            Stream.of(place.getResearchSourceSummary(), place.getDescription())
                    .map(TourApiPlanInputExtractor::cleanStoryAnchor)
                    .filter(value -> !blank(value))
                    .forEach(anchors::add);
            Stream.of(place.getExternalResearchNotes(), place.getKeywords())
                    .flatMap(values -> safeList(values).stream())
                    .map(TourApiPlanInputExtractor::cleanStoryAnchor)
                    .filter(value -> !blank(value))
                    .forEach(anchors::add);
            if (anchors.size() >= 3) break;
        }
        return anchors.stream().limit(3).toList();
    }

    private static List<String> includedInputs(AiEpisodeDraftRequest request) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < request.getPlaces().size(); i++) {
            AiEpisodeDraftRequest.PlaceInput place = request.getPlaces().get(i);
            if (place == null) continue;
            int placeIndex = i;
            appendIncluded(result, placeIndex, "researchSourceSummary", place.getResearchSourceSummary());
            appendIncluded(result, placeIndex, "description", place.getDescription());
            safeList(place.getExternalResearchNotes()).forEach(value -> appendIncluded(result, placeIndex, "externalResearchNotes", value));
            safeList(place.getKeywords()).forEach(value -> appendIncluded(result, placeIndex, "keywords", value));
        }
        return result.stream().limit(20).toList();
    }

    private static List<String> excludedInputs(AiEpisodeDraftRequest request) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < request.getPlaces().size(); i++) {
            AiEpisodeDraftRequest.PlaceInput place = request.getPlaces().get(i);
            if (place == null) continue;
            int placeIndex = i;
            appendExcluded(result, placeIndex, "adminMemo", place.getAdminMemo(), "admin memo is site-review only");
            safeList(place.getVerificationNotes()).forEach(value -> appendExcluded(result, placeIndex, "verificationNotes", value, "verification note is site-review only"));
            safeList(place.getSiteVerificationSignals()).forEach(value -> appendExcluded(result, placeIndex, "siteVerificationSignals", value, "site verification signal is not TourAPI history"));
            appendExcluded(result, placeIndex, "researchSourceSummary", place.getResearchSourceSummary(), "filtered as Kakao/site noise");
            appendExcluded(result, placeIndex, "description", place.getDescription(), "filtered as Kakao/site noise");
            safeList(place.getExternalResearchNotes()).forEach(value -> appendExcluded(result, placeIndex, "externalResearchNotes", value, "filtered as Kakao/site noise"));
            safeList(place.getKeywords()).forEach(value -> appendExcluded(result, placeIndex, "keywords", value, "filtered as Kakao/site noise"));
        }
        return result.stream().limit(30).toList();
    }

    private static String historicalContext(AiEpisodeDraftRequest request) {
        return request.getPlaces().stream()
                .filter(Objects::nonNull)
                .map(TourApiPlanInputExtractor::historicalContextForPlace)
                .filter(value -> !blank(value))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static String answerSeedContext(AiEpisodeDraftRequest request) {
        return request.getPlaces().stream()
                .filter(Objects::nonNull)
                .map(TourApiPlanInputExtractor::answerSeedContextForPlace)
                .filter(value -> !blank(value))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static String historicalContextForPlace(AiEpisodeDraftRequest.PlaceInput place) {
        List<String> fragments = new ArrayList<>();
        Stream.of(place.getDescription(), place.getResearchSourceSummary())
                .map(TourApiPlanInputExtractor::cleanTourApiPlanResearchText)
                .filter(value -> !blank(value))
                .forEach(fragments::add);
        Stream.of(place.getKeywords(), place.getExternalResearchNotes())
                .flatMap(values -> safeList(values).stream())
                .map(TourApiPlanInputExtractor::cleanTourApiPlanResearchText)
                .filter(value -> !blank(value))
                .forEach(fragments::add);
        return safePromptText(String.join(" ", fragments));
    }

    private static String answerSeedContextForPlace(AiEpisodeDraftRequest.PlaceInput place) {
        List<String> fragments = new ArrayList<>();
        Stream.of(place.getResearchSourceSummary())
                .map(TourApiPlanInputExtractor::cleanTourApiPlanResearchText)
                .filter(value -> !blank(value))
                .forEach(fragments::add);
        Stream.of(place.getKeywords(), place.getExternalResearchNotes())
                .flatMap(values -> safeList(values).stream())
                .map(TourApiPlanInputExtractor::cleanTourApiPlanResearchText)
                .filter(value -> !blank(value))
                .forEach(fragments::add);
        return safePromptText(String.join(" ", fragments));
    }

    private static void appendIncluded(List<String> result, int placeIndex, String source, String value) {
        String cleaned = cleanTourApiPlanResearchText(value);
        if (!blank(cleaned)) {
            result.add("place " + (placeIndex + 1) + " " + source + ": " + cleaned);
        }
    }

    private static void appendExcluded(List<String> result, int placeIndex, String source, String value, String reason) {
        String raw = safePromptText(value);
        if (blank(raw)) return;
        if (source.equals("adminMemo") || source.equals("verificationNotes") || source.equals("siteVerificationSignals") || blank(cleanTourApiPlanResearchText(raw))) {
            result.add("place " + (placeIndex + 1) + " " + source + " excluded (" + reason + "): " + abbreviateForLog(raw, 160));
        }
    }

    private static String cleanStoryAnchor(String value) {
        String text = cleanTourApiPlanResearchText(value);
        if (blank(text)) return "";
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 120 ? text.substring(0, 120).trim() : text;
    }

    private static String cleanTourApiPlanResearchText(String value) {
        String text = safePromptText(value);
        if (blank(text)) return "";
        text = stripKakaoSiteSignalSuffix(text);
        text = text.replaceAll("\\s+", " ").trim();
        if (blank(text) || isKakaoOrSiteVerificationNoise(text)) return "";
        return text;
    }

    private static String stripKakaoSiteSignalSuffix(String value) {
        String text = trim(value);
        for (String marker : List.of(
                "주변 확인 후보:",
                "주요 확인 후보:",
                "nearby=",
                "Nearby:",
                "Kakao Local",
                "RAG/사이트 보강",
                "RAG/site enrichment"
        )) {
            int index = text.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
            if (index > 0) {
                text = text.substring(0, index).trim();
            }
        }
        return text;
    }

    private static boolean isKakaoOrSiteVerificationNoise(String value) {
        String compacted = compact(value);
        return containsAny(compacted,
                "kakaolocal",
                "ragsite",
                "rag/사이트",
                "사이트보강",
                "현장확인",
                "관리자확인",
                "검수",
                "간판",
                "입구",
                "영업시간",
                "주변후보",
                "확인후보",
                "현장단서",
                "동선흔적",
                "카페쉼터",
                "식당상권",
                "문화전시",
                "관광명소",
                "주요확인후보",
                "주변확인후보",
                "주변장소",
                "주변상점",
                "주변카페",
                "주변식당",
                "근처후보",
                "실제현장",
                "운영공개전",
                "동선확인",
                "좌표",
                "위도",
                "경도",
                "selectedplacecontext",
                "external-search-failed",
                "rag_error",
                "verification",
                "siteverification",
                "nearbyfamousplacesignal"
        );
    }

    private static String safePromptText(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 700) {
            return normalized.substring(0, 700);
        }
        return normalized;
    }

    private static String abbreviateForLog(String value, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) {
            if (!blank(target) && text.contains(target)) return true;
        }
        return false;
    }
}
