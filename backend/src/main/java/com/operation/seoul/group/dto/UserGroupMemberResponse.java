package com.operation.seoul.group.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupMemberResponse {
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private String role;
    private LocalDateTime joinedAt;
}
