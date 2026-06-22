# Codex Handoff: TourAPI 기반 Gemini 최종 키워드 생성

## 목적

관리자 AI 에피소드 생성의 `최종 정답 키워드 계획` 단계가 서버 하드코딩/폴백 템플릿이 아니라 TourAPI 및 외부 리서치로 보강된 장소의 역사/사건/문화 앵커를 기반으로 Gemini가 생성하도록 만드는 작업이다.

사용자가 문제로 확인한 반복 결과:

- `서민재 / 마취 성분이 섞인 향수병 / 비공개 계약 파기 은폐 / 향수병에 마취 성분을 넣어 피해자에게 분사`
- `윤서진 / 독성 잉크가 든 붓펜 / 위작 전시 의혹 은폐 / 독성 잉크가 든 붓펜으로 감정 확인 서명란을 오염시킴`
- `윤서진 / 독성 세척제가 든 붓 세척통 / 감정 결과 조작 은폐 / 붓 세척통에 독성 세척제를 넣어 피해자가 감정 도구를 정리하며 접촉하게 함`

위 값들은 Gemini가 TourAPI 앵커로 생성한 결과가 아니라 서버 `SERVER_TEMPLATE` 폴백 또는 프롬프트 예시 복사 성향에서 나온 값으로 간주해야 한다.

## 현재 정책

- `POST /api/v1/admin/episodes/ai-draft/plan`은 TourAPI 기반 Gemini 생성 단계다.
- 이 단계에서 `SERVER_TEMPLATE` 폴백 결과를 성공처럼 반환하면 안 된다.
- Gemini API 키가 없거나 Gemini 호출/파싱/품질 검증이 실패하면 에러로 실패해야 한다.
- 성공 응답의 키워드 `sourceType`은 `GEMINI`여야 한다.
- 응답에는 `tourApiStoryAnchors`가 포함되어야 하며, 관리자 UI에서 확인 가능해야 한다.

## 주요 변경 파일

### Backend

- `backend/src/main/java/com/operation/seoul/admin/episode/service/AdminEpisodeGeminiService.java`
  - `createAnswerPlan()`에서 `extractTourApiStoryAnchors(request)`를 호출한다.
  - `tourApiStoryAnchors`를 `AiEpisodePlanResponse`에 넣는다.
  - 키워드별 `sourceBasis`에 TourAPI 앵커를 넣는다.
  - `buildPlanPrompt()`에 다음 방향을 추가했다.
    - TourAPI story anchors에서 키워드를 도출한다.
    - 미술관/카페/시장/산/역 같은 일반 도메인 템플릿 선택을 금지한다.
    - 범인은 현대 가상 한국 인물이어야 한다.
    - `이몽룡`, `성춘향`, `홍길동`, `임꺽정`, `장보고`, `유관순`, `세종대왕`, `이순신`, `안중근`, `김구` 같은 역사/문학/공적 인물명 금지.
    - `혼란을 야기함`, `몰래 투여함`, `정신을 잃게 함`, `상태를 악화시킴` 같은 결과 중심 method 금지.
  - `answerPlanKeywords()`는 이제 Gemini 키가 없거나 Gemini 실패 시 서버 템플릿으로 fallback하지 않는다.
    - 키 없음: `GEMINI_API_KEY_MISSING`
    - Gemini 호출/파싱 등 일반 실패: `GEMINI_PLAN_FAILED`
    - Gemini 응답 품질 실패: `GEMINI_PLAN_INVALID`
  - `weakFinalAnswerKeyword()` 계열 검증에서 고전 인물명과 약한 method를 reject한다.
  - `deterministicAnswerKeywords()`와 `SERVER_TEMPLATE` 코드는 아직 남아 있지만, `createAnswerPlan()` 경로에서는 성공 fallback으로 쓰면 안 된다. 다른 내부 테스트/비상용 코드로만 간주한다.

- `backend/src/main/java/com/operation/seoul/admin/episode/dto/AiEpisodePlanResponse.java`
  - `private List<String> tourApiStoryAnchors;` 추가.

- `backend/src/test/java/com/operation/seoul/admin/episode/service/AdminEpisodeGeminiServiceTest.java`
  - Gemini 키 없음 시 `createAnswerPlan()`이 실패해야 한다.
  - TourAPI story anchor 추출이 장소명/주소를 포함하지 않아야 한다.
  - plan 프롬프트가 `TourAPI story anchors` 기반 생성을 지시해야 한다.
  - `이몽룡 / 오염된 죽염 안약 / 춘향가 위조본 유통 은폐 / 눈에 몰래 투여하여 혼란을 야기함`은 reject되어야 한다.

### Frontend

- `frontend/src/views/AdminEpisodesView.vue`
  - 키워드 계획 패널에 `TourAPI 사건/역사 앵커` 표시.
  - 키워드별 `sourceType` 및 `sourceBasis` 표시.
  - `sourceType=SERVER_TEMPLATE`가 포함되면 경고 표시.
  - 현재 정책상 정상 plan 응답에서는 이 경고가 나오면 안 된다. 나온다면 구버전 서버가 떠 있거나 다른 경로에서 폴백이 반환된 것이다.

## 검증 명령

```powershell
cd C:\Users\khsoo\Desktop\operation-seoul\backend
.\gradlew.bat test --tests com.operation.seoul.admin.episode.service.AdminEpisodeGeminiServiceTest

cd C:\Users\khsoo\Desktop\operation-seoul\frontend
npm run build
```

마지막 확인 결과:

- Backend 단일 테스트: 통과
- Frontend build: 통과

## 직접 QA 기준

관리자 화면에서 장소를 선택하고 `최종 정답 키워드 설계`를 실행한다.

성공 기준:

- 응답이 성공하면 키워드별 `sourceType`이 `GEMINI`여야 한다.
- `TourAPI 사건/역사 앵커`가 실제 리서치/장소 문맥으로 표시되어야 한다.
- `sourceBasis`에 해당 앵커가 보여야 한다.
- 결과가 특정 도메인 템플릿처럼 반복되면 안 된다.

실패 기준:

- `Gemini 생성이 아니라 서버 폴백 템플릿입니다...` 경고가 뜨면 QA 실패.
- `sourceType=SERVER_TEMPLATE`이면 QA 실패.
- `이몽룡`, `성춘향` 등 문학/역사 인물이 범인으로 나오면 실패.
- `혼란을 야기함`, `몰래 투여함` 같은 method가 나오면 실패.
- `마취 성분이 섞인 향수병`, `독성 잉크가 든 붓펜`, `독성 세척제가 든 붓 세척통` 같은 서버 템플릿 반복값이 나오면 실패.

## 다음 Codex가 이어서 할 일

1. 실제 로컬 서버가 최신 코드로 재시작되어 있는지 확인한다.
   - 사용자가 여전히 `SERVER_TEMPLATE` 경고를 본다면 구버전 서버가 떠 있거나, 배포/실행 프로필이 최신 빌드를 반영하지 않았을 가능성이 있다.
2. 실행 중인 Spring profile과 Gemini 설정을 확인한다.
   - `application-prod.properties`: `gemini.api.key=${GEMINI_API_KEY:}`
   - `application-local.properties`: 현재 하드코딩된 키처럼 보이는 값이 있다. 실제 유효 키인지 확인 필요.
   - 민감정보를 커밋하거나 문서에 추가 노출하지 말 것.
3. `createAnswerPlan()` 호출이 실패할 때 프론트에서 에러 메시지가 명확히 보이는지 QA한다.
4. 실제 Gemini 응답이 `GEMINI_PLAN_INVALID`로 자주 실패한다면 프롬프트를 더 구체화한다.
   - 단, 서버 템플릿 fallback을 되살리면 안 된다.
5. 필요하면 plan endpoint에 별도 debug field를 추가한다.
   - 예: `generatorType`, `model`, `failureReason`.
   - 단, API 키나 민감정보는 절대 노출하지 않는다.

## 주의사항

- TourAPI 장소명/주소 자체를 사건 발생 장소처럼 쓰면 안 된다.
- 장소는 배경 모티브이며, 사건은 가상의 실내/관계자 환경에서 일어나야 한다.
- 최종 장소는 정답 키워드가 아니다.
- 최종 정답 슬롯은 계속 `CULPRIT`, `WEAPON`, `MOTIVE`, `METHOD` 4개다.
- `finalAnswerKeywordItems`와 `finalAnswerKeywords` 호환 흐름이 남아 있으므로 수정 시 둘 다 확인한다.
