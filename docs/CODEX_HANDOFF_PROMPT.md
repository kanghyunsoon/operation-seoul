# Operation KOREA Codex Handoff Prompt

작업 위치:
`C:\Users\khsoo\Desktop\operation-seoul`

작업 전 반드시 `git status`를 확인하고 기존 변경을 되돌리지 말 것.

특히 아래 규칙을 지킨다.

- 사용자 `/map` 응답에는 `isFinalPlace`, `clueRole`, `FINAL_PLACE`, `finalPlace`를 절대 포함하지 않는다.
- 프론트 지도/장소 표시는 `publicMarkerType`만 사용한다.
- 실제 최종 장소는 서버 내부 `mission_spots.is_final_place`로만 판정한다.
- devMode 도착 판정은 backend `app.dev-mode.arrival-enabled=true`와 frontend `VITE_DEV_ARRIVAL=true`가 동시에 켜진 시연 환경에서만 동작해야 한다.
- 운영 모드에서는 devMode 도착 판정이 403이어야 한다.
- OAuth를 구현하지 않았으므로 구글/네이버 로그인 버튼을 만들지 않는다.
- 실제 현장 간판, 숫자, 계단 수, 조형물 존재 여부를 AI가 상상해서 만들면 안 된다.
- 상용 사건파일 키트의 문구/문제/이미지/디자인을 복제하지 않는다.
- `frontend/package-lock.json`, `.idea` 파일은 임의 변경하지 않는다.

## 프로젝트 요약

Operation KOREA는 Vue 3 + Pinia + Vue Router + Axios 프론트엔드와 Spring Boot + Spring Security + JWT + MyBatis + MySQL 백엔드 기반의 모바일 야외 방탈출/사건파일 앱이다.

서비스 중심은 관광 설명이 아니라 다음 흐름이다.

`로그인 -> 에피소드 선택 -> 사건 브리핑 -> 지도 -> 장소 도착 -> 퍼즐 -> 단서/사건자료 해금 -> 사건파일 확인 -> 최종 장소 추리 -> 최종 추리 채팅 -> 최종 정답 -> CLEARED -> 클리어 리포트 -> 리뷰`

## 현재 구현 완료 상태

실제 코드 기준 완료:

- 인증/JWT/현재 사용자
- ACTIVE/SUSPENDED/DELETED 계정 처리
- 관리자 회원 관리
- 내 정보 수정/비밀번호 변경/회원 탈퇴
- episode/mission_spot/puzzle/puzzle_hint/progress 구조
- final_deduction_sessions/questions 구조
- case_suspects/case_evidences/partner_rewards 구조
- EP.01 seed
- 사용자 에피소드 목록/상세/브리핑/지도/사건파일/최종추리/클리어 리포트
- GPS/devMode 도착 판정
- 퍼즐 제출
- reward_payload 해금
- 단서 보드
- 최종 추리 제한 답변
- 최종 정답 alias 판정
- CLEARED 처리
- CLEARED 기준 리뷰
- 관리자 리뷰 관리
- 관리자 에피소드 생성/수정/DRAFT/PUBLISHED/readiness
- TourAPI 기준 장소 후보
- Kakao Local 주변 후보
- 수동 후보 추가 UX
- TourAPI/Kakao 키 없음 에러 메시지
- episode_favorites API, 목록/상세 favorited, MyPage 관심 목록

부분 또는 운영 QA 필요:

- Gemini 초안 생성은 구조가 있으나 운영 품질 검수 필요
- 최종 추리는 규칙 기반 MVP이며 Gemini 실시간 추리 고도화는 미구현
- Kakao Map/Tmap/GPS는 실제 모바일/현장 검증 필요
- 지역 리워드는 placeholder만 있음

미구현:

- 일정 관리
- 팔로우/그룹
- 챌린지/랭킹
- 사용자 AI 추천
- AI 코칭/분석
- OAuth 소셜 로그인
- 실제 쿠폰/상품권 지급
- 다국어/무장애/두루누비/기상청 API 연계

## 주요 파일

Backend:

- `auth/*`: 회원가입, 로그인, JWT, current user
- `user/*`: 내 정보, 관리자 회원 관리
- `episode/*`: 사용자 플레이 API
- `casefile/*`: 사건파일 탭 API
- `review/*`: 에피소드 리뷰/관리자 리뷰
- `admin/episode/*`: 관리자 에피소드 생성/운영
- `global/config/EpisodeSchemaMigration.java`: episode MVP 테이블과 EP.01 seed
- `game/*`, `location/*`, `community/*`: legacy 관광/미션/커뮤니티 기능

Frontend:

- `src/router/index.js`: episode/admin routes
- `src/stores/sessionStore.js`: 인증 상태
- `src/api/*`: API modules
- `src/views/EpisodeListView.vue`
- `src/views/EpisodeDetailView.vue`
- `src/views/EpisodeBriefingView.vue`
- `src/views/EpisodeMapView.vue`
- `src/views/EpisodeCaseFileView.vue`
- `src/views/FinalDeductionView.vue`
- `src/views/ClearReportView.vue`
- `src/views/AdminEpisodesView.vue`
- `src/views/AdminUsersView.vue`
- `src/views/AdminReviewsView.vue`

## 검증 명령

Backend:

```powershell
cd backend
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava test
```

Frontend:

```powershell
cd frontend
npm.cmd run build
```

## 다음에 기능 개발을 재개한다면

우선순위:

1. 실제 모바일 Kakao Map/Tmap/GPS 현장 QA
2. PUBLISHED 전환 전 현장 검수 체크박스 또는 운영 승인 필드
3. Gemini 최종 추리 고도화
4. 일정 관리
5. 챌린지/랭킹
6. 사용자 AI 추천/AI 코칭

기능 개발 전에는 반드시 `docs/MVP_STATUS.md`와 `docs/REQUIREMENTS_AUDIT.md`를 읽고, 문서가 아니라 실제 코드 기준으로 구현 여부를 다시 확인한다.
