# Operation KOREA

Operation KOREA는 Vue 3 + Pinia + Vue Router + Axios 프론트엔드와 Spring Boot + Spring Security + JWT + MyBatis + MySQL 백엔드 기반의 모바일 야외 방탈출/사건파일 앱입니다.

현재 방향은 단순 관광 미션 앱이 아니라, TourAPI/Kakao Local로 실제 장소 후보를 모으고 관리자가 검수한 뒤, 사용자가 지도와 사건파일을 오가며 퍼즐, 단서, 최종 추리를 진행하는 사건파일형 야외 방탈출 MVP입니다.

## 현재 MVP 결론

2026-06-05 실제 코드와 API 파일 기준으로, 샘플 또는 관리자 생성 에피소드 1개를 플레이하는 핵심 MVP는 구현되어 있습니다.

핵심 완료 흐름:

1. 로그인
2. 공개 에피소드 목록 조회
3. 사건 브리핑 확인
4. 지도 진입
5. 전체 조사 장소 표시
6. `publicMarkerType` 기반 마커 표시
7. Tmap 길찾기
8. GPS 또는 시연용 devMode 도착 판정
9. 장소 퍼즐 열기
10. 퍼즐 정답 제출
11. `reward_payload` 기반 단서/증거/용의자/메모 해금
12. 사건파일 탭 갱신
13. 실제 최종 장소 도착 판정
14. 바다거북 스프식 최종 추리
15. 최종 정답 제출
16. `CLEARED` 처리
17. 클리어 리포트와 실제 역사 해설 확인
18. `CLEARED` 기준 리뷰 작성

## 실제 코드 기준 구현 상태

| 영역 | 상태 | 실제 확인 기준 |
| --- | --- | --- |
| 인증/JWT/현재 사용자 | 완료 | `AuthController`, `AuthService`, `JwtAuthenticationFilter`, `CurrentUserResolver`, `UserController` |
| ACTIVE 아닌 사용자 로그인 차단 | 완료 | `AuthService`에서 `SUSPENDED`, `DELETED`, inactive 차단 |
| ACTIVE 아닌 사용자 보호 API 차단 | 완료 | JWT 필터는 active 사용자만 principal 설정, resolver도 active 검사 |
| 관리자 회원 관리 | 완료 | `AdminUserController`, `AdminUserService`, `AdminUsersView.vue` |
| 내 정보/비밀번호/탈퇴 | 완료 | `GET/PUT/DELETE /api/v1/users/me`, `PUT /api/v1/users/me/password` |
| 사건파일 플레이 DB | 완료 | `episodes`, `mission_spots`, `puzzles`, `puzzle_hints`, `user_episode_progress` migration |
| 최종 추리 DB | 완료 | `final_deduction_sessions`, `final_deduction_questions` migration |
| 사건 자료 DB | 완료 | `case_suspects`, `case_evidences`, `episode_partner_rewards` migration |
| EP.01 seed | 완료 | `EpisodeSchemaMigration` seed |
| 지도 전체 장소 표시 | 완료 | `GET /api/v1/episodes/{episodeId}/map`, `EpisodeMapView.vue` |
| 최종 장소 은닉 | 완료 | 사용자 map 응답은 `publicMarkerType`만 사용, 내부 `is_final_place` 미노출 |
| 도착 판정 | 완료 | `POST /api/v1/episodes/{episodeId}/spots/{spotId}/arrive` |
| devMode 도착 판정 분리 | 완료 | backend `app.dev-mode.arrival-enabled`, frontend `VITE_DEV_ARRIVAL` 조건 |
| 퍼즐 정답 제출 | 완료 | `POST /api/v1/puzzles/{puzzleId}/submit`, `PuzzleCard.vue` |
| reward_payload 해금 | 완료 | `EpisodePlayService.applyReward` |
| 단서 보드 | 완료 | `GET /api/v1/episodes/{episodeId}/clue-board`, `ClueBoard.vue` |
| 사건파일 탭 | 완료 | `GET /api/v1/episodes/{episodeId}/case-file`, `EpisodeCaseFileView.vue` |
| 최종 추리 | MVP 완료 | 규칙 기반 제한 답변. Gemini 실시간 추리 고도화는 미구현 |
| 최종 정답/CLEARED | 완료 | `POST /api/v1/episodes/{episodeId}/final-answer` |
| 클리어 리포트 | 완료 | `GET /api/v1/episodes/{episodeId}/clear-report`, `ClearReportView.vue` |
| 에피소드 리뷰 | 완료 | `EpisodeReviewController`, `AdminEpisodeReviewController`, `EpisodeReviewPanel.vue` |
| CLEARED 기준 리뷰 제한 | 완료 | `EpisodeReviewService.requireCleared` |
| 관리자 에피소드 생성 | 완료 | `AdminEpisodeController`, `AdminEpisodeService`, `AdminEpisodesView.vue` |
| TourAPI 기준 장소 후보 | 완료 | `TourApiService`, 키 없음 에러 `TOURAPI_SERVICE_KEY_MISSING` |
| Kakao Local 주변 후보 | 완료 | `KakaoLocalCandidateService`, 키 없음 에러 `KAKAO_REST_API_KEY_MISSING` |
| 수동 후보 추가 UX | 완료 | `AdminEpisodesView.vue` |
| readiness/PUBLISHED 검증 | 완료 | `/publish-readiness`, PUBLISHED 전환 검증 |
| 지역 리워드/쿠폰 | Placeholder | `episode_partner_rewards` PLANNED/DISABLED 구조만 있음. 실제 쿠폰 지급 없음 |
| OAuth 소셜 로그인 | 미구현 | 구글/네이버/카카오 로그인 버튼도 만들지 않음 |
| 에피소드 찜/즐겨찾기 | 완료 | episode_favorites API, 목록/상세 favorited, 내 관심 목록 화면 구현 |
| 일정 관리 | 미구현 | plans API/화면 없음 |
| 팔로우/그룹 | 미구현 | follows/groups API/화면 없음 |
| 챌린지/랭킹 | 미구현 | challenges/rankings API/화면 없음 |
| 사용자 AI 추천 | 미구현 | user recommendation API/화면 없음 |
| AI 코칭/분석 | 미구현 | play report 기반 coaching API/화면 없음 |

## 사용자 플레이 API

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/episodes` | 공개 에피소드 목록 |
| `GET` | `/api/v1/episodes/{episodeId}` | 에피소드 상세 |
| `POST` | `/api/v1/episodes/{episodeId}/favorite` | 관심 에피소드 추가 |
| `DELETE` | `/api/v1/episodes/{episodeId}/favorite` | 관심 에피소드 제거 |
| `GET` | `/api/v1/users/me/favorites` | 내 관심 에피소드 목록 |
| `POST` | `/api/v1/episodes/{episodeId}/start` | 시작/이어하기 |
| `GET` | `/api/v1/episodes/{episodeId}/map` | 전체 장소 지도 데이터. 내부 최종 장소 필드 미노출 |
| `POST` | `/api/v1/episodes/{episodeId}/spots/{spotId}/arrive` | GPS/devMode 도착 판정 |
| `GET` | `/api/v1/spots/{spotId}/puzzle` | 도착한 장소의 퍼즐 조회 |
| `POST` | `/api/v1/puzzles/{puzzleId}/submit` | 퍼즐 정답 제출 및 보상 적용 |
| `GET` | `/api/v1/episodes/{episodeId}/clue-board` | 단서 보드 조회 |
| `GET` | `/api/v1/episodes/{episodeId}/case-file` | 사건파일 자료 조회 |
| `POST` | `/api/v1/episodes/{episodeId}/deduction/start` | 최종 추리 세션 시작 |
| `POST` | `/api/v1/deduction/{sessionId}/ask` | 제한형 추리 질문 |
| `GET` | `/api/v1/deduction/{sessionId}/questions` | 추리 질문 기록 |
| `POST` | `/api/v1/episodes/{episodeId}/final-answer` | 최종 정답 제출 |
| `GET` | `/api/v1/episodes/{episodeId}/clear-report` | 클리어 리포트 |
| `GET` | `/api/v1/episodes/{episodeId}/reviews` | 리뷰 목록 및 작성 가능 여부 |
| `POST` | `/api/v1/episodes/{episodeId}/reviews` | 리뷰 작성 |
| `PUT` | `/api/v1/reviews/{reviewId}` | 리뷰 수정 |
| `DELETE` | `/api/v1/reviews/{reviewId}` | 리뷰 삭제 |

## 관리자 API

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/admin/users` | 회원 목록/검색/필터 |
| `GET` | `/api/v1/admin/users/{userId}` | 회원 상세 |
| `PUT` | `/api/v1/admin/users/{userId}` | 허용 필드만 수정: nickname, role, status, profileImageUrl |
| `DELETE` | `/api/v1/admin/users/{userId}` | soft delete |
| `GET` | `/api/v1/admin/reviews` | 관리자 리뷰 목록 |
| `PUT` | `/api/v1/admin/reviews/{reviewId}/hide` | 리뷰 숨김 |
| `PUT` | `/api/v1/admin/reviews/{reviewId}/restore` | 리뷰 복구 |
| `DELETE` | `/api/v1/admin/reviews/{reviewId}` | 리뷰 삭제 |
| `GET` | `/api/v1/admin/episodes` | 관리자 에피소드 목록 |
| `GET` | `/api/v1/admin/episodes/{episodeId}` | 관리자 에피소드 상세 |
| `GET` | `/api/v1/admin/episodes/place-candidates` | TourAPI 기준 장소 후보 |
| `GET` | `/api/v1/admin/episodes/place-candidates/nearby` | Kakao Local 주변 후보 |
| `POST` | `/api/v1/admin/episodes/ai-draft` | 규칙 기반 초안 생성 |
| `POST` | `/api/v1/admin/episodes/ai-draft/gemini` | Gemini 초안 생성 구조 |
| `POST` | `/api/v1/admin/episodes/ai-draft/validate` | 초안 검증 |
| `POST` | `/api/v1/admin/episodes/ai-draft/save` | DRAFT 저장 |
| `GET` | `/api/v1/admin/episodes/{episodeId}/publish-readiness` | 공개 준비도 점검 |
| `PUT` | `/api/v1/admin/episodes/{episodeId}` | 상태/기본 정보 수정, PUBLISHED 전환 |
| `PUT` | `/api/v1/admin/episodes/{episodeId}/spots/{spotId}` | 장소 수정 |
| `PUT` | `/api/v1/admin/episodes/{episodeId}/puzzles/{puzzleId}` | 퍼즐/힌트/reward_payload 수정 |

## 주요 화면

| 경로 | 화면 | 상태 |
| --- | --- | --- |
| `/intro` | 로그인/회원가입 | 완료 |
| `/episodes` | 사건파일 목록 | 완료 |
| `/episodes/:episodeId` | 에피소드 상세 | 완료 |
| `/episodes/:episodeId/briefing` | 사건 브리핑 | 완료 |
| `/episodes/:episodeId/map` | 지도/장소/도착/퍼즐 | 완료 |
| `/episodes/:episodeId/case-file` | 사건파일/자료/조사 기록 | 완료 |
| `/episodes/:episodeId/deduction` | 최종 추리 | 완료 |
| `/episodes/:episodeId/clear-report` | 클리어 리포트/리뷰 | 완료 |
| `/admin/users` | 관리자 회원 관리 | 완료 |
| `/admin/reviews` | 관리자 리뷰 관리 | 완료 |
| `/admin/episodes` | 관리자 에피소드 생성/운영 | 완료 |
| `/home`, `/regions`, `/map`, `/chat`, `/clear` | legacy 관광/미션 화면 | 유지. 새 MVP의 중심은 episode 플로우 |

## 실행 방법

### Backend

```powershell
cd backend
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain bootRun
```

검증:

```powershell
cd backend
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava test
```

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

검증:

```powershell
cd frontend
npm run build
```

## 환경 변수/설정

### frontend/.env

```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_KAKAO_MAP_KEY=KAKAO_JAVASCRIPT_KEY
VITE_TMAP_APP_KEY=TMAP_APP_KEY
VITE_DEV_ARRIVAL=true
```

### backend application properties

```properties
app.dev-mode.arrival-enabled=true
tourapi.key=TOURAPI_SERVICE_KEY
kakao.rest.api.key=KAKAO_REST_API_KEY
gemini.api.key=GEMINI_API_KEY
jwt.secret=CHANGE_ME
```

운영 모드에서는 `app.dev-mode.arrival-enabled=false`, `VITE_DEV_ARRIVAL=false`로 둡니다.

## 중요한 보안/게임 규칙

- 사용자 `/map` 응답에는 `isFinalPlace`, `clueRole`, `FINAL_PLACE`, `finalPlace`를 포함하지 않습니다.
- 프론트는 `publicMarkerType`만 보고 마커를 표시합니다.
- 실제 최종 장소는 서버 내부 `mission_spots.is_final_place`로만 판정합니다.
- 잘못된 최종 후보 장소에서는 최종 추리를 시작할 수 없습니다.
- 실제 최종 장소에 도착하면 단서가 적어도 최종 추리를 시도할 수 있지만 점수 페널티가 적용됩니다.
- 최종 추리 답변은 정답 또는 최종 장소를 직접 노출하지 않아야 합니다.
- AI 생성 퍼즐은 실제 현장 간판, 숫자, 계단 수, 조형물 존재 여부를 상상으로 만들면 안 됩니다.
- OAuth를 구현하지 않았으므로 구글/네이버 로그인 버튼은 없습니다.

## 운영 체크리스트

- Backend `tourapi.key`: TourAPI 기준 장소 후보 조회에 필요합니다.
- Backend `kakao.rest.api.key`: Kakao Local 주변 후보 조회에 필요합니다.
- Frontend `VITE_KAKAO_MAP_KEY`: Kakao 지도 표시용 JavaScript 키입니다.
- Frontend `VITE_TMAP_APP_KEY`: Tmap 길찾기 실행에 필요합니다.
- Kakao JavaScript 허용 도메인에 `localhost`와 실제 배포 도메인을 등록해야 합니다.
- Tmap 길찾기는 실제 모바일 기기에서 앱/웹 내비 실행 방식을 확인해야 합니다.
- GPS 실측 도착 판정은 실제 현장/실기기에서 별도 확인해야 합니다.
- 후보 장소는 운영 공개 전 좌표, 접근 가능 여부, 운영시간, 현장 관찰 요소를 검수해야 합니다.
- AI 생성 문제와 사건 자료는 관리자 검수 후 DRAFT에서 PUBLISHED로 전환합니다.

## 이어받기 문서

- `docs/MVP_STATUS.md`
- `docs/REQUIREMENTS_AUDIT.md`
- `docs/CODEX_HANDOFF_PROMPT.md`
