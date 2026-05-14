# Operation KOREA

TourAPI와 AI를 결합해 전국 관광지를 위치 기반 야외 방탈출 미션으로 전환하는 관광 게이미피케이션 서비스입니다. 사용자는 지역 작전을 선택하고, 지도 기반 현장 미션을 수행하며, AI 채팅을 통해 최종 역사 키워드를 추론합니다.

작성 기준: 2026-05-14

## 1. 프로젝트 개요

| 항목 | 내용 |
| --- | --- |
| 서비스명 | Operation KOREA |
| 한 줄 소개 | 관광지 방문을 AI 추리 미션, GPS 이동, 현장 인증, 역사 해설로 연결하는 체류형 관광 플랫폼 |
| 기획 배경 | 해외여행 선호 증가와 국내 여행 비용 부담으로 국내 관광 관심이 분산되고, 유명 랜드마크 중심의 짧은 방문이 반복되고 있습니다. |
| 문제 정의 | 단순 정보 조회와 유명지 단기 방문 위주의 국내 관광은 지역 상권 유입, 체류 시간 증대, 지역 역사 학습에 한계가 있습니다. |
| 핵심 해결 | TourAPI 공공데이터와 AI 시나리오 생성을 활용해 유명 관광지와 주변 POI를 하나의 미션 동선으로 연결합니다. |
| 주요 사용자 | MZ세대 여행자, 가족 단위 관광객, 지역 관광 담당자, 향후 외국인 자유여행객 |
| PoC 방향 | 서울 정동길을 시작점으로 삼되, TourAPI 기반 전국 확장을 목표로 합니다. |

## 2. 서비스 기획 배경

1. 해외여행 선호와 국내 관광 분산
   국내 여행 물가와 숙박비 부담으로 해외여행 선호가 커지고, 국내의 역사 명소와 로컬 상권은 관광객의 관심에서 멀어질 수 있습니다.

2. 랜드마크 편중과 짧은 체류 시간
   사용자는 유명 장소만 짧게 방문하고 이동하는 경우가 많습니다. 지역 경제에 실질적인 도움이 되려면 관광객을 주변 골목과 소상공인 상권까지 자연스럽게 이동시키는 장치가 필요합니다.

3. 수동적 관광 모델의 한계
   기존 관광은 설명을 읽고 사진을 찍는 방식에 머무르는 경우가 많습니다. Operation KOREA는 사용자가 요원 역할로 직접 걷고, 찾고, 추리하고, 인증하는 경험형 관광으로 전환합니다.

4. 공공 관광데이터의 확장 가능성
   TourAPI, 다국어 관광정보, 무장애 관광정보, 두루누비 걷기여행 코스 등 공공 API를 단계적으로 결합하면 지역별, 사용자별, 접근성별 맞춤 미션을 만들 수 있습니다.

5. B2G와 사용자 생성 콘텐츠로의 확장성
   초기 MVP는 TourAPI 기반 AI 미션 생성과 현장 인증에 집중합니다. 이후에는 지자체용 지역 미션 패키지, 소상공인 리워드, 비식별 이동 데이터 기반 인사이트, 사용자 생성 미션을 더해 지속 가능한 관광 콘텐츠 생태계로 확장합니다.

## 3. 제안서 반영 요약

제안서의 핵심 방향은 단순 관광 정보 제공이 아니라, 관광객이 직접 걷고 추리하고 인증하는 경험형 관광 모델입니다.

| 제안서 항목 | README 반영 내용 |
| --- | --- |
| 국내 관광 위기와 랜드마크 편중 | 지역별 작전 카드, 주변 POI 힌트 미션, 체류형 동선으로 해결 |
| MZ 경험 소비와 SNS 인증 | 미션 클리어 카드, 점수, 시간, 역사 아카이브 제공 |
| 가족 단위 에듀테인먼트 | 역사 사실 기반 AI 추리와 클리어 리포트 제공 |
| 지역 상권 체류 유도 | 최종지 주변 subSpot을 힌트 미션으로 연결 |
| TourAPI 자동화 파이프라인 | 관리자 후보지 스캔, AI 작전 생성, DB 저장 흐름 구현 |
| Fiction + Fact 융합 | 게임 중에는 첩보/방탈출 서사, 클리어 후에는 실제 역사 해설 제공 |
| 다국어/무장애/걷기여행 확장 | 향후 외국인, 교통약자, 도보 여행자 맞춤 미션으로 확장 |
| UGC와 커뮤니티 | 사용자 생성 미션, 팔로우/팔로잉, 랭킹, 미션별 리뷰로 확장 |
| Truth Unlocked 아카이브 | 클리어 후 TourAPI 원본과 실제 역사 정보를 바탕으로 관광지 본연의 가치 제공 |
| 지역 상생 리워드 | 향후 서브 미션 완료 보상으로 지역 상점 쿠폰 또는 지역사랑상품권 연계 검토 |
| 지자체 인사이트 | 향후 체류 시간, 힌트 사용 위치, 이동 동선을 비식별 통계로 제공하는 B2G 확장 검토 |

## 4. 공공 API 및 외부 API 활용 계획

| 구분 | API | 현재 상태 | 활용 방식 |
| --- | --- | --- | --- |
| 현재 구현 | TourAPI 국문관광정보 | 구현 | 지역별 관광지 후보, 위치, 설명 데이터를 AI 작전 생성 원천으로 사용 |
| 현재 구현 | Kakao Maps JavaScript API | 구현 | 지도 표시, 현재 위치, 미션 마커 표시 |
| 현재 구현 | Kakao Local/REST API | 일부 구현 | 최종 목적지 주변 POI 보강 |
| 현재 구현 | Tmap Pedestrian API | 구현 | 직선거리만 가까운 후보를 줄이고 도보 이동 가능성 확인 |
| 현재 구현 | Google Cloud Vision API | 구현 | 사진 속 객체/텍스트 기반 현장 인증 |
| 현재 구현 | Gemini API | 구현 | 브리핑, 힌트, 정답 판정, 클리어 리포트 생성 |
| 향후 확장 | TourAPI 다국어 관광정보 API | 계획 | 외국인 자유여행객 대상 다국어 미션 제공 |
| 향후 확장 | 무장애 관광정보 API | 계획 | 휠체어/유모차 이용자도 가능한 배리어프리 미션 코스 필터링 |
| 향후 확장 | 두루누비 걷기여행 코스 API | 계획 | 안전하고 경관이 좋은 도보 코스를 힌트 동선에 반영 |
| 향후 확장 | 기상청 단기예보 API | 계획 | 우천/폭염 시 실내 미션 추천 또는 난이도 조정 |

### 4.1 제안서 기반 공공 API 작성 방식

제출 문서와 발표에서는 구현 완료 API와 확장 예정 API를 분리해서 설명합니다. 확장 API는 “이미 구현했다”가 아니라 “MVP 구조 위에 연결할 다음 데이터 레이어”로 표현합니다.

| API | 발표/문서 표현 | 적용 기능 |
| --- | --- | --- |
| TourAPI 국문관광정보 | `locationBasedList`, `detailCommon`, `detailIntro`를 활용해 관광지 좌표와 설명을 수집하고 AI 미션 생성의 원천 데이터로 사용 | 현재 MVP 핵심 데이터 |
| TourAPI 다국어 관광정보 | 한국관광공사 다국어 데이터를 Gemini 번역/요약과 결합해 외국인도 플레이 가능한 미션으로 확장 | 글로벌 K-관광 |
| 무장애 관광정보 API | 휠체어, 유모차 이용자가 접근 가능한 관광지만 필터링해 배리어프리 작전을 구성 | 접근성 맞춤 미션 |
| 두루누비 걷기여행 코스 API | 안전하고 경관이 좋은 도보 코스를 힌트 이동 동선에 반영 | 걷기여행형 작전 |
| 기상청 단기예보 API | 우천, 폭염, 한파 등 상황에서 실내 미션 추천 또는 난이도 조정 | 안전/운영 고도화 |

## 5. 팀원별 진행사항

실제 제출 전 `이름 기입` 영역을 팀원 실명으로 바꿔야 합니다.

| 역할 | 담당자 | 현재 진행사항 | 다음 작업 |
| --- | --- | --- | --- |
| 팀원 1 / 팀장 | 이름 기입 | 게이미피케이션 구조, AI 프롬프트, MyBatis DB 모델, 보안/인증, 제출 산출물 총괄 | 리뷰/랭킹/팔로우 정책 확정, 발표자료 완성 |
| 팀원 2 / 지도·로케이션 | 이름 기입 | Kakao 지도, GPS 도착 판정, 지역 선택, TourAPI 후보, 주변 POI, 이동 동선, DB 연계 보조 | 무장애/두루누비 기반 코스 필터링, 현장 QA, 지도 화면 안정화 |
| 공통 | 전체 | 기능 통합, 시연 플로우 점검, README/기획서 정리 | 화면 캡처, DB seed, 시연 패키징, 발표 리허설 |

상세 팀원별 완료/잔여 작업은 [TEAM_PROGRESS.md](docs/TEAM_PROGRESS.md)를 참고합니다.

## 6. 현재 구현 기능

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

## 7. 향후 확장 기능

| 기능 | 설명 | 우선순위 |
| --- | --- | --- |
| 미션별 리뷰 | 클리어 후 별점/한줄평/난이도 평가를 남기고 다음 사용자에게 참고 정보 제공 | 높음 |
| 찜/즐겨찾기 | 나중에 플레이할 작전을 저장 | 중간 |
| 팔로우/팔로잉 | 사용자가 만든 미션이나 플레이 기록을 기반으로 커뮤니티 관계 형성 | 중간 |
| 랭킹 | 미션별 소요 시간, 이동 거리, 힌트 사용량을 기준으로 점수 랭킹 제공 | 중간 |
| 사용자 생성 미션 | 사용자가 TourAPI 데이터를 조합해 지역 미션을 제작하고 공유 | 낮음 |
| 다국어 미션 | TourAPI 다국어 API와 AI 번역을 결합해 외국인 관광객 지원 | 낮음 |
| 배리어프리 미션 | 무장애 관광정보 API로 접근 가능한 코스만 필터링 | 낮음 |
| 걷기여행 코스 연계 | 두루누비 데이터를 활용해 안전하고 경관 좋은 도보 코스 추천 | 낮음 |
| 지역 상생 리워드 | 서브 미션 완료 시 주변 상점 쿠폰이나 지역사랑상품권 보상 연계 | 낮음 |
| 지자체 인사이트 대시보드 | 체류 시간, 힌트 사용 위치, 이동 동선을 비식별 통계로 분석 | 낮음 |

## 8. 사용자 흐름

1. 사용자가 `/intro`에서 회원가입 또는 로그인합니다.
2. `/home`에서 전국 권역 중 작전 지역을 선택합니다.
3. 일반 사용자는 지역별 작전 카드로 진입하고, 관리자는 후보지를 선택해 AI 작전을 생성합니다.
4. `/briefing`에서 작전 배경을 읽고 `/map`으로 이동합니다.
5. 지도에서 힌트 마커를 선택하고 현장 도착 범위에 들어가면 카메라 인증을 수행합니다.
6. 힌트 미션을 완료하면 획득 단서가 누적되고 최종 미션이 해금됩니다.
7. 최종 현장에서 `/chat/:sessionId`로 이동해 AI와 대화하며 정답 키워드를 추론합니다.
8. 정답 입력 후 `/clear/:missionId`에서 역사 해설, 단서 해석, 점수 기록을 확인합니다.
9. 향후 리뷰, 랭킹, 팔로우 기능을 통해 완료 경험을 공유합니다.

## 9. 기술 스택

| 구분 | 현재 구현 | 제출 제약 검토 |
| --- | --- | --- |
| Frontend | Vue 3, Vite, Pinia, Vue Router, Axios | Vue 3 사용 가능 |
| Backend | Java 17, Spring Boot, Spring MVC, Spring Security | Spring 중심 구성 가능 |
| Persistence | MyBatis, MySQL, `schema.sql` | JPA 제거 완료. 초기 테이블은 SQL 스크립트로 관리 |
| 지도/위치 | Kakao Maps JavaScript API, Tmap Pedestrian API, Geolocation API | 외부 API 키 관리 필요 |
| AI/데이터 | Gemini API, Google Cloud Vision API, TourAPI | AI 활용 로그와 프롬프트 근거 제출 필요 |

## 10. 프로젝트 구조

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
│   ├── PROJECT_PLANNING.md
│   └── TEAM_PROGRESS.md
└── README.md
```

## 11. 로컬 실행

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

## 12. 주요 API

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

## 13. 제출 산출물 위치

| 산출물 | 위치 | 상태 |
| --- | --- | --- |
| README | `README.md` | 작성 |
| 통합 기획서 | `docs/PROJECT_PLANNING.md` | 작성 |
| 팀원별 진행 문서 | `docs/TEAM_PROGRESS.md` | 작성 |
| WBS | `docs/PROJECT_PLANNING.md` 내 WBS 섹션 | 작성 |
| 간트차트 | `docs/PROJECT_PLANNING.md` 내 Mermaid Gantt | 작성 |
| 유스케이스 다이어그램 | `docs/PROJECT_PLANNING.md` 내 Mermaid 다이어그램 | 작성 |
| 화면 설계 | `docs/PROJECT_PLANNING.md` 내 핵심 화면 8종 | 작성 |
| 발표 PPT 구성안 | `docs/PROJECT_PLANNING.md` 내 발표자료 섹션 | 작성 |
| AI 활용 로그 | `docs/PROJECT_PLANNING.md` 내 프롬프트 로그 | 작성 |

## 14. 남은 작업

| 우선순위 | 작업 | 설명 |
| --- | --- | --- |
| 높음 | 제출 제약 검토 | JPA 제거 및 MyBatis 전환 완료. JSP 필수 여부만 추가 확인 필요 |
| 높음 | 리뷰 기능 | 공통 필수 요구사항이면 미션별 리뷰 API/UI를 우선 추가합니다. |
| 높음 | 시연 패키지 | IDE 없이 실행 가능한 jar, 설정 파일, DB seed, start script 준비 |
| 높음 | 화면 캡처 | 핵심 화면 5~10개 캡처 후 발표자료에 삽입 |
| 중간 | QA | GPS 오차, 카메라 권한, 카카오맵 키 도메인, AI 응답 실패 케이스 점검 |
| 중간 | DB 초기 데이터 | 관리자 계정, 샘플 작전, 샘플 미션 seed 절차 정리 |
| 중간 | 커뮤니티 확장 설계 | 찜/즐겨찾기, 팔로우/팔로잉, 랭킹, 사용자 생성 미션의 데이터 모델 설계 |
| 낮음 | 공공 API 확장 | 다국어 관광정보, 무장애 관광정보, 두루누비, 기상청 API 연계 |
| 낮음 | B2G 확장 설계 | 지자체용 작전 템플릿, 지역 리워드, 비식별 관광 인사이트 대시보드 구상 |

## 15. 개발/문서 규칙

- 새 기능을 추가하면 관련 README 또는 `docs` 문서를 함께 수정합니다.
- 새 클래스, 복잡한 메서드, AI 프롬프트, 외부 API 연동부에는 의도와 제약을 주석으로 남깁니다.
- AI 프롬프트를 바꿀 때는 정답 키워드 직접 노출 여부와 특정 주제 하드코딩 여부를 확인합니다.
- 비밀값은 `.env`, `application-local.properties`에만 보관하고 커밋하지 않습니다.
- 과제 제출 제약과 현재 구현이 충돌하는 부분은 README와 기획서의 리스크 항목에 기록합니다.
