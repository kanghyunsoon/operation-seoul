package com.operation.seoul.location.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OperationAreaResolver {

    /** 알 수 없는 권역 코드나 좌표는 서울로 보정합니다. */
    public static final String DEFAULT_AREA_CODE = "seoul";

    /** 프론트 권역 카탈로그와 백엔드 저장 값이 어긋나지 않도록 허용 목록을 한곳에서 관리합니다. */
    private static final Set<String> VALID_AREA_CODES = Set.of(
            "seoul",
            "gangwon",
            "chungbuk",
            "chungnam",
            "jeonbuk",
            "jeonnam",
            "gyeongbuk",
            "gyeongnam",
            "jeju"
    );

    /**
     * 위경도가 어느 권역에 속하는지 판정하기 위한 근사 폴리곤입니다.
     * 행정 경계 정밀 데이터가 아니라 서비스 초기 분류용 값이므로, 필요하면 공식 GeoJSON으로 교체해야 합니다.
     */
    private static final Map<String, List<Point>> AREA_POLYGONS = Map.of(
            "seoul", List.of(
                    new Point(126.02, 37.06),
                    new Point(126.16, 37.46),
                    new Point(126.10, 38.12),
                    new Point(126.85, 38.02),
                    new Point(127.70, 38.14),
                    new Point(127.88, 37.46),
                    new Point(127.34, 36.94),
                    new Point(126.55, 36.92)
            ),
            "gangwon", List.of(
                    new Point(127.70, 38.14),
                    new Point(128.54, 38.32),
                    new Point(129.12, 38.23),
                    new Point(129.45, 37.52),
                    new Point(129.28, 36.88),
                    new Point(128.68, 36.58),
                    new Point(127.88, 37.46)
            ),
            "chungbuk", List.of(
                    new Point(126.55, 36.92),
                    new Point(127.34, 36.94),
                    new Point(127.88, 37.46),
                    new Point(128.68, 36.58),
                    new Point(128.36, 35.92),
                    new Point(127.54, 35.76),
                    new Point(126.94, 35.96)
            ),
            "chungnam", List.of(
                    new Point(125.74, 36.32),
                    new Point(126.02, 35.78),
                    new Point(126.28, 35.54),
                    new Point(126.94, 35.96),
                    new Point(126.55, 36.92),
                    new Point(126.18, 36.86)
            ),
            "jeonbuk", List.of(
                    new Point(126.02, 35.78),
                    new Point(126.28, 35.54),
                    new Point(126.94, 35.96),
                    new Point(127.54, 35.76),
                    new Point(127.70, 35.28),
                    new Point(127.14, 35.00),
                    new Point(126.34, 35.12),
                    new Point(126.05, 35.22)
            ),
            "jeonnam", List.of(
                    new Point(125.82, 34.58),
                    new Point(126.55, 34.28),
                    new Point(127.35, 34.43),
                    new Point(127.70, 35.28),
                    new Point(127.14, 35.00),
                    new Point(126.34, 35.12),
                    new Point(126.05, 35.22)
            ),
            "gyeongbuk", List.of(
                    new Point(127.54, 35.76),
                    new Point(128.36, 35.92),
                    new Point(128.68, 36.58),
                    new Point(129.28, 36.88),
                    new Point(129.45, 35.95),
                    new Point(129.25, 35.22),
                    new Point(128.48, 35.06),
                    new Point(127.92, 35.26)
            ),
            "gyeongnam", List.of(
                    new Point(127.14, 35.00),
                    new Point(127.70, 35.28),
                    new Point(127.92, 35.26),
                    new Point(128.48, 35.06),
                    new Point(129.25, 35.22),
                    new Point(128.90, 34.78),
                    new Point(128.16, 34.46),
                    new Point(127.35, 34.43)
            ),
            "jeju", List.of(
                    new Point(126.10, 33.36),
                    new Point(126.32, 33.24),
                    new Point(126.66, 33.24),
                    new Point(126.92, 33.36),
                    new Point(126.82, 33.54),
                    new Point(126.48, 33.60),
                    new Point(126.18, 33.52)
            )
    );

    /** 폴리곤이 겹치는 곳에서 우선 판정할 순서입니다. */
    private static final List<String> AREA_MATCH_ORDER = List.of(
            "seoul",
            "gangwon",
            "chungbuk",
            "chungnam",
            "jeonbuk",
            "jeonnam",
            "gyeongbuk",
            "gyeongnam",
            "jeju"
    );

    /** 좌표가 포함된 권역을 찾고, 어느 권역에도 들어가지 않으면 요청 권역을 정규화해 반환합니다. */
    public String resolveAreaCode(double lat, double lng, String requestedAreaCode) {
        for (String areaCode : AREA_MATCH_ORDER) {
            if (isInsidePolygon(lng, lat, AREA_POLYGONS.get(areaCode))) {
                return areaCode;
            }
        }
        return normalizeAreaCode(requestedAreaCode);
    }

    /** 특정 좌표가 주어진 권역 폴리곤 안에 있는지 확인합니다. */
    public boolean isInsideAreaCode(String areaCode, double lat, double lng) {
        String normalizedAreaCode = normalizeAreaCode(areaCode);
        List<Point> polygon = AREA_POLYGONS.get(normalizedAreaCode);
        return polygon != null && isInsidePolygon(lng, lat, polygon);
    }

    /** null, 공백, 미등록 코드를 모두 기본 권역으로 보정합니다. */
    public String normalizeAreaCode(String areaCode) {
        if (areaCode == null || areaCode.isBlank()) {
            return DEFAULT_AREA_CODE;
        }

        String normalized = areaCode.trim().toLowerCase();
        return VALID_AREA_CODES.contains(normalized) ? normalized : DEFAULT_AREA_CODE;
    }

    /** ray casting 알고리즘으로 점이 다각형 내부에 있는지 계산합니다. */
    private boolean isInsidePolygon(double lng, double lat, List<Point> polygon) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Point current = polygon.get(i);
            Point previous = polygon.get(j);
            boolean intersects = ((current.lat() > lat) != (previous.lat() > lat))
                    && (lng < (previous.lng() - current.lng()) * (lat - current.lat())
                    / (previous.lat() - current.lat()) + current.lng());
            if (intersects) {
                inside = !inside;
            }
        }
        return inside;
    }

    private record Point(double lng, double lat) {
    }
}
