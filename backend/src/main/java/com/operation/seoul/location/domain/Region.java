package com.operation.seoul.location.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * [Domain: 지역/지자체 데이터 모델]
 * 용도: 전국 서비스 확장을 위한 미션 그룹화 및 최상위 카테고리 관리
 * 특징: MyBatis 매퍼를 통한 데이터 조회 시 기준 객체로 활용
 */
@Getter @Setter
public class Region {

    /** 지역 고유 식별 번호입니다. MySQL AUTO_INCREMENT 기본키와 매핑됩니다. */
    private Long id;

    /** 지역 명칭입니다. 예: "서울특별시 정동길", "부산 해운대구" */
    private String name;

    /** 홈 화면 전국 권역 필터에 사용하는 코드입니다. 예: seoul, gangwon, jeju */
    private String areaCode = "seoul";

    /** 지역 테마 및 상세 설명입니다. 해당 구역에서 진행되는 미션의 전체 배경을 담습니다. */
    private String description;

    /** MyBatis 저장 직전에 호출해 null/공백 권역 코드가 DB에 들어가지 않게 보정합니다. */
    public void normalizeAreaCodeForPersistence() {
        if (areaCode == null || areaCode.isBlank()) {
            areaCode = "seoul";
            return;
        }
        areaCode = areaCode.trim().toLowerCase();
    }
}
