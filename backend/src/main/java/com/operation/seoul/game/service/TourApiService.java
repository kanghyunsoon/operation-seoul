// backend/src/main/java/com/operation/seoul/game/service/TourApiService.java

package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
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

    // 백엔드 표준 키(kakao.rest.api.key)를 우선 사용하되, 기존 로컬 설정(VITE_KAKAO_REST_KEY)도 호환합니다.
    @Value("${kakao.rest.api.key:${VITE_KAKAO_REST_KEY:}}")
    private String kakaoRestApiKey;

    @Value("${tmap.app.key:}")
    private String tmapAppKey;

    private final RestTemplate restTemplate = utf8RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static RestTemplate utf8RestTemplate() {
        RestTemplate template = new RestTemplate();
        template.getMessageConverters().removeIf(StringHttpMessageConverter.class::isInstance);
        template.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return template;
    }

    /**
     * [역사적 장소 수집] TourAPI 호출 (ContentType 12: 관광지)
     * 관리자 후보지 스캔에서 최종 목적지 후보를 모으는 첫 단계입니다.
     */
    public List<Map<String, String>> fetchHistoricalPlaces(double lat, double lng, int radius) {
        ensureTourApiKey();
        List<Map<String, String>> spots = new ArrayList<>();
        try {
            String safeKey = tourApiKey.trim();

            StringBuilder urlBuilder = new StringBuilder("https://apis.data.go.kr/B551011/KorService2/locationBasedList2");
            urlBuilder.append("?serviceKey=").append(safeKey);
            urlBuilder.append("&numOfRows=15");
            urlBuilder.append("&pageNo=1");
            urlBuilder.append("&MobileOS=ETC");
            urlBuilder.append("&MobileApp=OperationSeoul");
            urlBuilder.append("&_type=json");
            urlBuilder.append("&mapX=").append(lng);
            urlBuilder.append("&mapY=").append(lat);
            urlBuilder.append("&radius=").append(radius);
            urlBuilder.append("&contentTypeId=12");

            URI uri = new URI(urlBuilder.toString());
            log.info("🚀 [TourAPI 최종 요청 주소]: {}", uri);

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode header = root.path("response").path("header");
            String resultCode = header.path("resultCode").asText("");
            if (!resultCode.isBlank() && !"0000".equals(resultCode)) {
                String resultMessage = header.path("resultMsg").asText("TourAPI 응답 오류");
                throw new ApiException(HttpStatus.BAD_GATEWAY, "TOURAPI_REQUEST_FAILED", "TourAPI 후보 조회에 실패했습니다: " + resultMessage);
            }
            JsonNode items = root.path("response").path("body").path("items").path("item");

            if (items.isArray()) {
                for (JsonNode item : items) {
                    Map<String, String> spot = new HashMap<>();
                    spot.put("title", recoverMojibake(item.path("title").asText()));
                    spot.put("address", recoverMojibake(item.path("addr1").asText()));
                    spot.put("mapX", item.path("mapx").asText());
                    spot.put("mapY", item.path("mapy").asText());
                    spots.add(spot);
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("🚨 TourAPI 오류: {}", e.getMessage());
        }
        return spots;
    }

    private void ensureTourApiKey() {
        if (tourApiKey == null || tourApiKey.isBlank() || tourApiKey.startsWith("YOUR_")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOURAPI_SERVICE_KEY_MISSING", "TourAPI 서비스 키가 설정되어 있지 않습니다. backend application-local.properties 또는 운영 환경변수에 TourAPI 서비스 키를 설정하세요.");
        }
    }

    private String recoverMojibake(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (!(value.contains("ì") || value.contains("ê") || value.contains("ë") || value.contains("í"))) {
            return value;
        }
        try {
            return new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * [주변 장소 수집] 카카오 API 호출
     * 최종 목적지 주변의 카페/공원/시장 등 힌트 미션 후보 POI를 수집합니다.
     */
    public List<Map<String, String>> fetchNearbyLocalPOIs(double lat, double lng, int radius, String keyword) {
        List<Map<String, String>> spots = new ArrayList<>();
        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
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

            if (documents != null && documents.isArray()) {
                for (JsonNode doc : documents) {
                    Map<String, String> spot = new HashMap<>();
                    spot.put("title", doc.path("place_name").asText());
                    spot.put("address", doc.path("road_address_name").asText());
                    spot.put("mapX", doc.path("x").asText());
                    spot.put("mapY", doc.path("y").asText());
                    spot.put("category", doc.path("category_name").asText());
                    spot.put("source", "KakaoAPI");
                    spots.add(spot);
                }
            }
        } catch (Exception e) {
            log.error("🚨 Kakao API 오류: {}", e.getMessage());
        }
        return spots;
    }

    /**
     * Tmap 도보 경로 API로 실제 보행 거리를 확인합니다.
     * 후보지 필터링에서 직선거리는 가깝지만 도보 접근성이 나쁜 장소를 제외하는 데 사용합니다.
     */
    public Double fetchPedestrianDistanceMeters(double startLat, double startLng, double endLat, double endLng) {
        if (tmapAppKey == null || tmapAppKey.isBlank()) {
            return null;
        }

        try {
            String url = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1&format=json";

            Map<String, Object> payload = Map.of(
                    "startX", String.valueOf(startLng),
                    "startY", String.valueOf(startLat),
                    "endX", String.valueOf(endLng),
                    "endY", String.valueOf(endLat),
                    "reqCoordType", "WGS84GEO",
                    "resCoordType", "WGS84GEO",
                    "startName", "target",
                    "endName", "hint"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("appKey", tmapAppKey.trim());

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(payload, headers),
                    String.class
            );

            JsonNode features = objectMapper.readTree(response.getBody()).path("features");
            if (features.isArray()) {
                for (JsonNode feature : features) {
                    JsonNode totalDistance = feature.path("properties").path("totalDistance");
                    if (totalDistance.isNumber() && totalDistance.asDouble() > 0) {
                        return totalDistance.asDouble();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Tmap pedestrian distance check failed: {}", e.getMessage());
        }
        return null;
    }
}
