package com.operation.seoul.location.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/**
 * [Entity: 지역/지자체 테이블]
 * 용도: 전국 확장을 위해 미션들을 그룹화하는 최상위 폴더 역할
 * 호출: RegionRepository에서 DB 조회 시 이 객체 형태로 데이터를 퍼옴
 */
@Entity // JPA가 이 클래스를 보고 'region' 테이블을 만듭니다.
@Getter @Setter
public class Region {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;            // 지역 고유 번호 (PK)
    private String name;        // 지역 이름 (예: "서울특별시 정동길")
    private String description; // 지역 테마 설명
}