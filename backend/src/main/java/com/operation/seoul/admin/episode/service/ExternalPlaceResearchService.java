package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ExternalPlaceResearchService {
    private static final int MAX_RESULTS = 3;
    private static final int MAX_NOTE_LENGTH = 420;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${external.research.wikipedia.enabled:true}")
    private boolean wikipediaEnabled;

    @Value("${external.research.wikipedia.endpoint:https://ko.wikipedia.org/w/api.php}")
    private String wikipediaEndpoint;

    @Autowired
    public ExternalPlaceResearchService(ObjectMapper objectMapper) {
        this(objectMapper, utf8RestTemplate());
    }

    ExternalPlaceResearchService(ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public ResearchResult research(AiEpisodeDraftRequest.PlaceInput place) {
        String query = buildQuery(place);
        if (!wikipediaEnabled || query.isBlank()) {
            return ResearchResult.empty();
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(wikipediaEndpoint)
                    .queryParam("action", "query")
                    .queryParam("format", "json")
                    .queryParam("generator", "search")
                    .queryParam("gsrsearch", query)
                    .queryParam("gsrlimit", MAX_RESULTS)
                    .queryParam("prop", "extracts|info")
                    .queryParam("exintro", "true")
                    .queryParam("explaintext", "true")
                    .queryParam("inprop", "url")
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "OperationSeoul/1.0 (admin episode research; contact: local)");
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            ResearchResult parsed = parseWikipediaResponse(response.getBody(), place.getName());
            if (parsed.isEmpty()) {
                return ResearchResult.empty();
            }
            return parsed.withSummary("Wikipedia reference search for '" + query + "' returned "
                    + parsed.notes().size() + " usable page(s).");
        } catch (RestClientException e) {
            log.warn("External place research request failed for query '{}': {}", query, e.getMessage());
            return ResearchResult.empty();
        } catch (Exception e) {
            log.warn("External place research parse failed for query '{}': {}", query, e.getMessage());
            return ResearchResult.empty();
        }
    }

    ResearchResult parseWikipediaResponse(String json) throws Exception {
        return parseWikipediaResponse(json, "");
    }

    ResearchResult parseWikipediaResponse(String json, String preferredTitle) throws Exception {
        if (json == null || json.isBlank()) {
            return ResearchResult.empty();
        }
        JsonNode pages = objectMapper.readTree(json).path("query").path("pages");
        if (!pages.isObject()) {
            return ResearchResult.empty();
        }
        List<WikipediaPage> candidates = new ArrayList<>();
        pages.elements().forEachRemaining(page -> {
            String title = clean(page.path("title").asText(""));
            String extract = clean(page.path("extract").asText(""));
            String url = clean(page.path("fullurl").asText(""));
            if (title.isBlank() && extract.isBlank()) {
                return;
            }
            candidates.add(new WikipediaPage(title, extract, url, wikipediaScore(title, extract, preferredTitle)));
        });
        List<String> notes = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        boolean requiresDirectMatch = !compact(preferredTitle).isBlank();
        List<WikipediaPage> usableCandidates = candidates.stream()
                .filter(page -> !requiresDirectMatch || page.score() > 0)
                .toList();
        if (usableCandidates.isEmpty()) {
            return ResearchResult.empty();
        }
        usableCandidates.stream()
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .limit(MAX_RESULTS)
                .forEach(page -> {
                    notes.add("Reference: " + page.title() + " - " + truncate(page.extract(), MAX_NOTE_LENGTH));
                    if (!page.url().isBlank()) {
                        urls.add(page.url());
                    }
                });
        if (notes.isEmpty()) {
            return ResearchResult.empty();
        }
        return new ResearchResult(notes, urls, "Wikipedia reference pages parsed: " + notes.size());
    }

    private int wikipediaScore(String title, String extract, String preferredTitle) {
        String compactTitle = compact(title);
        String compactPreferred = compact(preferredTitle);
        if (compactPreferred.isBlank()) {
            return 0;
        }
        int score = 0;
        if (compactTitle.equals(compactPreferred)) {
            score += 120;
        } else if (compactTitle.contains(compactPreferred) || compactPreferred.contains(compactTitle)) {
            score += 90;
        }
        if (compact(extract).contains(compactPreferred)) {
            score += 30;
        }
        return score;
    }

    private String buildQuery(AiEpisodeDraftRequest.PlaceInput place) {
        if (place == null || place.getName() == null || place.getName().isBlank()) {
            return "";
        }
        String address = clean(place.getAddress());
        if (address.isBlank()) {
            return clean(place.getName());
        }
        String[] parts = address.split("\\s+");
        String area = parts.length >= 2 ? parts[0] + " " + parts[1] : address;
        return clean(place.getName() + " " + area);
    }

    private static RestTemplate utf8RestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(8_000);
        RestTemplate template = new RestTemplate(requestFactory);
        template.getMessageConverters().removeIf(StringHttpMessageConverter.class::isInstance);
        template.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return template;
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String compact(String value) {
        return clean(value).replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength).trim() + "...";
    }

    public record ResearchResult(List<String> notes, List<String> referenceUrls, String summary) {
        public static ResearchResult empty() {
            return new ResearchResult(List.of(), List.of(), null);
        }

        boolean isEmpty() {
            return (notes == null || notes.isEmpty()) && (referenceUrls == null || referenceUrls.isEmpty());
        }

        ResearchResult withSummary(String newSummary) {
            return new ResearchResult(notes == null ? List.of() : notes,
                    referenceUrls == null ? List.of() : referenceUrls,
                    newSummary);
        }
    }

    private record WikipediaPage(String title, String extract, String url, int score) {
    }
}
