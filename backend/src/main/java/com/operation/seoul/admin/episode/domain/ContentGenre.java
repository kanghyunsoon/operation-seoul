package com.operation.seoul.admin.episode.domain;

import java.util.Arrays;
import java.util.List;

public enum ContentGenre {
    CRIME_MYSTERY("범죄 미스터리"),
    MURDER_MYSTERY("범죄 미스터리"),
    MISSING_CASE("실종 사건"),
    TREASURE_HUNT("보물찾기"),
    CODE_BREAKING("암호 해독");

    private final String displayName;

    ContentGenre(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static List<String> allowedNames() {
        return Arrays.stream(values()).map(ContentGenre::displayName).toList();
    }

    public static boolean isAllowedName(String value) {
        return value != null && Arrays.stream(values())
                .anyMatch(genre -> genre.displayName.equals(value.trim()));
    }

    public static ContentGenre fromIdOrName(String id, String name) {
        return Arrays.stream(values())
                .filter(genre -> genre.name().equalsIgnoreCase(id == null ? "" : id.trim())
                        || genre.displayName.equals(name == null ? "" : name.trim()))
                .findFirst()
                .orElse(null);
    }
}
