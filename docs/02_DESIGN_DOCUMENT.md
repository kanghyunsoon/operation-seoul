# 설계 문서

## 1. 시스템 아키텍처

```mermaid
flowchart LR
    User[사용자 모바일 브라우저] --> Vue[Vue 3 Front-End]
    Admin[관리자] --> Vue
    Vue --> Axios[Axios API Client]
    Axios --> Spring[Spring Boot REST API]
    Spring --> Security[Spring Security + JWT]
    Spring --> MyBatis[MyBatis Repository]
    MyBatis --> MySQL[(MySQL)]
    Spring --> TourAPI[TourAPI]
    Spring --> KakaoLocal[Kakao Local API]
    Vue --> KakaoMap[Kakao Maps JS]
    Vue --> Tmap[Tmap Navigation]
    Spring --> Gemini[Gemini API]
```

## 2. 백엔드 설계

| 계층 | 역할 | 대표 패키지 |
| --- | --- | --- |
| Controller | REST API 요청/응답 처리 | `auth`, `episode`, `community`, `admin` |
| Service | 비즈니스 규칙, 검증, 상태 전이 | `EpisodePlayService`, `AdminEpisodeService` |
| Repository | MyBatis SQL 접근 | `*Repository` |
| Domain | DB 엔티티 형태 객체 | `domain` |
| DTO | API 요청/응답 모델 | `dto` |
| Config | 보안, 마이그레이션, 필터 | `global.config` |

## 3. 프론트엔드 설계

| 영역 | 역할 |
| --- | --- |
| `views` | 라우트 단위 화면 |
| `components` | 재사용 UI와 미니게임 |
| `api` | Axios 기반 API 래퍼 |
| `stores` | Pinia 세션 상태 |
| `router` | 인증/관리자 접근 제어 |
| `constants` | 권역 지도/메타 데이터 |

## 4. Use-Case Diagram

```mermaid
flowchart TB
    Guest((비회원))
    Player((일반 사용자))
    Admin((관리자))

    Guest --> UC1[회원가입/로그인]
    Player --> UC2[에피소드 선택]
    Player --> UC3[지도에서 장소 이동]
    Player --> UC4[퍼즐 풀이]
    Player --> UC5[단서/증거 확인]
    Player --> UC6[최종 추리/정답 제출]
    Player --> UC7[리뷰/커뮤니티 작성]
    Player --> UC8[랭킹/챌린지/추천/코칭 확인]

    Admin --> UC9[회원 관리]
    Admin --> UC10[리뷰 관리]
    Admin --> UC11[장소 후보 조회]
    Admin --> UC12[AI 에피소드 초안 생성]
    Admin --> UC13[에피소드 검수/공개]
```

## 5. ER Diagram

```mermaid
erDiagram
    users ||--o{ user_episode_progress : plays
    users ||--o{ episode_reviews : writes
    users ||--o{ region_question : posts
    users ||--o{ region_answer : comments
    users ||--o{ episode_favorites : favorites
    users ||--o{ user_follow : follows
    episodes ||--o{ mission_spots : has
    episodes ||--o{ puzzles : has
    episodes ||--o{ user_episode_progress : records
    episodes ||--o{ episode_reviews : receives
    mission_spots ||--o{ puzzles : contains
    puzzles ||--o{ puzzle_hints : has
    episodes ||--o{ case_suspects : has
    episodes ||--o{ case_evidences : has
    episodes ||--o{ final_deduction_sessions : has
    final_deduction_sessions ||--o{ final_deduction_questions : logs
    region ||--o{ region_question : has
    region_question ||--o{ region_answer : has
    region_question ||--o{ region_question_like : liked
    challenges ||--o{ user_challenge_entries : joined
```

## 6. 핵심 설계 결정

- 사용자 지도 API에는 `is_final_place`를 노출하지 않고 `publicMarkerType`만 제공한다.
- 퍼즐 보상은 `reward_payload` JSON으로 단서/증거/용의자 해금을 유연하게 처리한다.
- 관리자 AI 생성 결과는 즉시 공개하지 않고 DRAFT 저장, 검증, publish readiness를 거친다.
- 외부 API 키는 properties 파일에 직접 저장하지 않고 환경변수로 주입한다.
- 커뮤니티 권역은 기본 seed를 보장해 비어 있는 DB에서도 게시글 작성이 가능하게 한다.

