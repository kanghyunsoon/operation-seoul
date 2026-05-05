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
    private String answer; // 🚨 [추가] 요원에게 노출될 실제 단서 (answerKeyword)
    private boolean isFinal;
    private boolean isUnlocked;
    private String sessionStatus;

    public static MissionResponse of(Mission mission, String sessionStatus, boolean isUnlocked) {
        boolean shouldHideLocation = mission.isFinal() && !isUnlocked;
        return MissionResponse.builder()
                .id(mission.getId())
                .title(mission.getTitle())
                .targetLat(shouldHideLocation ? null : mission.getTargetLat())
                .targetLng(shouldHideLocation ? null : mission.getTargetLng())
                .visionKeyword(mission.getVisionKeyword())
                .answer(mission.getAnswerKeyword()) // 🚨 [수정] DB의 정답 키워드를 힌트로 맵핑
                .isFinal(mission.isFinal())
                .isUnlocked(isUnlocked)
                .sessionStatus(sessionStatus)
                .build();
    }
}