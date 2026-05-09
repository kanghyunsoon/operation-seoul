package com.operation.seoul.location.dto;

import com.operation.seoul.location.domain.Mission;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MissionResponse {
    private Long id;
    private String title;
    private Double targetLat;
    private Double targetLng;
    private String visionKeyword;
    private boolean isFinal;
    private boolean isUnlocked;
    private String sessionStatus;
    private String clue;

    public static MissionResponse of(Mission mission, String sessionStatus, boolean isUnlocked) {
        String acquiredClue = null;
        if ("CLEARED".equals(sessionStatus)) {
            acquiredClue = mission.getClue() != null && !mission.getClue().isBlank()
                    ? mission.getClue()
                    : mission.getAnswerKeyword();
        }

        return MissionResponse.builder()
                .id(mission.getId())
                .title(mission.getTitle())
                // 🚨 힌트를 찾기 시작했을 때 거리를 계산해야 하므로 좌표는 가리지 않고 무조건 전송합니다.
                // (프론트엔드에서 마커 자체는 투명하게 가려줍니다)
                .targetLat(mission.getTargetLat())
                .targetLng(mission.getTargetLng())
                .visionKeyword(mission.getVisionKeyword())
                .isFinal(mission.isFinal())
                .isUnlocked(isUnlocked)
                .sessionStatus(sessionStatus)
                .clue(acquiredClue)
                .build();
    }
}
