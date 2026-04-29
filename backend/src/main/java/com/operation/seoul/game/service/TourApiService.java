package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [Service: 한국관광공사 TourAPI 연동]
 * - 역할: 특정 좌표(위도, 경도)를 기준으로 주변의 관광지(명소) 정보를 수집합니다.
 * - 수집된 데이터는 이후 Gemini AI에게 전달되어 스파이 미션 스토리를 생성하는 기초 데이터가 됩니다.
 */
@Service
public class TourApiService {

    // application.properties에 세팅한 인코딩 키 주입
    @Value("${tourapi.key}")
    private String tourApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TourApiService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 특정 좌표 기준 반경(radius) 내의 관광지 목록을 가져옵니다.
     *
     * @param mapX   경도 (Longitude)
     * @param mapY   위도 (Latitude)
     * @param radius 검색 반경 (미터 단위, 예: 2000)
     * @return 관광지 명칭, 주소, 좌표가 담긴 맵의 리스트
     */
    public List<Map<String, String>> getNearbyTouristSpots(double mapX, double mapY, int radius) {
        List<Map<String, String>> spots = new ArrayList<>();

        try {
            // 1. 키의 앞뒤 공백을 제거합니다 (f4d333... 확인 완료!)
            String safeKey = tourApiKey.trim();

            // 2. URL을 통째로 조립합니다. (V2 / List2 적용)
            // 💡 주의: 키에 특수문자가 없으므로 인코딩 과정 없이 바로 꽂아 넣습니다.
            StringBuilder urlBuilder = new StringBuilder("https://apis.data.go.kr/B551011/KorService2/locationBasedList2");
            urlBuilder.append("?serviceKey=").append(safeKey);
            urlBuilder.append("&numOfRows=5");
            urlBuilder.append("&pageNo=1");
            urlBuilder.append("&MobileOS=ETC");
            urlBuilder.append("&MobileApp=OperationSeoul");
            urlBuilder.append("&_type=json");
            urlBuilder.append("&mapX=").append(mapX);
            urlBuilder.append("&mapY=").append(mapY);
            urlBuilder.append("&radius=").append(radius);
            urlBuilder.append("&contentTypeId=12");

            // 3. String을 URI 객체로 변환 (중복 인코딩 방지)
            URI uri = new URI(urlBuilder.toString());

            System.out.println("🚀 [최종 요청 주소]: " + uri);

            // 4. 통신 실행
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

            // JSON 파싱 (이후 로직은 동일)
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode items = root.path("response").path("body").path("items").path("item");

            if (items.isArray()) {
                for (JsonNode item : items) {
                    Map<String, String> spot = new HashMap<>();
                    spot.put("title", item.path("title").asText());
                    spot.put("address", item.path("addr1").asText());
                    spot.put("mapX", item.path("mapx").asText());
                    spot.put("mapY", item.path("mapy").asText());
                    spots.add(spot);
                }
            }
        } catch (Exception e) {
            // 401 에러가 난다면 주소를 복사해서 브라우저에 직접 붙여넣어 볼 수 있도록 로그를 남깁니다.
            System.err.println("🚨 TourAPI 오류: " + e.getMessage());
        }

        return spots;
    }
}