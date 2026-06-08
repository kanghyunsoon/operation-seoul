package com.operation.seoul.group.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.global.dto.ApiResponse;
import com.operation.seoul.group.dto.UserGroupMemberResponse;
import com.operation.seoul.group.dto.UserGroupRequest;
import com.operation.seoul.group.dto.UserGroupResponse;
import com.operation.seoul.group.service.UserGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class UserGroupController {
    private final CurrentUserResolver currentUserResolver;
    private final UserGroupService groupService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserGroupResponse>>> groups() {
        return ResponseEntity.ok(ApiResponse.ok("Groups.", groupService.getVisibleGroups(currentUserResolver.requireCurrentUser())));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<UserGroupResponse>>> myGroups() {
        return ResponseEntity.ok(ApiResponse.ok("My groups.", groupService.getMyGroups(currentUserResolver.requireCurrentUser())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserGroupResponse>> createGroup(@RequestBody UserGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Group created.", groupService.createGroup(currentUserResolver.requireCurrentUser(), request)));
    }

    @PostMapping("/{groupId}/join")
    public ResponseEntity<ApiResponse<UserGroupResponse>> joinGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.ok("Group joined.", groupService.joinGroup(currentUserResolver.requireCurrentUser(), groupId)));
    }

    @DeleteMapping("/{groupId}/join")
    public ResponseEntity<ApiResponse<UserGroupResponse>> leaveGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.ok("Group left.", groupService.leaveGroup(currentUserResolver.requireCurrentUser(), groupId)));
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<List<UserGroupMemberResponse>>> members(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.ok("Group members.", groupService.getMembers(currentUserResolver.requireCurrentUser(), groupId)));
    }
}
