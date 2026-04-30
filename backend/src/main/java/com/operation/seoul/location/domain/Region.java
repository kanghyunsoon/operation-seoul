package com.operation.seoul.location.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/**[Entity: 지역/지자체 데이터 모델]
 용도: 전국 서비스 확장을 위한 미션 그룹화 및 최상위 카테고리 관리
 특징: RegionRepository를 통한 데이터 조회 시 기준 객체로 활용 */
@Entity
@Getter @Setter
public class Region {

    /**지역 고유 식별 번호
     기본키(Primary Key)이며, 데이터베이스 IDENTITY 전략을 통해 자동 생성 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**지역 명칭
     예: "서울특별시 정동길", "부산 해운대구" 등 사용자에게 노출되는 지역의 공식 이름  */
    private String name;

    /**지역 테마 및 상세 설명
     해당 구역에서 진행되는 미션의 전체적인 배경 스토리나 테마 정보 수록 */
    @Column(columnDefinition = "TEXT")
    private String description;
}