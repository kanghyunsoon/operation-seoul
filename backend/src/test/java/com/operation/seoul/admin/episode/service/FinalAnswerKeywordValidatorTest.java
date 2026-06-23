package com.operation.seoul.admin.episode.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FinalAnswerKeywordValidatorTest {

    @Test
    void acceptsAnyNonBlankFinalAnswerKeywords() {
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("CULPRIT", "박지성"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("CULPRIT", "관리자"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("CULPRIT", "이몽룡"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("WEAPON", "칼"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("WEAPON", "망치"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("WEAPON", "톱"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("WEAPON", "붓펜"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("WEAPON", "독성 분말이 묻은 붓펜"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("WEAPON", "오염된 보존 처리액이 남은 신문 기록지"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("WEAPON", "산성 보존액이 묻은 신문 원본 봉투"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("WEAPON", "느슨해진 전망대 난간"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("WEAPON", "날카롭게 갈린 의식용 칼날"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("WEAPON", "잠긴 냉동창고 문"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("MOTIVE", "은폐"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("MOTIVE", "비공개 거래 은폐"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", "독살"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", "투여"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", "피부에 닿게 함"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", "피해자가 서명란을 만지게 해 독성 분말을 손에 접촉시킴"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", "독성 잉크를 붓펜 끝에 묻혀 피해자가 매일 첫 기록을 할 때 손에 묻게 하여 흡수되도록 함"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", "오염된 골목 안내 지도 접힘면을 피해자가 동선을 확인하려고 펼칠 때 손에 닿게 함"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", "훼손된 한옥 개조 허가서 봉인면을 피해자가 원본 확인 중 개봉할 때 손에 닿게 함"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", "오염된 기록지 모서리를 피해자가 궁궐 영역 축소 자료를 열람하려고 펼칠 때 손에 닿게 함"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", "느슨해진 전망대 난간을 피해자가 야간 동선을 확인하려고 기대는 순간 추락으로 이어지게 함"));
        assertFalse(FinalAnswerKeywordValidator.weakFinalAnswerKeyword("METHOD", "잠긴 냉동창고 문을 피해자가 보관품을 확인하러 들어간 뒤 열리지 않게 해 동사로 이어지게 함"));
    }
}
