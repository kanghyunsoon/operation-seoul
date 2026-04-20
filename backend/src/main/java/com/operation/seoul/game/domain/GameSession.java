package com.operation.seoul.game.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * [Entity: 게임 진행 상태 및 데이터 보존]
 * - 역할: 사용자의 실시간 미션 진행 단계(세이브 포인트)를 관리하는 테이블 매핑 클래스
 */
@Entity
@Getter @Setter
public class GameSession {

    /** 세션 고유 식별자 (자동 생성 PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 플레이어 식별 번호 (시스템 내 유저 고유 ID) */
    @Column(nullable = false)
    private Long userId;

    /** 수행 중인 미션 식별 번호 (Location 모듈 연동 키) */
    @Column(nullable = false)
    private Long missionId;

    /**
     * [진행 단계 상태값]
     * - ARRIVED: GPS 기반 현장 진입 확인 완료
     * - PHOTO_VERIFIED: Vision AI 기반 사물/텍스트 인증 통과
     * - CLEARED: 최종 퀴즈 정답 제출 및 미션 성공
     */
    @Column(nullable = false)
    private String status;

    /**
     * [AI 분석 결과 로그]
     * - 기능: Vision AI가 사진 분석 후 반환한 가공 전 문자열 전체 저장
     * - 목적: 인증 실패 케이스 분석 및 서비스 성능 최적화를 위한 기초 데이터 확보
     */
    @Column(columnDefinition = "TEXT")
    private String extractedLog;
}