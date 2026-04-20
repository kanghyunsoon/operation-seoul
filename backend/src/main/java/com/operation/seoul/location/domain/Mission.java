package com.operation.seoul.location.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * [Entity: 미션 정보 관리 모델]
 * 용도: 현장 방문 미션의 위치 정보, 인증 키워드, 상태값 정의
 * 특징: 데이터베이스 'mission' 테이블과 매핑되어 미션 비즈니스 로직의 기초 데이터 제공
 */
@Entity
@Getter @Setter
public class Mission {

    /**
     * 미션 고유 식별 번호
     * 자동 생성 전략(IDENTITY)을 사용하여 데이터베이스 레벨에서 PK 관리
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소속 지역 식별자
     * 특정 구역(Region)별 미션 필터링 및 그룹화를 위한 외래키 역할
     */
    private Long regionId;

    /**
     * 미션 제목
     * 사용자 화면 및 안내 가이드에 표시될 공식 명칭
     */
    private String title;

    /**
     * 목표 지점 위도
     * EPSG:4326(WGS84) 표준 좌표계 기준의 위도 값
     */
    private Double targetLat;

    /**
     * 목표 지점 경도
     * EPSG:4326(WGS84) 표준 좌표계 기준의 경도 값
     */
    private Double targetLng;

    /**
     * 도착 인정 유효 반경
     * 단위: 미터(m), 사용자 위치와 목표 좌표 간의 허용 거리 오차 범위
     */
    private Double radiusInMeters;

    /**
     * 시각 인증 정답 키워드
     * Google Vision API를 통해 추출된 텍스트와 비교 검증할 타겟 문자열
     */
    private String visionKeyword;

    /**
     * 최종 퀴즈 정답 키워드
     * AI 대화 세션 또는 정답 입력란에서 검증할 최종 통과용 텍스트
     */
    private String answerKeyword;
}