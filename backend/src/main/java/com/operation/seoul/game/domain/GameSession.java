package com.operation.seoul.game.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * [Domain: 게임 진행 상태 및 데이터 보존]
 * 역할: 사용자의 실시간 미션 진행 단계(세이브 포인트)를 관리하는 MyBatis 매핑 모델입니다.
 */
@Getter @Setter
public class GameSession {

    /** 세션 고유 식별자입니다. MySQL AUTO_INCREMENT 기본키와 매핑됩니다. */
    private Long id;

    /** 플레이어 식별 번호 (시스템 내 유저 고유 ID) */
    private Long userId;

    /** 수행 중인 미션 식별 번호 (Location 모듈 연동 키) */
    private Long missionId;

    /**[진행 단계 상태값]
     - ARRIVED: GPS 기반 현장 진입 확인 완료
     - PHOTO_VERIFIED: Vision AI 기반 사물/텍스트 인증 통과
     - CLEARED: 최종 퀴즈 정답 제출 및 미션 성공 */
    private String status;

    /**[AI 분석 결과 로그]
     - 기능: Vision AI가 사진 분석 후 반환한 가공 전 문자열 전체 저장
     - 목적: 인증 실패 케이스 분석 및 서비스 성능 최적화를 위한 기초 데이터 확보  */
    private String extractedLog;

    /** 작전 또는 미션 세션이 처음 생성된 시각입니다. 점수 계산과 소요 시간 보정에 사용합니다. */
    private LocalDateTime startedAt;

    /** 최종 정답 또는 현장 인증이 성공한 시각입니다. */
    private LocalDateTime clearedAt;

    /** 프론트가 보고하거나 서버가 보정한 전체 소요 시간입니다. */
    private Long elapsedSeconds;

    /** 프론트 GPS 기록으로 계산한 누적 이동 거리입니다. */
    private Double routeDistanceMeters;

    /** 시간과 이동 거리 감점으로 계산한 클리어 점수입니다. */
    private Integer score;
}
