# 🕵️‍♂️ Operation: SEOUL - AI 기반 몰입형 야외 방탈출

![Vue.js](https://img.shields.io/badge/Vue.js-35495E?style=for-the-badge&logo=vue.js&logoColor=4FC08D)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Google Gemini](https://img.shields.io/badge/Google_Gemini-8E75B2?style=for-the-badge&logo=google&logoColor=white)
![Kakao Map API](https://img.shields.io/badge/Kakao_Map-FFCD00?style=for-the-badge&logo=kakao&logoColor=black)

> "도심 속 명소가 거대한 방탈출 무대가 된다."
> 한국관광공사 TourAPI와 KAKAO mapsAPI, Google AI(Vision, LLM)를 결합한 위치 기반(LBS) 게이미피케이션 관광 활성화 서비스입니다.

<br>

## 0. 기획 배경 및 목적 (Background & Objectives)
1) 고물가 시대의 국내 여행 패러다임 전환 필요성
현재 높은 물가로 인해 내국인의 국내 여행 수요가 위축되고, 해외여행을 선호하는 현상이 심화되고 있습니다. 이에 지자체 및 숙박 업계와 연계해 '미션 클리어 시 지역 상품권 제공' 등의 보상을 연계하여 비용 저항감을 낮추고 국내 여행의 매력도를 높이고자 합니다.

2) 게이미피케이션을 통한 능동적 관광 및 건강 증진
단순히 눈으로 보는 수동적인 관광을 넘어, 사용자가 비밀 요원이 되어 명소에 숨겨진 단서를 찾고 걷게 만드는 '첩보 액티비티'를 제공합니다. 이를 통해 역사적 지식 습득은 물론 자연스러운 도보 이동을 유도하여 국민 건강 증진에 기여합니다.

<br>

## 📌 1. 시스템 아키텍처 (System Architecture)

본 프로젝트는 실시간 데이터 처리와 외부 지능형 API 연동을 위한 비동기 스트리밍 구조를 채택하고 있습니다.

```mermaid
graph TD
    subgraph "Client Side (Vue.js 3 / PWA 지향)"
        UI[UI/UX Engine]
        TB[Typing Buffer System\n0.05s Delay]
        HW[Hardware Control\nGPS & Camera]
    end

    subgraph "Server Side (Spring Boot 4)"
        API[REST API Controller]
        SSE[SSE Stream Handler\nResponseBodyEmitter]
        MF[Mission Factory\nPrompt Engineering]
        SEC[Spring Security & JWT]
    end

    subgraph "Database Layer"
        RDB[(MySQL 8.0)]
        SESSION[(GameSession Table\nMission Progress)]
    end

    subgraph "External Intelligence & Data"
        MAP[Kakao Maps API\nDynamic Routing]
        TOUR[TourAPI 4.0\nPOI Seed Data]
        VISION[Google Cloud Vision\nOCR/Object Detection]
        GEMINI[Google Gemini API\nStory & Hint Generation]
    end

    UI <-->|HTTPS/REST| API
    SSE -.->|Server-Sent Events| UI
    HW -->|Lat/Lng & Image Blob| UI
    
    API <-->|JPA/Hibernate| RDB
    API <--> SEC
    API <-->|Read/Write Progress| SESSION
    
    UI -->|Map Rendering| MAP
    MF -->|Content Scrapping| TOUR
    API -->|Image Base64| VISION
    SSE <-->|Stream Prompt| GEMINI
```

### 💡 아키텍처 상세 설명
* **Client Side**: Vue 3 Composition API를 활용해 상태(Pinia)를 관리합니다. 
모바일 브라우저 환경에서 Geolocation API로 사용자 좌표를 추적하고, 프론트엔드 자체 타이핑 버퍼(Typing Buffer) 로직을 구현하여 네트워크 지연(Latency)에도 끊김 없는 AI 타자기 연출을 보장합니다.
* **Server Side**: Spring Boot 4를 기반으로 RESTful API를 제공합니다. 핵심인 SSE Stream Handler는 AI(Gemini)의 응답을 청크(Chunk) 단위로 쪼개어 클라이언트에 실시간으로 밀어내는(Push) 단방향 비동기 통신을 담당합니다.
* **External Integration**: TourAPI에서 수집한 명소(POI) 데이터를 Mission Factory 파이프라인에 통과시켜 Gemini 프롬프트로 주입, 매번 새로운 세계관의 미션 스토리를 자동 생성합니다.

<br>

## 🧩 2. 핵심 도메인 모델 (Core Domain Model)

서비스의 근간이 되는 RDB(MySQL) 설계입니다. 5대 핵심 엔티티 간의 관계를 통해 "유저가 특정 지역의 미션을 선택하여 세션을 생성하고, 채팅 기록을 남기는" 흐름을 정의합니다.

```mermaid
erDiagram
    USER ||--o{ GAME_SESSION : plays
    REGION ||--o{ MISSION : contains
    MISSION ||--o{ GAME_SESSION : tracks

    USER {
        Long id PK
        String email "UK (로그인 ID)"
        String password "BCrypt 해시"
        String nickname "요원 코드네임"
        Boolean isAdmin "관리자 여부"
    }
    
    REGION {
        Long id PK
        String name "ex: 서울 종로구"
        String description "지역 세계관 설명"
    }
    
    MISSION {
        Long id PK
        Long regionId FK
        String title "미션명"
        Text description "장소/미션 설명"
        Double targetLat "목적지 위도"
        Double targetLng "목적지 경도"
        Double radiusInMeters "도착 인정 반경"
        String visionKeyword "OCR 통과 키워드"
        Text clue "힌트 미션 클리어 후 공개 단서"
        String answerKeyword "최종 정답 키워드"
        Long chapterId "챕터 그룹 ID"
        Boolean isFinal "최종 목적지 여부"
        Text realStory "클리어 후 실제 역사 해설"
    }
    
    GAME_SESSION {
        Long id PK
        Long userId FK
        Long missionId FK
        String status "IN_PROGRESS / CLEARED 등"
        Text extractedLog "Vision 분석 로그"
    }
```

### 💡 도메인 상세 설명
* **Region과 Mission (1:N)**: 하나의 테마 지역 안에는 여러 개의 세부 스토리 미션이 존재합니다.
* **Mission과 GameSession (1:N)**: 미션 정보는 정적인 마스터 데이터이며, 유저가 미션을 시작할 때마다 고유한 GameSession 인스턴스가 생성되어 진행 상태(Status)를 추적합니다.
* **Mission의 힌트/최종 분리**: 일반 힌트 미션은 `visionKeyword`와 `clue`를 중심으로 동작하고, 최종 미션은 `answerKeyword`, `realStory`, `isFinal`을 통해 챗봇 추론과 클리어 해설을 담당합니다.
* **ChatLog 상태**: 현재 `ChatLog`는 클래스만 존재하고 JPA 엔티티로 영속화되지는 않습니다. 채팅 저장은 추후 점수/회고 기능과 함께 별도 구현이 필요합니다.

<br>

## 🏗 3. 프로젝트 계층 구조 (Layer Structure)

단일 책임 원칙(SRP)과 관심사 분리(SoC)를 철저히 지키며, 기능 확장이 용이하도록 도메인형 디렉토리 구조를 채택했습니다.

```text
operation-seoul
├── backend (Spring Boot 4 - Java 17)
│   ├── src/main/java/com/operation/seoul
│   │   ├── global (공통 예외 처리, 보안, 설정) 
│   │   ├── game (게임 코어 & AI 스트리밍 도메인)
│   │   └── location (지도 & 미션 공장 도메인) 
│   └── build.gradle
└── frontend (Vue.js 3 - Vite)
    ├── src
    │   ├── api (Axios 인스턴스) 
    │   ├── assets (폰트, 이미지, 스타일시트)
    │   ├── components (CameraScanner) 
    │   ├── composables (useTypingBuffer) 
    │   ├── stores (Pinia - sessionStore.js) 
    │   ├── views (Intro, Home, Briefing, Map, AiChat, Clear) 
    │   └── router (vue-router 설정)
    └── vite.config.js
```

<br>

## 🛠 4. 기술 스택 (Tech Stack)

프론트엔드와 백엔드 모두 엔터프라이즈 환경에서 검증된 최신 기술 스택을 도입하여 안정성과 유지보수성을 극대화했습니다.

| 구분 | 기술 스택 | 도입 목적 (Why) |
| :--- | :--- | :--- |
| **Frontend** | Vue 3 (Composition API) | 로직 재사용성(composables) 극대화 및 반응형 UI 구축 |
| | Pinia | 가볍고 직관적인 전역 상태 관리 (유저 세션, 토큰 보관) |
| | Axios & Vue Router | REST API 비동기 통신 및 네비게이션 가드를 통한 라우팅 보안 |
| **Backend** | Java 17 & Spring Boot 4.x | 최신 문법 활용 및 안정적인 서버 아키텍처 |
| | Spring Data JPA | 객체 지향적 데이터 접근 및 유지보수 용이성 |
| | Spring Security & JWT | Stateless 기반의 안전한 REST API 인증/인가 체계 구축 |
| **Database** | MySQL 8.0 (Aiven - 개발, AWS RDS-배포)[예정] | 공간 데이터(Spatial Data) 확장성과 트랜잭션 무결성 보장 |
| **AI Engine** | Google Gemini API (LLM) | 빠른 응답 속도(Latency)와 문맥 파악 능력을 통한 실시간 대화 연출 |
| | Google Cloud Vision (OCR) | 현장 구조물/텍스트를 판독하여 어뷰징을 방지하는 OCR 검증 |
| **Data & API**| 한국관광공사 TourAPI 4.0 | 공공데이터 기반 초기 지역/명소 데이터 시딩 |
| | Kakao Maps API | 한국 지형에 최적화된 지도 렌더링 및 동적 마커 표시 |

<br>

## ⚙️ 5. 로컬 환경 설정 (Local Setup)

민감정보가 포함되는 실제 설정 파일은 Git에 올리지 않습니다. 처음 실행하는 팀원은 예시 파일을 복사한 뒤 각자 발급받은 키와 로컬 DB 정보를 입력해야 합니다.

### Backend

```powershell
Copy-Item backend/src/main/resources/application-example.properties backend/src/main/resources/application.properties
```

`application.properties`에 아래 항목을 채웁니다.

| Key | 용도 |
| :--- | :--- |
| `spring.datasource.url` | MySQL 접속 URL |
| `spring.datasource.username` | MySQL 계정 |
| `spring.datasource.password` | MySQL 비밀번호 |
| `gemini.api.key` | Gemini API 키 |
| `google.vision.key` | Google Cloud Vision API 키 |
| `kakao.rest.api.key` | Kakao REST API 키 |
| `tmap.app.key` | Tmap API 키 |
| `jwt.secret` | JWT 서명용 비밀키. 최소 32자 이상의 임의 문자열 권장 |

```powershell
cd backend
java -jar gradle/wrapper/gradle-wrapper.jar build -x test
java -jar build/libs/seoul-0.0.1-SNAPSHOT.jar
```

### Frontend

```powershell
Copy-Item frontend/.env.example frontend/.env
```

`frontend/.env`에 아래 항목을 채웁니다.

| Key | 용도 |
| :--- | :--- |
| `VITE_API_BASE_URL` | 백엔드 API 기본 주소. 로컬 기본값은 `http://localhost:8080/api` |
| `VITE_KAKAO_MAP_KEY` | Kakao Map JavaScript 키 |
| `VITE_TMAP_APP_KEY` | Tmap 앱 키 |

```powershell
cd frontend
npm install
npm run dev
```

<br>

## 📱 6. 전체 앱 뷰(View) 흐름도

유저가 앱을 실행하여 미션을 클리어하기까지 겪게 되는 코어 사이클입니다.

### 1) 준비 단계 (작전 탐색 및 선택)
* **`IntroView`**: 시스템 접속 관문 (로그인/회원가입).
* **`HomeView`**: 작전 생성/선택 대시보드. 관리자 모드에서는 후보지 조회와 미션 생성을 수행합니다.

### 2) 게임 진행 단계 (코어 게임 사이클)
* **`BriefingView`**: 프롤로그. 임무 배경 스토리와 첫 목적지 힌트 하달.
* **`MapView`**: 실제 도심 지도를 보며 이동. 실시간 GPS 위치와 목적지 마커 표시.
* **`CameraScanner`**: 현장 도착 후 특정 구조물을 비추어 Vision API 기반 단서를 획득하는 공용 컴포넌트입니다.
* **`AiChatView`**: 획득한 단서로 본부 오퍼레이터(AI)와 채팅하며 최종 사건 키워드를 추론합니다.

### 3) 결과 단계 (작전 종료)
* **`ClearView`**: 최종 키워드 정답 시 실제 역사 해설과 수집 단서 해석을 제공하고, 확인 후 `HomeView`로 복귀합니다.

<br>

## 🔒 7. 기술적 해결 과제 (Key Highlights)

1. **[SSE 스트리밍]**: `ResponseBodyEmitter`를 활용하여 AI 텍스트 생성 완료 전부터 데이터를 전송, 체감 대기 시간을 혁신적으로 단축
2. **[타자기 버퍼]**: 프론트엔드 자체 버퍼 큐(Queue) 로직으로 네트워크 환경에 구애받지 않는 일관된 0.05초 타자기 연출 구현
3. **[하이브리드 인증 체계]**: 유저의 모바일 GPS 좌표(1차 검증)와 실시간 카메라 OCR 사진 인증(2차 검증)을 결합하여 위치 조작 어뷰징 원천 차단
4. **[Mission Factory 파이프라인]**: TourAPI의 건조한 장소 설명 데이터를 Gemini 프롬프트로 가공하여 흥미로운 추리 스토리를 가진 미션 데이터로 자동 변환
5. **[Human In the Loop]** : AI로 생성된 데이터를 인간 참여형 검증으로 보안, 수정하여 왜곡이 없도록 진행
6. **[프로젝트 확장]** : 외국인들도 유명한 장소만을 가는 것이 아닌 한국적인 명소들을 찾아볼 수 있도록 언어 선택 가능

### 🕵️‍♂️ [Step 2] 탐문 수사 및 지역 상생 동선 (Map & Camera)
1. **AI 지능형 경로 생성:** - Kakao Maps API를 통해 목적지 인근의 **평점 4.0 이상의 맛집, 로컬 카페, 테마 공원**을 실시간 필터링합니다.
   - 분석된 상권 데이터를 기반으로 AI가 힌트 마커를 배치하여, 유저가 게임을 즐기며 자연스럽게 지역 핫플레이스를 방문하도록 유도합니다.
2. **서브 퀘스트 수행:** - 지도상의 힌트 장소(추천 맛집/명소 앞)로 이동하여 주변 사물(간판, 특정 문구 등)을 촬영합니다.

<br>

## 🤝 8. 업무 분담 및 프로젝트 로드맵

도메인 지식을 깊이 있게 파악하기 위해 프론트/백엔드를 나누지 않고, **기능 단위로 기획부터 배포까지 전담하는 수직적(Vertical Slice) 분담 원칙**을 채택했습니다.

### 🌿 Git 협업 수칙 (GitHub Flow)
* `main`: 상시 배포 가능한 안정적인 버전을 유지합니다.
* `feature/도메인명`: 개별 기능 개발 브랜치 (예: `feature/auth`, `feature/ai-streaming`).
* PR(Pull Request) 시 충돌 방지를 위해 각자 맡은 도메인 영역 코드를 우선적으로 리뷰 및 병합합니다.

---

### 📍 [Phase 1] 근시점: 기반 공사 및 사용자 인증 (DB/로그인)
- [ ] [팀원 A] MySQL 8.0 인스턴스 세팅 및 도메인(User, Region 등) JPA 엔티티 매핑
- [ ] [팀원 A] Spring Security + JWT 기반 인증 로직 및 User CRUD API 개발
- [ ] [팀원 B] Vue 3 + Pinia를 활용한 전역 유저 상태(Session Store) 관리 세팅 완료
- [ ] [팀원 B] Axios 인터셉터(토큰 자동 전송) 및 Vue Router 네비게이션 가드 적용
- [ ] [팀원 B] `IntroView` (로그인/회원가입 UI) 개발 및 라우팅 연동

### 📍 [Phase 2] 중기: 본격적인 도메인 기능 개발 (수직 분담)
**팀원 A (Location & Mission Domain 담당)**
- [ ] [BE] 한국관광공사 TourAPI 연동 및 Region/Mission 데이터 시딩 로직 개발
- [ ] [FE] Kakao Maps API 연동 및 `MapMarker`를 활용한 동적 지도 뷰어 구현
- [ ] [FE] HTML5 Geolocation API(`useGeolocation`)를 활용한 실시간 위치 추적 기능 개발

**팀원 B (AI & Game Core Domain 담당)**
- [ ] [BE] Google Gemini API 연동 및 비동기 스트리밍(SSE) 서버 응답 로직 구축
- [ ] [BE] Google Cloud Vision API 연동 및 현장 이미지 판독(`VisionAiService`) 로직 작성
- [ ] [FE] SSE 데이터 수신 처리 및 프론트엔드 자체 타자기 버퍼(`useTypingBuffer`) 개발
- [ ] [FE] `CameraScanner` 컴포넌트 및 `AiChatView` UI/UX 최적화 구현

### 📍 [Phase 3] 후기: 기능 융합 및 트러블 슈팅
- [ ] [팀원 A] TourAPI 데이터를 Gemini로 가공하여 미션 스토리를 자동 양산하는 시스템 구축
- [ ] [팀원 B] 'GPS 좌표 도달 여부' + 'OCR 사진 인증' 하이브리드 검증 로직 완성
- [ ] [공통/팀원 B] 로그인 → 지역 선택 → 미션 시작(`GameSession` 생성) → 지도 이동 → 사진 인증 → 채팅 도출로 이어지는 전체 상태 머신 통합

### 📍 [Phase 4] 만료 시점: QA, 최적화 및 배포
- [ ] [공통] 실제 도심(야외 환경)에서 스마트폰 테스트 진행 (GPS 오차 및 OCR 인식률 점검)
- [ ] [팀원 A] DB 성능 최적화 (조회가 잦은 위경도 데이터 및 채팅 로그 대상 인덱싱 적용)
- [ ] [공통] 클라우드 인프라(AWS EC2, RDS 등)에 `main` 브랜치 최종 배포 및 런칭
