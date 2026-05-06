package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiService {

    @Value("${tourapi.key}")
    private String tourApiKey;

    @Value("${kakao.rest.api.key}")
    private String kakaoRestApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * [역사적 장소 수집] TourAPI 호출 (ContentType 12: 관광지)
     * 역할: 최종 목적지가 될 스토리가 있는 장소를 가져옵니다.
     */
    public List<Map<String, String>> fetchHistoricalPlaces(double lat, double lng, int radius) {
        List<Map<String, String>> spots = new ArrayList<>();
        try {
            // TourAPI URL 구성 (contentTypeId=12 관광지 고정)
            String urlString = String.format(
                    "https://apis.data.go.kr/B551011/KorService1/locationBasedList1?serviceKey=%s&numOfRows=20&MobileOS=ETC&MobileApp=OperationSeoul&_type=json&mapX=%f&mapY=%f&radius=%d&contentTypeId=12",
                    tourApiKey, lng, lat, radius
            );

            URI uri = new URI(urlString);
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode items = root.path("response").path("body").path("items").path("item");

            if (items.isArray()) {
                for (JsonNode item : items) {
                    Map<String, String> spot = new HashMap<>();
                    spot.put("title", item.path("title").asText());
                    spot.put("address", item.path("addr1").asText());
                    spot.put("mapX", item.path("mapx").asText());
                    spot.put("mapY", item.path("mapy").asText());
                    spot.put("source", "TourAPI"); // 출처 명시
                    spots.add(spot);
                }
            }
        } catch (Exception e) {
            log.error("🚨 TourAPI 오류: {}", e.getMessage());
        }
        return spots;
    }

    /**
     * [골목 상권/사물 수집] Kakao Local API 호출
     * 역할: 역사적 목적지 주변에 있는 카페, 서점 등 다채로운 경유지 후보를 가져옵니다.
     */
    public List<Map<String, String>> fetchNearbyLocalPOIs(double lat, double lng, int radius, String keyword) {
        List<Map<String, String>> spots = new ArrayList<>();
        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString());

            String urlString = String.format(
                    "https://dapi.kakao.com/v2/local/search/keyword.json?query=%s&y=%f&x=%f&radius=%d&sort=distance",
                    encodedKeyword, lat, lng, radius
            );
            URI uri = new URI(urlString);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoRestApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode documents = root.path("documents");

            if (documents.isArray()) {
                for (JsonNode doc : documents) {
                    Map<String, String> spot = new HashMap<>();
                    spot.put("title", doc.path("place_name").asText());
                    spot.put("address", doc.path("road_address_name").asText());
                    spot.put("mapX", doc.path("x").asText());
                    spot.put("mapY", doc.path("y").asText());
                    spot.put("category", doc.path("category_name").asText()); // AI가 참고할 카테고리 정보
                    spot.put("source", "KakaoAPI"); // 출처 명시
                    spots.add(spot);
                }
            }

        } catch (Exception e) {
            log.error("🚨 카카오 로컬 API 오류: {}", e.getMessage());
        }
        return spots;
    }
}