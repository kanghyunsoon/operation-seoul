package com.operation.seoul.group.repository;

import com.operation.seoul.group.dto.UserGroupMemberResponse;
import com.operation.seoul.group.dto.UserGroupResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserGroupRepository {

    @Insert("""
            insert into user_groups (name, description, owner_id, visibility)
            values (#{name}, #{description}, #{ownerId}, #{visibility})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertGroup(UserGroupResponse group);

    @Insert("""
            insert ignore into user_group_members (group_id, user_id, role)
            values (#{groupId}, #{userId}, #{role})
            """)
    int insertMember(@Param("groupId") Long groupId, @Param("userId") Long userId, @Param("role") String role);

    @Delete("""
            delete from user_group_members
            where group_id = #{groupId}
              and user_id = #{userId}
              and role <> 'OWNER'
            """)
    int leaveGroup(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Select("""
            select g.id, g.name, g.description, g.visibility, g.owner_id, owner.nickname as owner_nickname,
                   (select count(*) from user_group_members gm where gm.group_id = g.id) as member_count,
                   case when mine.user_id is null then false else true end as joined,
                   mine.role as my_role,
                   g.created_at
            from user_groups g
            join users owner on owner.id = g.owner_id
            left join user_group_members mine on mine.group_id = g.id and mine.user_id = #{viewerId}
            where g.status = 'ACTIVE'
              and (g.visibility = 'PUBLIC' or mine.user_id is not null)
            order by g.created_at desc, g.id desc
            """)
    @Results(id = "UserGroupResponseMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "name", column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "visibility", column = "visibility"),
            @Result(property = "ownerId", column = "owner_id"),
            @Result(property = "ownerNickname", column = "owner_nickname"),
            @Result(property = "memberCount", column = "member_count"),
            @Result(property = "joined", column = "joined"),
            @Result(property = "myRole", column = "my_role"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<UserGroupResponse> findVisibleGroups(Long viewerId);

    @Select("""
            select g.id, g.name, g.description, g.visibility, g.owner_id, owner.nickname as owner_nickname,
                   (select count(*) from user_group_members gm where gm.group_id = g.id) as member_count,
                   true as joined,
                   mine.role as my_role,
                   g.created_at
            from user_group_members mine
            join user_groups g on g.id = mine.group_id
            join users owner on owner.id = g.owner_id
            where mine.user_id = #{viewerId}
              and g.status = 'ACTIVE'
            order by mine.joined_at desc, g.id desc
            """)
    @ResultMap("UserGroupResponseMap")
    List<UserGroupResponse> findMyGroups(Long viewerId);

    @Select("""
            select g.id, g.name, g.description, g.visibility, g.owner_id, owner.nickname as owner_nickname,
                   (select count(*) from user_group_members gm where gm.group_id = g.id) as member_count,
                   case when mine.user_id is null then false else true end as joined,
                   mine.role as my_role,
                   g.created_at
            from user_groups g
            join users owner on owner.id = g.owner_id
            left join user_group_members mine on mine.group_id = g.id and mine.user_id = #{viewerId}
            where g.id = #{groupId}
              and g.status = 'ACTIVE'
            limit 1
            """)
    @ResultMap("UserGroupResponseMap")
    UserGroupResponse findById(@Param("groupId") Long groupId, @Param("viewerId") Long viewerId);

    @Select("""
            select u.id as user_id, u.nickname, u.profile_image_url, gm.role, gm.joined_at
            from user_group_members gm
            join users u on u.id = gm.user_id
            where gm.group_id = #{groupId}
              and u.status = 'ACTIVE'
            order by case gm.role when 'OWNER' then 0 else 1 end, gm.joined_at asc
            """)
    @Results(id = "UserGroupMemberResponseMap", value = {
            @Result(property = "userId", column = "user_id"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "profileImageUrl", column = "profile_image_url"),
            @Result(property = "role", column = "role"),
            @Result(property = "joinedAt", column = "joined_at")
    })
    List<UserGroupMemberResponse> findMembers(Long groupId);

    @Select("select count(*) from user_group_members where group_id = #{groupId} and user_id = #{userId}")
    int countMembership(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
