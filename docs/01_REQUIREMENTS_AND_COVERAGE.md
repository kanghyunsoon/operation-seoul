# 요구사항 정의서 및 구현 충족도

작성일: 2026-06-25

## 1. 프로젝트 요구사항

Operation KOREA는 실제 장소 기반 야외 방탈출/미션 파일 서비스다. 사용자는 권역과 에피소드를 선택하고, 지도 기반 장소 방문, 퍼즐 풀이, 단서 해금, 최종 추리를 통해 사건을 해결한다. 관리자는 외부 장소 API와 생성형 AI를 활용해 에피소드 초안을 만들고 검수 후 공개한다.

## 2. 이미지 요구사항 기준 기능 목록

| 번호 | 분류 | 요구사항 | 우선순위 | 현재 구현 | 구현률 | 근거 |
| --- | --- | --- | --- | --- | ---: | --- |
| F01 | 콘텐츠 | 콘텐츠 등록 | 필수 | 관리자 에피소드/장소/퍼즐/증거 등록 구조 구현 | 90% | `AdminEpisodeController`, `AdminEpisodesView.vue` |
| F02 | 콘텐츠 | 콘텐츠 조회 | 필수 | 공개 에피소드 목록/상세/지도/미션 파일 조회 구현 | 95% | `EpisodePlayController`, `EpisodeListView.vue` |
| F03 | 콘텐츠 | 콘텐츠 수정 | 필수 | 관리자 에피소드 및 하위 자료 수정 구현 | 90% | `AdminEpisodeService`, update API |
| F04 | 콘텐츠 | 콘텐츠 삭제 | 필수 | 일부 soft delete/상태 관리 중심. 에피소드 완전 삭제 UX는 제한적 | 70% | 관리자 API 일부 구현 |
| F05 | 콘텐츠 | 콘텐츠 검색/정렬 | 필수 | 관리자 목록 검색/상태 필터, 사용자 권역 필터 구현. 일반 사용자 검색 UI는 약함 | 70% | `areaCode`, admin params |
| F06 | 리뷰 | 리뷰 작성 | 필수 | 클리어 사용자 기준 에피소드 리뷰 작성 구현 | 90% | `EpisodeReviewController` |
| F07 | 리뷰 | 리뷰 조회 | 필수 | 에피소드 리뷰 목록, 관리자 리뷰 목록 구현 | 95% | `EpisodeReviewPanel.vue`, `AdminReviewsView.vue` |
| F08 | 리뷰 | 리뷰 수정 | 필수 | 사용자 리뷰 수정 구현 | 90% | `EpisodeReviewService` |
| F09 | 리뷰 | 리뷰 삭제 | 필수 | 사용자 삭제, 관리자 숨김/삭제 구현 | 90% | `AdminEpisodeReviewController` |
| F10 | 회원 | 회원 등록 | 필수 | 이메일/비밀번호 회원가입 구현 | 95% | `AuthController`, `IntroView.vue` |
| F11 | 회원 | 회원 조회 | 필수 | 내 정보, 관리자 회원 조회 구현 | 90% | `UserController`, `AdminUserController` |
| F12 | 회원 | 회원 수정 | 필수 | 내 프로필/비밀번호, 관리자 수정 구현 | 90% | `UserService`, `AdminUserService` |
| F13 | 회원 | 회원 삭제 | 필수 | 탈퇴/관리자 soft delete 구현 | 85% | user status 처리 |
| F14 | 회원 | 로그인/로그아웃 | 필수 | JWT 로그인/프론트 세션 로그아웃 구현 | 95% | `JwtAuthenticationFilter`, `sessionStore` |
| F15 | 콘텐츠 | 찜/즐겨찾기 | 추가 | 에피소드 관심 목록 구현 | 95% | `EpisodeFavoriteController`, `FavoriteEpisodesView.vue` |
| F16 | 회원 | 팔로우/팔로잉 | 추가 | 팔로우/언팔로우, 팔로워/팔로잉 조회 구현 | 85% | `UserFollowController`, `MyPageView.vue` |
| F17 | 계획 관리 | 계획/일정 관리 | 선택 | 개인 일정 CRUD 구현. 팀 일정/알림은 미구현 | 75% | `UserPlanController`, `PlansView.vue` |
| F18 | 챌린지 | 챌린지 관리 | 선택 | 챌린지 목록/참여/진행률 구현. 관리자 운영 UI는 약함 | 70% | `ChallengeController`, `ChallengesView.vue` |
| F19 | AI | AI 추천 | 필수 | 플레이/관심 기반 규칙 추천 구현. 필수 기능으로 승격되어 생성형 AI 기반 추천 설명 고도화 필요 | 70% | `EpisodeRecommendationService`, `RecommendationsView.vue` |
| F20 | AI | AI 코칭/분석 | 필수 | 플레이 기록 기반 규칙 코칭 구현. 필수 기능으로 승격되어 AI 분석 근거와 응답 품질 보강 필요 | 65% | `CoachingService`, `CoachingView.vue` |

## 3. 전체 구현률

| 영역 | 평가 |
| --- | ---: |
| 필수 기능 평균 | 84% |
| 추가 기능 평균 | 85% |
| 선택/심화 기능 평균 | 72% |
| 화면 완성도 | 75% |
| 운영/검증 완성도 | 70% |
| 전체 추정 구현률 | 80% |

## 4. 실 서비스 수준 충족 여부

현재 상태는 “Final 프로젝트 제출 및 시연 가능한 MVP+” 수준이다. 핵심 REST API, Vue SPA, MySQL/MyBatis DB 구조, JWT 인증, 관리자 에피소드 운영, 외부 API/AI 활용 구조는 갖춰져 있다.

다만 실 서비스 수준으로 보려면 다음이 더 필요하다.

| 보완 영역 | 현재 한계 | 필요 조치 |
| --- | --- | --- |
| UI 완성도 | 에피소드 목록 하단 네비/페이지네이션/AI 버튼 간격이 화면 크기에 민감함 | 모바일/데스크톱 기준 디자인 토큰화, 실제 기기별 캡처 검증 |
| 검색/정렬 | 사용자용 검색/정렬이 약함 | 제목/권역/난이도/장르 검색 필터 UI 추가 |
| 삭제 정책 | 콘텐츠 완전 삭제/비공개/복구 정책이 혼재 | 관리자 상태 전환 정책 문서화 및 UI 통합 |
| 추천/코칭 | 필수 기능으로 변경되었으나 현재는 대부분 규칙 기반 | Gemini 기반 추천 이유 생성, 코칭 문장 생성, 실패 시 규칙 기반 fallback 구성 |
| 현장 검증 | 실제 GPS/장소 접근성 검증 미완료 | 현장 테스트 체크리스트와 좌표 보정 |
| 테스트 | 백엔드 단위 테스트 중심, 프론트 자동 테스트 부족 | 주요 화면 E2E 또는 최소 라우팅 smoke test 추가 |

## 5. 제출 시 표현 주의

- “AI 추천/코칭”은 요구사항상 필수 기능으로 업데이트되었으므로 현재 규칙 기반 MVP에서 생성형 AI 응답을 포함하는 구조로 보강해야 한다.
- Gemini는 관리자 에피소드 초안 생성/검증 쪽 활용도가 높다.
- 실제 서비스 운영 전에는 API 키, 도메인 등록, GPS 현장 테스트가 필요하다.
- 프로젝트 목표인 Spring Boot REST API + Vue SPA + MySQL/MyBatis 연동은 충족한다.

## 6. AI 추천/코칭 필수화에 따른 추가 구현 필요사항

| 기능 | 현재 | 필수 기능 수준으로 보완할 내용 | 권장 수정 위치 |
| --- | --- | --- | --- |
| AI 추천 | 사용자 관심/클리어/일정 기반 규칙 추천 | Gemini를 통해 추천 이유, 다음 플레이 전략, 권역/난이도 근거를 자연어로 생성. 실패 시 기존 규칙 추천으로 fallback | `EpisodeRecommendationService`, `EpisodeRecommendationController`, `RecommendationsView.vue` |
| AI 코칭 | 기록 기반 규칙 코칭 | 클리어 시간, 실패 횟수, 선호 장르, 단서 수집 패턴을 AI 입력으로 구성해 개인화 코칭 문장 생성. 정량 지표와 AI 문장을 함께 표시 | `CoachingService`, `CoachingController`, `CoachingView.vue` |
| 응답 UX | 일반 목록 화면 이동 | 응답 대기 시간을 줄이기 위한 비동기 타자기/버퍼 표시 | `useTypingBuffer.js`, 추천/코칭 화면 |
| 품질/안전 | 일부 규칙 기반 제한 | 정답 키워드 유출, 장소 정보 오염, 과도한 hallucination 방지 guardrail 적용 | Gemini prompt builder, validator |
