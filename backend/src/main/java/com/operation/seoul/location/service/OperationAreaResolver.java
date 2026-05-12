package com.operation.seoul.location.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OperationAreaResolver {

    public static final String DEFAULT_AREA_CODE = "seoul";

    private static final Set<String> VALID_AREA_CODES = Set.of(
            "seoul",
            "gangwon",
            "chungbuk",
            "chungnam",
            "jeonbuk",
            "jeonnam",
            "gyeongbuk",
            "gyeongnam"
    );

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
            )
    );

    private static final List<String> AREA_MATCH_ORDER = List.of(
            "seoul",
            "gangwon",
            "chungbuk",
            "chungnam",
            "jeonbuk",
            "jeonnam",
            "gyeongbuk",
            "gyeongnam"
    );

    public String resolveAreaCode(double lat, double lng, String requestedAreaCode) {
        for (String areaCode : AREA_MATCH_ORDER) {
            if (isInsidePolygon(lng, lat, AREA_POLYGONS.get(areaCode))) {
                return areaCode;
            }
        }
        return normalizeAreaCode(requestedAreaCode);
    }

    public String normalizeAreaCode(String areaCode) {
        if (areaCode == null || areaCode.isBlank()) {
            return DEFAULT_AREA_CODE;
        }

        String normalized = areaCode.trim().toLowerCase();
        return VALID_AREA_CODES.contains(normalized) ? normalized : DEFAULT_AREA_CODE;
    }

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
