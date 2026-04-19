package com.operation.seoul.location.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * [Entity: 개별 미션(장소) 테이블]
 * 용도: 현실 세계의 특정 좌표(위경도)와 정답 키워드를 품고 있는 마스터 데이터
 * 호출: MissionRepository를 통해 DB에서 꺼내지며, ValidationService에서 계산용으로 쓰임
 */
@Entity
@Getter @Setter
public class Mission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;              // 미션 고유 번호
    private Long regionId;        // 소속 지역 번호 (Region의 ID 참조)
    private String title;         // 지도 마커 제목 (예: "중명전의 비밀")
    private Double targetLat;     // 목표 위도
    private Double targetLng;     // 목표 경도
    private Double radiusInMeters; // 도착 인정 반경 (m)
    private String visionKeyword; // 사진 인식 키워드
    private String answerKeyword; // 퀴즈 정답 키워드
}