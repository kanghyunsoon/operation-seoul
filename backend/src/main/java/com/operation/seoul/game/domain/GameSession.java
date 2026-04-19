package com.operation.seoul.game.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/**
 * [Entity: 게임 진행 상태 테이블]
 * 용도: 유저별로 미션 진행 상황(세이브 데이터)을 관리합니다.
 * 흐름: 유저가 미션지에 도착하면 생성되고, 사진 인증과 정답을 맞힐 때마다 status가 바뀝니다.
 */
@Entity
@Getter @Setter
public class GameSession {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;              // PK: 세션 고유 식별자

    private Long userId;          // 플레이 중인 유저의 ID (프로토타입에서는 임시로 1L 고정)

    private Long missionId;       // 진행 중인 미션 ID (Location 모듈의 Mission 테이블과 연결됨)

    /**
     * 현재 게임 진행 상태를 나타냅니다.
     * [상태 종류]
     * "ARRIVED" : 현장에 도착함 (타이머 시작)
     * "PHOTO_VERIFIED" : Vision AI 현판 인식 통과함 (채팅 시작)
     * "CLEARED" : 최종 정답 맞힘 (미션 종료)
     */
    private String status;

    private String extractedLog;  // 향후 데이터 분석용: Vision AI가 사진에서 읽어낸 텍스트 원본을 저장
}