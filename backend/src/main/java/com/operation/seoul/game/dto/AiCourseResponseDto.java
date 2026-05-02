package com.operation.seoul.game.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // 💡 AI가 예상치 못한 필드를 응답해도 무시하고 파싱 (에러 방지)
public class AiCourseResponseDto {

    // 1. Region(홈 뷰 카드) 테이블에 들어갈 데이터
    private String regionName;        // 예: "작전명: 정동길의 그림자"
    private String regionDescription; // 예: "당신은 비밀요원입니다. 이준 열사의 밀서를..."

    // 2. Mission(지도 마커 및 힌트) 테이블에 들어갈 데이터들
    private List<AiMissionDto> missions;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true) // 💡 서브 객체에도 방어 코드 추가
    public static class AiMissionDto {
        private String title;          // 장소 이름 (예: 덕수궁 돌담길)
        private Double lat;            // 위도
        private Double lng;            // 경도
        private String visionKeyword;  // OCR 힌트 (예: "돌담")
        private String answerKeyword;  // 최종 정답 키워드

        @JsonProperty("isFinal")       // 💡 JSON 파서에게 키 이름이 정확히 "isFinal" 임을 강제 인식시킴
        private boolean isFinal;       // 최종 목적지 여부 (true/false)
    }
}