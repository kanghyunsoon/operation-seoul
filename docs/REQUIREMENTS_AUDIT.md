# 요구사항 점검 결과

점검일: 2026-05-17

## 1. 제약조건 점검

| 항목 | 현재 상태 | 판정 |
| --- | --- | --- |
| Spring Boot MVC | `backend/build.gradle`의 `spring-boot-starter-webmvc`, Spring Boot 4.0.5 사용 | 충족 |
| Vue 3 + Vite | `frontend/package.json`의 `vue`, `vite`, `@vitejs/plugin-vue` 사용 | 충족 |
| MyBatis + MySQL | MyBatis starter, MySQL connector, `schema.sql`, Mapper SQL 사용 | 충족 |
| JPA 미사용 | JPA 의존성/엔티티/Repository 미사용 | 충족 |
| React 미사용 | React 의존성/소스 미사용 | 충족 |
| S3 미사용 | AWS/S3 의존성/소스 미사용 | 충족 |
| JSP | 현재는 Vue SPA + REST API 구조이며 JSP 뷰가 없음 | 제출 기준에서 JSP가 필수이면 보완 필요 |

## 2. 미션별 즉시 수정 기능

| 항목 | 구현 위치 | 상태 |
| --- | --- | --- |
| 생성된 작전의 미션 목록 조회 | `GET /api/v1/admin/missions/regions/{regionId}` | 구현 |
| 힌트 스팟 후보 조회 | `GET /api/v1/admin/missions/regions/{regionId}/spot-candidates` | 구현 |
| 개별 미션 수정 | `PUT /api/v1/admin/missions/{missionId}` | 구현 |
| 선택 스팟 기반 AI 재구성 | `POST /api/v1/admin/missions/{missionId}/recompose` | 구현 |
| 수정 입력값 검증 | 제목, 위도, 경도, 인증 반경 검증 | 구현 |
| 관리자 카드 수정 버튼 | `HomeView.vue` 작전 카드의 `수정` 버튼 | 구현 |
| 스팟 선택형 편집 | 힌트 미션별 후보 스팟 목록, 좌표 적용, AI 재구성 버튼 | 구현 |
| 생성 직후 편집 연결 | AI 생성 응답에 새 작전/미션 목록을 담고 편집 모달 즉시 오픈 | 구현 |

## 3. 공통 필수 요구사항 구현 상태

| 번호 | 요구사항 | 현재 상태 | 판정 |
| --- | --- | --- | --- |
| F01 | 콘텐츠 등록 | 관리자 AI 작전 생성으로 Region/Mission 등록 | 충족 |
| F02 | 콘텐츠 조회 | 지역 카드, 브리핑, 미션 보드 조회 | 충족 |
| F03 | 콘텐츠 수정 | 관리자 미션 단위 수정 API/UI 추가 | 충족 |
| F04 | 콘텐츠 삭제 | 관리자 작전 삭제 API/UI | 충족 |
| F05 | 필터/검색/정렬 | 작전 카드 검색, 대표 시대/테마/진행상태/평점 필터, 최신순/오래된순/평점순/시기순/테마순/제목순 정렬 | 충족 |
| F06 | 리뷰 작성 | `POST /api/v1/regions/{regionId}/reviews`, 최종 미션 CLEARED 사용자만 작성 가능 | 충족 |
| F07 | 리뷰 조회 | `GET /api/v1/regions/{regionId}/reviews`, 평균 평점/리뷰 수/작성자/클리어 시간 포함 | 충족 |
| F08 | 리뷰 수정 | `PUT /api/v1/regions/{regionId}/reviews/{reviewId}`, 작성자 또는 관리자만 수정 | 충족 |
| F09 | 리뷰 삭제 | `DELETE /api/v1/regions/{regionId}/reviews/{reviewId}`, 작성자 또는 관리자만 삭제 | 충족 |
| F10 | 회원 등록 | 회원가입 API/UI | 충족 |
| F11 | 회원 조회 | 현재 사용자/회원 상세 조회 API가 없음 | 미구현 |
| F12 | 회원 수정 | Repository update만 있고 API/UI 없음 | 미구현 |
| F13 | 회원 삭제 | API/UI 없음 | 미구현 |
| F14 | 로그인/로그아웃 | 로그인/JWT, 프론트 로컬 로그아웃 | 부분 |

## 4. 추가/심화 기능 상태

| 번호 | 요구사항 | 현재 상태 | 판정 |
| --- | --- | --- | --- |
| F15 | 찜/즐겨찾기 | 없음 | 미구현 |
| F16 | 팔로우/그룹 | 없음 | 미구현 |
| F17 | 계획/일정 관리 | 없음 | 미구현 |
| F18 | 챌린지 관리 | 점수/시간/거리 기록은 있으나 챌린지 CRUD/랭킹 관리 없음 | 부분 |
| F19 | AI 추천 | 관리자 후보지 기반 AI 작전 생성은 있으나 사용자 맞춤 추천 없음 | 부분 |
| F20 | AI 코칭/분석 | AI 힌트 채팅, 클리어 리포트는 있으나 개인화 코칭/통계 분석 없음 | 부분 |

## 5. 우선 보완 대상

1. JSP가 평가 필수인지 확인한다. 필수라면 관리자 또는 요약 조회 화면부터 JSP로 최소 보완한다.
2. 제출 필수 기능이면 리뷰 CRUD를 우선 추가한다.
3. 회원 조회/수정/삭제 API와 간단한 마이페이지 UI를 추가한다.
4. 선택 기능은 찜/즐겨찾기를 먼저 추가하는 것이 범위 대비 효과가 크다.
