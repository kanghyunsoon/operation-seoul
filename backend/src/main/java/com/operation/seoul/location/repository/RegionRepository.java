package com.operation.seoul.location.repository;

import com.operation.seoul.location.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RegionRepository extends JpaRepository<Region, Long> {

    @Query("""
            select r from Region r
            where r.areaCode = :areaCode
               or (:areaCode = 'seoul' and (r.areaCode is null or r.areaCode = ''))
            """)
    List<Region> findCardsByAreaCode(@Param("areaCode") String areaCode);
}
