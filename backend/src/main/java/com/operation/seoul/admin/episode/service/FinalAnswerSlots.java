package com.operation.seoul.admin.episode.service;

import java.util.List;
import java.util.Map;

final class FinalAnswerSlots {
    static final List<String> IDS = List.of("CULPRIT", "WEAPON", "MOTIVE", "METHOD");
    static final Map<String, String> LABELS = Map.of(
            "CULPRIT", "범인",
            "WEAPON", "흉기",
            "MOTIVE", "동기",
            "METHOD", "사인"
    );

    private FinalAnswerSlots() {
    }
}
