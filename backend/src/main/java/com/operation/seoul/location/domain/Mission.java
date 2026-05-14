package com.operation.seoul.location.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * [Domain: 미션 정보 관리 모델]
 * 용도: 현장 방문 미션의 위치 정보, 인증 키워드, 상태값 정의
 * 특징: MyBatis 매퍼를 통해 데이터베이스 `mission` 테이블과 매핑됩니다.
 */
@Getter @Setter
public class Mission {

    /** 미션 고유 식별 번호입니다. MySQL AUTO_INCREMENT 기본키와 매핑됩니다. */
    private Long id;

    /**소속 지역 식별자
     특정 구역(Region)별 미션 필터링 및 그룹화를 위한 외래키 역할 */
    private Long regionId;

    /**미션 제목
     사용자 화면 및 안내 가이드에 표시될 공식 명칭  */
    private String title;

    /** 지도 카드와 브리핑에서 쓰는 미션별 짧은 서사 또는 현장 설명입니다. */
    private String description;

    /**목표 지점 위도
     EPSG:4326(WGS84) 표준 좌표계 기준의 위도 값 */
    private Double targetLat;

    /**목표 지점 경도
     EPSG:4326(WGS84) 표준 좌표계 기준의 경도 값 */
    private Double targetLng;

    /** 도착 인정 유효 반경
     단위: 미터(m), 사용자 위치와 목표 좌표 간의 허용 거리 오차 범위 */
    private Double radiusInMeters;


     /** 시각 인증 정답 키워드
     Google Vision API를 통해 추출된 텍스트와 비교 검증할 타겟 문자열 */
    private String visionKeyword;

    /** 일반 힌트 미션 클리어 후 플레이어에게 공개할 단서 */
    private String clue;


     /** 최종 퀴즈 정답 키워드
     AI 대화 세션 또는 정답 입력란에서 검증할 최종 통과용 텍스트 */
    private String answerKeyword;

    /** 같은 챕터끼리 묶어주는 ID. 현재 흐름은 regionId 중심이며, 확장용으로 남겨둔 필드입니다. */
    private Long chapterId;

    /** true면 힌트를 다 모으기 전까지 지도에서 숨기는 최종 미션입니다. */
    private boolean finalMission = false;

    /** 미션 성공 후 보여줄 TourAPI 기반 '진짜 역사/정보' */
    private String realStory;

    /** 기존 서비스 코드의 `mission.isFinal()` 호출과 호환되는 최종 미션 getter입니다. */
    public boolean isFinal() {
        return finalMission;
    }

    /** 기존 서비스 코드의 `mission.setFinal(...)` 호출과 호환되는 최종 미션 setter입니다. */
    public void setFinal(boolean finalMission) {
        this.finalMission = finalMission;
    }
}
