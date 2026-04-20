package com.operation.seoul.location.repository;

import com.operation.seoul.location.domain.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * [Repository: 미션 영속성 계층]
 * 용도: 데이터베이스 Mission 엔티티에 대한 직접적인 접근 및 조회 로직 담당
 * 특징: Spring Data JPA를 통해 인터페이스 선언만으로 런타임에 구현체 생성
 */
public interface MissionRepository extends JpaRepository<Mission, Long> {

    /**
     * 특정 지역에 귀속된 미션 목록 조회
     * * @param regionId 조회하고자 하는 지역의 고유 식별자 (FK)
     * @return 해당 지역 아이디와 일치하는 Mission 엔티티 리스트
     * * 기능 상세:
     * 1. 메서드 명명 규칙(Query Method)에 따라 'SELECT * FROM mission WHERE region_id = ?' 쿼리 자동 생성
     * 2. 사용자가 특정 테마나 구역을 선택했을 때 관련 미션들만 필터링하여 반환
     */
    List<Mission> findByRegionId(Long regionId);
}