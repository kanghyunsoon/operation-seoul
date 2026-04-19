package com.operation.seoul.location.repository;

import com.operation.seoul.location.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [Repository: Region DB 접근]
 * 용도: Region 테이블에 접근하여 데이터를 CRUD(생성,조회,수정,삭제) 하는 인터페이스
 * 주의: class가 아니라 interface여야 JpaRepository가 정상 작동
 */
public interface RegionRepository extends JpaRepository<Region, Long> {
    // 현재는 기본 제공되는 findById, findAll 등만 사용하므로 추가 코드가 없습니다.
}