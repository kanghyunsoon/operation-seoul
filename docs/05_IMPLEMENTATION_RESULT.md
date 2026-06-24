# 구현 결과물 정리

작성일: 2026-06-25

## 1. Back-End

| 항목 | 내용 |
| --- | --- |
| 위치 | `backend/src/main/java/com/operation/seoul` |
| Framework | Spring Boot 4, Spring Web MVC |
| 인증 | Spring Security, JWT |
| DB 연동 | MyBatis |
| DB | MySQL |
| 주요 패키지 | `auth`, `user`, `episode`, `admin.episode`, `review`, `community`, `favorite`, `plan`, `challenge`, `ranking`, `recommendation`, `coaching` |

주요 구현:

- 회원가입/로그인/로그아웃 세션 처리.
- JWT 인증 필터와 현재 사용자 resolver.
- 관리자 회원/리뷰/에피소드 관리.
- 공개 에피소드 목록, 상세, 지도, 도착 판정, 퍼즐, 단서 해금, 최종 추리, 클리어 처리.
- 리뷰 CRUD 및 클리어 사용자 제한.
- 찜, 팔로우, 일정, 그룹, 챌린지, 랭킹.
- TourAPI/Kakao Local 기반 장소 후보.
- Gemini 기반 관리자 초안 생성 구조.

## 2. Front-End

| 항목 | 내용 |
| --- | --- |
| 위치 | `frontend/src` |
| Framework | Vue 3 |
| 상태 관리 | Pinia |
| 라우팅 | Vue Router |
| HTTP | Axios |
| 주요 화면 | `views` 디렉터리 |
| API 모듈 | `frontend/src/api` |

주요 구현:

- 로그인/회원가입.
- 권역 선택 SVG 지도.
- 에피소드 목록/상세/브리핑.
- 지도 기반 장소 방문 및 퍼즐.
- 미션 파일, 최종 추리, 클리어 리포트.
- 마이페이지, 관심목록, 팔로우.
- 챌린지, 추천, 코칭, 랭킹, 그룹, 일정.
- 관리자 회원/리뷰/에피소드 화면.

## 3. DB Schema

| 산출물 | 위치 |
| --- | --- |
| Schema SQL | `backend/src/main/resources/schema.sql` |

주요 테이블:

- `users`
- `region`, `mission`
- `episodes`
- `game_session`
- `clear_report`
- `episode_reviews`, `episode_review_comments`
- `region_review`, `region_question`, `region_answer`
- `region_question_like`, `region_review_like`
- `region_like`, `region_favorite`
- `user_follow`
- `user_plans`
- `user_groups`, `user_group_members`
- `challenges`, `user_challenge_entries`

## 4. 활용 데이터셋

| 데이터 | 활용 방식 | 상태 |
| --- | --- | --- |
| TourAPI 장소 후보 | 관리자 에피소드 초안의 기준 장소 후보 조회 | 구현 |
| Kakao Local 주변 장소 | 기준 장소 주변 후보 보강 | 구현 |
| 내부 seed/관리자 생성 에피소드 | 시연용 에피소드 데이터 | 구현 |
| 사용자 플레이 기록 | 추천/코칭/랭킹/챌린지 계산 | 구현 |
| Gemini 생성 초안 | 미션 서사/퍼즐/단서 초안 생성 | 구현 |

## 5. 검증 결과

현재 확인된 검증:

- Front-End: `npm run build` 통과.
- Back-End: JUnit 테스트 파일 존재. 주요 서비스 단위 테스트 포함.

추가 권장 검증:

- `backend` 전체 `test` 실행 결과 캡처.
- 실제 MySQL 연결 상태에서 API smoke test.
- 모바일 기기에서 Kakao Map/Tmap/GPS 도착 판정 검증.
- 에피소드 목록 UI의 1366x768, 1440x900, 모바일 viewport 캡처 검증.
