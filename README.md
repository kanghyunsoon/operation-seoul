# Operation: SEOUL

AI 기반 위치 추적형 야외 방탈출 서비스입니다. 사용자는 지역별 작전을 선택하고, 지도에서 현장 미션을 수행하며, 사진 인증과 AI 채팅을 통해 최종 역사 키워드를 추론합니다.

## 1. 현재 진행 요약

작성 기준: 2026-05-13

### 완료된 기능

| 영역 | 진행 상태 | 구현 파일 |
| --- | --- | --- |
| 인증 | 회원가입, 로그인, JWT 발급, 요청 인터셉터, 라우터 가드 구현 완료 | `AuthController`, `JwtTokenProvider`, `JwtAuthenticationFilter`, `sessionStore.js`, `router/index.js` |
| 권한 | 관리자 여부를 JWT 인증 사용자 기준으로 판별하고 `/api/v1/admin/**` 보호 | `SecurityConfig`, `CurrentUserResolver` |
| 지역 선택 | 전국 권역 선택 화면, GPS 기반 사용자 위치 표시, 권역별 작전 카드 조회 구현 | `HomeView.vue`, `RegionController`, `OperationAreaResolver` |
| 작전 생성 | 관리자 후보지 스캔, TourAPI 후보 수집, Kakao 주변 POI 보강, Tmap 도보 거리 필터, Gemini 작전 JSON 생성 구현 | `AdminMissionController`, `TourApiService`, `GeminiAiService` |
| 미션 저장 | AI 응답을 `Region`, `Mission`으로 저장하고 최종 정답 노출을 막는 마스킹 로직 구현 | `AdminMissionController`, `Mission`, `Region` |
| 브리핑 | 지역 설명과 미션 데이터를 기반으로 타자기 브리핑 화면 구현 | `BriefingView.vue` |
| 지도 진행 | Kakao 지도, 커스텀 마커, GPS 거리 계산, 최종 미션 해금, Tmap 경로 표시, 단서 모달 구현 | `MapView.vue` |
| 현장 인증 | 카메라/파일 캡처, Vision API 라벨 추출, Gemini 의미 비교, 성공 시 세션 클리어 저장 | `CameraScanner.vue`, `VisionAiService`, `GameSessionController` |
| AI 채팅 | 최종 미션 채팅, Gemini 스트리밍 응답, 힌트 질문 판별, 정답 판정, 질문 횟수 제한 UI 구현 | `AiChatView.vue`, `GeminiAiService`, `useTypingBuffer.js` |
| 클리어 화면 | 점수, 소요 시간, 이동 거리, 실제 역사 리포트, 단서별 해설 표시 구현 | `ClearView.vue`, `GameSessionController`, `GeminiAiService` |
| 진행 기록 | `GameSession`으로 미션 상태, 시작/완료 시간, 거리, 점수 저장 | `GameSession`, `GameSessionRepository` |

### 진행 중이거나 주의가 필요한 부분

| 항목 | 현재 상태 | 다음 판단 |
| --- | --- | --- |
| 테스트 | 자동화 테스트가 아직 없습니다. | 백엔드 서비스 단위 테스트와 프론트 핵심 플로우 테스트를 우선 추가해야 합니다. |
| DB 마이그레이션 | `spring.jpa.hibernate.ddl-auto=update`에 의존합니다. | 팀 개발/배포 전 Flyway 또는 Liquibase 도입이 필요합니다. |
| Vision 인증 | 현재는 Google Vision `LABEL_DETECTION` 결과를 Gemini가 목표 키워드와 의미 비교합니다. | 간판/문구 인증이 중요하면 `TEXT_DETECTION` 또는 혼합 방식으로 확장해야 합니다. |
| 지도 권역 | 프론트/백엔드의 권역 폴리곤은 서비스용 근사값입니다. | 실제 행정 경계 정확도가 필요하면 공식 GeoJSON으로 교체해야 합니다. |
| API 키 | 실제 키는 `application-local.properties`, `.env`에 두고 Git에 올리지 않습니다. | 신규 팀원은 예시 파일을 복사해 개인 키를 넣어야 합니다. |
| 관리자 계정 | `User.isAdmin` 필드는 있으나 관리자 승격 UI/API는 없습니다. | DB에서 직접 변경하거나 별도 관리자 관리 API를 추가해야 합니다. |

## 2. 전체 사용자 흐름

1. 사용자가 `/intro`에서 회원가입 또는 로그인합니다.
2. 로그인 후 `/home`에서 작전 권역을 선택합니다.
3. 일반 사용자는 권역별 작전 카드를 확인하고 브리핑으로 진입합니다.
4. 관리자는 후보지 스캔 모달에서 TourAPI 후보지를 고르고 AI 작전을 생성할 수 있습니다.
5. `/briefing`에서 지역 작전 배경을 읽고 `/map`으로 이동합니다.
6. 지도에서 힌트 마커를 선택하고 현장 도착 범위에 들어가면 카메라 인증을 수행합니다.
7. 힌트 미션 3개가 클리어되면 최종 미션 위치가 해금되고 경로가 표시됩니다.
8. 최종 현장에서 `/chat/:sessionId`로 이동해 AI와 대화하며 정답 키워드를 입력합니다.
9. 정답이 맞으면 `/clear/:missionId`에서 실제 역사 해설, 단서 해석, 점수 기록을 확인합니다.

## 3. 기술 스택

| 구분 | 기술 |
| --- | --- |
| Frontend | Vue 3, Vite, Pinia, Vue Router, Axios |
| Backend | Java 17, Spring Boot 4, Spring MVC, Spring Data JPA, Spring Security |
| Database | MySQL |
| 지도/위치 | Kakao Maps JavaScript API, Tmap Pedestrian API, Geolocation API |
| AI/데이터 | Google Gemini API, Google Cloud Vision API, 한국관광공사 TourAPI |

## 4. 프로젝트 구조

```text
operation-seoul
├── backend
│   ├── src/main/java/com/operation/seoul
│   │   ├── auth        # 로그인, 회원가입, JWT, 현재 사용자 해석
│   │   ├── game        # 게임 세션, AI 작전 생성, Vision 인증, Gemini 채팅
│   │   ├── global      # Spring Security, CORS, 공통 설정
│   │   └── location    # 지역, 미션, 위치 검증, 권역 판별
│   └── src/main/resources
│       └── application-example.properties
├── frontend
│   ├── src
│   │   ├── api         # Axios 공통 인스턴스
│   │   ├── components  # 카메라 스캐너 등 공용 컴포넌트
│   │   ├── composables # 타자기 버퍼 같은 재사용 로직
│   │   ├── router      # 화면 라우팅과 로그인 가드
│   │   ├── stores      # Pinia 세션 상태
│   │   └── views       # Intro, Home, Briefing, Map, Chat, Clear
│   └── .env.example
└── README.md
```

## 5. 로컬 실행

### 5.1 백엔드 설정

```powershell
Copy-Item backend/src/main/resources/application-example.properties backend/src/main/resources/application-local.properties
```

`backend/src/main/resources/application-local.properties`를 열어 아래 값을 채웁니다.

| Key | 설명 |
| --- | --- |
| `spring.datasource.url` | MySQL 접속 URL |
| `spring.datasource.username` | MySQL 사용자 |
| `spring.datasource.password` | MySQL 비밀번호 |
| `gemini.api.key` | Gemini API 키 |
| `google.vision.key` | Google Cloud Vision API 키 |
| `tourapi.key` | 한국관광공사 TourAPI 키 |
| `kakao.rest.api.key` | Kakao REST API 키 |
| `tmap.app.key` | Tmap App Key |
| `jwt.secret` | JWT 서명용 32자 이상 비밀키 |
| `app.cors.allowed-origins` | 허용할 프론트엔드 origin |

실행:

```powershell
cd backend
java -jar gradle/wrapper/gradle-wrapper.jar bootRun
```

빌드:

```powershell
cd backend
java -jar gradle/wrapper/gradle-wrapper.jar build
```

### 5.2 프론트엔드 설정

```powershell
Copy-Item frontend/.env.example frontend/.env
```

`frontend/.env`를 열어 아래 값을 채웁니다.

| Key | 설명 |
| --- | --- |
| `VITE_API_BASE_URL` | 백엔드 API 기본 주소. 기본값은 `http://localhost:8080/api` |
| `VITE_KAKAO_MAP_KEY` | Kakao Maps JavaScript 키 |
| `VITE_TMAP_APP_KEY` | Tmap App Key |

실행:

```powershell
cd frontend
npm install
npm run dev
```

## 6. 주요 API

### 인증

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | 신규 사용자 등록 |
| `POST` | `/api/v1/auth/login` | 로그인 및 JWT 발급 |

### 지역/미션

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/regions` | 전체 지역 원본 조회 |
| `GET` | `/api/v1/regions/cards?areaCode=seoul` | 홈 화면 작전 카드 조회 |
| `GET` | `/api/v1/regions/{regionId}` | 특정 지역 상세 조회 |
| `GET` | `/api/v1/regions/{regionId}/missions` | 지역 내 미션 목록과 해금 상태 조회 |
| `POST` | `/api/v1/missions/{missionId}/arrive` | GPS 도착 여부 검증 |

### 게임 세션

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/v1/sessions/{missionId}/vision` | 사진 인증 후 미션 클리어 기록 |
| `POST` | `/api/v1/sessions/{missionId}/chat/stream` | Gemini 스트리밍 채팅 및 정답 판정 |
| `GET` | `/api/v1/sessions/{missionId}/status` | 최종 미션 클리어 여부 조회 |
| `GET` | `/api/v1/sessions/{missionId}/clear-report` | 클리어 리포트 조회 |

### 관리자

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/admin/missions/region-candidates?areaCode=seoul` | 권역별 TourAPI 후보지 조회 |
| `POST` | `/api/v1/admin/missions/generate-selected` | 선택 후보지 기반 AI 작전 생성 |
| `DELETE` | `/api/v1/admin/missions/regions/{regionId}` | 작전 지역과 하위 미션 삭제 |

## 7. 신규 팀원 온보딩 체크리스트

1. Java 17, Node.js 20.19 이상 또는 22.12 이상, MySQL을 준비합니다.
2. `backend/src/main/resources/application-example.properties`를 `application-local.properties`로 복사합니다.
3. `frontend/.env.example`을 `frontend/.env`로 복사합니다.
4. Gemini, Google Vision, TourAPI, Kakao, Tmap 키를 개인 환경 파일에 입력합니다.
5. MySQL에 `operation_seoul` 데이터베이스를 생성합니다.
6. 백엔드를 `java -jar gradle/wrapper/gradle-wrapper.jar bootRun`으로 실행합니다.
7. 프론트엔드를 `npm run dev`로 실행합니다.
8. `/intro`에서 계정을 만들고 로그인합니다.
9. 관리자 기능을 확인해야 한다면 DB에서 해당 사용자의 `is_admin` 값을 `true`로 바꿉니다.

## 8. 앞으로 진행해야 할 일

우선순위는 테스트 안정화, 데이터 관리, 배포 준비 순서입니다.

| 우선순위 | 작업 | 설명 |
| --- | --- | --- |
| 1 | 자동화 테스트 추가 | `MissionService`, `LocationValidationService`, `GeminiAiService`의 핵심 분기부터 테스트합니다. |
| 1 | 설정 키 정리 | 모든 외부 키 이름을 백엔드/프론트/문서에서 같은 명명 규칙으로 유지합니다. |
| 1 | DB 마이그레이션 도입 | JPA `update` 대신 Flyway/Liquibase로 스키마 변경 이력을 관리합니다. |
| 2 | Vision 인증 고도화 | `LABEL_DETECTION`만으로 부족한 간판/문구 미션을 위해 OCR 병행을 검토합니다. |
| 2 | 관리자 계정 관리 | DB 직접 수정 대신 관리자 승격/해제 API 또는 초기 관리자 시드 절차를 만듭니다. |
| 2 | API 에러 응답 표준화 | 문자열 응답과 JSON 응답이 섞여 있어 프론트 처리 규칙을 통일해야 합니다. |
| 2 | 중복 Vision endpoint 정리 | `/api/v1/sessions/{missionId}/vision`과 `/api/v1/missions/{missionId}/vision` 중 하나로 표준화합니다. |
| 3 | 실제 야외 QA | GPS 오차, 카메라 권한, 모바일 브라우저, Tmap 실패 시 UX를 현장에서 검증합니다. |
| 3 | 배포 파이프라인 | EC2/RDS 또는 대체 인프라에 맞춰 프로필, CORS, 비밀값 주입 방식을 정리합니다. |
| 3 | 접근성/반응형 점검 | 작은 모바일 화면에서 버튼, 텍스트, 지도 오버레이가 겹치지 않는지 확인합니다. |

## 9. 개발 규칙

- 비밀값은 절대 Git에 커밋하지 않습니다. `application-local.properties`, `.env`는 개인 로컬 전용입니다.
- 새 API를 추가하면 README의 주요 API 표와 관련 프론트 호출부 주석을 함께 업데이트합니다.
- AI 프롬프트를 수정할 때는 정답 키워드가 미리 노출되지 않는지 반드시 확인합니다.
- 지도/위치 계산 로직을 바꿀 때는 프론트 거리 계산과 백엔드 도착 검증 기준이 함께 맞는지 확인합니다.
- 단순 UI 문구 변경이 아니라 게임 진행 상태에 영향을 주는 변경은 `GameSession.status`와 화면 분기를 같이 점검합니다.
