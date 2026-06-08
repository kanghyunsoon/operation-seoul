package com.operation.seoul.group.dto;

import lombok.Data;

@Data
public class UserGroupRequest {
    private String name;
    private String description;
    private String visibility;
}
