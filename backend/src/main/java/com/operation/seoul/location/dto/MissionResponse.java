package com.operation.seoul.location.dto;

import com.operation.seoul.location.domain.Mission;
import lombok.Builder;
import lombok.Getter;

/**
 * [DTO: 클라이언트 전달용 미션/힌트 데이터 규격]
 * 보안 및 스포일러 방지를 위해, 해금되지 않은 최종 목적지의 좌표는 숨겨서 프론트엔드로 전달합니다.
 */
@Getter
@Builder
public class MissionResponse {
    private Long id;
    private String title;
    private Double targetLat;     // 위도 (미해금 시 null)
    private Double targetLng;     // 경도 (미해금 시 null)
    private String visionKeyword; // 사진 인증용 키워드
    private boolean isFinal;      // 최종 목적지 여부 (true/false)
    private boolean isUnlocked;   // 유저의 해금 상태 (true면 지도에 노출)
    private String sessionStatus; // 현재 유저의 미션 진행 상태 (null, "ARRIVED", "CLEARED" 등)

    /**
     * 엔티티를 DTO로 변환하는 정적 팩토리 메서드
     * @param mission DB에서 조회한 원본 미션 엔티티
     * @param sessionStatus 유저의 해당 미션 진행 상태
     * @param isUnlocked 최종 목적지 해금 여부
     */
    public static MissionResponse of(Mission mission, String sessionStatus, boolean isUnlocked) {
        // 최종 미션인데 아직 해금되지 않았다면 좌표를 null로 처리하여 꼼수 방지 (보안)
        boolean shouldHideLocation = mission.isFinal() && !isUnlocked;

        return MissionResponse.builder()
                .id(mission.getId())
                .title(mission.getTitle())
                .targetLat(shouldHideLocation ? null : mission.getTargetLat())
                .targetLng(shouldHideLocation ? null : mission.getTargetLng())
                .visionKeyword(mission.getVisionKeyword())
                .isFinal(mission.isFinal())
                .isUnlocked(isUnlocked)
                .sessionStatus(sessionStatus)
                .build();
    }
}