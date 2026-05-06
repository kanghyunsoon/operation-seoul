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
    private String clue; // 💡 획득한 단서 필드 추가

    public static MissionResponse of(Mission mission, String sessionStatus, boolean isUnlocked) {
        boolean shouldHideLocation = mission.isFinal() && !isUnlocked;

        // 💡 핵심: 미션이 클리어(CLEARED)된 경우에만 DB의 answerKeyword(저장된 단서)를 송출
        String acquiredClue = "CLEARED".equals(sessionStatus) ? mission.getAnswerKeyword() : null;

        return MissionResponse.builder()
                .id(mission.getId())
                .title(mission.getTitle())
                .targetLat(shouldHideLocation ? null : mission.getTargetLat())
                .targetLng(shouldHideLocation ? null : mission.getTargetLng())
                .visionKeyword(mission.getVisionKeyword())
                .isFinal(mission.isFinal())
                .isUnlocked(isUnlocked)
                .sessionStatus(sessionStatus)
                .clue(acquiredClue)
                .build();
    }
}