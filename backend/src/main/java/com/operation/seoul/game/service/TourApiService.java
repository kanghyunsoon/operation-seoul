package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
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

    private final RestTemplate restTemplate = utf8RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static RestTemplate utf8RestTemplate() {
        RestTemplate template = new RestTemplate();
        template.getMessageConverters().removeIf(StringHttpMessageConverter.class::isInstance);
        template.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return template;
    }

    /**
     * Collect historical/cultural POIs from TourAPI near the selected coordinates.
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
            log.info("TourAPI lookup lat={} lng={} radius={}", lat, lng, radius);

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            if (response.getBody() == null || response.getBody().isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "TOURAPI_EMPTY_RESPONSE",
                        "TourAPI가 빈 응답을 반환했습니다."
                );
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode header = root.path("response").path("header");
            String resultCode = header.path("resultCode").asText("");
            if (!resultCode.isBlank() && !"0000".equals(resultCode)) {
                String resultMessage = header.path("resultMsg").asText("TourAPI response error");
                throw new ApiException(HttpStatus.BAD_GATEWAY, "TOURAPI_REQUEST_FAILED", "TourAPI place lookup failed: " + resultMessage);
            }
            JsonNode items = root.path("response").path("body").path("items").path("item");

            if (items.isArray()) {
                for (JsonNode item : items) {
                    addTourApiSpot(spots, item);
                }
            } else if (items.isObject()) {
                addTourApiSpot(spots, items);
            }

            int totalCount = root.path("response").path("body").path("totalCount").asInt(0);
            if (totalCount > 0 && spots.isEmpty()) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "TOURAPI_RESPONSE_PARSE_FAILED",
                        "TourAPI 응답에 장소가 있지만 후보 데이터를 해석하지 못했습니다."
                );
            }
            log.info("TourAPI lookup completed totalCount={} parsedCount={}", totalCount, spots.size());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // 요청 URL에 serviceKey가 들어 있어 예외 메시지를 그대로 찍으면 키가 로그에 남는다.
            log.error("TourAPI lookup failed: {}", e.getClass().getSimpleName());
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "TOURAPI_REQUEST_FAILED",
                    "TourAPI 장소 조회에 실패했습니다."
            );
        }
        return spots;
    }

    private void addTourApiSpot(List<Map<String, String>> spots, JsonNode item) {
        String title = recoverMojibake(item.path("title").asText());
        String mapX = item.path("mapx").asText();
        String mapY = item.path("mapy").asText();
        if (title == null || title.isBlank() || mapX.isBlank() || mapY.isBlank()) {
            return;
        }

        Map<String, String> spot = new HashMap<>();
        spot.put("title", title);
        spot.put("address", recoverMojibake(item.path("addr1").asText()));
        spot.put("mapX", mapX);
        spot.put("mapY", mapY);
        spot.put("source", "TourAPI");
        spots.add(spot);
    }

    private void ensureTourApiKey() {
        if (tourApiKey == null || tourApiKey.isBlank() || tourApiKey.startsWith("YOUR_")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOURAPI_SERVICE_KEY_MISSING",
                    "TourAPI service key is not configured. Set tourapi.key in backend application-local.properties or the runtime environment."
            );
        }
    }

    private String recoverMojibake(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (!(value.contains("챙") || value.contains("챗") || value.contains("챘") || value.contains("챠"))) {
            return value;
        }
        try {
            return new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
