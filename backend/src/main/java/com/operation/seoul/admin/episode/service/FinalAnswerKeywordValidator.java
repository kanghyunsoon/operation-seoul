package com.operation.seoul.admin.episode.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

final class FinalAnswerKeywordValidator {
    private FinalAnswerKeywordValidator() {
    }

    static boolean weakFinalAnswerKeyword(String slot, String value) {
        String compacted = compact(value);
        if (blank(compacted)) return true;
        return switch (slot) {
            case "CULPRIT" -> weakCulpritKeyword(compacted);
            case "WEAPON" -> weakWeaponKeyword(compacted);
            case "MOTIVE" -> weakKeyword(compacted, 6, "범죄", "복수", "돈", "질투", "은폐", "원한", "분노", "실수", "협박", "비밀");
            case "METHOD" -> weakMethodKeyword(compacted);
            default -> false;
        };
    }

    private static boolean weakCulpritKeyword(String compacted) {
        return !isSpecificKoreanPersonName(compacted);
    }

    private static boolean weakWeaponKeyword(String compacted) {
        if (containsAny(compacted, "독성", "마취", "진정", "수면", "청산", "시안", "오염", "섞인", "묻힌", "주입", "변조", "유독", "환각")
                && containsAny(compacted, "약", "독", "캡슐", "병", "컵", "잔", "보온병", "향수", "시약", "분말", "액체", "주사", "칼", "도구", "붓펜", "펜", "잉크", "마커", "붓", "카드", "접착제", "소독제", "장갑", "안료", "스프레이", "세척제", "세척통")) {
            return false;
        }
        if (weakKeyword(compacted, 5, "약", "독", "흉기", "도구", "칼", "약물", "고산병약", "수면제", "캡슐", "향수병", "약병", "컵", "잔", "보온병", "붓펜", "펜", "연필", "마커", "붓", "카드", "접착제", "소독제", "장갑", "안료", "스프레이", "세척제", "세척통")) {
            return true;
        }
        return containsAny(compacted, "병", "컵", "잔", "보온병", "봉투", "상자", "붓펜", "펜", "연필", "마커", "붓", "카드", "접착제", "소독제", "장갑", "안료", "스프레이", "세척제", "세척통")
                && !containsAny(compacted, "독성", "마취", "진정", "수면", "청산", "시안", "오염", "섞인", "묻힌", "주입", "변조", "유독", "환각");
    }

    private static boolean weakMethodKeyword(String compacted) {
        if (List.of("함", "넣기", "투여", "주입", "교체", "은폐", "조작", "살해", "독살", "바꿔치기", "유인", "방치", "사용", "사용함", "실행", "실행함", "시도", "시도함").contains(compacted)) {
            return true;
        }
        if (containsAny(compacted, "혼란을야기", "몰래투여", "정신을잃게", "상태를악화", "의식을잃게", "쓰러지게함")) {
            return true;
        }
        if (containsAny(compacted, "내용물섭취유도", "이식하여", "몰래이식", "사용하게함", "접촉하게함")
                && !containsAny(compacted, "피해자", "서명란", "손", "호흡", "흡입", "개봉", "장부", "문서", "봉투", "컵", "잔", "음료", "약")) {
            return true;
        }
        if (compacted.length() < 6) {
            return true;
        }
        boolean hasAction = containsAny(compacted, "넣", "섞", "바꿔", "교체", "투여", "분사", "주입", "묻혀", "먹여", "마시게", "흡입", "접촉", "조작", "유인", "오염", "서명", "바름", "발라", "칠함");
        boolean hasObjectOrVictim = containsAny(compacted, "피해자", "약", "캡슐", "병", "컵", "잔", "보온병", "향수", "음료", "시약", "문서", "서명", "서명란", "붓펜", "펜", "잉크", "마커", "봉투", "열쇠", "서랍", "준비물", "카드", "접착제", "소독제", "장갑", "안료", "스프레이", "세척제", "세척통");
        return !hasAction || !hasObjectOrVictim;
    }

    private static boolean weakKeyword(String compacted, int minLength, String... genericValues) {
        if (compacted.length() < minLength) return true;
        for (String generic : genericValues) {
            if (compacted.equals(compact(generic))) return true;
        }
        return false;
    }

    private static boolean isSpecificKoreanPersonName(String compacted) {
        Set<String> forbiddenNames = Set.of("이몽룡", "성춘향", "춘향", "몽룡", "홍길동", "임꺽정", "장보고", "유관순", "세종대왕", "이순신", "안중근", "김구");
        if (forbiddenNames.contains(compacted)) return false;
        Set<String> genericRoles = Set.of("여행사직원", "사업파트너", "피해자", "용의자", "관계자", "관리자", "직원", "가이드", "비서", "조카", "동료", "연구원", "큐레이터", "투자자", "운영자");
        if (genericRoles.contains(compacted)) return false;
        String namePattern = "(김|이|박|최|정|강|조|윤|장|임|한|오|서|신|권|황|안|송|전|홍|유|고|문|양|손|배|백|허|남|심|노|하|곽|성|차|주|우|구|민|류|나|진|지|엄|채|원|천|방|공|현|함|변|염|여|추|도|소|석|선|설|마|길|위|표|명|기|반|왕|금|옥|육|인|맹|제|모|탁|국|어|은|편|용|예|경|봉|사|부|가|복|태|목|형|계|피|두)[가-힣]{1,2}";
        return compacted.matches("^" + namePattern + "$")
                || compacted.matches("^" + namePattern + "\\([가-힣A-Za-z0-9·/\\-]+\\)$")
                || compacted.matches("^" + namePattern + "(팀장|대표|실장|매니저|가이드|직원|비서|교수|연구원|관리자|기자|작가|큐레이터|조교|의사|간호사|변호사|파트너)$");
    }

    private static boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) {
            if (!blank(target) && text.contains(target)) return true;
        }
        return false;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
