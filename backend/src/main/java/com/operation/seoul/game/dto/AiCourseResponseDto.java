package com.operation.seoul.game.dto;

import lombok.Data;
import java.util.List;

/**
 * [DTO: AI 작전 코스 응답 데이터 규격]
 * - 역할: Gemini AI가 TourAPI 데이터를 바탕으로 동적 생성한 작전 시나리오와 미션 목록을 매핑하는 객체입니다.
 * - AdminMissionController에서 AI의 JSON 응답을 이 클래스의 객체로 변환하여 DB에 저장합니다.
 */
@Data
public class AiCourseResponseDto {

    // ==========================================
    // 1. Region(홈 뷰 작전 카드) 테이블에 들어갈 데이터
    // ==========================================

    // 작전명 (예: "작전명 정동길의 그림자"). Region.name으로 저장됩니다.
    private String regionName;

    // 작전 배경 스토리 및 설명. Home 카드와 Briefing 화면의 기본 서사로 사용됩니다.
    private String regionDescription;

    // ==========================================
    // 2. Mission(지도 마커 및 수집할 단서) 테이블에 들어갈 데이터
    // ==========================================

    // 해당 작전 구역 내에 생성된 하위 미션(경유지) 리스트
    private List<AiMissionDto> missions;

    @Data
    public static class AiMissionDto {

        // 장소 이름 (예: "덕수궁 돌담길")
        private String title;

        // 목표 지점 위도 (GPS 좌표)
        private Double lat;

        // 목표 지점 경도 (GPS 좌표)
        private Double lng;

        // 현장 요원이 카메라로 스캔해야 할 사물/텍스트 키워드 (예: "붉은색 문")
        private String visionKeyword;

        // 🚨 [일반 미션용 추가 필드] 요원이 사진 인증을 통과했을 때 획득하는 단서 내용
        // (예: "첫 번째 금고 비밀번호는 7입니다.")
        private String clue;

        // 🚨 [최종 미션용 복구 필드] 채팅방에서 요원이 최종적으로 대답해야 할 진짜 정답
        // (예: "742" 또는 "독립선언서")
        private String answerKeyword;

        // 최종 목적지 여부 플래그. true일 경우 힌트 3개를 모으기 전까지 지도에서 숨겨집니다.
        private boolean isFinal;
    }
}
