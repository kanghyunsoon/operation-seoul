# Use-Case Diagram

## 1. Use-Case Diagram

```mermaid
flowchart TB
    subgraph System["Operation KOREA System"]
        UC_Register(("회원가입"))
        UC_Login(("일반 로그인"))
        UC_OAuth(("Google/Kakao 로그인"))
        UC_Profile(("프로필 관리"))
        UC_Search(("에피소드 검색/필터"))
        UC_Select(("에피소드 선택"))
        UC_Briefing(("사건 브리핑 확인"))
        UC_Map(("지도에서 장소 탐색"))
        UC_Arrive(("현장 도착 판정"))
        UC_Puzzle(("퍼즐/미니게임 풀이"))
        UC_Reward(("단서/증거 해금"))
        UC_CaseFile(("미션 파일 확인"))
        UC_Deduction(("최종 추리 진행"))
        UC_Clear(("클리어 리포트 확인"))
        UC_Review(("리뷰 작성"))
        UC_Community(("커뮤니티 이용"))
        UC_Ranking(("랭킹/챌린지 확인"))
        UC_AdminUser(("회원 관리"))
        UC_AdminReview(("리뷰 관리"))
        UC_AdminCandidate(("장소 후보 조회"))
        UC_AdminEpisode(("에피소드 검수/공개"))
        UC_Readiness(("공개 준비도 검증"))
    end

    Guest((비회원)) --> UC_Register
    Guest --> UC_Login
    Guest --> UC_OAuth

    Player((일반 사용자)) --> UC_Profile
    Player --> UC_Search
    Player --> UC_Select
    Player --> UC_Briefing
    Player --> UC_Map
    Player --> UC_CaseFile
    Player --> UC_Deduction
    Player --> UC_Clear
    Player --> UC_Review
    Player --> UC_Community
    Player --> UC_Ranking

    Admin((관리자)) --> UC_AdminUser
    Admin --> UC_AdminReview
    Admin --> UC_AdminCandidate
    Admin --> UC_AdminEpisode

    UC_Select -. include .-> UC_Briefing
    UC_Map -. include .-> UC_Arrive
    UC_Arrive -. include .-> UC_Puzzle
    UC_Puzzle -. include .-> UC_Reward
    UC_Reward -. include .-> UC_CaseFile
    UC_Deduction -. extend .-> UC_Clear
    UC_AdminEpisode -. include .-> UC_Readiness
```

## 2. Use-Case 명세

| Use Case | Actor | 선행 조건 | 기본 흐름 | 예외/대안 |
| --- | --- | --- | --- | --- |
| 로그인 | 비회원 | 계정 또는 OAuth 계정 존재 | 인증 → JWT 발급 → 권역 지도 이동 | 비활성/오류 계정은 실패 |
| 에피소드 플레이 | 사용자 | 로그인, 공개 에피소드 존재 | 목록 → 브리핑 → 지도 → 도착 → 퍼즐 → 단서 해금 | GPS 실패 시 안내 |
| 최종 추리 | 사용자 | 조사 미션 완료 | 최종 장소 도착 → 질문/가설 → 정답 제출 | 오답 시 시간 패널티 |
| 에피소드 공개 | 관리자 | DRAFT 존재 | readiness 확인 → 공개 전환 | 필수 데이터 누락 시 차단 |

