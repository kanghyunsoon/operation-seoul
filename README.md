# Operation KOREA

TourAPI와 AI를 결합해 전국 관광지를 위치 기반 야외 방탈출 미션으로 전환하는 관광 게이미피케이션 서비스입니다. 사용자는 지역 작전을 선택하고, 지도 기반 현장 미션을 수행하며, AI 채팅을 통해 최종 역사 키워드를 추론합니다.

작성 기준: 2026-05-14

## 1. 프로젝트 개요

| 항목 | 내용 |
| --- | --- |
| 서비스명 | Operation KOREA |
| 한 줄 소개 | 관광지 방문을 AI 추리 미션, GPS 이동, 현장 인증, 역사 해설로 연결하는 체류형 관광 플랫폼 |
| 문제 정의 | 유명 랜드마크 단기 방문 위주의 국내 관광은 지역 상권 유입과 체류 시간 증대에 한계가 있습니다. |
| 핵심 해결 | TourAPI 공공데이터와 AI 시나리오 생성을 활용해 유명 관광지와 주변 POI를 하나의 미션 동선으로 연결합니다. |
| 주요 사용자 | MZ세대 여행자, 가족 단위 관광객, 지역 관광 담당자, 향후 외국인 자유여행객 |
| PoC 방향 | 서울 정동길을 시작점으로 삼되, TourAPI 기반 전국 확장을 목표로 합니다. |

## 2. 제안서 반영 요약

제안서의 핵심 방향은 단순 관광 정보 제공이 아니라, 관광객이 직접 걷고 추리하고 인증하는 경험형 관광 모델입니다.

| 제안서 항목 | README 반영 내용 |
| --- | --- |
| 국내 관광 위기와 랜드마크 편중 | 지역별 작전 카드, 주변 POI 힌트 미션, 체류형 동선으로 해결 |
| MZ 경험 소비와 SNS 인증 | 미션 클리어 카드, 점수, 시간, 역사 아카이브 제공 |
| 가족 단위 에듀테인먼트 | 역사 사실 기반 AI 추리와 클리어 리포트 제공 |
| 지역 상권 체류 유도 | 최종지 주변 subSpot을 힌트 미션으로 연결 |
| TourAPI 자동화 파이프라인 | 관리자 후보지 스캔, AI 작전 생성, DB 저장 흐름 구현 |
| Fiction + Fact 융합 | 게임 중에는 첩보/방탈출 서사, 클리어 후에는 실제 역사 해설 제공 |

## 3. 팀원별 진행사항

실제 제출 전 `이름 기입` 영역을 팀원 실명으로 바꿔야 합니다.

| 역할 | 담당자 | 현재 진행사항 | 다음 작업 |
| --- | --- | --- | --- |
| PM / 기획 | 이름 기입 | 제안서 기반 문제 정의, 사용자 가치, 산출물 목록 정리 | 발표자료 최종 스토리라인과 데모 시나리오 확정 |
| Frontend | 이름 기입 | Intro/Home/Briefing/Map/Chat/Clear 화면, 지도 UX, 힌트 카드, 클리어 카드 구현 | 핵심 화면 캡처, 반응형 QA, 화면 설계 문서 보강 |
| Backend | 이름 기입 | 인증, 지역/미션 API, 관리자 작전 생성, 세션/점수/클리어 리포트 구현 | 과제 제약에 맞춘 MyBatis 전환 검토, API 응답 표준화 |
| AI / Data | 이름 기입 | Gemini 작전 생성, 힌트/정답 판정, Vision 인증, TourAPI 후보 수집 구현 | 프롬프트 로그 정리, 실제 역사 사실 검증 기준 강화 |
| QA / Demo | 이름 기입 | 로컬 빌드 검증, 지도/카메라/채팅 플로우 점검 | 시연 PC 실행 패키지, DB seed, 장애 대응 체크리스트 준비 |

## 4. 현재 구현 기능

| 영역 | 구현 상태 | 주요 파일 |
| --- | --- | --- |
| 회원관리 | 회원가입, 로그인, JWT 발급, 라우터 가드 구현 | `AuthController`, `JwtTokenProvider`, `JwtAuthenticationFilter`, `sessionStore.js` |
| 권한관리 | 관리자 API 보호, 현재 사용자 권한 해석 | `SecurityConfig`, `CurrentUserResolver` |
| 지역 선택 | 전국 권역 지도, GPS 표시, 지역별 작전 카드 | `HomeView.vue`, `RegionController`, `OperationAreaResolver` |
| 작전 생성 | TourAPI 후보 조회, Kakao 주변 POI 보강, Tmap 거리 필터, Gemini 시나리오 생성 | `AdminMissionController`, `TourApiService`, `GeminiAiService` |
| 브리핑 | AI 생성 regionDescription 기반 작전 브리핑 | `BriefingView.vue` |
| 지도 미션 | Kakao 지도, 마커, 도착 판정, 힌트 획득, 최종 미션 해금 | `MapView.vue` |
| 현장 인증 | 카메라 캡처, Vision API 분석, Gemini 의미 비교 | `CameraScanner.vue`, `VisionAiService` |
| AI 채팅 | 힌트 질문, 가설 검증, 정답 판정, 스트리밍 응답 | `AiChatView.vue`, `GeminiAiService` |
| 클리어 화면 | 점수, 소요 시간, 이동 거리, 실제 역사 해설, 단서 해석 | `ClearView.vue`, `GameSessionController` |

## 5. 사용자 흐름

1. 사용자가 `/intro`에서 회원가입 또는 로그인합니다.
2. `/home`에서 전국 권역 중 작전 지역을 선택합니다.
3. 일반 사용자는 지역별 작전 카드로 진입하고, 관리자는 후보지를 선택해 AI 작전을 생성합니다.
4. `/briefing`에서 작전 배경을 읽고 `/map`으로 이동합니다.
5. 지도에서 힌트 마커를 선택하고 현장 도착 범위에 들어가면 카메라 인증을 수행합니다.
6. 힌트 미션을 완료하면 획득 단서가 누적되고 최종 미션이 해금됩니다.
7. 최종 현장에서 `/chat/:sessionId`로 이동해 AI와 대화하며 정답 키워드를 추론합니다.
8. 정답 입력 후 `/clear/:missionId`에서 역사 해설, 단서 해석, 점수 기록을 확인합니다.

## 6. 기술 스택

| 구분 | 현재 구현 | 제출 제약 검토 |
| --- | --- | --- |
| Frontend | Vue 3, Vite, Pinia, Vue Router, Axios | Vue 3 사용 가능 |
| Backend | Java 17, Spring Boot, Spring MVC, Spring Security | Spring 중심 구성 가능 |
| Persistence | Spring Data JPA, MySQL | 과제 제약이 JPA 금지라면 MyBatis 전환 필요 |
| 지도/위치 | Kakao Maps JavaScript API, Tmap Pedestrian API, Geolocation API | 외부 API 키 관리 필요 |
| AI/데이터 | Gemini API, Google Cloud Vision API, TourAPI | AI 활용 로그와 프롬프트 근거 제출 필요 |

## 7. 프로젝트 구조

```text
operation-seoul
├── backend
│   ├── src/main/java/com/operation/seoul
│   │   ├── auth        # 회원관리, JWT, 현재 사용자 해석
│   │   ├── game        # 게임 세션, AI 생성, Vision 인증, 채팅
│   │   ├── global      # 보안, CORS, 공통 설정
│   │   └── location    # 지역, 미션, 위치/권역 판별
│   └── src/main/resources
│       └── application-example.properties
├── frontend
│   ├── src
│   │   ├── api
│   │   ├── components
│   │   ├── composables
│   │   ├── router
│   │   ├── stores
│   │   └── views
├── docs
│   └── PROJECT_PLANNING.md
└── README.md
```

## 8. 로컬 실행

### Backend

```powershell
Copy-Item backend/src/main/resources/application-example.properties backend/src/main/resources/application-local.properties
cd backend
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain bootRun
```

`application-local.properties`에는 DB, Gemini, Vision, TourAPI, Kakao, Tmap, JWT 값을 입력합니다. 비밀값은 Git에 올리지 않습니다.

### Frontend

```powershell
Copy-Item frontend/.env.example frontend/.env
cd frontend
npm install
npm run dev
```

### Build Check

```powershell
cd frontend
npm run build

cd ..\backend
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test
```

## 9. 주요 API

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | 회원가입 |
| `POST` | `/api/v1/auth/login` | 로그인 및 JWT 발급 |
| `GET` | `/api/v1/regions/cards?areaCode={code}` | 지역별 작전 카드 조회 |
| `GET` | `/api/v1/regions/{regionId}/missions` | 미션 목록과 해금 상태 조회 |
| `POST` | `/api/v1/sessions/{missionId}/vision` | 현장 사진 인증 |
| `POST` | `/api/v1/sessions/{missionId}/chat/stream` | AI 채팅 및 정답 판정 |
| `GET` | `/api/v1/sessions/{missionId}/clear-report` | 클리어 리포트 조회 |
| `GET` | `/api/v1/admin/missions/region-candidates` | 관리자 후보지 조회 |
| `POST` | `/api/v1/admin/missions/generate-selected` | 선택 후보지 기반 AI 작전 생성 |

## 10. 제출 산출물 위치

| 산출물 | 위치 | 상태 |
| --- | --- | --- |
| README | `README.md` | 작성 |
| 통합 기획서 | `docs/PROJECT_PLANNING.md` | 작성 |
| WBS | `docs/PROJECT_PLANNING.md` 내 WBS 섹션 | 작성 |
| 간트차트 | `docs/PROJECT_PLANNING.md` 내 Mermaid Gantt | 작성 |
| 유스케이스 다이어그램 | `docs/PROJECT_PLANNING.md` 내 Mermaid 다이어그램 | 작성 |
| 화면 설계 | `docs/PROJECT_PLANNING.md` 내 핵심 화면 8종 | 작성 |
| 발표 PPT 구성안 | `docs/PROJECT_PLANNING.md` 내 발표자료 섹션 | 작성 |
| AI 활용 로그 | `docs/PROJECT_PLANNING.md` 내 프롬프트 로그 | 작성 |

## 11. 남은 작업

| 우선순위 | 작업 | 설명 |
| --- | --- | --- |
| 높음 | 제출 제약 검토 | JPA 사용 금지 조건이 확정이면 MyBatis로 전환해야 합니다. |
| 높음 | 시연 패키지 | IDE 없이 실행 가능한 jar, 설정 파일, DB seed, start script 준비 |
| 높음 | 화면 캡처 | 핵심 화면 5~10개 캡처 후 발표자료에 삽입 |
| 중간 | QA | GPS 오차, 카메라 권한, 카카오맵 키 도메인, AI 응답 실패 케이스 점검 |
| 중간 | DB 초기 데이터 | 관리자 계정, 샘플 작전, 샘플 미션 seed 절차 정리 |
| 낮음 | 확장 기능 | 찜/즐겨찾기, 팔로우, 계획/일정관리, 챌린지 관리, AI 추천/코칭 |

## 12. 개발/문서 규칙

- 새 기능을 추가하면 관련 README 또는 `docs` 문서를 함께 수정합니다.
- 새 클래스, 복잡한 메서드, AI 프롬프트, 외부 API 연동부에는 의도와 제약을 주석으로 남깁니다.
- AI 프롬프트를 바꿀 때는 정답 키워드 직접 노출 여부와 특정 주제 하드코딩 여부를 확인합니다.
- 비밀값은 `.env`, `application-local.properties`에만 보관하고 커밋하지 않습니다.
- 과제 제출 제약과 현재 구현이 충돌하는 부분은 README와 기획서의 리스크 항목에 기록합니다.
