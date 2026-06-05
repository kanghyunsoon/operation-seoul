package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AdminPlaceCandidateResponse;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KakaoLocalCandidateService {
    private static final List<String> CATEGORY_CODES = List.of("FD6", "CE7", "CT1", "AT4");
    private static final int MAX_RADIUS = 20_000;
    private static final int DEFAULT_RADIUS = 1_500;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${kakao.rest.api.key:}")
    private String kakaoRestApiKey;

    public List<AdminPlaceCandidateResponse> getNearbyCandidates(double latitude, double longitude, Integer radius) {
        ensureApiKey();
        int safeRadius = radius == null ? DEFAULT_RADIUS : Math.max(100, Math.min(radius, MAX_RADIUS));
        Map<String, AdminPlaceCandidateResponse> unique = new LinkedHashMap<>();
        for (String categoryCode : CATEGORY_CODES) {
            fetchCategory(latitude, longitude, safeRadius, categoryCode).forEach(candidate -> {
                String key = (candidate.getTitle() + "|" + candidate.getAddress() + "|" + candidate.getLatitude() + "|" + candidate.getLongitude()).toLowerCase();
                unique.putIfAbsent(key, candidate);
            });
        }
        return unique.values().stream().limit(60).toList();
    }

    private List<AdminPlaceCandidateResponse> fetchCategory(double latitude, double longitude, int radius, String categoryCode) {
        String url = UriComponentsBuilder.fromUriString("https://dapi.kakao.com/v2/local/search/category.json")
                .queryParam("category_group_code", categoryCode)
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .queryParam("radius", radius)
                .queryParam("sort", "distance")
                .queryParam("size", 15)
                .build()
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoRestApiKey);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode documents = objectMapper.readTree(response.getBody()).path("documents");
            List<AdminPlaceCandidateResponse> result = new ArrayList<>();
            if (documents.isArray()) {
                for (JsonNode node : documents) {
                    Double lat = parseDouble(node.path("y").asText());
                    Double lng = parseDouble(node.path("x").asText());
                    if (lat == null || lng == null) continue;
                    result.add(AdminPlaceCandidateResponse.builder()
                            .title(node.path("place_name").asText("이름 없는 후보지"))
                            .address(firstNonBlank(node.path("road_address_name").asText(), node.path("address_name").asText()))
                            .latitude(lat)
                            .longitude(lng)
                            .areaCode("nearby")
                            .source("KakaoLocal:" + categoryCode)
                            .description("Kakao Local 기준 주변 상권/문화 후보지입니다. 실제 운영 전 현장 관찰 요소를 관리자 메모로 보강하세요.")
                            .contentId(node.path("id").asText(null))
                            .build());
                }
            }
            return result;
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "KAKAO_LOCAL_REQUEST_FAILED", "Kakao Local 후보지 조회에 실패했습니다: " + e.getMessage());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "KAKAO_LOCAL_PARSE_FAILED", "Kakao Local 응답을 해석할 수 없습니다.");
        }
    }

    private void ensureApiKey() {
        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank() || kakaoRestApiKey.startsWith("YOUR_")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "KAKAO_REST_API_KEY_MISSING", "kakao.rest.api.key가 설정되어 있지 않습니다. backend application-local.properties 또는 운영 환경변수에 Kakao REST API 키를 설정하세요.");
        }
    }

    private Double parseDouble(String value) {
        try {
            return value == null || value.isBlank() ? null : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
