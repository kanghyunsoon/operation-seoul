package com.operation.seoul.location.repository;

import com.operation.seoul.location.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [Repository: 지역 정보 영속성 계층]
 * 용도: Region 엔티티를 통해 데이터베이스 'region' 테이블과 직접 소통
 * 특징: Spring Data JPA 인터페이스 형식을 사용하여 DB 연동 로직 자동화
 */
public interface RegionRepository extends JpaRepository<Region, Long> {

    /**
     * 표준 CRUD 메서드 활용 섹션
     * * 기능 정의:
     * 1. findAll(): 등록된 모든 지역(구역) 목록을 조회하여 초기 선택 화면 구성 시 활용
     * 2. findById(id): 특정 지역의 상세 테마 정보나 설명 데이터가 필요할 때 호출
     * 3. save(entity): 신규 지역 테마 및 미션 구역 등록 시 사용
     * * 주의사항: 인터페이스 선언만으로 JpaRepository 내장 로직이 자동 매핑됨
     */
}