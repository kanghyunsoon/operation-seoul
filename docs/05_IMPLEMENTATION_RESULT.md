# 구현 결과

## 1. 백엔드

| 구분 | 구현 내용 |
| --- | --- |
| 인증 | 일반 로그인, OAuth, JWT, 사용자 상태 확인 |
| 사용자 | 내 정보, 비밀번호, 탈퇴, 팔로우, 피드 |
| 에피소드 | 목록, 상세, 시작, 지도, 도착, 퍼즐, 추리, 클리어 |
| 사건 파일 | 용의자, 증거, 단서, 제휴 보상 구조 |
| 리뷰 | 리뷰, 댓글, 관리자 숨김/복구/삭제 |
| 커뮤니티 | 지역 리뷰, Q&A, 답변, 좋아요 |
| 부가 기능 | 추천, 랭킹, 챌린지, 코칭 |
| 관리자 | 회원/리뷰/에피소드/장소/퍼즐/자료 관리 |
| AI | 장소 후보, Wikipedia 보강, Gemini/OpenAI 초안, 검증, 저장 |

## 2. 프론트

| 구분 | 구현 내용 |
| --- | --- |
| 인증 | 인트로, 로그인, 회원가입, OAuth callback |
| 플레이 | 목록, 상세, 브리핑, 지도, 사건 파일, 추리, 클리어 리포트 |
| 지도 | Kakao 지도, Tmap 길찾기, 개발용 도착 버튼 |
| 미니게임 | 숫자락, 단어 조합, 카드 매칭, 패턴락, 빠른 탭 등 |
| 커뮤니티 | 지역 커뮤니티, 전체 허브, 글 작성/상세 |
| 소셜 | 피드, 팔로우, 내 리뷰, 관심 에피소드 |
| 관리자 | 회원, 리뷰, 에피소드 관리 |

## 3. DB

기본 스키마는 `schema.sql`에 있고, 실행 시 필요한 컬럼/테이블은 `global/config`의 migration 클래스에서 보강한다.

주요 테이블은 아래와 같다.

- `users`, `user_social_accounts`
- `region`, `episodes`, `mission_spots`
- `puzzles`, `puzzle_hints`, `puzzle_attempt_limits`
- `user_episode_progress`
- `case_suspects`, `case_evidences`, `episode_partner_rewards`
- `final_deduction_sessions`, `final_deduction_questions`
- `episode_reviews`, `episode_review_comments`
- `region_review`, `region_question`, `region_answer`
- `challenges`, `user_challenge_entries`

## 4. 테스트/스크립트

- 백엔드 테스트: `backend/src/test/java/com/operation/seoul`
- 프론트 빌드: `frontend/package.json`
- 전체 확인: `scripts/verify-release.ps1`
- AI/smoke 확인: `scripts/smoke-*.mjs`
