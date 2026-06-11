# Operation KOREA Requirements Audit

Last updated: 2026-06-08
Audit basis: 실제 Controller, Service, Repository, DTO, Vue route, API module, migration 파일 기준

## 1. 필수 기술 스택

| 요구사항 | 실제 위치 | 판정 |
| --- | --- | --- |
| Vue 3 | `frontend/package.json`, `src/main.js` | 충족 |
| Pinia | `frontend/src/stores/sessionStore.js` | 충족 |
| Vue Router | `frontend/src/router/index.js` | 충족 |
| Axios | `frontend/src/api/axiosInstance.js` | 충족 |
| Spring Boot | `backend/build.gradle`, `OperationSeoulApplication.java` | 충족 |
| Spring Security | `SecurityConfig`, JWT filter | 충족 |
| JWT | `JwtTokenProvider`, `JwtAuthenticationFilter` | 충족 |
| MyBatis/MySQL | repository SQL, migration, MySQL connector | 충족 |
| OAuth | 관련 Controller/Service 없음 | 미구현 |

## 2. 인증/회원 요구사항

| 요구사항 | 실제 상태 | 판정 |
| --- | --- | --- |
| 회원가입 | `POST /api/v1/auth/register` | 완료 |
| 로그인 | `POST /api/v1/auth/login` | 완료 |
| 중복 이메일/닉네임 검증 | `AuthService` | 완료 |
| BCrypt 암호화 | `AuthService`, `UserService` | 완료 |
| JWT 발급 | `JwtTokenProvider` | 완료 |
| Bearer 인증 처리 | `JwtAuthenticationFilter` | 완료 |
| 현재 사용자 조회 | `GET /api/v1/users/me` | 완료 |
| 내 정보 수정 | `PUT /api/v1/users/me` | 완료 |
| 비밀번호 변경 | `PUT /api/v1/users/me/password` | 완료 |
| 회원 탈퇴 soft delete | `DELETE /api/v1/users/me` | 완료 |
| ACTIVE 아닌 사용자 로그인 차단 | `AuthService` | 완료 |
| ACTIVE 아닌 사용자 보호 API 차단 | `JwtAuthenticationFilter`, `CurrentUserResolver` | 완료 |
| 관리자 회원 목록/상세/수정/삭제 | `AdminUserController`, `AdminUsersView.vue` | 완료 |
| 관리자 수정 허용 필드 제한 | `AdminUserUpdateRequest`, `AdminUserService` | 완료 |
| 소셜 로그인 | 버튼/API 없음 | 미구현 |

## 3. 미션 파일 플레이 요구사항

| 요구사항 | 실제 상태 | 판정 |
| --- | --- | --- |
| episode DB | `EpisodeSchemaMigration` | 완료 |
| mission_spot DB | `EpisodeSchemaMigration` | 완료 |
| puzzle/puzzle_hint DB | `EpisodeSchemaMigration` | 완료 |
| user_episode_progress DB | `EpisodeSchemaMigration` | 완료 |
| final_deduction DB | `EpisodeSchemaMigration` | 완료 |
| case_suspects/case_evidences DB | `EpisodeSchemaMigration` | 완료 |
| EP.01 seed | `EpisodeSchemaMigration` | 완료 |
| 에피소드 목록/상세 | `EpisodePlayController`, `EpisodeListView`, `EpisodeDetailView` | 완료 |
| 브리핑 | `EpisodeBriefingView` | 완료 |
| 지도 전체 장소 표시 | `EpisodeMapView` | 완료 |
| 최종 장소 은닉 | `SpotMarkerResponse`, `EpisodePlayService` | 완료 |
| 도착 판정 | `/spots/{spotId}/arrive` | 완료 |
| devMode 운영 분리 | backend/frontend env 조건 | 완료 |
| 퍼즐 제출 | `/puzzles/{puzzleId}/submit` | 완료 |
| reward_payload 해석 | `EpisodePlayService.applyReward` | 완료 |
| 단서 보드 | `/clue-board`, `ClueBoard.vue` | 완료 |
| 미션 파일 탭 | `/case-file`, `EpisodeCaseFileView.vue` | 완료 |
| 최종 추리 | `/deduction/start`, `/deduction/{sessionId}/ask` | MVP 완료 |
| 최종 정답 제출 | `/final-answer` | 완료 |
| 클리어 리포트 | `/clear-report` | 완료 |

## 4. 리뷰 요구사항

| 요구사항 | 실제 상태 | 판정 |
| --- | --- | --- |
| 리뷰 작성 | `POST /api/v1/episodes/{episodeId}/reviews` | 완료 |
| 리뷰 목록 | `GET /api/v1/episodes/{episodeId}/reviews` | 완료 |
| 내 리뷰 목록 | `GET /api/v1/users/me/reviews` | 완료 |
| 수정/삭제 | `PUT/DELETE /api/v1/reviews/{reviewId}` | 완료 |
| CLEARED 기준 제한 | `EpisodeReviewService.requireCleared` | 완료 |
| 1인 1에피소드 1리뷰 | `findByEpisodeIdAndUserId` 검사 | 완료 |
| 작성자/관리자 권한 | `requireOwnerOrAdmin` | 완료 |
| 관리자 숨김/복구/삭제 | `AdminEpisodeReviewController` | 완료 |
| spoiler | `EpisodeReviewRequest/Response`, UI | 완료 |

## 5. 관리자 생성/운영 요구사항

| 요구사항 | 실제 상태 | 판정 |
| --- | --- | --- |
| 관리자 에피소드 목록/상세 | `AdminEpisodeController`, `AdminEpisodesView` | 완료 |
| TourAPI 기준 장소 후보 | `/place-candidates`, `TourApiService` | 완료 |
| TourAPI 키 없음 명시 에러 | `TOURAPI_SERVICE_KEY_MISSING` | 완료 |
| Kakao Local 주변 후보 | `/place-candidates/nearby`, `KakaoLocalCandidateService` | 완료 |
| Kakao 키 없음 명시 에러 | `KAKAO_REST_API_KEY_MISSING` | 완료 |
| 수동 후보 추가 | `AdminEpisodesView.vue` | 완료 |
| 규칙 기반 초안 | `/ai-draft` | 완료 |
| Gemini 초안 구조 | `/ai-draft/gemini` | 부분: 키/품질 운영 QA 필요 |
| 초안 검증 | `/ai-draft/validate` | 완료 |
| DRAFT 저장 | `/ai-draft/save` | 완료 |
| readiness | `/{episodeId}/publish-readiness` | 완료 |
| PUBLISHED 전환 | `PUT /admin/episodes/{episodeId}` | 완료 |
| 장소/퍼즐/힌트/reward_payload 수정 | admin episode update APIs | 완료 |
| 용의자/증거/리워드 placeholder 수정 | admin episode update APIs | 완료 |

## 6. 확장 기능 감사

| 요구사항 | 실제 상태 | 판정 |
| --- | --- | --- |
| 에피소드 찜/즐겨찾기 | `episode_favorites`, favorite API, `MyPageView` | 완료 |
| 일정 관리 | `UserPlanController`, `PlansView.vue` | MVP 완료 |
| 팔로우 | `UserFollowController`, `MyPageView.vue` | MVP 완료 |
| 그룹 | `UserGroupController`, `GroupsView.vue` | MVP 완료 |
| 랭킹 | `RankingController`, `RankingView.vue` | MVP 완료 |
| 챌린지 | `ChallengeController`, `ChallengesView.vue` | MVP 완료 |
| 사용자 AI 추천 | `EpisodeRecommendationController`, `RecommendationsView.vue` | MVP 완료. 현재는 규칙 기반 추천 |
| AI 코칭/분석 | `CoachingController`, `CoachingView.vue` | MVP 완료. 현재는 플레이 기록 기반 규칙 코칭 |
| 실제 제휴 쿠폰/상품권 지급 | placeholder만 있음 | 미구현 |
| 다국어/무장애/두루누비/기상청 API | 설계 문서 수준 | 미구현 |

## 7. 주의할 점

- `region_review`, `region_favorite` 등 legacy region 기능은 존재하지만 새 episode MVP의 리뷰/찜 요구사항과 동일하지 않습니다.
- 사용자 플레이 MVP의 중심은 `/episodes` 하위 플로우입니다.
- `/home`, `/regions`, `/map`, `/chat`, `/clear`는 legacy 관광/미션 화면으로 유지됩니다.
- `schema.sql`에는 legacy 테이블이 있고, episode MVP 테이블은 `EpisodeSchemaMigration`에서 생성됩니다.
