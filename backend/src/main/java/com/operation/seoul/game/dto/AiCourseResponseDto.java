package com.operation.seoul.game.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiCourseResponseDto {
    // 1. Region(홈 뷰 카드) 테이블에 들어갈 데이터
    private String regionName;        // 예: "작전명: 정동길의 그림자"
    private String regionDescription; // 예: "당신은 비밀요원입니다. 이준 열사의 밀서를..."

    // 2. Mission(지도 마커 및 힌트) 테이블에 들어갈 데이터들
    private List<AiMissionDto> missions;

    @Data
    public static class AiMissionDto {
        private String title;          // 장소 이름 (예: 덕수궁 돌담길)
        private Double lat;            // 위도
        private Double lng;            // 경도
        private String visionKeyword;  // OCR 힌트 (예: "돌담")
        private boolean isFinal;       // 최종 목적지 여부 (true/false)
    }
}