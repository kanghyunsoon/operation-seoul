package com.operation.seoul.group.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.group.dto.UserGroupMemberResponse;
import com.operation.seoul.group.dto.UserGroupRequest;
import com.operation.seoul.group.dto.UserGroupResponse;
import com.operation.seoul.group.repository.UserGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserGroupService {
    private static final Set<String> VISIBILITIES = Set.of("PUBLIC", "PRIVATE");

    private final UserGroupRepository groupRepository;

    public List<UserGroupResponse> getVisibleGroups(User user) {
        return groupRepository.findVisibleGroups(user.getId());
    }

    public List<UserGroupResponse> getMyGroups(User user) {
        return groupRepository.findMyGroups(user.getId());
    }

    public UserGroupResponse createGroup(User user, UserGroupRequest request) {
        String name = requireName(request);
        UserGroupResponse group = UserGroupResponse.builder()
                .name(name)
                .description(normalizeDescription(request.getDescription()))
                .visibility(normalizeVisibility(request.getVisibility()))
                .ownerId(user.getId())
                .build();
        groupRepository.insertGroup(group);
        groupRepository.insertMember(group.getId(), user.getId(), "OWNER");
        return requireGroup(group.getId(), user.getId());
    }

    public UserGroupResponse joinGroup(User user, Long groupId) {
        UserGroupResponse group = requireGroup(groupId, user.getId());
        if (!"PUBLIC".equals(group.getVisibility()) && !Boolean.TRUE.equals(group.getJoined())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PRIVATE_GROUP", "Private group cannot be joined directly.");
        }
        groupRepository.insertMember(groupId, user.getId(), "MEMBER");
        return requireGroup(groupId, user.getId());
    }

    public UserGroupResponse leaveGroup(User user, Long groupId) {
        UserGroupResponse group = requireGroup(groupId, user.getId());
        if ("OWNER".equals(group.getMyRole())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OWNER_CANNOT_LEAVE", "Group owner cannot leave the group.");
        }
        groupRepository.leaveGroup(groupId, user.getId());
        return requireGroup(groupId, user.getId());
    }

    public List<UserGroupMemberResponse> getMembers(User user, Long groupId) {
        UserGroupResponse group = requireGroup(groupId, user.getId());
        if (!"PUBLIC".equals(group.getVisibility()) && groupRepository.countMembership(groupId, user.getId()) == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GROUP_ACCESS_DENIED", "Group access denied.");
        }
        return groupRepository.findMembers(groupId);
    }

    private UserGroupResponse requireGroup(Long groupId, Long viewerId) {
        if (groupId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GROUP_REQUIRED", "Group is required.");
        }
        UserGroupResponse group = groupRepository.findById(groupId, viewerId);
        if (group == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Group not found.");
        }
        return group;
    }

    private String requireName(UserGroupRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GROUP_NAME_REQUIRED", "Group name is required.");
        }
        String name = request.getName().trim();
        return name.length() > 80 ? name.substring(0, 80) : name;
    }

    private String normalizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private String normalizeVisibility(String visibility) {
        String normalized = StringUtils.hasText(visibility) ? visibility.trim().toUpperCase() : "PUBLIC";
        if (!VISIBILITIES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GROUP_VISIBILITY", "Invalid group visibility.");
        }
        return normalized;
    }
}
