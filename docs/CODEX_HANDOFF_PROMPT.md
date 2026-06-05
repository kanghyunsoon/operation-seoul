아래를 새 Codex 세션 첫 메시지로 붙여넣으면 됩니다.

```text
너는 Operation KOREA 프로젝트의 풀스택 개발을 이어받는다.

작업 위치:
C:\Users\user\Desktop\operation_korea\operation-seoul

프로젝트:
Vue 3 + Pinia + Vue Router + Axios 프론트엔드와 Spring Boot + Spring Security + JWT + MyBatis + MySQL 백엔드 기반의 모바일 야외 방탈출/사건파일 앱이다.

중요:
문서에 구현됐다고 적힌 내용을 그대로 믿지 말고, 실제 Controller, Service, Repository/Mapper, DTO, Vue route, Pinia store, API 파일, 빌드 결과 기준으로 판단하라.
작업 전 반드시 `git status`를 확인하고, 기존 변경을 되돌리지 말라.

현재 서비스 방향:
단순 관광 미션 앱이 아니라 오프라인 사건파일 키트를 모바일 앱으로 디지털화한 구조다.

핵심 사용자 플로우:
1. 로그인
2. 에피소드 선택
3. 사건 브리핑
4. 지도 진입
5. 전체 조사 장소 최초 표시
6. publicMarkerType 기반 색상 마커 표시
7. 장소 선택 바텀시트 또는 장소 목록 fallback
8. Tmap 내비 시작
9. GPS/devMode 도착 판정
10. 도착한 장소 퍼즐 열기
11. 퍼즐 정답 제출
12. reward_payload 기반 단서/증거/용의자/메모/사진 해금
13. 사건파일 탭에서 해금 자료 확인
14. 최종 후보 장소 도착
15. 실제 최종 장소 도착 시 최종 추리 허용
16. 바다거북 스프식 최종 추리 채팅
17. 최종 정답 제출
18. CLEARED 처리
19. 클리어 리포트와 실제 역사 해설 확인
20. CLEARED 기준 리뷰 작성

절대 유지 규칙:
- GET /api/v1/episodes/{episodeId}/map 응답에는 isFinalPlace, clueRole, FINAL_PLACE, 실제 최종 장소를 유추할 내부 필드를 절대 포함하지 않는다.
- 프론트 지도/장소 표시 기준은 publicMarkerType만 사용한다.
- 실제 최종 장소는 mission_spots.is_final_place로 서버 내부에서만 판정한다.
- 잘못된 FINAL_CANDIDATE 도착 시 최종 추리를 막고 목적지 힌트를 확인하라는 안내만 제공한다.
- 실제 최종 장소 도착 시 단서가 부족해도 최종 추리를 허용하되 점수 페널티로 처리한다.
- devMode 도착 판정은 backend `app.dev-mode.arrival-enabled=true` 및 frontend `VITE_DEV_ARRIVAL=true`일 때만 동작해야 한다.
- 운영 모드에서는 devMode 도착 판정이 절대 동작하지 않아야 한다.
- user_episode_progress의 JSON 문자열 필드는 DTO/서비스에서 배열처럼 다룬다.
- OAuth를 구현하지 않았으므로 구글/네이버 로그인 버튼을 만들지 않는다.
- 실제 현장 간판 문구, 숫자, 계단 수, 조형물 존재 여부를 상상으로 만들지 않는다.
- 상용 사건파일 키트의 문구/문제/이미지/디자인을 복제하지 않는다.
- .idea/modules.xml, frontend/package-lock.json은 임의로 되돌리거나 덮어쓰지 않는다.

현재 구현/확인된 주요 내용:
- /map 응답에서 clueRole 제거됨.
- /map 응답에서 isFinalPlace, FINAL_PLACE 미노출 확인됨.
- ArriveResponse는 JSON에 `isActualFinalArrived`로 내려감.
- devMode 요청은 backend dev flag가 꺼져 있으면 403 `DEV_ARRIVAL_DISABLED`.
- reward_payload 처리 구현됨.
- 처리 타입:
  - ANSWER_CLUE
  - DESTINATION_CLUE
  - STORY_CLUE
  - SUSPECT_UNLOCK
  - SUSPECT_UPDATE
  - EVIDENCE_UNLOCK
  - PHOTO_UNLOCK
  - MEMO_UNLOCK
- 퍼즐 성공 응답에 새 해금 자료 목록 포함됨.
- 사건파일 탭에서 잠김/해금 카드 구분됨.
- 최종 추리 시작 조건:
  - 실제 final place 도착 전 403
  - 잘못된 FINAL_CANDIDATE 도착 시 canStartDeduction=false
  - 실제 final place 도착 시 canStartDeduction=true
  - 단서 0개여도 실제 final place 도착 시 deduction start 가능
- 최종 추리 질문 제한 20회 동작 확인됨.
- 최종 추리 답변 타입 제한:
  - YES
  - NO
  - RELATED
  - NOT_RELATED
  - PARTIAL
  - AMBIGUOUS
  - INSUFFICIENT_CLUE
  - REFUSED_DIRECT_REVEAL
- 정답/alias/실제 최종 장소 직접 노출 방지 로직 있음.
- 최종 정답 alias 판정 정상.
- 오답 시 wrongAnswerCount/finalGuessCount 증가.
- 정답 시 CLEARED, clearedAt, score 저장 확인.
- clear report 조회와 canReview=true 확인.
- 리뷰 작성 후 canReview=false 확인.

5차 UI QA에서 확인/수정된 내용:
- 실제 브라우저/모바일 기준 QA 진행.
- Playwright + Edge headless로 390px 전체 플로우 확인:
  로그인 → 목록 → 상세 → 브리핑 → 지도 → 장소 선택 → 도착/퍼즐 API → 사건파일 → 최종 추리 → 정답 제출 → 클리어 리포트/리뷰.
- 360px, 430px 주요 화면 한글 표시 정상, 가로 overflow 0 확인.
- Kakao 지도 마커 DOM이 headless 환경에서 생성되지 않는 상황을 확인했고, 지도 SDK 실패/지연 시에도 플레이가 끊기지 않도록 EpisodeMapView에 publicMarkerType 기반 조사 장소 목록 fallback 추가.
- 대상 사용자 플레이 화면의 mojibake 문구 정상화:
  - IntroView
  - EpisodeDetailView
  - EpisodeBriefingView
  - EpisodeMapView
  - EpisodeCaseFileView
  - FinalDeductionView
  - ClearReportView
  - CaseFileTabMenu
  - ClueBoard
  - PuzzleCard
  - SpotBottomSheet
  - FinalAnswerSubmitBox
  - EpisodeReviewPanel
  - axiosInstance 기본 오류 메시지

현재 변경 파일 주요 목록:
Backend:
- backend/src/main/java/com/operation/seoul/admin/episode/service/AdminEpisodeService.java
- backend/src/main/java/com/operation/seoul/casefile/service/CaseFileService.java
- backend/src/main/java/com/operation/seoul/episode/dto/ArriveResponse.java
- backend/src/main/java/com/operation/seoul/episode/dto/PuzzleSubmitResponse.java
- backend/src/main/java/com/operation/seoul/episode/dto/SpotMarkerResponse.java
- backend/src/main/java/com/operation/seoul/episode/service/EpisodePlayService.java
- backend/src/main/java/com/operation/seoul/global/config/EpisodeSchemaMigration.java

Frontend:
- frontend/src/api/axiosInstance.js
- frontend/src/components/episode/CaseFileTabMenu.vue
- frontend/src/components/episode/ClueBoard.vue
- frontend/src/components/episode/EpisodeReviewPanel.vue
- frontend/src/components/episode/FinalAnswerSubmitBox.vue
- frontend/src/components/episode/PuzzleCard.vue
- frontend/src/components/episode/SpotBottomSheet.vue
- frontend/src/views/IntroView.vue
- frontend/src/views/EpisodeDetailView.vue
- frontend/src/views/EpisodeBriefingView.vue
- frontend/src/views/EpisodeMapView.vue
- frontend/src/views/EpisodeCaseFileView.vue
- frontend/src/views/FinalDeductionView.vue
- frontend/src/views/ClearReportView.vue

기존에 남아 있는 변경/주의:
- docs/CODEX_HANDOFF_PROMPT.md는 기존 수정 상태로 남아 있음.
- .idea/material_theme_project_new.xml untracked가 있음.
- 위 항목들을 임의로 되돌리지 말 것.
- frontend/package-lock.json은 변경하지 말 것.

최근 검증 결과:
Backend:
cd backend
C:\Users\user\.jdks\ms-17.0.19\bin\java.exe -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava test
결과: BUILD SUCCESSFUL

Frontend:
cd frontend
npm.cmd run build
결과: vite build 성공

실제 QA 시 dev arrival 실행 방법:
- backend 실행 시 `--app.dev-mode.arrival-enabled=true`를 넣어야 함.
- frontend 실행 프로세스 환경변수 `VITE_DEV_ARRIVAL=true`가 필요함.
- 파일을 영구 수정하지 말고 QA 실행 환경에서만 켜라.
- 운영 모드 테스트에서는 둘 다 꺼진 상태에서 devMode가 403 되는지 확인하라.

남은 우선순위/TODO:
1. 실제 모바일 기기에서 Kakao Map 키/도메인 설정과 마커 렌더링 확인.
2. GPS 실측 도착 판정 현장/실기기 확인.
3. 레거시 화면과 관리자 화면에 남은 mojibake 문구 정리.
4. 관리자 생성 흐름 UI QA:
   - TourAPI 기준 장소 불러오기
   - 기준 장소 선택
   - Kakao Local 주변 후보
   - 후보 부족 시 수동 후보 추가 UX
   - AI draft/validate/save/publish readiness
5. Gemini 최종 추리 고도화:
   - 단, 현재 sanitizeDeductionAnswer 후처리와 직접 정답 노출 방지는 반드시 유지.
6. 점수 계산에서 클리어 시간 페널티는 아직 TODO.
7. 실제 DB의 오래된 QA 계정/리뷰 데이터 정리 필요 시 별도 판단.

다음 작업 추천:
우선 `git status` 확인 후, 기능 확장보다 관리자/레거시 화면 mojibake 정리와 실제 모바일 Kakao Map/Tmap QA를 먼저 진행하라.
변경 후 반드시 backend compile/test와 frontend build를 실행하고 결과를 보고하라.
```