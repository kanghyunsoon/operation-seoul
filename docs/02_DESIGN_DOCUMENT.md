# 설계 문서

## 1. 시스템 아키텍처

```mermaid
flowchart LR
    Guest[비회원/사용자/관리자] --> Browser[모바일/데스크톱 브라우저]
    Browser --> Vue[Vue 3 Front-End]
    Vue --> Router[Vue Router + Pinia Session]
    Vue --> Axios[Axios API Client]
    Axios --> Spring[Spring Boot REST API]
    Spring --> Security[Spring Security + JWT]
    Spring --> Service[Domain Services]
    Service --> MyBatis[MyBatis Repository]
    MyBatis --> MySQL[(MySQL)]
    Service --> TourAPI[TourAPI]
    Service --> KakaoLocal[Kakao Local API]
    Service --> GoogleVision[Google Vision API]
    Vue --> KakaoMap[Kakao Maps JS]
    Vue --> Tmap[TMAP Pedestrian Route]
```

## 2. 백엔드 구조

| 계층 | 역할 | 대표 구성 |
| --- | --- | --- |
| Controller | REST API 엔드포인트, 인증 사용자 주입 | `AuthController`, `EpisodePlayController`, `AdminEpisodeController` |
| Service | 비즈니스 규칙, 상태 전이, 외부 API 연동 | `EpisodePlayService`, `AdminEpisodeService`, `OAuthService` |
| Repository | MyBatis SQL 접근 | `*Repository` |
| Domain | DB 테이블과 매핑되는 객체 | `Episode`, `CaseEvidence`, `User` |
| DTO | 요청/응답 모델 | `*Request`, `*Response` |
| Config | 보안, CORS, 마이그레이션, 운영 검증 | `SecurityConfig`, `EpisodeSchemaMigration` |

## 3. 프론트엔드 구조

| 영역 | 역할 | 대표 파일 |
| --- | --- | --- |
| `views` | 라우트 단위 화면 | `EpisodeMapView.vue`, `AdminEpisodesView.vue` |
| `components` | 재사용 UI | `PuzzleCard.vue`, `CaseFileTabMenu.vue` |
| `components/episode/minigames` | 미니게임 UI | `MemoryCardGame.vue`, `ColorStroopMiniGame.vue` |
| `api` | Axios API 래퍼 | `episodeApi.js`, `adminEpisodeApi.js` |
| `stores` | 세션 상태 | `sessionStore.js` |
| `router` | 인증/관리자 라우팅 | `router/index.js` |
| `styles` | 공통 디자인 시스템 | `operation-korea-design-system.css` |

## 4. Use-Case Diagram

```mermaid
flowchart TB
    subgraph System["Operation KOREA System"]
        UC_Register(("회원가입"))
        UC_Login(("일반 로그인"))
        UC_OAuth(("Google/Kakao 로그인"))
        UC_Profile(("프로필 관리"))
        UC_Search(("에피소드 검색/필터"))
        UC_Select(("에피소드 선택"))
        UC_Briefing(("사건 브리핑 확인"))
        UC_Map(("지도에서 장소 탐색"))
        UC_Arrive(("현장 도착 판정"))
        UC_Puzzle(("퍼즐/미니게임 풀이"))
        UC_Reward(("단서/증거 해금"))
        UC_CaseFile(("미션 파일 확인"))
        UC_Deduction(("최종 추리 진행"))
        UC_Clear(("클리어 리포트 확인"))
        UC_Review(("리뷰 작성"))
        UC_Community(("커뮤니티 이용"))
        UC_Ranking(("랭킹/챌린지 확인"))
        UC_AdminUser(("회원 관리"))
        UC_AdminReview(("리뷰 관리"))
        UC_AdminCandidate(("장소 후보 조회"))
        UC_AdminEpisode(("에피소드 검수/공개"))
        UC_Readiness(("공개 준비도 검증"))
    end

    Guest((비회원)) --> UC_Register
    Guest --> UC_Login
    Guest --> UC_OAuth

    Player((일반 사용자)) --> UC_Profile
    Player --> UC_Search
    Player --> UC_Select
    Player --> UC_Briefing
    Player --> UC_Map
    Player --> UC_CaseFile
    Player --> UC_Deduction
    Player --> UC_Clear
    Player --> UC_Review
    Player --> UC_Community
    Player --> UC_Ranking

    Admin((관리자)) --> UC_AdminUser
    Admin --> UC_AdminReview
    Admin --> UC_AdminCandidate
    Admin --> UC_AdminEpisode

    UC_Select -. include .-> UC_Briefing
    UC_Map -. include .-> UC_Arrive
    UC_Arrive -. include .-> UC_Puzzle
    UC_Puzzle -. include .-> UC_Reward
    UC_Reward -. include .-> UC_CaseFile
    UC_Deduction -. extend .-> UC_Clear
    UC_AdminEpisode -. include .-> UC_Readiness
```

## 5. Use-Case 명세

| Use Case | Actor | 선행 조건 | 기본 흐름 | 예외/대안 |
| --- | --- | --- | --- | --- |
| 로그인 | 비회원 | 계정 또는 OAuth 계정 존재 | 인증 정보 입력 → JWT 발급 → 권역 지도 이동 | 비활성/오류 계정은 실패 메시지 |
| 에피소드 플레이 | 사용자 | 로그인, 공개 에피소드 존재 | 목록 선택 → 브리핑 → 지도 → 도착 → 퍼즐 → 단서 해금 | GPS 실패 시 개발 모드 또는 오류 안내 |
| 최종 추리 | 사용자 | 조사 미션 완료 | 최종 장소 도착 → 질문/가설 → 최종 정답 제출 | 오답 시 시간 패널티 |
| 에피소드 공개 | 관리자 | DRAFT 존재 | publish readiness 확인 → 공개 전환 | 필수 단서/증거 누락 시 차단 |

## 6. 상세 ER Diagram

```mermaid
erDiagram
    users {
        bigint id PK
        varchar email UK
        varchar password
        varchar nickname
        boolean is_admin
        varchar role
        mediumtext profile_image_url
        varchar status
        datetime created_at
    }

    user_social_accounts {
        bigint id PK
        bigint user_id FK
        varchar provider
        varchar provider_user_id
        datetime created_at
    }

    region {
        bigint id PK
        varchar name
        varchar area_code
        text description
        varchar period_code
        varchar theme_code
    }

    episodes {
        bigint id PK
        varchar title UK
        varchar subtitle
        bigint region_id FK
        varchar era
        varchar genre
        varchar difficulty
        varchar final_answer_type
        varchar final_answer
        text final_question
        varchar status
    }

    mission_spots {
        bigint id PK
        bigint episode_id FK
        varchar place_name
        double latitude
        double longitude
        int route_order
        varchar marker_type
        double radius_meters
        boolean final_place
    }

    puzzles {
        bigint id PK
        bigint episode_id FK
        bigint spot_id FK
        varchar puzzle_type
        text question_text
        varchar answer_format
        varchar answer
        text reward_clue
        text reward_payload
    }

    puzzle_hints {
        bigint id PK
        bigint puzzle_id FK
        int hint_level
        text hint_text
    }

    user_episode_progress {
        bigint id PK
        bigint user_id FK
        bigint episode_id FK
        varchar status
        text visited_spot_ids
        text completed_spot_ids
        text unlocked_clues_json
        text unlocked_evidence_ids
        int active_elapsed_seconds
        int clear_time_penalty_seconds
    }

    case_suspects {
        bigint id PK
        bigint episode_id FK
        varchar display_name
        varchar alias
        varchar portrait_image_url
        text image_prompt
        text suspicious_point
        boolean unlocked_by_default
    }

    case_evidences {
        bigint id PK
        bigint episode_id FK
        bigint source_spot_id FK
        bigint related_suspect_id FK
        varchar title
        varchar type
        varchar image_url
        text image_prompt
        text text_summary
        boolean unlocked_by_default
    }

    final_deduction_sessions {
        bigint id PK
        bigint user_id FK
        bigint episode_id FK
        varchar status
        int question_count
        int hypothesis_count
    }

    final_deduction_questions {
        bigint id PK
        bigint session_id FK
        text question
        text answer
        datetime created_at
    }

    episode_reviews {
        bigint id PK
        bigint episode_id FK
        bigint user_id FK
        int rating
        int difficulty_rating
        text content
        varchar status
    }

    region_question {
        bigint id PK
        bigint region_id FK
        bigint user_id FK
        varchar title
        text content
        boolean is_notice
    }

    region_answer {
        bigint id PK
        bigint question_id FK
        bigint user_id FK
        text content
    }

    challenges {
        bigint id PK
        varchar title
        varchar target_type
        int target_count
        varchar status
    }

    user_challenge_entries {
        bigint challenge_id PK
        bigint user_id PK
        varchar status
        datetime joined_at
    }

    users ||--o{ user_social_accounts : has
    region ||--o{ episodes : contains
    episodes ||--o{ mission_spots : has
    episodes ||--o{ puzzles : has
    mission_spots ||--o{ puzzles : opens
    puzzles ||--o{ puzzle_hints : has
    users ||--o{ user_episode_progress : plays
    episodes ||--o{ user_episode_progress : records
    episodes ||--o{ case_suspects : has
    episodes ||--o{ case_evidences : has
    mission_spots ||--o{ case_evidences : sources
    case_suspects ||--o{ case_evidences : relates
    users ||--o{ final_deduction_sessions : starts
    episodes ||--o{ final_deduction_sessions : has
    final_deduction_sessions ||--o{ final_deduction_questions : logs
    users ||--o{ episode_reviews : writes
    episodes ||--o{ episode_reviews : receives
    region ||--o{ region_question : has
    users ||--o{ region_question : writes
    region_question ||--o{ region_answer : has
    users ||--o{ region_answer : writes
    challenges ||--o{ user_challenge_entries : includes
    users ||--o{ user_challenge_entries : joins
```

## 7. 핵심 상태 전이

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> PUBLISHED: publish readiness 통과
    PUBLISHED --> PLAYING: 사용자 시작
    PLAYING --> INVESTIGATING: 장소 도착/퍼즐 진행
    INVESTIGATING --> DEDUCTION_READY: 조사 미션 완료
    DEDUCTION_READY --> DEDUCING: 최종 장소 도착
    DEDUCING --> CLEARED: 최종 정답 정답
    DEDUCING --> DEDUCING: 오답/패널티
```

## 8. 핵심 설계 결정

- 사용자 지도 API에는 실제 최종 장소 여부를 직접 노출하지 않고 `publicMarkerType`만 제공한다.
- 퍼즐 보상은 `reward_payload` JSON으로 단서, 증거, 용의자 해금을 유연하게 처리한다.
- OAuth와 외부 API 키는 운영 환경에서 환경변수로 주입한다.
- 화면은 모바일 우선으로 설계하고, 지도·팝업·미니게임 상호작용을 중심으로 구성한다.
