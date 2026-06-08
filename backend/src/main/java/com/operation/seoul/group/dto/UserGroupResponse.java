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
public class UserGroupResponse {
    private Long id;
    private String name;
    private String description;
    private String visibility;
    private Long ownerId;
    private String ownerNickname;
    private Integer memberCount;
    private Boolean joined;
    private String myRole;
    private LocalDateTime createdAt;
}
