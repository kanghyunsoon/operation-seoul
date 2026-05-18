package com.operation.seoul.location.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegionCardResponse {
    /** Region id. 브리핑/삭제/미션 목록 조회의 기준값입니다. */
    private Long id;
    /** 홈 화면에서 선택한 전국 권역 코드입니다. */
    private String areaCode;
    /** 작전 카드 제목입니다. */
    private String name;
    /** 브리핑 진입 전 카드에 표시하는 작전 배경 설명입니다. */
    private String description;
    private String periodCode;
    private String themeCode;
    private String createdAt;
    private double averageRating;
    private int reviewCount;
    private int likeCount;
    private boolean liked;
    private int favoriteCount;
    private boolean favorited;
    /** 클리어 기록 화면으로 이동할 때 사용할 최종 Mission id입니다. */
    private Long finalMissionId;
    /** 현재 사용자가 이 작전의 최종 미션을 클리어했는지 여부입니다. */
    private boolean cleared;
    /** 클리어 카드에 표시할 최종 키워드입니다. */
    private String answerKeyword;
    /** 클리어 시 계산된 점수입니다. */
    private Integer score;
    /** 작전 시작부터 클리어까지 걸린 시간입니다. */
    private Long elapsedSeconds;
    /** 프론트가 GPS 이동 기록으로 계산해 전달한 누적 이동 거리입니다. */
    private Double routeDistanceMeters;
}
