# Operation KOREA MVP Status

Last updated: 2026-06-08
Basis: 실제 코드 파일, Controller/API, Vue route/API module, migration class 확인 기준

## 한 줄 결론

MVP 기능 개발은 현재 멈출 수 있는 상태입니다. 인증, 사건파일 플레이, 지도, 도착 판정, 퍼즐, reward_payload 해금, 사건파일 탭, 최종 추리, 클리어 리포트, CLEARED 기준 리뷰, 관리자 에피소드 생성/공개 QA는 구현되어 있습니다.

또한 일정, 팔로우/그룹, 챌린지/랭킹, 사용자 추천, 플레이 코칭/분석도 MVP 범위로 구현되어 있습니다. 추천과 코칭은 현재 LLM 호출이 아니라 플레이 데이터 기반 규칙 MVP입니다.

## 완료된 MVP 범위

### 인증/회원

- 회원가입/로그인
- BCrypt 비밀번호 암호화
- JWT 발급 및 Bearer 인증
- 현재 사용자 조회
- 내 프로필 수정
- 비밀번호 변경
- 회원 탈퇴 soft delete
- ACTIVE가 아닌 사용자 로그인 차단
- 이미 발급된 JWT라도 inactive 사용자는 보호 API 접근 차단
- ROLE_USER/ROLE_ADMIN 구분
- 관리자 회원 목록/상세/수정/삭제
- 관리자 수정 DTO 허용 필드 제한: nickname, role, status, profileImageUrl

### 사건파일 플레이

- `episodes`
- `mission_spots`
- `puzzles`
- `puzzle_hints`
- `user_episode_progress`
- `final_deduction_sessions`
- `final_deduction_questions`
- `case_suspects`
- `case_evidences`
- `episode_partner_rewards`
- EP.01 seed
- 전체 장소 최초 표시 지도
- `publicMarkerType` 기반 마커 표시
- 사용자 `/map` 응답 내부 최종 장소 정보 미노출
- GPS/devMode 도착 판정
- 장소별 퍼즐 조회/제출
- `reward_payload` 해석
- 정답 힌트/목적지 힌트/스토리 단서 저장
- 용의자/증거/메모/사진 카드 해금
- 사건파일 탭 갱신
- 최종 후보 도착 판정
- 실제 최종 장소에서만 최종 추리 시작
- 제한형 최종 추리 질문 20회
- 정답 직접 노출 방지
- 최종 정답 alias 판정
- 오답 횟수/질문 횟수/단서 부족 점수 페널티
- CLEARED 처리
- 클리어 리포트

### 리뷰

- 에피소드 리뷰 목록
- CLEARED + cleared_at 기준 리뷰 작성 제한
- 사용자당 에피소드 1개 리뷰 제한
- 작성자 또는 관리자만 수정/삭제
- 관리자 리뷰 목록/상세
- 관리자 숨김/복구/삭제
- spoiler 필드와 UI 접힘 구조

### 관심 에피소드

- `episode_favorites` 전용 테이블
- `POST /api/v1/episodes/{episodeId}/favorite`
- `DELETE /api/v1/episodes/{episodeId}/favorite`
- `GET /api/v1/users/me/favorites`
- 에피소드 목록/상세 `favorited` 응답
- `MyPageView` 내 관심 목록

### 사용자 확장 기능

- 일정 관리: `user_plans`, `GET/POST/PUT/DELETE /api/v1/users/me/plans`, `PlansView.vue`
- 팔로우: `user_follow`, 팔로워/팔로잉 API, `MyPageView.vue`
- 그룹: `user_groups`, `user_group_members`, 그룹 생성/가입/탈퇴/멤버 API, `GroupsView.vue`
- 랭킹: `GET /api/v1/rankings`, `GET /api/v1/rankings/me`, `RankingView.vue`
- 챌린지: `challenges`, `user_challenge_entries`, 챌린지 목록/내 챌린지/참가 API, `ChallengesView.vue`
- 사용자 추천: `GET /api/v1/recommendations/episodes`, `RecommendationsView.vue`
- AI 코칭/분석: `GET /api/v1/coaching/me`, `GET /api/v1/coaching/episodes/{episodeId}`, `CoachingView.vue`

### 관리자 에피소드 생성/운영

- 관리자 에피소드 목록/상세
- TourAPI 기준 장소 후보 조회
- TourAPI 키 없음/placeholder 명시 에러: `TOURAPI_SERVICE_KEY_MISSING`
- Kakao Local 주변 후보 조회
- Kakao REST 키 없음 명시 에러: `KAKAO_REST_API_KEY_MISSING`
- 수동 후보 추가 UX
- 기준 장소를 내부 최종 장소로 사용하는 생성 흐름
- 규칙 기반 초안 생성
- Gemini 초안 생성 구조
- 초안 검증
- DRAFT 저장
- 관리자 내부 미리보기
- 공개 준비도 점검
- PUBLISHED 전환 검증
- 장소/퍼즐/힌트/reward_payload/용의자/증거/리워드 placeholder 수정

## 부분 구현 또는 운영 검증 필요

| 항목 | 현재 상태 | 남은 일 |
| --- | --- | --- |
| 최종 추리 AI | 규칙 기반 MVP 완료 | Gemini 실시간 추리 연결은 고도화 과제 |
| 사용자 추천 | 규칙 기반 MVP 완료 | Gemini 기반 개인화 추천 문장 생성은 고도화 과제 |
| AI 코칭/분석 | 규칙 기반 MVP 완료 | LLM 기반 심층 피드백은 고도화 과제 |
| Gemini 생성 | 관리자 초안 생성 구조 존재 | 실제 운영 품질은 프롬프트/검수/키 설정 QA 필요 |
| 지역 리워드 | DB/API/UI placeholder | 실제 쿠폰, 상품권, 제휴 사용 처리 미구현 |
| Kakao Map | 지도 SDK와 fallback 목록 구현 | 실제 도메인 등록 및 모바일 지도 표시 확인 필요 |
| Tmap | 길찾기 버튼 구현 | 실제 모바일 앱/웹 내비 실행 확인 필요 |
| GPS 도착 | 서버 계산과 devMode 분리 구현 | 현장 실측 반경 검증 필요 |
| legacy region/mission | 기존 기능 유지 | 새 episode MVP와 완전 통합된 구조는 아님 |

## 미구현 또는 운영 고도화 영역

1. OAuth 소셜 로그인
2. 실제 쿠폰/상품권 지급
3. 다국어/무장애/두루누비/기상청 API 연계
4. 사용자 생성 사건파일/UGC 제작자 생태계
5. 일정/그룹/챌린지의 관리자 운영 UI와 알림 고도화
6. Gemini 기반 추천/코칭/최종 추리 고도화

## QA 상태

- 관리자 생성 -> DRAFT 저장 -> readiness -> PUBLISHED -> 사용자 노출 플로우는 실제 DB 기준으로 확인되었습니다.
- QA 생성 에피소드 ID 3은 삭제하지 않고 DRAFT로 되돌렸습니다.
- 일반 사용자 `/api/v1/episodes`에서는 ID 3이 노출되지 않습니다.
- 관리자 `/api/v1/admin/episodes`에서는 ID 3이 DRAFT로 확인됩니다.
- 사용자 `/map` 응답에 `isFinalPlace`, `clueRole`, `FINAL_PLACE`, `finalPlace`가 노출되지 않는 것을 확인했습니다.
- devMode 비활성 서버에서 devMode 도착 요청은 `403 DEV_ARRIVAL_DISABLED`입니다.

## 최종 운영 TODO

1. 실제 모바일 기기에서 Kakao 지도 표시 확인
2. 실제 모바일 기기에서 Tmap 내비 실행 확인
3. 현장 GPS 도착 반경 실측
4. TourAPI/Kakao 후보 장소 좌표, 접근 가능 여부, 운영시간 검수
5. AI 생성 문제의 실제 현장 근거 검수
6. PUBLISHED 전환 전 운영 체크리스트를 관리자 프로세스로 고정
7. QA 계정/QA 에피소드 처리 정책 결정

## 빌드 검증 명령

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
