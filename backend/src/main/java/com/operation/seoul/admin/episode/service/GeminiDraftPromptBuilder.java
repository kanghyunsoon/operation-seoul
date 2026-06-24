package com.operation.seoul.admin.episode.service;

import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GeminiDraftPromptBuilder {
    private static final List<String> SLOT_IDS = FinalAnswerSlots.IDS;

    private GeminiDraftPromptBuilder() {
    }

    static String build(AiEpisodeDraftRequest request) {
        return """
                JSON만 반환한다. 모든 문장은 한국어로 작성한다.
                장르는 항상 "범죄 미스터리"다.

                너의 역할은 장소 안내문 작성자가 아니라 크라임씬 사건 작가다.
                승인된 최종 정답 4개(CULPRIT, WEAPON, MOTIVE, METHOD)를 바탕으로 하나의 완성된 살인 사건을 만든다.
                실제 장소명, 주소, 상호명, Kakao 주변 후보, 지도 동선은 사건 줄거리와 단서에 사용하지 않는다.
                장소는 나중에 미션에 배정될 지도 좌표일 뿐이다.

                반드시 지킬 것:
                - CULPRIT 값은 한국인 인명이어야 하며 suspects[0..2].displayName 중 정확히 한 명과 완전히 일치해야 한다.
                - CULPRIT에 직업/역할/관계 표현이 들어오면 그대로 쓰지 말고, 그 역할을 가진 새 한국인 이름을 범인으로 설정한다. 역할 표현은 suspect alias에만 둔다.
                - suspect displayName은 반드시 새로 만든 한국인 이름만 쓴다. 직업/직함/관계/역할은 displayName에 쓰지 않는다.
                - CULPRIT identity contract: approvedFinalAnswers.CULPRIT is locked. Do not rename, translate, mask, alias, or replace it with another person name.
                - Exactly one suspect displayName must be byte-for-byte identical to approvedFinalAnswers.CULPRIT. Alias, relationToVictim, shortDescription, or suspiciousPoint matches are not enough.
                - If the synopsis must hide the culprit, hide only the synopsis wording. Do not change finalAnswers.culprit, finalAnswerKeywordItems.CULPRIT, or the matching suspect displayName.
                - Do not put placeholders such as 핵심 단서, 기록 속 인물, 관계자, 관리인, 조카, 상인, 후계자, or any role label in a suspect displayName.
                - suspect alias에는 현재 사건 배경에서 자연스러운 직업이나 역할을 쓴다. 같은 직업/역할 예시를 반복하지 말고 storyAnchors와 approvedFinalAnswers에 맞춰 만든다.
                - suspect relationToVictim에는 피해자와의 관계를 쓴다.
                - suspect shortDescription에는 현재 의심 포인트처럼 보이는 구체적 정황을 쓰지 않는다. 직업/역할, 사건 배경상 위치, 피해자와 얽힌 공식 업무만 짧게 쓴다.
                - suspect suspiciousPoint에는 지금 shortDescription에 넣고 싶은 의심 정황을 넣는다. 특정 예시 문장을 베끼지 말고 현재 사건의 갈등과 단서 구조에 맞게 만든다.
                - suspect alibiSummary는 반드시 한국어 문장으로 쓰고, 사건 시간대에 어디서 무엇을 했다고 주장하는지 스토리에 맞게 구체화한다. 영어 템플릿 문장이나 "There is..." 문장을 절대 쓰지 않는다.
                - era에는 request의 시대 값을 그대로 우선 사용하고, 반드시 명확한 한국어 시대명으로 쓴다. 시대와 소재를 섞은 설명형 문장을 era에 쓰지 않는다.
                - fictionSynopsis와 finalTruthSummary에는 사건이 벌어진 시대를 명확히 드러내는 직업, 생활 도구, 장비, 공간 운영 방식, 사회적 갈등을 반영한다.
                - finalTruthSummary에는 승인된 CULPRIT, WEAPON, MOTIVE, METHOD 값을 그대로 모두 포함한다.
                - 승인된 MOTIVE가 "산업 스파이", "은폐", "갈등", "복수", "협박"처럼 짧거나 추상적이면 그대로 반복하지 말고, 어떤 기술/장부/계약/책임/평판/직위/상속 문제가 누구에게 어떤 손실과 폭로 압박을 만드는지 사건 안에서 구체화한다.
                - METHOD는 화면에는 "사인"으로 표시되는 최종 정답 슬롯이다. 단순 실행 방법이 아니라 피해자가 직접적으로 어떤 치명상/사망 경로로 죽었는지를 답해야 한다.
                - 승인된 METHOD가 "잠금해제", "잠금", "해제", "은폐", "조작", "침입", "유인", "사고", "방치", "교란", "위장"처럼 접근 조작, 결과, 의도만 말하면 그대로 사인으로 쓰지 말고, 피해자가 무엇을 하던 중 어떤 물건/설비 상태 변화 때문에 어떤 치명상 또는 사망 경로를 입었는지 구체화한다.
                - METHOD는 최종 정답으로 입력 가능한 사인 문구여야 한다. 단일 추상명사나 기능명은 실패다. "복부 자상 과다출혈", "목 압박 질식", "노출 전선 감전사", "독극물 중독", "두부 둔상"처럼 치명상과 사망 경로가 보여야 한다.
                - 자상/절단 계열 사건에서는 "찔렀다"와 "과다출혈"을 분리하지 말고 가능하면 한 사인 문구에 묶는다. 예: "복부 자상 과다출혈". 단서에는 피해자 루틴과 공격 기회, 부검상 상처와 출혈 소견을 나눠 배치한다.
                - METHOD와 finalTruthSummary에는 "잠금장치를 풀어둔", "잠금장치를 해제한", "잠금을 풀어", "해제하여", "떨어지게 하여", "추락하게 하여", "추락하여", "느슨하게 풀어둔 ...의", "...의 떨어지게 하여" 같은 번역투 문형을 쓰지 않는다. "빼", "부숴", "막아", "바꿔", "노출해", "찔러", "내리쳐", "조여"처럼 자연스러운 한국어 동사로 쓴다.
                - METHOD 단서에는 최소 두 요소를 나눠 포함한다: 피해자의 루틴/행동, 조작된 WEAPON 또는 설비 상태, 범인의 실행 행위, 사망에 이른 치명상/사망 경로. "잠금해제", "은폐", "조작"만으로 끝나는 METHOD는 만들지 않는다.
                - 승인된 WEAPON이 넓은 물건명이라면 부검/감식에서 드러나는 재질, 크기, 손상면, 잔류 성분, 표면 흔적, 무게감 중 최소 두 가지로 식별 가능한 물건이 되게 사건 안에서 좁힌다.
                - finalTruthSummary는 짧은 정답 요약이 아니라 진실 파일이다. 7~10문장으로, 피해자와 범인의 관계, 범행 전 갈등, 범인이 준비한 조작, 피해자가 사건 당일 어떤 행동을 했는지, WEAPON이 어떤 상태였는지, METHOD가 어떤 물리적 결과로 사망에 이르렀는지, 범행 후 은폐와 단서가 왜 남았는지를 크라임씬 사건 전말로 길게 쓴다.
                - finalTruthSummary에서 MOTIVE는 한 구절로 끝내지 않는다. 범인이 무엇을 잃을 위기였는지, 피해자가 어떤 증거/결정/폭로로 그 위기를 현실화하려 했는지, 협상·은폐·회유가 왜 실패했는지, 그래서 범인이 살인을 선택했다고 플레이어가 납득할 수 있는 심리적·현실적 압박을 2~3문장으로 설명한다.
                - "관광지 개발 이권 다툼", "소유권 분쟁", "계약 갈등" 같은 명목만 쓰고 끝내지 않는다. 누가 어떤 권한, 돈, 평판, 직위, 지역 이해관계, 가족/조직 책임을 잃게 되는지까지 구체화한다.
                - finalTruthSummary는 "정답은 CULPRIT, WEAPON, MOTIVE, METHOD다"처럼 목록으로 끝내지 않는다. 사건의 전 과정이 시간 순서로 읽히는 완결된 범죄 서사여야 한다.
                - fictionSynopsis는 경로 설명이 아니라 크라임씬 사건 줄거리다. 피해자 신원, 시신/사건 발견 상황, 밀접한 용의자 3명, 각자의 이해관계, 은폐된 갈등, 수사해야 할 핵심 의문을 포함하되 직접적인 사망 방식은 숨긴다.
                - fictionSynopsis에서 최종 범인 이름을 직접 노출하면 정답이 새므로, 해당 인물은 displayName 대신 suspect alias나 relationToVictim의 역할/관계 표현으로 언급한다. 다른 용의자도 가능하면 이름보다 역할/관계 중심으로 균형 있게 소개한다. "핵심 단서" 같은 마스킹 문구를 인물명 자리에 쓰지 않는다.
                - fictionSynopsis는 플레이어가 사건을 시작하게 만드는 공개 개요이지 정답 단서가 아니다. 승인된 최종 키워드(CULPRIT, WEAPON, MOTIVE, METHOD)를 명시적으로 알아낼 수 있는 문장을 쓰지 않는다.
                - fictionSynopsis에는 사망의 직접 원인, 정확한 흉기명, 흉기의 독특한 물리 특성, 범행 조작 순서, 특정 용의자를 확정하는 증거, 동기를 확정하는 문서/계약/협박 내용을 밝히지 않는다.
                - fictionSynopsis의 발견 상황은 "쓰러진 채 발견", "외상이 보임", "현장이 어지럽혀짐", "일부 물건이 사라짐/흐트러짐"처럼 모호한 관찰까지만 허용한다. "목이 졸린 흔적", "칼에 찔린 상처", "독극물 냄새", "추락으로 인한 골절", "특정 도구가 남아 있었다"처럼 METHOD나 WEAPON을 좁히는 표현은 금지한다.
                - fictionSynopsis에서 용의자 3명은 모두 그럴듯한 이해관계를 가진 인물로 균형 있게 소개한다. 한 사람만 접근 권한, 알리바이 모순, 피해자와의 결정적 갈등, 증거 소지 사실을 동시에 갖게 쓰지 않는다.
                - 정답을 좁히는 정보는 missions[2~9].rewardClue와 evidences에서 단계적으로 공개한다. fictionSynopsis는 "무엇을 조사해야 하는가"만 제시하고 "무엇이 정답인가"를 추론 가능한 수준까지 설명하지 않는다.
                - fictionSynopsis에는 "북창동먹자골목에서 흔적이 발견되었습니다"처럼 장소 나열형 문장을 쓰지 않는다. 사건은 특정 시설 내부, 사무실, 작업 공간, 회의실, 통로, 계단, 창고, 설비실, 무대 뒤, 점포 뒤편 같은 허구의 사건 공간에서 벌어진다.
                - actualHistorySummary는 TourAPI/외부조사에서 온 실제 장소 해설이다. 허구 사건의 전말을 반복하지 말고, storyAnchors/historicalContext에 담긴 장소의 실제 역사 사건, 관련 인물, 제도적 의미, 건축적 특징, 행정/상업/문화 기능, 보존 가치, 시대적 배경, 지역적 맥락을 5~7문장으로 설명한다.
                - actualHistorySummary에는 "본 사건은 직접적인 역사 사건을 다루지 않습니다", "직접 역사 사건이 아니라", "허구 사건입니다", "모티브로 삼았습니다" 같은 방어적 문구를 쓰지 않는다. 사용자가 알아야 할 것은 실제 장소가 어떤 특성을 가진 곳인지다.
                - storyAnchors/historicalContext에 순정효황후 윤씨 친가처럼 특정 인물, 가문, 정치 사건, 왕실/관청/상업 사건, 독립운동, 재난, 분쟁, 재판, 처형, 의례, 소유권 변동이 명확하면 그것을 가장 강한 모티브로 삼는다. "실제 장소성과 지역적 분위기를 배경 모티브로 구성되었습니다"처럼 장소 분위기만 말하는 문장으로 대체하지 않는다.
                - actualHistorySummary는 명확한 역사 사건이 있으면 그 사건의 인물·갈등·결과를 먼저 설명하고, 마지막 1~2문장에서 그 역사적 갈등이 게임의 권한, 이동 동선, 건물 구조, 물건의 쓰임, 지역 갈등 같은 미스터리 소재로 어떻게 연결되는지 자연스럽게 설명한다. 명확한 사건이 없을 때만 장소의 특성을 먼저 설명한다.
                - actualHistorySummary에서 "중요한 증거", "결정적 단서", "물증"처럼 허구 사건의 증거 카드처럼 읽히는 표현을 쓰지 않는다. 실제 유물이나 시설 조각을 설명할 때도 역사적 특징/보존 흔적으로만 설명한다.
                - finalTruthSummary에서 특정 물건을 사건 해결의 핵심 증거처럼 다루려면 같은 물건이 missions[2~9].rewardClue와 evidences 중 적어도 하나에 먼저 등장해야 한다. 단서/증거 카드에 없는 물건을 최종 해설에서 새 증거처럼 추가하지 않는다.
                - actualHistorySummary에 깨진 돌절구 조각, 비석 조각, 오래된 장부, 봉인, 문양, 도구, 시설 일부처럼 눈에 띄는 실제 유물/구조물이 나오면 그것은 역사 해설용 소재다. 그 물건을 finalTruthSummary에서 사건 해결 증거로 쓰려면 먼저 WEAPON 또는 METHOD 단서와 evidence에 같은 물건의 사건상 흔적을 넣어야 한다.
                - 최종 해설에 새 물건을 갑자기 추가하지 않는다. 플레이어가 본 missions[2~9].rewardClue와 evidences만으로 CULPRIT, WEAPON, MOTIVE, METHOD를 설명할 수 있어야 한다.
                - episodeTitle, fictionSynopsis, missionDescription, storyText에는 "미션메모", "제목", "보상 단서", "rewardClue" 같은 제작 메타 문구를 쓰지 않는다.
                - missions는 10개다. 1번 START, 2~9번 ANSWER_HINT, 10번 FINAL.
                - suspects는 정확히 3명이다. 세 명 모두 displayName, alias, shortDescription, relationToVictim, suspiciousPoint, alibiSummary를 채운다. 세 명 모두 피해자와의 관계, 알리바이, 의심 지점이 서로 달라야 한다.
                - Suspect design must support elimination, not equal suspicion. One suspect is the real culprit. The other two are plausible red herrings at first, but their alibi, role, or later evidence must give players a reason to rule them out.
                - Do not make all three suspects look equally guilty in rewardClue or evidence text. Non-culprit suspects may have motive-like pressure, but they must not also have matching opportunity, weapon access, and method knowledge.
                - The two CULPRIT rewardClues must narrow the culprit by opportunity and contradiction. They should point to one unnamed suspect profile through access, timing, movement, or alibi inconsistency.
                - WEAPON, MOTIVE, and METHOD rewardClues must identify the object, reason, and cause-of-death chain. They must not introduce new suspicious acts for every suspect or imply that all three could have committed the murder.
                - WEAPON clue generation is prompt-first, not fallback. Read approvedFinalAnswers.WEAPON and write both WEAPON rewardClues so the player can infer that exact object from category, everyday use, and physical traits without seeing the answer word itself.
                - WEAPON-1 must give the weapon's parent category and normal use. If WEAPON is "톱날", the clue should include a word like "공구" and the idea of a serrated/cutting edge. If WEAPON is "칼", the clue should mention an everyday cutting use such as cutting fruit, food, rope, or paper, and may call it a kitchen tool or cutting tool.
                - WEAPON-2 must give forensic narrowing details: blade/edge shape, teeth, material, weight, residue, broken surface, handle mark, position change, or wound/trace correspondence.
                - Do not write useless WEAPON clues such as "흉기가 있었을 가능성이 있다", "중요한 물증이 발견되었다", "현장 분위기가 평소와 달랐다", or a clue that only explains motive/alibi/method.
                - 2~9번 rewardClue는 각각 구체적인 증거 문장이어야 한다. 지문, 출입 흔적, CCTV 공백, 물증 상태, 분석 결과, 소유/거래/책임 관계, 목격 진술처럼 다양한 수사 자료로 쓴다.
                - 2~9번 rewardClue는 1문장, 45~90자 안팎으로 압축한다. 내용은 구체적으로 유지하되 배경 설명, 감정 묘사, 장소 분위기, 결론 반복은 빼고 관찰 사실/분석 결과만 남긴다.
                - rewardClue에 정답 값을 그대로 쓰지 않는다. 특히 범인 이름, 흉기명, 동기 문구, 방법 문장을 직접 노출하지 않는다.
                - Keep suspect names out of rewardClue unless the clue is explicitly about excluding a non-culprit. Prefer role-neutral wording such as "the person with access to the locked area" or "the alibi that conflicts with the camera gap".
                - evidences는 8개이며 sourceMissionOrder 2~9에 각각 연결한다.
                - "단순 사고", "반복되는 숫자", "방향 표식", "최종 장소를 찾아라", "장소를 비교하라", "TourAPI", "RAG", "Kakao" 같은 표현은 쓰지 않는다.

                단서 슬롯 분리 규칙:
                - CULPRIT 단서는 "누가 접근 가능했는가", "누구의 알리바이가 깨지는가"만 답한다. 허용 정보는 접근 권한, 시간대, 동선, CCTV 공백, 지문/출입기록 대조다. 금지 정보는 흉기의 성분 분석, 동기의 손실 규모, 범행 순서 전체다.
                - WEAPON 단서는 "무엇이 살해 도구로 쓰였는가"만 답한다. 허용 정보는 부검 결과서, 감정서, 현장 감식표처럼 물건의 종류를 좁히는 물리적 흔적, 상처 형태, 압흔/절단면/골절/화상/질식 흔적, 손상, 재질, 성분, 무게감, 날/모서리/표면 형태, 위치 변화, 사용 흔적, 피해자 상처/사망 원인과의 대응이다. 금지 정보는 특정 용의자의 알리바이, 협박 메시지, 채무/계약/평판 갈등, 범행 조작 순서 전체다.
                - MOTIVE 단서는 "왜 죽여야 했는가"만 답한다. 허용 정보는 피해자가 범인에게 만든 손실, 폭로 위험, 책임 추궁, 이권 상실, 계약/평판/직위 압박이다. 금지 정보는 흉기의 손상/성분만 설명하는 문장, 범행 순서, CCTV 동선만 설명하는 문장이다.
                - METHOD 단서는 화면상 "사인" 단서이며 "어떤 치명상/사망 경로였는가"를 답한다. 허용 정보는 피해자 루틴, 사건 직전 행동, 접근 경로, 조작 순서, 시간표, 부검상 치명상, 출혈/질식/감전/익사/압사 등 물리적 사망 과정이다. 금지 정보는 동기만 설명하는 문장, 흉기 성분만 설명하는 문장, 범인을 바로 특정하는 인명 노출이다.
                - 각 rewardClue는 자기 targetKeywordType의 질문 하나만 답한다. 한 단서 안에서 범인, 흉기, 동기, 사인을 동시에 설명하지 않는다.
                - WEAPON 단서가 부실해지지 않도록 order 4와 order 5에는 반드시 부검/감정/감식 관점의 상처 형태, 파손면, 재질, 성분, 무게감, 날/모서리/표면 형태, 위치 변화, 사용 흔적 중 서로 다른 두 종류 이상의 정보를 나눠 넣는다.
                - MOTIVE 단서가 부실해지지 않도록 order 6과 order 7에는 반드시 손실 또는 폭로 위험과 범인이 받는 압박의 증거를 나눠 넣는다.
                - METHOD 단서가 부실해지지 않도록 order 8과 order 9에는 반드시 피해자 루틴 또는 사건 직전 행동과 조작/공격 기회 또는 부검상 치명상/사망 경로를 나눠 넣는다.

                사건 구성 품질:
                - 승인된 METHOD가 독살/오염/접촉이 아니라면 독성, 오염, 잉크, 약품, 피부 접촉, 호흡기 질환을 새로 추가하지 않는다.
                - 승인된 WEAPON 또는 METHOD에 없는 별도 도구, 별도 약품, 별도 화학물질을 새로 만들지 않는다.
                - 사망 방식은 승인된 METHOD에 맞춘다. 추락, 교살, 익사, 충돌, 감금/동사, 폭발, 감전, 알레르기, 둔기 가격, 자상, 독살 중 METHOD가 암시하는 방식을 선택해 사건 전체에 일관되게 반영한다.
                - METHOD가 짧거나 압축된 사인 표현이어도, finalTruthSummary와 단서에서는 "피해자가 무엇을 하던 중", "WEAPON의 어떤 상태가", "범인이 무엇을 실행했는지", "어떤 치명상/사망 경로로 이어졌는지"를 구체적으로 설명한다.
                - 역사/지역 배경은 사건의 동기, 숨긴 이해관계, 갈등의 종류, 물건의 성격을 정하는 소재로만 쓴다. 실제 역사 사건을 살인 사건처럼 꾸미지 않는다.
                - 직접적인 사건 앵커보다 장소 설명이 많은 경우에도 지역의 시대성, 상업 변화, 보존 대상, 이동 동선, 관광화, 오래된 건물 재사용, 지형과 시설 구조 같은 구체적 배경을 골라 갈등으로 바꾼다.

                JSON 작성 규칙:
                - 미션과 증거 카드는 서로 같은 말을 반복하지 않는다.
                - 각 rewardClue는 하나의 관찰 사실이나 분석 결과만 말하되, 최종 키워드를 직접 말하지 않고도 플레이어가 CULPRIT, WEAPON, MOTIVE, METHOD 중 해당 슬롯의 후보를 실제로 좁힐 수 있을 만큼 구체적이어야 한다. 단순한 분위기, 사건문구, 장소 설명, "무언가 수상하다" 수준의 문장은 실패다.
                - 각 rewardClue는 길게 설명하지 않는다. 쉼표로 사실을 두세 개 이상 나열하지 말고, 핵심 관찰 1개와 그 의미 1개만 담는다.
                - Across the 8 rewardClues, the deduction flow must be: first identify who had the real opportunity, then identify weapon, motive, and method. Do not distribute equally incriminating facts across all three suspects.
                - At least two clue/evidence pairs should help eliminate red herrings by showing a claimed alibi is consistent, their access was impossible, or their suspicious behavior explains a lesser secret unrelated to the murder.
                - evidences[i].textSummary는 같은 sourceMissionOrder의 rewardClue를 더 구체화하되, 최종 정답 값을 직접 말하지 않는다.
                - START 미션은 사건 파일 개봉과 조사 기준 안내다. FINAL 미션은 최종 정답 입력 지점이며 단서를 제공하지 않는다.
                - missionDescription은 "8개의 조사 단서로 범인, 흉기, 동기, 사인을 검증한다"는 플레이 목표를 짧게 쓴다.

                미션 슬롯:
                - order 2: targetKeywordType CULPRIT, CULPRIT-1 접근 권한 단서. 범인 후보를 3명에서 2명으로 줄인다.
                - order 3: targetKeywordType CULPRIT, CULPRIT-2 알리바이 모순 단서. 범인 후보를 2명에서 1명으로 줄인다.
                - order 4: targetKeywordType WEAPON, WEAPON-1 부검 결과서/현장 감식표형 단서. 상처 형태, 압흔, 골절, 절단면, 잔류 성분, 표면 흔적으로 물건의 종류를 좁힌다.
                - order 5: targetKeywordType WEAPON, WEAPON-2 감정서/물증 분석형 단서. 재질, 무게감, 파손면, 위치 변화, 사용 흔적으로 흉기를 확정한다.
                - order 6: targetKeywordType MOTIVE, MOTIVE-1 피해자가 범인에게 만든 손실/폭로 위험.
                - order 7: targetKeywordType MOTIVE, MOTIVE-2 범인이 살인을 선택할 수밖에 없었던 압박의 증거.
                - order 8: targetKeywordType METHOD, METHOD-1 피해자의 루틴과 사건 직전 행동.
                - order 9: targetKeywordType METHOD, METHOD-2 공격/조작 기회와 부검상 치명상/사망 경로.

                반환 JSON 필수 필드:
                episodeTitle, subtitle, genre, selectedGenre, fictionSynopsis, missionDescription,
                finalTruthSummary, actualHistorySummary, finalQuestion, missions, suspects, evidences.

                Context:
                """ + buildStoryGenerationContext(request);
    }

    private static String buildStoryGenerationContext(AiEpisodeDraftRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("area: ").append(safePromptText(request == null ? "" : request.getArea())).append('\n');
        builder.append("era: ").append(safePromptText(request == null ? "" : request.getEra())).append('\n');
        builder.append("theme: ").append(safePromptText(request == null ? "" : request.getTheme())).append('\n');
        builder.append("playTime: ").append(safePromptText(request == null ? "" : request.getPlayTime())).append('\n');
        appendApprovedAnswers(builder, request);
        appendTourApiContext(builder, request);
        builder.append("missionOrders:\n");
        builder.append("- 1 START: 사건 파일, 피해자 발견 상황, 조사 규칙\n");
        builder.append("- 2 ANSWER_HINT CULPRIT-1: 접근 권한 단서. 후보를 3명에서 2명으로 줄인다.\n");
        builder.append("- 3 ANSWER_HINT CULPRIT-2: 알리바이 모순 단서. 후보를 2명에서 1명으로 줄인다.\n");
        builder.append("- 4 ANSWER_HINT WEAPON-1: 부검 결과서/현장 감식표형 단서. 상처 형태, 압흔, 골절, 절단면, 잔류 성분, 표면 흔적으로 물건의 종류를 좁힌다.\n");
        builder.append("- 5 ANSWER_HINT WEAPON-2: 감정서/물증 분석형 단서. 재질, 무게감, 파손면, 위치 변화, 사용 흔적으로 흉기를 확정한다.\n");
        builder.append("  WEAPON clue writing rule: use approvedFinalAnswers.WEAPON to derive category/use/physical traits. For 톱날, mention 공구 or serrated cutting edge; for 칼, mention everyday cutting use such as fruit/food/rope/paper. Do not reveal the exact weapon word.\n");
        builder.append("- 6 ANSWER_HINT MOTIVE-1: 피해자가 범인에게 만든 손실/폭로 위험.\n");
        builder.append("- 7 ANSWER_HINT MOTIVE-2: 범인이 살인을 선택할 수밖에 없었던 압박의 증거.\n");
        builder.append("- 8 ANSWER_HINT METHOD-1: 피해자의 루틴과 사건 직전 행동.\n");
        builder.append("- 9 ANSWER_HINT METHOD-2: 조작 순서/접근 경로/실행 가능성 교차 검증과 사망에 이른 물리적 과정.\n");
        builder.append("- 10 FINAL: all 8 investigation missions cleared 후 정답 입력\n");
        return builder.toString();
    }

    private static void appendTourApiContext(StringBuilder builder, AiEpisodeDraftRequest request) {
        TourApiPlanContext context = TourApiPlanInputExtractor.extract(request);
        builder.append("storyAnchors:\n");
        if (context.storyAnchors().isEmpty()) {
            builder.append("- 지역/시대/공간 성격을 바탕으로 사건 배경을 만든다.\n");
        } else {
            context.storyAnchors().forEach(anchor -> builder.append("- ").append(safePromptText(anchor)).append('\n'));
        }
        builder.append("historicalContext:\n");
        builder.append(safePromptText(context.historicalContext())).append('\n');
        builder.append("forbiddenSourcePolicy:\n");
        builder.append("- Kakao Local, 주변 후보, 현장 검수 메모, 실제 route place names are not story material.\n");
        builder.append("- Story and clues must be written before assigning real map places.\n");
    }

    private static void appendApprovedAnswers(StringBuilder builder, AiEpisodeDraftRequest request) {
        Map<String, String> approved = approvedAnswers(request);
        builder.append("approvedFinalAnswers:\n");
        for (String slot : SLOT_IDS) {
            builder.append("- ").append(slot).append(": ").append(safePromptText(approved.get(slot))).append('\n');
        }
    }

    private static Map<String, String> approvedAnswers(AiEpisodeDraftRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        SLOT_IDS.forEach(slot -> result.put(slot, ""));
        if (request != null && request.getFinalAnswerKeywordItems() != null) {
            for (AiEpisodeDraftRequest.AnswerKeywordInput item : request.getFinalAnswerKeywordItems()) {
                String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
                if (SLOT_IDS.contains(slot)) putIfNotBlank(result, slot, answerKeywordValue(item));
            }
        }
        if (request != null && request.getFinalAnswers() != null) {
            putIfNotBlank(result, "CULPRIT", request.getFinalAnswers().getCulprit());
            putIfNotBlank(result, "WEAPON", request.getFinalAnswers().getWeapon());
            putIfNotBlank(result, "MOTIVE", request.getFinalAnswers().getMotive());
            putIfNotBlank(result, "METHOD", request.getFinalAnswers().getMethod());
        }
        return result;
    }

    private static String answerKeywordValue(AiEpisodeDraftRequest.AnswerKeywordInput item) {
        if (item == null) return "";
        String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
        String value = "CULPRIT".equals(slot) && !blank(item.getPersonName()) ? item.getPersonName() : defaultIfBlank(item.getKeyword(), item.getSourceText());
        return "CULPRIT".equals(slot) ? splitNameRole(value).name() : value;
    }

    private static void putIfNotBlank(Map<String, String> values, String key, String value) {
        if (!blank(value)) values.put(key, value.trim());
    }

    private static NameRole splitNameRole(String value) {
        String text = trim(value);
        if (blank(text)) return new NameRole("", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\s*([가-힣]{2,4})\\s*\\(([^)]+)\\)\\s*$")
                .matcher(text);
        if (matcher.matches()) {
            return new NameRole(matcher.group(1).trim(), matcher.group(2).trim());
        }
        return new NameRole(text, "");
    }

    private record NameRole(String name, String role) {}

    private static String safePromptText(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 700) {
            return normalized.substring(0, 700);
        }
        return normalized;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
