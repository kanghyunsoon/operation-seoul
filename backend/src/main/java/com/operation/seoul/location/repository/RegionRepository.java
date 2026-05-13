package com.operation.seoul.location.repository;

import com.operation.seoul.location.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Region 엔티티 조회 계층입니다.
 * 홈 화면은 전국 권역 탭을 기준으로 카드 목록을 필터링하므로 areaCode 조건 조회를 추가로 제공합니다.
 */
public interface RegionRepository extends JpaRepository<Region, Long> {

    /**
     * 선택 권역에 속한 작전 카드만 반환합니다.
     * 기존 데이터 중 areaCode가 비어 있는 서울 데이터는 서울 권역에서 계속 보이도록 보정합니다.
     */
    @Query("""
            select r from Region r
            where r.areaCode = :areaCode
               or (:areaCode = 'seoul' and (r.areaCode is null or r.areaCode = ''))
            """)
    List<Region> findCardsByAreaCode(@Param("areaCode") String areaCode);
}
