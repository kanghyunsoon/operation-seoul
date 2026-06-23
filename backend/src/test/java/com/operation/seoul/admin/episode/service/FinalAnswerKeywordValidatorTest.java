package com.operation.seoul.admin.episode.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalAnswerKeywordValidatorTest {

    @Test
    void acceptsConcreteFinalAnswerKeywords() {
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("CULPRIT", "박지성"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("WEAPON", "독성 분말이 묻은 붓펜"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("MOTIVE", "비공개 거래 은폐"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", "피해자가 서명란을 만지게 해 독성 분말을 손에 접촉시킴"));
    }

    @Test
    void rejectsGenericOrUnclearFinalAnswerKeywords() {
        assertTrue(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("CULPRIT", "관리자"));
        assertTrue(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("WEAPON", "붓펜"));
        assertTrue(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("MOTIVE", "은폐"));
        assertTrue(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", "필기구에 몰래 이식하여 내용물 섭취 유도"));
    }
}
