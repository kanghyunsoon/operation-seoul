package com.operation.seoul.admin.episode.domain;

import lombok.Data;

@Data
public class AdminEpisodeProgressStats {
    private Long totalPlayers;
    private Long inProgressPlayers;
    private Long clearedPlayers;
}
