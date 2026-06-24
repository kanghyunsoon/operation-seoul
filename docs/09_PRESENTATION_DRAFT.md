# 발표 자료 초안

작성일: 2026-06-25

## Slide 1. 제목

Operation KOREA  
실제 장소 기반 야외 방탈출/미션 파일 서비스

## Slide 2. 문제 정의

- 일반 관광 앱은 장소 설명 중심이라 몰입도가 낮다.
- 사용자가 실제 장소를 이동하며 콘텐츠를 경험하는 서비스가 필요하다.
- 팀 프로젝트 요구사항인 CRUD, 회원, 리뷰, 추가 기능, AI/외부 API 활용을 하나의 도메인에 통합했다.

## Slide 3. 서비스 소개

- 권역별 미션 파일 선택.
- 실제 장소 지도 이동.
- GPS 도착 판정.
- 현장 퍼즐 풀이.
- 단서 해금과 최종 추리.
- 클리어 리포트와 리뷰.

## Slide 4. 주요 기능

- 회원가입/로그인/JWT.
- 콘텐츠 CRUD.
- 리뷰 CRUD.
- 찜/팔로우/일정/챌린지.
- 관리자 에피소드 생성/검수/공개.
- AI 추천/코칭 및 Gemini 초안 생성.

## Slide 5. 기술 스택

- Front-End: Vue 3, Pinia, Vue Router, Axios.
- Back-End: Spring Boot, Spring Security, JWT, MyBatis.
- DB: MySQL.
- External: TourAPI, Kakao Local/Map, Tmap.
- AI: Gemini.

## Slide 6. 시스템 아키텍처

Vue SPA → Spring Boot REST API → MyBatis → MySQL  
Spring Boot → TourAPI/Kakao Local/Gemini  
Vue → Kakao Map/Tmap

## Slide 7. DB/ERD

주요 테이블:

- users
- episodes
- episode_reviews
- user_plans
- user_follow
- challenges
- region/mission
- community tables

## Slide 8. 사용자 시나리오

1. 로그인
2. 권역 선택
3. 미션 선택
4. 브리핑 확인
5. 지도 이동
6. 퍼즐 풀이
7. 단서 확인
8. 최종 추리
9. 클리어 리포트/리뷰

## Slide 9. 관리자 시나리오

1. 장소 후보 조회
2. AI 초안 생성
3. 퍼즐/단서/증거 검수
4. 공개 준비도 확인
5. PUBLISHED 전환

## Slide 10. 요구사항 충족도

- 필수 기능: 약 88%
- 추가 기능: 약 85%
- 심화 기능: 약 68~70%
- 전체 구현률: 약 82%

## Slide 11. 트러블슈팅

- 지도 응답에서 최종 장소 노출 방지.
- AI 생성 텍스트 품질/검수 guardrail.
- 에피소드 목록 하단 UI 간격 문제.
- API 키/실기기 검증 필요.

## Slide 12. 성과와 한계

성과:

- Spring Boot REST API + Vue SPA 완성.
- MySQL/MyBatis 기반 DB 설계.
- 외부 API와 생성형 AI 활용.
- 팀 프로젝트 요구 기능 대부분 구현.

한계:

- 추천/코칭은 규칙 기반 MVP.
- 현장 GPS 검증 필요.
- 일부 UI polish 필요.

## Slide 13. 향후 계획

- 반응형 UI 안정화.
- Gemini 개인화 추천/코칭 고도화.
- 관리자 운영 기능 강화.
- E2E 테스트와 배포 자동화.

## Slide 14. 마무리

Operation KOREA는 실제 장소 경험과 추리 게임을 결합한 Final 프로젝트 MVP이며, 요구사항의 전 개발 생명주기를 구현과 문서로 정리했다.
