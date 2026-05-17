package com.operation.seoul.location.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Region {

    private Long id;
    private String name;
    private String areaCode = "seoul";
    private String description;
    private String periodCode = "mixed";
    private String themeCode = "mystery";
    private LocalDateTime createdAt;

    public void normalizeAreaCodeForPersistence() {
        if (areaCode == null || areaCode.isBlank()) {
            areaCode = "seoul";
        } else {
            areaCode = areaCode.trim().toLowerCase();
        }
        periodCode = normalizeCode(periodCode, "mixed");
        themeCode = normalizeCode(themeCode, "mystery");
    }

    private String normalizeCode(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase();
    }
}
