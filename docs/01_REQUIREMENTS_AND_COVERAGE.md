# 01. 요구사항 및 구현 커버리지

작성일: 2026-06-25

## 1. 문서 목적

본 문서는 `Operation Seoul` 프로젝트의 주요 요구사항과 현재 구현 커버리지를 정리한다. 기능별 구현 상태, 근거 파일, 남은 보완 사항을 추적하기 위한 문서이다.

상세 요구사항 정의는 루트의 `REQUIREMENTS.md`를 기준 문서로 사용한다.

## 2. 프로젝트 요구사항 요약

`Operation Seoul`은 TourAPI 기반 관광지 데이터를 활용해 LLM/RAG로 관광형 방탈출 미션을 생성하고, 사용자가 실제 지역을 탐험하며 미션을 수행하는 웹 서비스이다.

핵심 흐름은 다음과 같다.

- TourAPI 관광지 데이터 수집
- LLM/RAG 기반 미션, 장소, 퍼즐, 단서, 최종 추리 구성
- 지역 기반 미션 플레이
- 위치 기반 장소 도착 및 퍼즐 풀이
- 최종 추리 대화 및 최종 정답 제출
- 플레이 로그 기반 AI 플레이어 성향 분석
- 커뮤니티, 피드, 팔로우, 챌린지, 랭킹 기능 제공
- 관리자 미션/사용자/리뷰 관리

## 3. 기능 요구사항 커버리지

| FR ID | 영역 | 요구사항 | 우선순위 | 현재 구현 상태 | 커버리지 | 근거 |
| --- | --- | --- | --- | --- | ---: | --- |
| F01 | 인증 | 회원가입, 로그인, JWT 인증 | 필수 | 구현됨 | 95% | `AuthController`, `JwtAuthenticationFilter`, `sessionStore` |
| F02 | 사용자 | 내 정보 조회 | 필수 | 구현됨 | 95% | `UserController`, `userApi.me` |
| F03 | 사용자 | 내 정보 수정 | 필수 | 닉네임, 사진, 상태메시지, 공개 여부 수정 구현 | 95% | `ProfileEditView.vue`, `UserService` |
| F04 | 사용자 | 비밀번호 변경 | 필수 | 구현됨 | 90% | `UserController`, `ProfileEditView.vue` |
| F05 | 사용자 | 프로필 공개/비공개 | 필수 | 공개 여부 저장 및 타인 피드 접근 제한 구현 | 90% | `profile_public`, `UserFeedService` |
| F06 | 미션 | 지역별 미션 목록 | 필수 | 구현됨 | 95% | `EpisodePlayController`, `EpisodeListView.vue` |
| F07 | 미션 | 미션 상세 및 시작 | 필수 | 구현됨 | 95% | `EpisodeDetailView.vue`, `EpisodePlayService` |
| F08 | 미션 | 지도/장소 도착/퍼즐 | 필수 | 구현됨 | 90% | `EpisodeMapView.vue`, `EpisodeRepository` |
| F09 | 미션 | 단서 수집 및 사건 파일 | 필수 | 구현됨 | 90% | `CaseFileService`, `EpisodeCaseFileView.vue` |
| F10 | 최종 추리 | 추리 질문/응답 | 필수 | 구현됨 | 90% | `FinalDeductionView.vue`, `final_deduction_questions` |
| F11 | 최종 추리 | 최종 정답 제출 및 채점 | 필수 | 기존 채점 로직 유지 | 95% | `EpisodePlayService` |
| F12 | AI 분석 | 최종 제출 후 플레이 분석 생성 | 필수 | 생성 및 저장 구현 | 90% | `PlayerAnalysisService`, `player_analysis` |
| F13 | AI 분석 | 플레이 MBTI 4축 표시 | 필수 | 피드/모달 표시 구현 | 90% | `FeedView.vue`, `AiAnalysisModal.vue` |
| F14 | 커뮤니티 | 전체 게시글 목록 | 필수 | 지역별 API 병렬 호출로 전체 조립 | 80% | `CommunityHubView.vue`, `RegionQuestionController` |
| F15 | 커뮤니티 | 지역권 탭 및 슬라이딩 | 필수 | 구현됨 | 90% | `CommunityHubView.vue` |
| F16 | 커뮤니티 | 검색 및 정렬 | 필수 | 검색 범위, 최신순/추천순 구현 | 85% | `CommunityHubView.vue` |
| F17 | 커뮤니티 | 게시글 작성 | 필수 | 별도 작성 화면 구현 | 90% | `CommunityPostWriteView.vue` |
| F18 | 커뮤니티 | 게시글 상세 | 필수 | 상세 화면 구현 | 90% | `CommunityPostDetailView.vue` |
| F19 | 커뮤니티 | 게시글 수정/삭제 | 필수 | 작성자/관리자 권한 기반 구현 | 90% | `RegionQuestionService`, `CommunityPostDetailView.vue` |
| F20 | 커뮤니티 | 댓글 작성/수정/삭제 | 필수 | 구현됨 | 90% | `RegionQuestionService`, `CommunityPostDetailView.vue` |
| F21 | 커뮤니티 | 게시글 추천 | 필수 | 구현됨 | 90% | `region_question_like`, `toggleQuestionLike` |
| F22 | 커뮤니티 | 공지사항 | 필수 | 관리자만 공지 작성, 상단 고정 구현 | 90% | `is_notice`, `CommunityHubView.vue` |
| F23 | 소셜 | 팔로우/팔로우 해제 | 필수 | 구현됨 | 90% | `UserFollowController`, `followApi` |
| F24 | 소셜 | 팔로워/팔로잉 목록 | 필수 | 피드 팝업 구현 | 90% | `FeedView.vue`, `UserFollowRepository` |
| F25 | 피드 | 사용자 피드 | 필수 | 본인/타인 피드 구현 | 90% | `UserFeedController`, `FeedView.vue` |
| F26 | 피드 | 비공개 피드 제한 | 필수 | 구현됨 | 90% | `UserFeedService` |
| F27 | 피드 | 커뮤니티 게시글 5개 페이지 | 필수 | 구현됨 | 90% | `UserFeedRepository`, `FeedView.vue` |
| F28 | 피드 | 클리어 맵 캐러셀 | 필수 | 구현됨 | 90% | `FeedView.vue` |
| F29 | 챌린지 | 4종 챌린지 목표 | 필수 | 계산형 요약 구현 | 85% | `ChallengeMetricRepository`, `ChallengeService` |
| F30 | 챌린지 | 달성 챌린지 목록 | 필수 | 구현됨 | 85% | `ChallengeSummaryResponse`, `ChallengesView.vue` |
| F31 | 랭킹 | 누적 점수 랭킹 | 필수 | 구현됨 | 90% | `RankingService`, `rankingApi` |
| F32 | 랭킹 | 10명씩 페이지네이션 | 필수 | 프론트 페이지네이션 구현 | 85% | `ChallengesView.vue` |
| F33 | 랭킹 | 달성 챌린지 수 표시 | 필수 | 구현됨 | 85% | `PlayerRankingResponse` |
| F34 | 관리자 | 미션 생성/관리 | 필수 | 구현됨 | 85% | `AdminEpisodesView.vue`, `AdminEpisodeService` |
| F35 | 관리자 | 사용자 관리 | 필수 | 구현됨 | 85% | `AdminUsersView.vue`, `AdminUserService` |
| F36 | 관리자 | 리뷰 관리 | 필수 | 구현됨 | 85% | `AdminReviewsView.vue`, `AdminReviewController` |
| F37 | 리뷰 | 미션/지역 리뷰 | 선택 | 기존 리뷰 기능 유지 | 80% | `RegionReviewController`, `MyReviewsView.vue` |
| F38 | 즐겨찾기 | 관심 미션 관리 | 선택 | 구현됨 | 85% | `FavoriteEpisodesView.vue`, `episodeApi` |

## 4. 비기능 요구사항 커버리지

| NFR ID | 요구사항 | 측정 기준 | 현재 구현 상태 | 커버리지 | 근거 |
| --- | --- | --- | --- | ---: | --- |
| NFR-001 | 모바일 반응형 UI | 360px, 390px, 768px, 1280px에서 주요 텍스트/버튼/카드 겹침 없음 | 주요 화면 반응형 스타일 적용 | 80% | Vue scoped CSS |
| NFR-002 | 프론트엔드 빌드 성공 | `npm run build` exit code 0 | 통과 | 90% | Vite build |
| NFR-003 | 백엔드 컴파일/테스트 성공 | 변경 범위에 따라 `gradlew compileJava` 또는 대상 테스트 exit code 0 | 대상 검증 통과 | 80% | Gradle targeted tests |
| NFR-004 | API 오류 안내 | API 실패 시 빈 화면 대신 오류 문구/fallback UI 표시 | 프론트 userMessage 처리 | 85% | `axiosInstance.js` |
| NFR-005 | AI 실패 대응 | AI 호출 실패 또는 JSON 파싱 실패 시 fallback 분석 반환 | fallback 분석 구현 | 90% | `PlayerAnalysisService` |
| NFR-006 | 인증 보호 | 비로그인 사용자가 인증 API/화면 접근 시 성공 응답 또는 화면 진입 불가 | 인증 필요 라우트 및 API 보호 | 90% | router guard, Spring Security |
| NFR-007 | 권한 검증 | 타 사용자의 게시글/댓글 수정/삭제 요청 거부 | 작성자/관리자 수정 삭제 제한 | 90% | `requireOwnerOrAdmin` |
| NFR-008 | DB 최소 변경 | 신규 기능별 필요한 테이블/컬럼만 추가 | 필요한 컬럼/테이블 중심 추가 | 85% | schema migration classes |
| NFR-009 | 한글 UI 무결성 | 주요 화면 한글 텍스트가 UTF-8로 정상 표시 | 일부 깨짐 수정, 추가 점검 필요 | 80% | `KoreanMojibakeRepair`, Vue text |
| NFR-010 | 목록 페이지네이션 | 커뮤니티 10개, 피드 게시글 5개, 랭킹 10명 단위 페이지 이동 | 주요 목록 페이지네이션 구현 | 85% | `CommunityHubView.vue`, `FeedView.vue`, `ChallengesView.vue` |

## 5. 외부 API 및 AI 연동 커버리지

| 영역 | 요구사항 | 현재 구현 상태 | 커버리지 | 근거 |
| --- | --- | --- | ---: | --- |
| TourAPI | 관광지 데이터 기반 미션 생성 | 관리자 미션 생성 파이프라인에 반영 | 80% | `TourApiPlaceClient`, admin episode services |
| Gemini/LLM | 미션 초안 생성 | 구현됨 | 85% | `GeminiDraftService`, `GeminiContentClient` |
| Gemini/LLM | 최종 추리 응답 | 구현됨 | 80% | final deduction service |
| Gemini/LLM | AI 플레이 분석 | 구현 및 fallback 포함 | 90% | `PlayerAnalysisService` |
| 위치/GPS | 장소 도착 판단 | 구현됨 | 80% | episode arrive APIs |

## 6. 데이터 커버리지

| 데이터 영역 | 주요 테이블/필드 | 현재 구현 상태 |
| --- | --- | --- |
| 사용자 | `users`, `profile_image_url`, `status_message`, `profile_public` | 구현됨 |
| 팔로우 | `user_follow` | 구현됨 |
| 미션 | `episodes`, `mission_spots`, `puzzles`, `puzzle_hints` | 구현됨 |
| 플레이 진행 | `user_episode_progress` | 구현됨 |
| 최종 추리 | `final_deduction_sessions`, `final_deduction_questions` | 구현됨 |
| AI 분석 | `player_analysis`, `player_analysis_mbti`, `reasoning_answer` | 구현됨 |
| 커뮤니티 | `region_question`, `region_answer`, `region_question_like` | 구현됨 |
| 공지 | `region_question.is_notice` | 구현됨 |
| 챌린지 | `challenges`, `user_challenge_entries`, 계산형 지표 | 부분 구현 |
| 랭킹 | `user_episode_progress.score` 기반 집계 | 구현됨 |

## 7. 화면 커버리지

| 화면 | 현재 상태 | 근거 |
| --- | --- | --- |
| 인트로/로그인 | 구현됨 | `IntroView.vue` |
| 지역 지도 | 구현됨 | `RegionMapView.vue` |
| 미션 목록 | 구현됨 | `EpisodeListView.vue` |
| 미션 상세 | 구현됨 | `EpisodeDetailView.vue` |
| 미션 브리핑 | 구현됨 | `EpisodeMissionBriefingView.vue` |
| 미션 지도 | 구현됨 | `EpisodeMapView.vue` |
| 사건 파일 | 구현됨 | `EpisodeCaseFileView.vue` |
| 최종 추리 | 구현됨 | `FinalDeductionView.vue` |
| 클리어 리포트 | 구현됨 | `ClearReportView.vue` |
| 커뮤니티 목록 | 구현됨 | `CommunityHubView.vue` |
| 게시글 작성 | 구현됨 | `CommunityPostWriteView.vue` |
| 게시글 상세 | 구현됨 | `CommunityPostDetailView.vue` |
| 피드 | 구현됨 | `FeedView.vue` |
| 챌린지/랭킹 | 구현됨 | `ChallengesView.vue` |
| 내 정보 | 구현됨 | `MyPageView.vue` |
| 내 정보 수정 | 구현됨 | `ProfileEditView.vue` |
| 관리자 미션 | 구현됨 | `AdminEpisodesView.vue` |
| 관리자 사용자 | 구현됨 | `AdminUsersView.vue` |
| 관리자 리뷰 | 구현됨 | `AdminReviewsView.vue` |

## 8. 현재 종합 커버리지

| 영역 | 평가 |
| --- | ---: |
| 필수 기능 평균 | 89% |
| 선택 기능 평균 | 75% |
| 화면 완성도 | 84% |
| 백엔드 API 완성도 | 88% |
| 데이터 모델 완성도 | 86% |
| AI/외부 API 연동 완성도 | 84% |
| 운영/검증 완성도 | 80% |
| 전체 추정 구현률 | 85% |

## 9. 주요 보완 필요 사항

| 보완 영역 | 현재 한계 | 권장 조치 |
| --- | --- | --- |
| 전체 커뮤니티 조회 | 현재 지역별 API를 프론트에서 병렬 호출해 조립 | `GET /api/v1/community/questions` 같은 전체 게시글 전용 API 추가 |
| 챌린지 저장 모델 | 4종 신규 챌린지는 계산형이며 DB 달성 이력 저장은 제한적 | 목표 달성 이력 테이블 추가 검토 |
| 랭킹 페이지네이션 | 프론트에서 100명 로드 후 10명씩 표시 | 백엔드 limit/offset 또는 page/size 기반 페이지네이션 추가 |
| 이미지 업로드 | 프로필 사진은 URL 또는 제한된 데이터 저장 방식 | 파일 업로드 서버 또는 오브젝트 스토리지 연동 |
| E2E 테스트 | 주요 사용자 흐름 자동화 부족 | Playwright/Cypress smoke test 추가 |
| GPS 검증 | 실제 현장 검증 미완료 | 현장 테스트 및 좌표/반경 보정 |
| AI 품질 검증 | fallback은 있으나 생성 결과 품질 평가지표 제한 | 프롬프트 테스트셋 및 응답 검증 규칙 강화 |

## 10. 인수 조건 체크리스트

### 10.1 관광 미션 핵심 흐름

- [x] 사용자가 로그인 후 지역 지도에서 지역을 선택할 수 있다.
- [x] 선택한 지역의 미션 목록이 표시된다.
- [x] 사용자가 미션 상세 화면에서 미션 정보를 확인하고 시작할 수 있다.
- [x] 미션 브리핑 이후 미션 지도 화면으로 이동한다.
- [x] 사용자가 장소 도착 인증 또는 도착 처리를 수행할 수 있다.
- [x] 장소별 퍼즐 완료 후 해당 장소가 완료 처리된다.
- [x] 퍼즐 완료 후 단서가 사건 파일에 반영된다.
- [x] 필수 미션 흐름 완료 후 최종 추리 단계로 진입할 수 있다.
- [x] 저장된 미션 진행 상태가 다시 조회된다.

### 10.2 최종 추리 핵심 흐름

- [x] 사용자가 최종 추리 화면에서 최대 20개의 추리 질문을 입력할 수 있다.
- [x] AI 응답과 사용자 질문이 화면에 표시된다.
- [x] 20개 제한에 도달하면 추가 질문이 제한된다.
- [x] 사용자가 최종 정답을 제출할 수 있다.
- [x] 제출된 최종 정답은 기존 채점 로직에 따라 정답/오답으로 판정된다.
- [x] 최종 정답 제출 후 점수, 오답 횟수, 플레이 시간 등 로그가 저장된다.
- [x] 최종 정답 제출 후 AI 플레이어 분석이 생성 또는 조회 가능하다.
- [x] AI 플레이어 분석 결과가 DB에 저장된다.
- [x] AI 호출 실패 시 fallback 분석 결과가 반환된다.

### 10.3 전체 인수 조건

- [x] 사용자가 로그인할 수 있다.
- [x] 사용자가 미션을 조회하고 시작할 수 있다.
- [x] 사용자가 미션 지도에서 장소 도착 및 퍼즐을 진행할 수 있다.
- [x] 사용자가 최종 추리 후 정답을 제출할 수 있다.
- [x] 최종 정답 제출 후 AI 플레이 분석이 저장된다.
- [x] 피드에서 AI 플레이 분석과 MBTI를 볼 수 있다.
- [x] 본인 피드에서만 분석 업데이트 버튼이 보인다.
- [x] 커뮤니티 전체 게시글 목록을 볼 수 있다.
- [x] 공지사항이 일반 게시글 위에 표시된다.
- [x] 게시글 작성, 수정, 삭제가 가능하다.
- [x] 댓글 작성, 수정, 삭제가 가능하다.
- [x] 게시글 작성자를 팔로우/해제할 수 있다.
- [x] 피드에서 팔로워/팔로잉 목록을 팝업으로 볼 수 있다.
- [x] 팔로워/팔로잉 목록에서 유저 피드로 이동할 수 있다.
- [x] 비공개 사용자 피드는 안내 문구를 표시한다.
- [x] 챌린지/랭킹 탭에서 두 파트를 전환할 수 있다.
- [x] 랭킹은 누적 점수 기준으로 10명씩 표시된다.
- [x] 피드 요약에는 달성 챌린지 수, 팔로워 수, 팔로잉 수가 표시된다.
- [x] 프론트엔드 빌드가 성공한다.
- [x] 백엔드 대상 테스트가 성공한다.

## 11. 제출 및 시연 시 주의사항

- AI 기능은 외부 API 키와 네트워크 상태에 따라 fallback으로 동작할 수 있다.
- TourAPI 데이터 품질에 따라 생성 미션의 장소/설명 품질이 달라질 수 있다.
- 커뮤니티 전체 조회는 현재 프론트 조립 방식이므로 데이터가 많을 때 성능 보완이 필요하다.
- 랭킹은 현재 최대 100명 조회 후 프론트에서 페이지네이션한다.
- 프로필 사진은 별도 파일 스토리지 연동 전까지 큰 이미지 업로드에 제한이 있다.
- 실제 GPS 기반 미션은 현장 테스트를 통해 반경과 좌표를 보정해야 한다.
