# 관통프로젝트 AI 필수 활용 산출물

## 0. 최종 산출물 점검 결과

| 제출 요구 항목 | 현재 산출물 | 점검 결과 | 보강 내용 |
| --- | --- | --- | --- |
| 설계 문서 | `01_REQUIREMENTS_AND_COVERAGE.md`, `02_DESIGN_DOCUMENT.md`, `03_WBS_AND_GANTT.md`, `04_SCREEN_DESIGN.md` | 충족 | 요구사항, Use-Case, ERD, WBS, Gantt, 화면 흐름 포함 |
| 구현 결과물 | `backend/src/main/java`, `frontend/src`, `backend/src/main/resources/schema.sql` | 충족 | Back-End, Front-End, DB Schema 위치가 제출 인덱스에 명시됨 |
| 결과 정리 자료 | `08_FINAL_REPORT.md`, `09_PRESENTATION_DRAFT.md`, `07_EXTERNAL_API_AND_AI_USAGE.md` | 충족 | 최종 보고서, 발표 원고, 외부 API/AI 활용 내용 포함 |
| AI 필수 활용 유형 선택 | 본 문서 1장 | 충족 | 유형 2, LLM API 추가기능 구현으로 명시 |
| 주요 프롬프트 캡처본 | 본 문서 3장, `GeminiDraftPromptBuilder.java` | 충족 | 캡처 대상 파일, 핵심 프롬프트 요약, 실제 코드 라인 기준 제공 |
| LLM 활용 전후 비교 | 본 문서 4장 | 충족 | 수작업 작성 방식과 Gemini API 적용 후 차이를 표로 제시 |
| AI 기능 구현 코드 캡처본 | 본 문서 5장, 7장 | 충족 | API 호출, 서비스, 컨트롤러 캡처 대상과 라인 기준 제공 |

최종 판정: **AI 필수 활용 산출물은 유형 2 기준으로 제출 가능하다.**  
보강 방향은 발표 자료 수정이 아니라, 이 문서 안에서 캡처 대상과 제출 근거를 명확히 하는 방식으로 처리했다.

## 1. 선택 유형

선택 유형: **2. LLM API를 활용한 추가기능 구현**

적용 기능: **관리자용 Gemini 기반 에피소드 초안 생성/검증 기능**

Operation Seoul은 관리자가 10개 장소 후보와 권역, 시대, 테마, 플레이 시간, 최종 정답 키워드를 입력하면 Gemini API가 미션 파일 초안을 생성한다. 생성 결과는 즉시 공개하지 않고 검증 API와 관리자 검수를 거쳐 DRAFT 상태로 저장한다.

## 2. 기능 흐름

```mermaid
flowchart LR
    A[관리자 입력\n권역/시대/테마/장소 10개] --> B[/api/v1/admin/episodes/ai-draft/plan]
    B --> C[Gemini 정답 키워드/장르 계획]
    C --> D[/api/v1/admin/episodes/ai-draft/gemini]
    D --> E[Gemini 에피소드 초안 JSON]
    E --> F[/api/v1/admin/episodes/ai-draft/validate]
    F --> G[검증 결과 확인]
    G --> H[/api/v1/admin/episodes/ai-draft/save]
    H --> I[DRAFT 에피소드 저장]
```

## 3. 발표 포함 산출물 1: 주요 프롬프트 캡처본

캡처 대상 파일:

- `backend/src/main/java/com/operation/seoul/admin/episode/service/GeminiDraftPromptBuilder.java`
- 주요 라인: `build(...)`, `buildStoryGenerationContext(...)`, `appendApprovedAnswers(...)`

캡처용 핵심 프롬프트 요약:

```text
JSON만 반환한다. 모든 문장은 한국어로 작성한다.
장르는 항상 "범죄 미스터리"다.

역할:
- 실제 장소 안내문 작성자가 아니라, 파라노말 사건 작가처럼 사건을 구성한다.
- 승인된 최종 정답 4개(CULPRIT, WEAPON, MOTIVE, METHOD)를 바탕으로 하나의 완성된 사건을 만든다.
- 실제 장소명, 주소, 상호명, 주변 후보, 이동 동선은 사건 증거로 직접 사용하지 않는다.

정답 계약:
- approvedFinalAnswers.CULPRIT는 잠긴 값이며 이름을 바꾸거나 별칭으로 대체하지 않는다.
- suspects 중 정확히 1명은 approvedFinalAnswers.CULPRIT와 완전히 일치해야 한다.
- finalTruthSummary에는 CULPRIT, WEAPON, MOTIVE, METHOD가 모두 설명되어야 한다.

미션 구성:
- missions는 10개다.
- 1번은 START, 2~9번은 ANSWER_HINT, 10번은 FINAL이다.
- 2~3번은 범인 단서, 4~5번은 흉기 단서, 6~7번은 동기 단서, 8~9번은 사인/방법 단서다.
- rewardClue는 최종 정답 단어를 직접 노출하지 않고 추론 가능해야 한다.

반환 JSON 필수 필드:
episodeTitle, subtitle, genre, selectedGenre, fictionSynopsis, missionDescription,
finalTruthSummary, actualHistorySummary, finalQuestion, missions, suspects, evidences.
```

입력 컨텍스트 프롬프트 구성:

```text
area: {관리자 입력 권역}
era: {관리자 입력 시대}
theme: {관리자 입력 테마}
playTime: {관리자 입력 플레이 시간}
approvedFinalAnswers:
- CULPRIT: {승인된 범인}
- WEAPON: {승인된 흉기}
- MOTIVE: {승인된 동기}
- METHOD: {승인된 사인/방법}
storyAnchors:
- {TourAPI/관리자 입력 기반 이야기 앵커}
historicalContext:
{장소/역사 맥락 요약}
missionOrders:
- 1 START
- 2 ANSWER_HINT CULPRIT-1
- 3 ANSWER_HINT CULPRIT-2
- 4 ANSWER_HINT WEAPON-1
- 5 ANSWER_HINT WEAPON-2
- 6 ANSWER_HINT MOTIVE-1
- 7 ANSWER_HINT MOTIVE-2
- 8 ANSWER_HINT METHOD-1
- 9 ANSWER_HINT METHOD-2
- 10 FINAL
```

## 4. 발표 포함 산출물 2: LLM 활용 전후 비교

| 구분 | LLM 활용 전 | LLM 활용 후 |
| --- | --- | --- |
| 콘텐츠 작성 | 관리자가 에피소드 제목, 줄거리, 장소별 미션, 단서, 용의자, 증거를 직접 작성 | Gemini가 구조화된 JSON 초안을 생성 |
| 정답 일관성 | 범인/흉기/동기/사인과 단서가 어긋날 수 있음 | `approvedFinalAnswers` 계약으로 정답 슬롯을 고정 |
| 단서 설계 | 단서가 너무 직접적이거나 추상적일 수 있음 | 미션 순서별 단서 역할을 프롬프트에서 강제 |
| 제작 속도 | 10개 장소 기반 에피소드 작성에 많은 시간이 필요 | 관리자 입력 후 초안 생성, 검증, 수정 중심으로 단축 |
| 품질 검증 | 수동 검토 의존 | `validateDraft`와 guardrail로 구조/정답/단서 품질 점검 |
| 공개 절차 | 작성 후 바로 반영될 위험 | DRAFT 저장 후 publish readiness와 관리자 검수 후 공개 |

정리:

- LLM은 최종 공개 콘텐츠를 자동 배포하지 않는다.
- LLM은 관리자용 초안 생성 보조 도구로 사용된다.
- 서비스 품질과 게임 규칙 보호를 위해 검증 API와 DRAFT 상태를 둔다.

## 5. 발표 포함 산출물 3: AI 기능 구현 코드 캡처본

### 5.1 Gemini API 호출 코드

캡처 대상:

- `backend/src/main/java/com/operation/seoul/admin/episode/service/GeminiContentClient.java`
- 주요 라인: `API_BASE_URL`, `generateContent(...)`, `restTemplate.postForObject(...)`, Gemini 응답 파싱

코드 설명:

```text
GeminiContentClient.generateContent(prompt, model, apiKey)
- Gemini generateContent API URL 생성
- prompt를 contents.parts.text 형식으로 전송
- candidates[0].content.parts[0].text를 추출
- API 요청 실패/응답 파싱 실패를 ApiException으로 변환
```

### 5.2 관리자 AI 서비스 코드

캡처 대상:

- `backend/src/main/java/com/operation/seoul/admin/episode/service/AdminEpisodeGeminiService.java`
- 주요 라인: `createAnswerPlan(...)`, `createGeminiDraft(...)`, `validateDraft(...)`, `callGemini(...)`, `ensureApiKey(...)`

코드 설명:

```text
createAnswerPlan(...)
- 장소 10개 입력 검증
- Gemini API 키 확인
- 최종 정답 키워드 계획 생성

createGeminiDraft(...)
- 최종 정답 계약 정규화/검증
- GeminiDraftGenerator로 초안 생성
- DraftResponseAssembler와 validateDraft로 검증 결과 포함

validateDraft(...)
- 장르, 필수 구조, 단서 규칙, 정답 계약을 검증
```

### 5.3 관리자 API 엔드포인트 코드

캡처 대상:

- `backend/src/main/java/com/operation/seoul/admin/episode/controller/AdminEpisodeController.java`
- 주요 라인: `/ai-draft/plan`, `/ai-draft/gemini`, `/ai-draft/validate`, `/ai-draft/save`

API 설명:

| Method | Path | 역할 |
| --- | --- | --- |
| POST | `/api/v1/admin/episodes/ai-draft/plan` | Gemini가 장르와 최종 정답 키워드 계획 제안 |
| POST | `/api/v1/admin/episodes/ai-draft/gemini` | Gemini 에피소드 초안 생성 |
| POST | `/api/v1/admin/episodes/ai-draft/validate` | AI 초안 검증 |
| POST | `/api/v1/admin/episodes/ai-draft/save` | 검수된 AI 초안을 DRAFT 에피소드로 저장 |

## 6. 제출 시 캡처 체크리스트

발표 또는 보고서에 캡처 이미지를 넣어야 할 경우 다음 3장을 사용하면 된다.

1. 주요 프롬프트 캡처  
   `GeminiDraftPromptBuilder.java`의 `JSON만 반환한다`, `approvedFinalAnswers`, `missionOrders`, `반환 JSON 필수 필드` 부분

2. LLM 활용 전후 비교 캡처  
   이 문서의 “4. 발표 포함 산출물 2: LLM 활용 전후 비교” 표

3. AI 기능 구현 코드 캡처  
   `GeminiContentClient.java`의 `generateContent(...)`와 `AdminEpisodeController.java`의 `/ai-draft/gemini` 엔드포인트

## 7. 실제 코드 캡처 기준 라인

아래 라인은 현재 코드 기준으로 확인한 캡처 시작 지점이다. IDE에서 해당 파일을 열고 아래 라인 주변을 캡처하면 된다.

| 캡처 목적 | 파일 | 시작 라인 | 캡처 범위 |
| --- | --- | ---: | --- |
| 주요 프롬프트 | `backend/src/main/java/com/operation/seoul/admin/episode/service/GeminiDraftPromptBuilder.java` | 16 | `build(...)` 메서드의 JSON 반환 규칙, 정답 계약, 미션 구성, 필수 JSON 필드 |
| Gemini API 호출 | `backend/src/main/java/com/operation/seoul/admin/episode/service/GeminiContentClient.java` | 28 | `generateContent(...)`, API URL, 요청 body, 응답 파싱, 예외 처리 |
| AI 초안 생성 서비스 | `backend/src/main/java/com/operation/seoul/admin/episode/service/AdminEpisodeGeminiService.java` | 46 | `createGeminiDraft(...)`, 최종 정답 계약 검증, Gemini 호출, 검증 결과 조립 |
| 관리자 API 엔드포인트 | `backend/src/main/java/com/operation/seoul/admin/episode/controller/AdminEpisodeController.java` | 250 | `/ai-draft/gemini`, `/ai-draft/plan`, `/ai-draft/validate`, `/ai-draft/save` |

## 8. 제출용 요약 문구

발표나 보고서에 짧게 넣을 문구:

```text
본 프로젝트는 AI 필수 활용 유형 중 2번 'LLM API를 활용한 추가기능 구현'을 선택했다.
관리자 에피소드 생성 화면에서 Gemini API를 호출해 범죄 미스터리 에피소드 초안을 JSON으로 생성하고,
생성 결과는 검증 API와 관리자 검수를 거쳐 DRAFT 상태로 저장한다.
LLM 적용 전에는 관리자가 제목, 줄거리, 10개 미션, 단서, 용의자, 증거를 직접 작성해야 했지만,
적용 후에는 장소/정답 키워드 입력 기반 초안을 생성하고 검수 중심으로 제작 시간을 줄일 수 있다.
```

## 9. 최종 제출 체크

- 선택 유형이 명확한가: **예, 유형 2**
- 주요 프롬프트가 제시되어 있는가: **예, 3장**
- LLM 활용 전후 비교가 있는가: **예, 4장**
- AI 기능 구현 코드 캡처 대상이 있는가: **예, 5장과 7장**
- 실제 코드와 연결되는가: **예, GeminiContentClient, AdminEpisodeGeminiService, AdminEpisodeController**
- 발표 자료를 수정했는가: **아니오, 본 문서만 보강**
