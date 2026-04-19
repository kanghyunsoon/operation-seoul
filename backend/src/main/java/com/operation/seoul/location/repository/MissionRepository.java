package com.operation.seoul.location.repository;

import com.operation.seoul.location.domain.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * [Repository: Mission DB 접근]
 * 용도: Mission 테이블에서 데이터를 가져옴, MissionService에서 이 인터페이스를 부름
 */
public interface MissionRepository extends JpaRepository<Mission, Long> {

    /**
     * 용도: 프론트엔드에서 특정 지역(예: 서울=1)을 선택했을 때,
     * SELECT * FROM mission WHERE region_id = 1; 쿼리를 자동으로 실행
     */
    List<Mission> findByRegionId(Long regionId);
}