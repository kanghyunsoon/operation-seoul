package com.operation.seoul.admin.episode.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DraftPlayerTextSanitizerTest {
    @Test
    void stripsMissionMemoTailFromPlayerFacingText() {
        String text = "조선 후기 한 저택에서 거상이 숨진 채 발견되었다. 미션 메모: 관리자는 현장 기록을 보강하세요.";

        String sanitized = DraftPlayerTextSanitizer.sanitizeText(text);

        assertEquals("조선 후기 한 저택에서 거상이 숨진 채 발견되었다.", sanitized);
    }

    @Test
    void stripsCompactMissionMemoMarkerFromPlayerFacingText() {
        String text = "용의자들의 진술이 엇갈린다. 미션메모 - 조사 단서 1번을 확인한다.";

        String sanitized = DraftPlayerTextSanitizer.sanitizeText(text);

        assertEquals("용의자들의 진술이 엇갈린다.", sanitized);
    }
}
