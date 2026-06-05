# Codex Handoff Prompt for Operation KOREA

아래 프롬프트를 새 Codex 세션 첫 메시지로 사용하면 된다.

```text
너는 Operation KOREA 프로젝트의 풀스택 개발을 이어받는다.

작업 위치는 C:\Users\khsoo\Desktop\operation-seoul 이다.

이 프로젝트는 Vue 3 + Pinia + Vue Router + Axios 프론트엔드와 Spring Boot + Spring Security + JWT + MyBatis + MySQL 백엔드 기반의 모바일 야외 방탈출/사건파일 앱이다.

중요: 문서에 구현됐다고 적힌 내용을 그대로 믿지 말고, 실제 Controller, Service, Repository/Mapper, DTO, Vue route, Pinia store, API 파일, 빌드 결과 기준으로 판단하라.

현재 서비스 방향은 단순 관광 미션 앱이 아니라 오프라인 사건파일 키트를 모바일 앱으로 디지털화한 구조다.

핵심 사용자 플로우:
1. 로그인
2. 에피소드 선택
3. 사건 브리핑
4. 지도 진입
5. 전체 조사 장소 최초 표시
6. publicMarkerType 기반 색상 마커 표시
7. 장소 선택 바텀시트
8. Tmap 내비 시작
9. GPS/devMode 도착 판정
10. 도착한 장소 퍼즐 열기
11. 퍼즐 정답 제출
12. reward_payload 기반 단서/증거/용의자/메모 해금
13. 사건파일 탭에서 해금 자료 확인
14. 실제 최종 장소 도착
15. 바다거북 스프식 최종 추리 채팅
16. 최종 정답 제출
17. CLEARED 처리
18. 클리어 리포트와 실제 역사 해설 확인
19. CLEARED 기준 리뷰 작성

절대 지켜야 할 규칙:
- GET /api/v1/episodes/{episodeId}/map 응답에는 isFinalPlace 또는 실제 최종 장소를 유추할 수 있는 내부 필드를 절대 포함하지 않는다.
- 프론트는 publicMarkerType만 사용해 마커를 표시한다.
- 실제 최종 장소는 mission_spots.is_final_place로 서버 내부에서만 판정한다.
- 잘못된 조사 후보에서는 최종 추리 시작을 막고, 목적지 힌트를 확인하라는 안내만 제공한다.
- 실제 최종 장소에 도착하면 단서가 부족해도 최종 추리를 허용하되 점수 페널티로 처리한다.
- devMode 도착 판정은 backend app.dev-mode.arrival-enabled=true 및 frontend VITE_DEV_ARRIVAL=true일 때만 동작해야 한다. 운영 모드에서는 절대 동작하지 않아야 한다.
- user_episode_progress의 visited_spot_ids, completed_spot_ids, collected_answer_clues, collected_destination_clues, collected_story_clues 등은 MVP에서 JSON 문자열로 저장하지만 DTO/서비스에서는 배열처럼 다룬다.
- OAuth를 구현하지 않았으므로 구글/네이버 로그인 버튼을 만들지 않는다.
- AI는 실제 현장 간판 문구, 숫자, 계단 수, 조형물 존재 여부를 상상으로 만들면 안 된다.
- 유능한 사건파일 같은 실제 상용 키트의 문구/문제/이미지/디자인을 복제하지 말고 구조와 경험만 참고한다.
- .idea/modules.xml, frontend/package-lock.json은 임의로 되돌리거나 덮어쓰지 않는다.

현재 구현된 주요 패키지:
- backend/src/main/java/com/operation/seoul/auth
- backend/src/main/java/com/operation/seoul/user
- backend/src/main/java/com/operation/seoul/admin
- backend/src/main/java/com/operation/seoul/episode
- backend/src/main/java/com/operation/seoul/casefile
- backend/src/main/java/com/operation/seoul/review
- backend/src/main/java/com/operation/seoul/global/config/EpisodeSchemaMigration.java

현재 구현된 주요 프론트 화면:
- frontend/src/views/EpisodeListView.vue
- frontend/src/views/EpisodeDetailView.vue
- frontend/src/views/EpisodeBriefingView.vue
- frontend/src/views/EpisodeMapView.vue
- frontend/src/views/EpisodeCaseFileView.vue
- frontend/src/views/FinalDeductionView.vue
- frontend/src/views/ClearReportView.vue
- frontend/src/views/AdminEpisodesView.vue
- frontend/src/views/AdminUsersView.vue
- frontend/src/views/AdminReviewsView.vue

관리자 생성 흐름:
1. /admin/episodes
2. TourAPI 기준 장소 불러오기
3. 기준 장소 선택
4. Kakao Local 주변 후보 불러오기
5. 기준 장소 포함 8~9개 선택
6. 선택 후보를 초안 입력에 적용
7. 규칙 기반 초안 또는 Gemini 초안 생성
8. 초안 검증
9. DRAFT 저장
10. 관리자 내부 미리보기
11. 공개 준비도 점검
12. 부족 항목 수정
13. PUBLISHED 전환
14. 사용자 지도/사건파일 화면 확인

사용자 API:
- GET /api/v1/episodes
- GET /api/v1/episodes/{episodeId}
- POST /api/v1/episodes/{episodeId}/start
- GET /api/v1/episodes/{episodeId}/map
- POST /api/v1/episodes/{episodeId}/spots/{spotId}/arrive
- GET /api/v1/spots/{spotId}/puzzle
- POST /api/v1/puzzles/{puzzleId}/submit
- GET /api/v1/episodes/{episodeId}/clue-board
- GET /api/v1/episodes/{episodeId}/case-file
- POST /api/v1/episodes/{episodeId}/deduction/start
- POST /api/v1/deduction/{sessionId}/ask
- GET /api/v1/deduction/{sessionId}/questions
- POST /api/v1/episodes/{episodeId}/final-answer
- GET /api/v1/episodes/{episodeId}/clear-report

관리자 API:
- GET /api/v1/admin/episodes
- GET /api/v1/admin/episodes/{episodeId}
- GET /api/v1/admin/episodes/place-candidates
- GET /api/v1/admin/episodes/place-candidates/nearby
- POST /api/v1/admin/episodes/ai-draft
- POST /api/v1/admin/episodes/ai-draft/gemini
- POST /api/v1/admin/episodes/ai-draft/validate
- POST /api/v1/admin/episodes/ai-draft/save
- GET /api/v1/admin/episodes/{episodeId}/publish-readiness
- PUT /api/v1/admin/episodes/{episodeId}
- PUT /api/v1/admin/episodes/{episodeId}/spots/{spotId}
- PUT /api/v1/admin/episodes/{episodeId}/puzzles/{puzzleId}
- PUT /api/v1/admin/episodes/{episodeId}/suspects/{suspectId}
- PUT /api/v1/admin/episodes/{episodeId}/evidences/{evidenceId}
- PUT /api/v1/admin/episodes/{episodeId}/partner-rewards/{rewardId}

빌드 명령:
Backend:
cd backend
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test

Frontend:
cd frontend
npm run build

현재 남은 우선순위:
1. 실제 DB에서 EP.01 seed와 관리자 계정 상태 확인
2. 전체 수동 QA: 관리자 생성 -> 공개 -> 사용자 플레이 -> 클리어 -> 리뷰
3. Kakao Map/Tmap 실제 키와 도메인 설정 확인
4. Kakao Local 후보 부족 시 수동 후보 추가 UX 보강
5. 최종 추리 Gemini 고도화. 단, 답변 타입 제한과 정답 직접 노출 방지 필수
6. 찜/일정/챌린지/랭킹/AI 추천/AI 코칭 확장

작업 전 항상 git status를 확인하고, 기존 변경을 되돌리지 말라.
구현 후 backend compile/test와 frontend build 결과를 보고하라.
```