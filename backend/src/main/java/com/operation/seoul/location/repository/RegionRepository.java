package com.operation.seoul.location.repository;

import com.operation.seoul.location.domain.Region;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * Region 조회/저장 계층입니다.
 * 홈 화면의 권역 카드 조회와 관리자 작전 생성 저장을 MyBatis SQL로 처리합니다.
 */
@Mapper
public interface RegionRepository {

    /** 신규/기존 작전을 같은 메서드로 저장하기 위한 JPA 호환 형태의 저장 헬퍼입니다. */
    default Region save(Region region) {
        region.normalizeAreaCodeForPersistence();
        if (region.getId() == null) {
            insert(region);
        } else {
            update(region);
        }
        return region;
    }

    default Optional<Region> findById(Long id) {
        return Optional.ofNullable(findOneById(id));
    }

    @Select("""
            select id, name, area_code, description
            from region
            order by id desc
            """)
    @Results(id = "RegionResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "name", column = "name"),
            @Result(property = "areaCode", column = "area_code"),
            @Result(property = "description", column = "description")
    })
    List<Region> findAll();

    @Select("""
            select id, name, area_code, description
            from region
            where id = #{id}
            limit 1
            """)
    @ResultMap("RegionResultMap")
    Region findOneById(Long id);

    /**
     * 선택 권역에 속한 작전 카드만 반환합니다.
     * 기존 데이터 중 areaCode가 비어 있는 서울 데이터는 서울 권역에서 계속 보이도록 보정합니다.
     */
    @Select("""
            select id, name, area_code, description
            from region
            where area_code = #{areaCode}
               or (#{areaCode} = 'seoul' and (area_code is null or area_code = ''))
            order by id desc
            """)
    @ResultMap("RegionResultMap")
    List<Region> findCardsByAreaCode(String areaCode);

    @Select("select count(*) > 0 from region where id = #{id}")
    boolean existsById(Long id);

    @Insert("""
            insert into region (name, area_code, description)
            values (#{name}, #{areaCode}, #{description})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Region region);

    @Update("""
            update region
            set name = #{name},
                area_code = #{areaCode},
                description = #{description}
            where id = #{id}
            """)
    int update(Region region);

    @Delete("delete from region where id = #{id}")
    int deleteById(Long id);
}
