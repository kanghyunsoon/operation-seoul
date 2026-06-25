# 발표 자료

## Slide 1. 제목

Operation KOREA  
실제 장소 기반 미션 메모형 야외 방탈출 서비스

## Slide 2. 문제 정의

- 일반 관광 앱은 장소 정보 제공에 머무르는 경우가 많다.
- 사용자가 직접 이동하고 판단하는 몰입형 경험이 부족하다.
- 장소 데이터를 활용해 콘텐츠 제작 효율을 높일 필요가 있다.

## Slide 3. 서비스 목표

- 실제 장소를 사건의 단서 공간으로 활용
- 지도 이동, 도착 판정, 퍼즐, 단서, 최종 추리 연결
- 관리자 검수 기반 에피소드 운영 지원
- 리뷰, 커뮤니티, 랭킹, 챌린지로 지속 사용 유도

## Slide 4. 사용자 흐름

1. 로그인
2. 에피소드 선택
3. 사건 브리핑
4. 지도 이동
5. 도착 판정
6. 퍼즐 풀이
7. 단서 확인
8. 최종 추리
9. 클리어 리포트

## Slide 5. 시스템 구조

- Front-End: Vue 3, Pinia, Vue Router, Axios
- Back-End: Spring Boot, Spring Security, JWT, MyBatis
- DB: MySQL
- External: TourAPI, Kakao Local/Map, Tmap

## Slide 6. 핵심 기능

- 공개 에피소드 목록/상세
- 지도 기반 장소 탐색
- GPS/devMode 도착 판정
- 퍼즐과 힌트
- 단서/증거/용의자 해금
- 최종 추리와 정답 제출
- 클리어 리포트와 리뷰

## Slide 7. 관리자 기능

- 회원 관리
- 리뷰 관리
- TourAPI/Kakao Local 후보 조회
- DRAFT 검증
- 공개 준비도 확인
- PUBLISHED 전환

## Slide 8. DB 설계

- `users`: 사용자/권한
- `episodes`: 에피소드
- `mission_spots`: 조사 장소
- `puzzles`, `puzzle_hints`: 퍼즐
- `case_suspects`, `case_evidences`: 미션 파일
- `user_episode_progress`: 진행 기록
- `region_question`, `region_answer`: 커뮤니티
- `episode_reviews`, `rankings`, `challenges`: 후속 활동

## Slide 9. 외부 API

- TourAPI: 장소 후보 수집
- Kakao Local: 주변 후보 보강
- Kakao Maps: 지도 UI
- Tmap: 길찾기

## Slide 10. 보안과 게임 규칙

- JWT 인증
- 관리자 API 권한 분리
- 비활성 계정 차단
- 최종 장소 내부 필드 미노출
- 운영 비밀값 환경변수 관리
- 개발용 도착 판정 운영 분리

## Slide 11. 구현 성과

- 핵심 플레이 루프 구현 완료
- 관리자 관리/검수 흐름 구현 완료
- 커뮤니티/리뷰/랭킹/챌린지 MVP 구현
- Backend compile, Frontend build 통과
- 커뮤니티 게시글 등록 오류 수정 및 검증 완료

## Slide 12. 한계와 개선

- OAuth 운영 도메인 설정 검증 필요
- 쿠폰 지급 로직 미구현
- 실제 현장 GPS 검증 필요
- 향후 운영 대시보드와 실기기 테스트 강화

## Slide 13. 결론

Operation KOREA은 실제 장소 데이터와 게임형 추리를 결합해 관광/탐방 경험을 미션 기반 콘텐츠로 확장했다. 핵심 MVP는 구현되어 있으며, 현장 검증과 운영 기능 보강을 통해 실제 서비스 수준으로 발전시킬 수 있다.
