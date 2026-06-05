# Operation KOREA Team Progress

Last updated: 2026-06-05

## 현재 진행률

MVP 기준 약 85%입니다.

진행률을 80%에서 85%로 잡는 이유:

- 사용자 플레이 핵심 흐름은 실제 API/브라우저 QA 기준으로 닫혔습니다.
- 관리자 생성 -> DRAFT -> readiness -> PUBLISHED -> 사용자 노출 QA도 실제 DB 기준으로 확인됐습니다.
- 남은 항목은 대부분 운영 QA, 현장 검수, 그리고 MVP 이후 확장 기능입니다.
- 일정/챌린지/랭킹/AI 추천/AI 코칭은 아직 구현하지 않았으므로 100%가 아닙니다.

## 완료된 작업

### 인증/회원

- 이메일/비밀번호 회원가입 및 로그인
- JWT 인증
- 현재 사용자 조회
- 내 정보 수정
- 비밀번호 변경
- 회원 탈퇴 soft delete
- ACTIVE/SUSPENDED/DELETED 상태 처리
- 관리자 회원 관리

### 사건파일 플레이

- episode 기반 플레이 구조
- 전체 장소 지도 표시
- 최종 장소 내부 은닉
- GPS/devMode 도착 판정
- 퍼즐 제출
- `reward_payload` 기반 단서/자료 해금
- 단서 보드
- 사건파일 탭
- 최종 추리
- 최종 정답 제출
- CLEARED 처리
- 클리어 리포트

### 리뷰

- CLEARED 사용자만 리뷰 작성
- 1인 1에피소드 1리뷰
- 작성자/관리자 수정 삭제
- 관리자 숨김/복구/삭제
- 스포일러 표시

### 관리자 에피소드 생성

- TourAPI 기준 장소 후보
- Kakao Local 주변 후보
- 수동 후보 추가
- 규칙 기반 초안
- Gemini 초안 구조
- 초안 검증
- DRAFT 저장
- 공개 준비도 점검
- PUBLISHED 전환
- 관리자 내부 미리보기
- 장소/퍼즐/힌트/reward_payload/사건자료 수정

## 실제 QA 완료 항목

- 관리자 생성 플로우 실제 DB QA
- QA 에피소드 ID 3 DRAFT 전환
- 일반 사용자 목록에서 DRAFT 에피소드 미노출 확인
- `/map` 내부 최종 장소 필드 미노출 확인
- TourAPI 키 없음/placeholder 에러 확인
- devMode 비활성 서버에서 403 확인
- backend compile/test 성공
- frontend build 성공

## 남은 작업

### 운영 QA

1. Kakao JavaScript 키 도메인 설정 확인
2. Tmap 모바일 길찾기 실제 기기 확인
3. GPS 도착 반경 현장 실측
4. TourAPI/Kakao 후보 장소 실제 접근 가능 여부 검수
5. AI 생성 문제 관리자 검수 프로세스 고정
6. QA 계정/QA 에피소드 운영 반영 정책 결정

### MVP 이후 확장

1. 일정 관리
2. 팔로우/그룹
3. 챌린지/랭킹
4. 사용자 AI 추천
6. AI 코칭/분석
7. 실제 제휴 쿠폰/상품권 지급
8. 다국어/무장애/두루누비/기상청 API 연계

## 다음 작업자가 먼저 봐야 할 파일

- `README.md`
- `docs/MVP_STATUS.md`
- `docs/REQUIREMENTS_AUDIT.md`
- `docs/CODEX_HANDOFF_PROMPT.md`

## 절대 깨면 안 되는 규칙

- 사용자 `/map` 응답에 `isFinalPlace`, `clueRole`, `FINAL_PLACE`, `finalPlace`를 넣지 않는다.
- 프론트 지도 표시는 `publicMarkerType`만 사용한다.
- 실제 최종 장소는 서버 내부 `mission_spots.is_final_place`로만 판정한다.
- devMode 도착 판정은 운영 모드에서 동작하면 안 된다.
- OAuth를 구현하지 않는 한 소셜 로그인 버튼을 만들지 않는다.
- 실제 현장 정보는 관리자 입력/검수 없이 AI가 상상해서 만들면 안 된다.
