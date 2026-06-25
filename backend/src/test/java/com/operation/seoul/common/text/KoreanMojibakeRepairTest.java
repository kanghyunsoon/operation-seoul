package com.operation.seoul.common.text;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanMojibakeRepairTest {
    @Test
    void keepsNormalKoreanText() {
        assertThat(KoreanMojibakeRepair.repair("최종 추리를 시작할 수 있습니다."))
                .isEqualTo("최종 추리를 시작할 수 있습니다.");
    }

    @Test
    void fallsBackWhenDecodedTextLostBytes() {
        String original = "범인, 흉기, 동기, 사인을 각각 입력하세요.";
        String mojibake = new String(original.getBytes(StandardCharsets.UTF_8), Charset.forName("windows-949"));

        assertThat(KoreanMojibakeRepair.repairOrFallback(mojibake, "복구 문구")).isEqualTo("복구 문구");
    }
}
