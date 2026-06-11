# Operation Korea Project Guide

이 문서는 프로젝트를 처음 보는 사람이 전체 구조와 핵심 흐름을 빠르게 이해하기 위한 입문용 문서입니다. 기존 `README.md`가 구현 상태와 API 목록을 넓게 정리한다면, 이 문서는 “무엇을 어디서 보면 되는지”와 “AI 사건파일 생성이 어떤 규칙으로 동작하는지”에 초점을 둡니다.

## 1. 프로젝트 한 줄 요약

Operation Korea는 실제 서울 장소 데이터를 기반으로 관리자가 사건파일형 야외 방탈출 에피소드를 만들고, 사용자가 지도 이동, 현장 퍼즐, 단서 수집, 최종 추리, 클리어 후 역사 해설까지 진행하는 웹 서비스입니다.

핵심 키워드:

- 위치 기반 야외 방탈출
- 사건파일/추리 게임
- TourAPI, Kakao Local 장소 후보
- Gemini 기반 관리자용 에피소드 초안 생성
- 실제 역사/문화 사실을 Fiction Mode와 Fact Mode로 분리
- 최종 장소 은닉, 단서 해금, 최종 정답 검증

## 2. 기술 스택

| 영역 | 사용 기술 |
| --- | --- |
| Frontend | Vue 3, Vue Router, Pinia, Axios, Vite |
| Backend | Spring Boot 4, Spring Security, JWT, MyBatis, MySQL |
| AI/외부 API | Gemini, TourAPI, Kakao Local, Kakao Map, Tmap |
| 주요 실행 환경 | Java 17, Node.js 20.19 이상 또는 22.12 이상 |

## 3. 루트 디렉터리 구조

```text
operation-seoul/
  backend/   Spring Boot API 서버
  frontend/  Vue/Vite 웹 클라이언트
  docs/      프로젝트 문서
  scripts/   릴리스/검증 스크립트
```

중요 파일:

- `backend/build.gradle`: 백엔드 의존성, Java 17 설정
- `backend/src/main/resources/application.properties`: 백엔드 환경변수 연결
- `frontend/package.json`: 프론트 실행/빌드 스크립트
- `frontend/src/router/index.js`: 프론트 전체 화면 라우팅
- `frontend/src/api/*Api.js`: 프론트 API 호출 래퍼
- `README.md`: 기존 전체 구현 상태/API 목록 문서

## 4. 사용자 경험 흐름

사용자는 아래 순서로 사건파일을 플레이합니다.

1. 로그인 또는 회원가입
2. 공개 에피소드 목록 확인
3. 에피소드 상세와 브리핑 확인
4. 지도 화면 진입
5. 표시된 조사 지점으로 이동
6. GPS 또는 개발 모드로 도착 판정
7. 장소 퍼즐 풀이
8. 퍼즐 보상으로 단서, 증거, 용의자, 메모 해금
9. 사건파일 화면에서 단서 조합
10. 숨겨진 실제 최종 장소 도착
11. 최종 추리 질문 진행
12. 최종 정답 입력
13. 클리어 처리
14. 클리어 리포트에서 실제 역사 해설 확인
15. 리뷰 작성

핵심 화면:

| 경로 | 역할 |
| --- | --- |
| `/intro` | 로그인/회원가입 |
| `/episodes` | 공개 사건파일 목록 |
| `/episodes/:episodeId` | 사건파일 상세 |
| `/episodes/:episodeId/briefing` | 시작 전 브리핑 |
| `/episodes/:episodeId/map` | 지도, 도착 판정, 퍼즐 진입 |
| `/episodes/:episodeId/case-file` | 단서/증거/용의자 확인 |
| `/episodes/:episodeId/deduction` | 최종 추리 |
| `/episodes/:episodeId/clear-report` | 클리어 리포트, 역사 해설, 리뷰 |
| `/admin/episodes` | 관리자 사건파일 생성/운영 |
| `/admin/users` | 관리자 회원 관리 |
| `/admin/reviews` | 관리자 리뷰 관리 |

## 5. 백엔드 주요 모듈

백엔드는 `com.operation.seoul` 아래 기능별 패키지로 나뉩니다.

| 패키지 | 역할 |
| --- | --- |
| `auth` | 로그인, JWT, 현재 사용자 확인, 권한 처리 |
| `admin.episode` | 관리자 사건파일 생성/수정/검수/AI 초안 생성 |
| `episode` | 사용자 사건파일 조회, 지도, 도착, 퍼즐, 최종 추리, 클리어 |
| `casefile` | 사건파일 탭, 단서/증거/용의자 자료 |
| `game` | legacy 또는 보조 게임/AI 코스 관련 기능 |
| `location` | 지역/장소 관련 기능 |
| `review` | 리뷰 작성/조회/관리 |
| `favorite` | 에피소드 찜 |
| `plan` | 사용자 플레이 일정 |
| `group` | 그룹 생성/가입 |
| `ranking` | 클리어 랭킹 |
| `challenge` | 챌린지 |
| `recommendation` | 사용자 추천 |
| `coaching` | 플레이 기록 기반 코칭 |
| `global` | 공통 설정, 응답, 예외 처리 |

가장 먼저 볼 백엔드 파일:

- `backend/src/main/java/com/operation/seoul/admin/episode/controller/AdminEpisodeController.java`
- `backend/src/main/java/com/operation/seoul/admin/episode/service/AdminEpisodeService.java`
- `backend/src/main/java/com/operation/seoul/admin/episode/service/AdminEpisodeGeminiService.java`
- `backend/src/main/java/com/operation/seoul/episode/service/EpisodePlayService.java`
- `backend/src/main/java/com/operation/seoul/episode/repository/EpisodeRepository.java`

## 6. 프론트엔드 주요 모듈

프론트는 `frontend/src` 아래 화면, API, 상태 저장소, 컴포넌트로 구성됩니다.

| 경로 | 역할 |
| --- | --- |
| `src/router/index.js` | URL과 화면 연결, 로그인/관리자 권한 가드 |
| `src/api/axiosInstance.js` | API 기본 설정과 토큰 처리 |
| `src/api/episodeApi.js` | 사용자 에피소드 플레이 API |
| `src/api/adminEpisodeApi.js` | 관리자 에피소드/AI 초안 API |
| `src/stores/sessionStore.js` | 로그인 세션, 사용자/관리자 상태 |
| `src/views/AdminEpisodesView.vue` | 관리자 사건파일 생성/운영 핵심 화면 |
| `src/views/EpisodeMapView.vue` | 지도, 도착 판정, 퍼즐 진입 |
| `src/views/EpisodeCaseFileView.vue` | 사건파일 단서/증거/용의자 화면 |
| `src/views/FinalDeductionView.vue` | 최종 추리 화면 |
| `src/views/ClearReportView.vue` | 클리어 후 역사 해설 화면 |
| `src/components/episode/*` | 퍼즐, 단서 보드, 최종 답안 UI |

## 7. 관리자 AI 에피소드 생성 흐름

현재 가장 중요한 비즈니스 플로우입니다. 관리자는 TourAPI/Kakao Local 장소를 고르고, AI가 장르와 정답 키워드를 먼저 제안한 뒤, 관리자가 확인하면 전체 사건파일 초안을 생성합니다.

전체 흐름:

1. 관리자가 `/admin/episodes` 진입
2. TourAPI 장소 후보 조회
3. Kakao Local 주변 후보 또는 수동 후보로 장소 보강
4. 선택 장소를 기반으로 현장 근거 보강
5. Gemini가 장르와 최종 정답 키워드 계획 생성
6. 관리자가 장르/키워드 확인
7. 확정된 장르/키워드를 기반으로 Gemini 전체 초안 생성
8. 백엔드가 초안 정규화 및 검증
9. 프론트에서 초안 수정 가능
10. DRAFT 저장
11. 공개 준비도 점검
12. PUBLISHED 전환

관련 API:

| Method | Path | 역할 |
| --- | --- | --- |
| `GET` | `/api/v1/admin/episodes/place-candidates` | TourAPI 장소 후보 조회 |
| `GET` | `/api/v1/admin/episodes/place-candidates/nearby` | Kakao Local 주변 후보 조회 |
| `POST` | `/api/v1/admin/episodes/ai-draft/enrich-site-data` | 선택 장소 현장 근거 보강 |
| `POST` | `/api/v1/admin/episodes/ai-draft/plan` | AI 장르/정답 키워드 계획 생성 |
| `POST` | `/api/v1/admin/episodes/ai-draft/gemini` | 확정 키워드 기반 전체 초안 생성 |
| `POST` | `/api/v1/admin/episodes/ai-draft/validate` | 초안 검증 |
| `POST` | `/api/v1/admin/episodes/ai-draft/save` | DRAFT 저장 |
| `GET` | `/api/v1/admin/episodes/{episodeId}/publish-readiness` | 공개 준비도 점검 |

## 8. AI 스토리 생성의 핵심 규칙

`AdminEpisodeGeminiService`가 Gemini 프롬프트와 후처리 검증을 담당합니다.

가장 중요한 원칙:

- TourAPI/장소 데이터와 관리자 메모를 사실 기반 자료로 사용합니다.
- 실제 역사적 사실은 게임 중 직접 해설하지 않습니다.
- 게임 진행 중에는 Fiction Mode로만 보여줍니다.
- 클리어 후에는 Fact Mode로 실제 역사적 배경을 해설합니다.
- 실존 역사 인물을 범인/악역으로 만들지 않습니다.
- 최종 장소는 내부적으로만 사용하고 사용자 지도에는 숨깁니다.
- 최종 질문, 최종 정답, 픽션 시놉시스의 목표가 서로 어긋나면 안 됩니다.
- AI가 선택한 장르에 따라 최종 정답 키워드 목록이 달라집니다.
- 최종 정답은 키워드 전체를 포함해야 정답 처리됩니다.

예시:

| 장르 | 정답 키워드 슬롯 |
| --- | --- |
| 살인 미스터리 | 범인, 범행도구, 사건장소 |
| 보물찾기 | 숨겨진 물건, 보관 장소, 해금 조건 |
| 암호 해독 | 최종 문장, 핵심 숫자, 해석 키워드 |
| 실종 사건 | 실종 원인, 마지막 장소, 관련 물건 |

정답 검증 방식:

- AI가 `finalAnswerKeywords`를 생성합니다.
- 저장 시 `finalAnswerAliases`에 `KW:키워드1|키워드2|키워드3` 형태의 키워드 계약이 들어갑니다.
- 사용자가 최종 답안을 입력하면 서버는 모든 키워드가 포함됐는지 확인합니다.
- 일부 키워드만 맞힌 답은 정답 처리하지 않습니다.

## 9. Fiction Mode와 Fact Mode

스토리 표현은 두 단계로 분리됩니다.

### Fiction Mode

사용자가 플레이하는 동안 보이는 이야기입니다.

- 실제 역사 사건을 직접 말하지 않습니다.
- 장소의 역사적 사실을 은유와 상징으로 바꿉니다.
- 지령, 퍼즐, 암호, 용의자, 조력자, 적의 정체는 실제 사실을 모티브로 한 픽션 장치입니다.
- `episodeTitle`, `subtitle`, `fictionSynopsis`, `finalQuestion`에는 정답 키워드를 직접 노출하지 않습니다.
- 단서/증거/용의자 카드는 개별 키워드 후보를 점진적으로 좁히는 역할을 합니다.

### Fact Mode

클리어 후 `ClearReportView`에서 보여주는 해설입니다.

반드시 포함해야 하는 섹션:

1. 모티브 공개
2. 실제 사건 해설
3. 픽션과 역사의 매칭, 디브리핑

Fact Mode에서는 최종 목적지와 실제 역사 모티브를 명확히 설명할 수 있습니다.

## 10. 최종 장소 은닉 규칙

이 프로젝트에서 최종 장소 은닉은 게임 구조상 매우 중요합니다.

서버 내부:

- `mission_spots.is_final_place` 또는 동등한 내부 필드로 실제 최종 장소를 판정합니다.
- 최종 장소 도착 시 `FINAL_READY` 상태가 됩니다.
- 최종 추리와 최종 정답 제출은 이 상태와 연결됩니다.

사용자 응답:

- 일반 사용자 지도 응답에는 `finalPlace`, `isFinalPlace`, `FINAL_PLACE`, `clueRole` 같은 내부 필드를 노출하지 않습니다.
- 사용자는 `publicMarkerType`만 보고 지도를 봅니다.
- 관리자는 검수 편의를 위해 별도 정보가 보일 수 있습니다.

관련 핵심 코드:

- `EpisodePlayService.getMap`
- `EpisodePlayService.arrive`
- `EpisodePlayService.arriveFinalPlace`

## 11. 퍼즐과 보상 구조

각 조사 지점은 보통 하나의 퍼즐을 가집니다.

퍼즐 구성:

- 질문
- 정답
- 퍼즐 타입
- 힌트 3단계
- 보상 단서 `reward_payload`
- 현장 검수 기준 또는 생성 근거

퍼즐 타입 예시:

- `OBSERVATION`
- `NUMBER_LOCK`
- `INITIAL_SOUND`
- `PATTERN`
- `STORY_COMBINATION`

주의할 점:

- 실제 간판, 숫자, 계단 수, 조형물 등은 제공된 데이터나 관리자 메모에 있을 때만 사용합니다.
- 장소명 글자 따기, 초성 따기, N번째 글자 추출 같은 퍼즐은 금지합니다.
- 보상 단서는 최종 답 또는 목적지 추론 중 하나를 진전시켜야 합니다.

## 12. 환경 변수

백엔드 주요 설정:

```properties
DB_URL=jdbc:mysql://...
DB_USERNAME=...
DB_PASSWORD=...
GEMINI_API_KEY=...
GOOGLE_VISION_KEY=...
VITE_KAKAO_REST_KEY=...
TMAP_APP_KEY=...
```

프론트 주요 설정:

```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_KAKAO_MAP_KEY=...
VITE_TMAP_APP_KEY=...
VITE_DEV_ARRIVAL=true
```

운영 환경에서는 개발용 도착 판정을 꺼야 합니다.

```env
VITE_DEV_ARRIVAL=false
```

```properties
app.dev-mode.arrival-enabled=false
```

## 13. 실행 방법

백엔드:

```powershell
cd backend
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain bootRun
```

백엔드 검증:

```powershell
cd backend
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava test
```

프론트:

```powershell
cd frontend
npm install
npm run dev
```

프론트 빌드:

```powershell
cd frontend
npm run build
```

## 14. 신규 개발자가 먼저 보면 좋은 순서

1. `README.md`로 현재 구현 범위 확인
2. `docs/readme2.md`로 프로젝트 구조 파악
3. `frontend/src/router/index.js`로 화면 흐름 확인
4. `frontend/src/views/AdminEpisodesView.vue`로 관리자 생성 플로우 확인
5. `frontend/src/api/adminEpisodeApi.js`로 관리자 API 확인
6. `backend/src/main/java/com/operation/seoul/admin/episode/controller/AdminEpisodeController.java`로 관리자 엔드포인트 확인
7. `backend/src/main/java/com/operation/seoul/admin/episode/service/AdminEpisodeGeminiService.java`로 AI 생성 규칙 확인
8. `backend/src/main/java/com/operation/seoul/admin/episode/service/AdminEpisodeService.java`로 저장/검수 로직 확인
9. `backend/src/main/java/com/operation/seoul/episode/service/EpisodePlayService.java`로 사용자 플레이 흐름 확인
10. `frontend/src/views/EpisodeMapView.vue`, `FinalDeductionView.vue`, `ClearReportView.vue`로 실제 사용자 경험 확인

## 15. 자주 헷갈리는 개념

### 정답 키워드와 최종 정답

`finalAnswerKeywords`는 정답 판정에 필요한 필수 키워드 목록입니다. `finalAnswer`는 이 키워드들을 자연어 문장으로 묶은 최종 진실입니다.

예시:

```text
finalAnswerKeywords = ["용의자 A", "얼린 북어", "궁전 뒷뜰"]
finalAnswer = "용의자 A가 얼린 북어로 궁전 뒷뜰에서 사건을 일으켰다."
```

사용자 답안에 세 키워드가 모두 포함되어야 정답입니다.

### Fiction Mode와 실제 역사

플레이 중에는 실제 역사 해설을 숨기고, 클리어 후에만 실제 사건과 픽션의 대응 관계를 보여줍니다.

### 최종 장소와 목적지 힌트

최종 장소는 내부 판정용입니다. 사용자에게는 여러 목적지 힌트 후보처럼 보이게 해야 하며, 실제 최종 장소를 지도 응답에서 직접 노출하면 안 됩니다.

### Secret facts

`deductionSecretFacts`는 최종 추리 질문에 답변하기 위한 서버/관리자용 비밀 사실 목록입니다. 사용자에게 그대로 보여주는 설명문이 아니라, “질문에 예/아니오/부분 관련으로 답할 근거”입니다.

### Forbidden reveals

`deductionForbiddenReveals`는 최종 추리 중 직접 말하면 안 되는 금지어 목록입니다. 최종 정답, 최종 장소, 직접 스포일러가 들어갑니다.

## 16. 공개 전 검수 체크리스트

관리자는 에피소드를 `PUBLISHED`로 전환하기 전에 아래를 확인해야 합니다.

- 장소 좌표가 실제 위치와 맞는가
- 도착 반경이 현장에서 무리 없는가
- 최종 장소가 사용자 지도 응답에서 숨겨지는가
- 퍼즐이 실제 제공 데이터 또는 관리자 메모에 근거하는가
- 픽션 시놉시스와 최종 질문과 최종 정답이 같은 목표를 가리키는가
- 정답 키워드가 초기 브리핑과 최종 질문에 노출되지 않는가
- 단서 카드가 정답을 한 번에 공개하지 않고 단계적으로 좁히는가
- 실존 역사 인물이 범인/악역으로 왜곡되지 않는가
- 클리어 리포트에 실제 역사 해설과 픽션 매칭이 충분한가
- 개발용 도착 판정이 운영에서 꺼져 있는가

## 17. 현재 개발 시 주의점

- 백엔드 컴파일은 Java 17과 Gradle wrapper 실행 환경이 필요합니다.
- 프론트 빌드는 Node 엔진 조건을 만족해야 합니다.
- AI 생성 결과는 항상 관리자 검수가 필요합니다.
- 외부 API 키가 없으면 TourAPI, Kakao Local, Gemini 관련 기능은 실패합니다.
- `application.properties`에 비밀값을 직접 쓰지 말고 환경변수로 주입하는 것이 안전합니다.
- 기존 변경사항이 많은 프로젝트이므로 수정 전 `git status`로 작업 중 파일을 확인하는 것이 좋습니다.
