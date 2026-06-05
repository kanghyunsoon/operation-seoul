# Operation KOREA MVP Status

Last updated: 2026-06-05

## 현재 기준 진행률

MVP 기준 약 80%입니다.

이 수치는 단순 파일 구현률이 아니라 다음 시연 흐름 기준입니다.

`관리자 사건파일 생성 -> 공개 -> 사용자 로그인 -> 지도 플레이 -> 퍼즐 -> 사건파일 갱신 -> 최종 추리 -> 클리어 -> 리뷰`

## 완료된 핵심 기능

### 인증/회원

- 이메일/비밀번호 회원가입/로그인
- JWT 발급 및 Authorization Bearer 처리
- 현재 사용자 조회
- ACTIVE 아닌 사용자 로그인/API 차단
- ROLE_USER/ROLE_ADMIN 구분
- 관리자 회원 관리
- soft delete: `status = DELETED`

### 게임플레이

- `episodes`, `mission_spots`, `puzzles`, `puzzle_hints`, `user_episode_progress`
- `final_deduction_sessions`, `final_deduction_questions`
- JSON 문자열 기반 progress 배열 필드
- EP.01 seed
- 전체 장소 최초 표시 지도
- publicMarkerType 기반 마커 표시
- 실제 최종 장소 내부 필드 미노출
- GPS/devMode 도착 판정
- 퍼즐 정답 제출
- reward_payload 해석
- 단서/용의자/증거/메모 해금
- 사건파일 탭 갱신
- 최종 추리 세션
- 최종 정답 제출 및 CLEARED
- 클리어 리포트

### 리뷰

- CLEARED + cleared_at 기준 리뷰 작성 제한
- 1인 1에피소드 1리뷰
- 작성자/관리자만 수정/삭제
- 관리자 숨김/복구/삭제

### 관리자 사건파일

- TourAPI 기준 장소 후보 조회
- Kakao Local 주변 후보 조회
- 8~9개 장소 선택 흐름
- 규칙 기반 초안 생성
- Gemini 초안 생성 구조
- 초안 검증
- DRAFT 저장
- 공개 준비도 점검
- PUBLISHED 전환 차단/검증
- 장소/퍼즐/힌트/reward_payload 수정
- 용의자/증거/리워드 placeholder 수정
- DRAFT 관리자 내부 미리보기
- PUBLISHED 사용자 화면 이동

## 남은 작업

### P0: 실제 시연 안정화

1. 실제 DB에서 EP.01 seed가 삽입되는지 다시 확인한다.
2. 관리자 계정 seed 또는 쿼리 가이드를 명확히 한다.
3. Kakao JavaScript key 도메인 설정을 확인한다.
4. Kakao REST key가 없을 때 관리자 주변 후보 UX를 더 명확히 한다.
5. Tmap app route가 모바일에서 실제 앱/웹 내비로 열리는지 확인한다.
6. devMode 도착 판정이 운영 모드에서 절대 동작하지 않는지 확인한다.
7. 전체 플레이 플로우를 1회 수동 QA한다.

### P1: 최종 추리 고도화

1. 현재 최종 추리는 규칙 기반 MVP다.
2. Gemini를 붙일 때도 답변 타입은 제한해야 한다.
3. 정답 직접 노출 방지 프롬프트와 서버 후처리를 같이 둔다.
4. 사용자가 수집한 단서 수가 적으면 `INSUFFICIENT_CLUE` 비율을 높인다.

### P1: 관리자 생성 UX

1. TourAPI/Kakao 후보가 부족할 때 수동 후보 추가 UX를 강화한다.
2. 후보 선택 후 역할을 직접 바꾸는 UI를 추가한다.
3. reward_payload 자동 생성 결과를 더 쉽게 편집하게 한다.
4. PUBLISHED 전환 전 현장 검수 체크박스를 추가한다.

### P2: 추가 도메인

1. 찜/즐겨찾기
2. 일정 관리
3. 팔로우/그룹
4. 챌린지/랭킹
5. AI 추천/AI 코칭
6. 제휴 리워드 실제 쿠폰 기능

## QA 체크리스트

### 관리자

1. 관리자 로그인
2. `/admin/episodes` 접속
3. TourAPI 기준 장소 불러오기
4. 기준 장소 선택(내부 최종 장소)
5. Kakao Local 주변 후보 불러오기
6. 8~9개 장소 선택
7. 선택 장소를 초안 입력에 적용
8. 규칙 기반 초안 생성
9. 초안 검증
10. DRAFT 저장
11. 관리자 내부 미리보기 확인
12. 공개 준비도 점검
13. 부족한 항목 수정
14. PUBLISHED 전환

### 사용자

1. 일반 사용자 로그인
2. `/episodes`에서 공개 사건파일 선택
3. 브리핑 확인
4. 지도 진입
5. 전체 장소 마커 확인
6. Tmap 내비 버튼 확인
7. devMode 또는 GPS 도착 판정
8. 퍼즐 열기
9. 정답 제출
10. 단서 보드 확인
11. 사건파일 탭에서 새 자료 확인
12. 실제 최종 장소 도착
13. 최종 추리 시작
14. 질문하기
15. 최종 정답 제출
16. 클리어 리포트 확인
17. 리뷰 작성

## 주의할 파일

- `.idea/modules.xml`: 기존 변경 있음. 임의 되돌림 금지.
- `frontend/package-lock.json`: 기존 변경 있음. 임의 되돌림 금지.
- 인코딩: PowerShell 콘솔에서 한글이 깨져 보일 수 있다. 파일 저장 시 UTF-8 no BOM 권장.
- `GET /map` 응답에 내부 최종 장소 필드를 추가하지 말 것.