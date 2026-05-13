package com.operation.seoul.location.dto;

import com.operation.seoul.location.domain.Mission;
import lombok.Builder;
import lombok.Data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@Builder
public class MissionResponse {
    /** Mission id. 지도 마커 선택, Vision 인증, 채팅 진입의 기준값입니다. */
    private Long id;
    /** 지도/목록에 표시하는 미션명입니다. */
    private String title;
    /** 목표 위도입니다. 최종 미션도 거리 계산 때문에 좌표는 내려보냅니다. */
    private Double targetLat;
    /** 목표 경도입니다. 프론트가 마커 표시 여부를 별도로 제어합니다. */
    private Double targetLng;
    /** 도착 인정 반경입니다. null이면 화면/서비스에서 기본값을 보정합니다. */
    private Double radiusInMeters;
    /** 현장 사진 인증 대상 키워드입니다. */
    private String visionKeyword;
    /** 미션별 짧은 서사 또는 현장 설명입니다. */
    private String description;
    /** 최종 미션 해금 후 현장에서 보여줄 마지막 단서입니다. */
    private String fieldClue;
    /** 프론트 호환용 미션 타입입니다. HINT 또는 FINAL 값을 사용합니다. */
    private String missionType;
    /** 최종 미션 여부입니다. Lombok getter 호환 때문에 필드명은 기존 형식을 유지합니다. */
    private boolean isFinal;
    /** 최종 미션이 지도에 표시될 수 있는지 여부입니다. */
    private boolean isUnlocked;
    /** 현재 사용자의 GameSession 상태입니다. 없으면 null입니다. */
    private String sessionStatus;
    /** 클리어된 힌트 미션에서 플레이어에게 공개할 단서입니다. */
    private String clue;
    /** 브리핑 화면에서 최종 미션 배경을 보정하는 짧은 맥락입니다. */
    private String briefingContext;

    /**
     * Mission 엔티티와 사용자별 진행 상태를 프론트 표시용 DTO로 변환합니다.
     * 클리어된 미션만 단서를 공개하고, 최종 미션의 현장 단서는 해금 조건을 반영합니다.
     */
    public static MissionResponse of(Mission mission, String sessionStatus, boolean isUnlocked) {
        String acquiredClue = null;
        if ("CLEARED".equals(sessionStatus)) {
            String rawClue = mission.getClue() != null && !mission.getClue().isBlank()
                    ? mission.getClue()
                    : mission.getAnswerKeyword();
            acquiredClue = sanitizeStoryClue(mission, rawClue);
        }

        return MissionResponse.builder()
                .id(mission.getId())
                .title(mission.getTitle())
                // 힌트를 찾기 시작했을 때 거리를 계산해야 하므로 좌표는 가리지 않고 무조건 전송합니다.
                // (프론트엔드에서 마커 자체는 투명하게 가려줍니다)
                .targetLat(mission.getTargetLat())
                .targetLng(mission.getTargetLng())
                .radiusInMeters(mission.getRadiusInMeters())
                .visionKeyword(mission.getVisionKeyword())
                .description(mission.getDescription())
                .fieldClue(resolveFieldClue(mission, isUnlocked))
                .missionType(mission.isFinal() ? "FINAL" : "HINT")
                .isFinal(mission.isFinal())
                .isUnlocked(isUnlocked)
                .sessionStatus(sessionStatus)
                .clue(acquiredClue)
                .briefingContext(resolveBriefingContext(mission))
                .build();
    }

    /** 최종 미션이 해금되었을 때 보여줄 현장 단서를 계산합니다. */
    private static String resolveFieldClue(Mission mission, boolean isUnlocked) {
        if (!mission.isFinal() || !isUnlocked) {
            return null;
        }

        if (mission.getClue() != null && !mission.getClue().isBlank()) {
            return sanitizeStoryClue(mission, mission.getClue());
        }
        if (mission.getVisionKeyword() != null && !mission.getVisionKeyword().isBlank()) {
            return "'" + mission.getVisionKeyword() + "'에 남은 흠집과 연도가 마지막 봉인의 결을 비춘다. 닫힌 문 너머에서 오래된 결정이 아직 흔들린다.";
        }
        return "마지막 표식은 이름을 감추고 연도와 인물의 그림자만 남긴다. 닫힌 사건의 방향이 한쪽으로 기울어 있다.";
    }

    /**
     * AI가 생성한 단서가 너무 기계적인 안내문이면 장르 톤에 맞는 스토리 단서로 보정합니다.
     */
    public static String sanitizeStoryClue(Mission mission, String clue) {
        if (clue == null || clue.isBlank()) {
            return clue;
        }

        String normalized = normalizeClueTone(clue);
        if (!isMechanicalClue(clue) && !isMechanicalClue(normalized)) {
            return normalized;
        }

        String source = String.join(" ",
                mission.getTitle() == null ? "" : mission.getTitle(),
                mission.getDescription() == null ? "" : mission.getDescription(),
                mission.getVisionKeyword() == null ? "" : mission.getVisionKeyword(),
                clue
        );

        if (isRailTheme(source)) {
            return railStoryClue(source);
        }
        if (containsAny(source, "궁", "왕", "왕실", "대한제국", "조선", "외세", "성문", "관아")) {
            return "봉투 안에는 반쯤 찢긴 명령서가 들어 있다.\n인장은 남았지만, 명령을 내린 이름은 칼끝으로 긁혀 사라졌다.\n누군가는 권력이 흔들리던 순간을 숨기려 했다.";
        }
        if (containsAny(source, "전쟁", "전투", "피난", "휴전", "참전", "호국")) {
            return "젖은 수첩 한 장이 접힌 채 남아 있다.\n돌아오지 못한 이름들 사이로 같은 약속이 반복된다.\n승패보다 오래 남은 것은 그날 숨긴 말이었다.";
        }
        if (containsAny(source, "바다", "해안", "항구", "등대", "섬", "포구", "어촌")) {
            return "낡은 지도 가장자리에 소금기가 말라붙어 있다.\n표시된 방향은 항로가 아니라, 누군가 돌아오지 못한 밤을 가리킨다.\n파도는 지운 이름을 끝내 같은 자리로 밀어 올린다.";
        }
        return "닫힌 상자 안에서 순서가 뒤바뀐 사진들이 쏟아졌다.\n웃고 있는 장면 뒤편에 같은 그림자가 반복된다.\n이야기의 시작을 바꾼 사람은 아직 결말을 숨기고 있다.";
    }

    /** 존댓말/명령형에 가까운 AI 문장을 게임 톤에 맞게 완화합니다. */
    private static String normalizeClueTone(String clue) {
        return clue
                .replace("관찰하십시오", "남아 있다")
                .replace("살펴보십시오", "드러난다")
                .replace("주목하십시오", "흔들린다")
                .replace("찾으십시오", "숨어 있다")
                .replace("확인하십시오", "남아 있다")
                .replace("기억하십시오", "잊히지 않는다")
                .replace("입니다", "이다")
                .replace("했습니다", "했다")
                .replace("하십시오", "하라")
                .trim();
    }

    /** 안내판 지시문처럼 느껴지는 단서인지 판단합니다. */
    private static boolean isMechanicalClue(String clue) {
        return containsAny(clue,
                "관찰하", "살펴보", "주목하", "찾으", "확인하", "기억하",
                "안내판", "비석", "간판", "벽면", "입구", "조형물", "발치",
                "지점", "연도를", "단서를", "비밀이 숨어", "명분을 찾");
    }

    /** 철도/열차/탄광 계열 장소인지 판단해 전용 단서 템플릿을 쓰기 위한 분기입니다. */
    private static boolean isRailTheme(String source) {
        return containsAny(source, "열차", "철도", "철길", "선로", "노선", "화물", "여객", "관광", "석탄", "영동선", "궤도", "강철 바퀴");
    }

    /** 철도 테마 작전에서 단서가 반복적으로 안내문처럼 보이지 않도록 대체 문장을 제공합니다. */
    private static String railStoryClue(String source) {
        if (containsAny(source, "화물", "철 조각", "공원", "마모")) {
            return "낡은 캐리어 안쪽에서 검은 가루가 묻은 표가 나왔다.\n승객 이름은 비어 있고, 도착지 칸에는 찢긴 풍경 사진만 남아 있다.\n이 길은 처음부터 여행자를 위해 놓인 것이 아니었다.";
        }
        if (containsAny(source, "간판", "광장", "강철", "바퀴", "결단", "명분")) {
            return "녹음기를 켜자 바퀴 소리 뒤로 낮은 목소리가 섞인다.\n무거운 짐을 싣던 길이 멈춘 뒤, 사람들은 같은 선로에 다른 이유를 올려놓았다.\n누군가 그 변화를 결심한 순간이 두 번째 잠금이다.";
        }
        if (containsAny(source, "사진", "연도", "박물관", "궤도", "도입")) {
            return "마지막 필름에는 빈 화물칸과 환한 창밖 풍경이 겹쳐 찍혀 있다.\n사라진 역무원은 그 장면을 보고서야 장부의 빈칸을 접었다.\n길의 목적이 바뀌는 순간, 결말의 이름도 가까워진다.";
        }
        return "찢어진 운행일지에는 같은 선로가 두 번 기록되어 있다.\n한 번은 무게를 나르던 길, 한 번은 기억을 태우던 길이다.\n두 기록 사이의 빈칸이 마지막 문을 향해 열린다.";
    }

    /** 최종 미션의 실제 역사 해설을 브리핑용으로 축약/분류합니다. */
    private static String resolveBriefingContext(Mission mission) {
        if (!mission.isFinal() || mission.getRealStory() == null || mission.getRealStory().isBlank()) {
            return null;
        }

        String context = mission.getRealStory()
                .replaceAll("<br\\s*/?>", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return buildCoarseBriefingContext(context);
    }

    /** 원문 해설의 키워드를 기준으로 브리핑 문맥을 큰 주제로 요약합니다. */
    private static String buildCoarseBriefingContext(String context) {
        String period = resolvePeriod(context);

        if (containsAny(context, "관광", "열차", "노선", "개통", "축제", "조성", "콘텐츠")) {
            return period + " 지역 사회는 이동 경험, 풍경, 생활권의 기억을 새로운 관광 콘텐츠로 다시 엮기 시작했다. "
                    + "이 배경은 특정 이름보다 익숙한 장소가 기록과 상품, 기억 사이에서 새 역할을 부여받던 흐름을 가리킨다.";
        }
        if (containsAny(context, "철도", "역", "탄광", "광산", "산업", "항구", "항만")) {
            return period + " 교통과 산업의 흐름은 사람들의 일상과 도시의 방향을 바꾸었다. "
                    + "이번 기록은 그 변화가 남긴 노동, 이동, 생활권의 흔적을 따라가도록 설계되어 있다.";
        }
        if (containsAny(context, "일제", "식민", "독립", "강점", "항일")) {
            return period + " 개인의 일상과 지역의 기억은 더 큰 권력의 압력 아래 다시 쓰였다. "
                    + "이번 기록은 공개된 역사보다 지워진 선택과 남겨진 목소리에 가까이 붙어 있다.";
        }
        if (containsAny(context, "대한제국", "개항", "외세", "왕실", "조약", "고종")) {
            return period + " 국가의 질서와 외부 압력이 충돌하며 오래된 체제가 흔들리던 시기다. "
                    + "이번 기록은 공식 문서 뒤에 남은 선택, 침묵, 방향 전환의 흔적을 따라가게 한다.";
        }
        if (containsAny(context, "전쟁", "피난", "휴전", "한국전쟁", "6.25")) {
            return period + " 사람들의 생활권은 갑작스러운 충돌과 이동 속에서 다시 짜였다. "
                    + "이번 기록은 거대한 사건의 이름보다 그 여파가 남긴 장소의 기억을 먼저 드러낸다.";
        }
        return period + " 지역의 생활과 기억은 눈에 잘 띄지 않는 변화 속에서 다른 방향으로 접혔다. "
                + "이번 기록은 그 변화가 남긴 빈칸을 따라가며 결말에 가까워지도록 설계되어 있다.";
    }

    /** 텍스트에 드러난 사건/연도를 바탕으로 대략적인 시대 표현을 반환합니다. */
    private static String resolvePeriod(String context) {
        if (containsAny(context, "대한제국", "개항", "외세", "왕실", "조약", "고종")) {
            return "근대 국가 체제가 흔들리던 시기,";
        }
        if (containsAny(context, "일제", "식민", "독립", "강점", "항일")) {
            return "일제강점기 전후,";
        }
        if (containsAny(context, "전쟁", "피난", "휴전", "한국전쟁", "6.25")) {
            return "전쟁과 분단의 충격이 남아 있던 시기,";
        }

        Integer year = extractYear(context);
        if (year != null) {
            if (year >= 2000) {
                return "2000년대 이후,";
            }
            if (year >= 1960) {
                return "산업화와 도시화가 빠르게 진행되던 시기,";
            }
            if (year >= 1945) {
                return "해방 이후 사회 질서가 재편되던 시기,";
            }
            if (year >= 1910) {
                return "일제강점기 전후,";
            }
            if (year >= 1876) {
                return "근대 국가 체제가 흔들리던 시기,";
            }
            return "오래된 왕조 질서가 지역의 삶을 규정하던 시기,";
        }

        if (containsAny(context, "관광", "콘텐츠", "축제")) {
            return "현대 지역 관광이 재편되던 시기,";
        }
        if (containsAny(context, "산업", "철도", "항구", "광산", "탄광")) {
            return "산업과 교통의 축이 바뀌던 시기,";
        }
        return "기록의 시대 구분은 아직 완전히 복원되지 않았지만,";
    }

    /** `0000년` 형태의 첫 연도를 추출합니다. */
    private static Integer extractYear(String context) {
        Matcher matcher = Pattern.compile("(\\d{3,4})\\s*년").matcher(context);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 단순 키워드 포함 여부를 반복적으로 쓰기 위한 유틸리티입니다. */
    private static boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
