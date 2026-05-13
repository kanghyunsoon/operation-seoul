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
    private Double radiusInMeters;
    private String visionKeyword;
    private String description;
    private String fieldClue;
    private String missionType;
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
                .radiusInMeters(mission.getRadiusInMeters())
                .visionKeyword(mission.getVisionKeyword())
                .description(mission.getDescription())
                .fieldClue(resolveFieldClue(mission, isUnlocked))
                .missionType(mission.isFinal() ? "FINAL" : "HINT")
                .isFinal(mission.isFinal())
                .isUnlocked(isUnlocked)
                .sessionStatus(sessionStatus)
                .clue(acquiredClue)
                .build();
    }

    private static String resolveFieldClue(Mission mission, boolean isUnlocked) {
        if (!mission.isFinal() || !isUnlocked) {
            return null;
        }

        if (mission.getClue() != null && !mission.getClue().isBlank()) {
            return mission.getClue();
        }
        if (mission.getVisionKeyword() != null && !mission.getVisionKeyword().isBlank()) {
            return "현장에서 '" + mission.getVisionKeyword() + "' 단서를 찾아 주변 안내문, 비문, 표식을 함께 대조하세요.";
        }
        return "최종 지점의 안내문, 비문, 표식, 연도, 인명 단서를 둘러보고 사건의 이름을 유추하세요.";
    }
}
