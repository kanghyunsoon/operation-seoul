# Operation KOREA

실제 장소를 돌아다니면서 사건을 해결하는 야외 방탈출 형식의 웹 서비스다.
처음에는 관광 미션 앱에 가까웠는데, 지금 코드는 에피소드 선택 → 지도 이동 → 장소 도착 → 퍼즐 풀이 → 단서 해금 → 최종 추리까지 이어지는 미션 메모 서비스로 정리돼 있다.

프론트는 Vue 3, 백엔드는 Spring Boot로 만들었고 DB는 MySQL을 기준으로 잡았다.

## 사용 기술

| 구분 | 내용 |
| --- | --- |
| Frontend | Vue 3, Vite, Pinia, Vue Router, Axios |
| Backend | Java 17, Spring Boot 4, Spring Security, MyBatis |
| DB | MySQL |
| 인증 | JWT, 이메일 로그인, Google/Kakao OAuth |
| 외부 연동 | TourAPI, Kakao Local/Map, Tmap, OpenAI/Gemini, Google Vision |

## 지금 구현된 흐름

1. 로그인하거나 소셜 로그인을 한다.
2. 권역이나 에피소드 목록에서 플레이할 사건을 고른다.
3. 브리핑을 확인하고 지도 화면으로 들어간다.
4. 지도에서 조사 장소를 찾아간다.
5. GPS 또는 개발용 도착 버튼으로 도착 처리를 한다.
6. 장소별 퍼즐이나 미니게임을 푼다.
7. 정답이면 단서, 증거, 용의자, 메모가 사건 파일에 열린다.
8. 모은 단서로 최종 장소와 정답을 추리한다.
9. 최종 장소에서 추리 질문을 하고 마지막 정답을 제출한다.
10. 정답이면 클리어 리포트와 리뷰 작성이 가능해진다.

## 주요 기능 정리

| 기능 | 현재 상태 | 관련 코드 |
| --- | --- | --- |
| 로그인/JWT | 구현됨 | `auth`, `JwtAuthenticationFilter` |
| Google/Kakao OAuth | 구현됨 | `OAuthService`, `OAuthCallbackView.vue` |
| 내 정보/비밀번호/탈퇴 | 구현됨 | `UserController`, `ProfileEditView.vue` |
| 관리자 회원 관리 | 구현됨 | `AdminUserController`, `AdminUsersView.vue` |
| 에피소드 목록/상세/시작 | 구현됨 | `EpisodePlayController`, `EpisodeListView.vue` |
| 지도/도착/퍼즐 | 구현됨 | `EpisodeMapView.vue`, `PuzzleCard.vue` |
| 미니게임 | 구현됨 | `frontend/src/components/episode/minigames` |
| 사건 파일/단서 보드 | 구현됨 | `CaseFileController`, `ClueBoard.vue` |
| 최종 추리/정답 제출 | 구현됨 | `DeductionAiService`, `FinalDeductionView.vue` |
| 클리어 리포트/리뷰 | 구현됨 | `ClearReportView.vue`, `EpisodeReviewController` |
| 지역 커뮤니티/Q&A | 구현됨 | `RegionReviewController`, `RegionQuestionController` |
| 피드/팔로우 | 구현됨 | `UserFeedController`, `UserFollowController` |
| 추천/랭킹/챌린지/코칭 | 구현됨 | `recommendation`, `ranking`, `challenge`, `coaching` |
| 관리자 에피소드 관리 | 구현됨 | `AdminEpisodeController`, `AdminEpisodesView.vue` |
| AI 에피소드 초안 | 구현됨 | `/api/v1/admin/episodes/ai-draft*` |
| 제휴 쿠폰 지급 | 아직 아님 | 테이블과 관리자 수정 구조만 있음 |

## 화면 경로

| 경로 | 화면 |
| --- | --- |
| `/intro` | 로그인/회원가입 |
| `/oauth/callback` | OAuth 콜백 |
| `/regions` | 권역 지도 |
| `/regions/:regionId/community` | 지역 커뮤니티 |
| `/community` | 전체 커뮤니티 |
| `/community/write` | 글 작성 |
| `/community/:regionId/posts/:questionId` | 글 상세 |
| `/episodes` | 에피소드 목록 |
| `/episodes/:episodeId` | 에피소드 상세 |
| `/episodes/:episodeId/briefing` | 브리핑 |
| `/episodes/:episodeId/map` | 지도/도착/퍼즐 |
| `/episodes/:episodeId/case-file` | 사건 파일 |
| `/episodes/:episodeId/deduction` | 최종 추리 |
| `/episodes/:episodeId/debriefing` | 해설 |
| `/episodes/:episodeId/clear-report` | 클리어 리포트 |
| `/favorites` | 관심 에피소드 |
| `/clear-map` | 클리어 지도 |
| `/feed`, `/feed/users/:userId` | 피드 |
| `/rankings` | 랭킹 |
| `/challenges` | 챌린지 |
| `/recommendations` | 추천 |
| `/coaching` | 코칭 |
| `/me`, `/me/edit`, `/me/reviews` | 마이페이지 |
| `/admin/users` | 관리자 회원 관리 |
| `/admin/reviews` | 관리자 리뷰 관리 |
| `/admin/episodes` | 관리자 에피소드 관리 |

`/home`, `/briefing`, `/map`, `/chat/:sessionId`, `/clear/:missionId` 같은 예전 경로는 새 에피소드 흐름으로 넘기게 해뒀다.

## 실행 방법

### Backend

```powershell
cd backend
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain bootRun
```

테스트/컴파일:

```powershell
cd backend
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava test
```

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

빌드:

```powershell
cd frontend
npm run build
```

전체 확인용:

```powershell
.\scripts\verify-release.ps1
```

## 환경 변수

### frontend/.env

```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_KAKAO_MAP_KEY=KAKAO_JAVASCRIPT_KEY
VITE_TMAP_APP_KEY=TMAP_APP_KEY
VITE_DEV_ARRIVAL=true
```

### backend

로컬에서는 `backend/src/main/resources/application-example.properties`를 보고 환경변수를 맞추면 된다.

주로 필요한 값은 아래 정도다.

```properties
DB_URL=jdbc:mysql://localhost:3306/operation_seoul
DB_USERNAME=root
DB_PASSWORD=
JWT_SECRET=change-me
OPENAI_API_KEY=
GEMINI_API_KEY=
TOURAPI_SERVICE_KEY=
KAKAO_REST_API_KEY=
GOOGLE_OAUTH_CLIENT_ID=
KAKAO_OAUTH_CLIENT_ID=
KAKAO_OAUTH_REDIRECT_URI=http://localhost:5173/oauth/callback
TMAP_APP_KEY=
CORS_ALLOWED_ORIGINS=http://localhost:5173
DEV_ARRIVAL_ENABLED=false
```

운영에서는 `DEV_ARRIVAL_ENABLED=false`, `VITE_DEV_ARRIVAL=false`로 둬야 한다. 실제 키는 properties 파일에 직접 적지 않고 환경변수로 넣는다.

## 구현하면서 신경 쓴 부분

- 사용자 지도 응답에는 최종 장소를 바로 알 수 있는 값을 빼뒀다.
- 프론트는 `publicMarkerType`만 보고 마커를 그린다.
- 실제 최종 장소 판정은 서버의 `mission_spots.is_final_place`로만 처리한다.
- 미니게임은 프론트에서 푼 값을 그대로 믿지 않고 `MG|TYPE|VALUE` proof를 서버에서 다시 검증한다.
- 최종 추리 답변은 정답이나 최종 장소를 바로 말하지 않게 제한했다.
- 리뷰는 해당 에피소드를 클리어한 사용자만 작성할 수 있다.

## 문서

정리 문서는 `docs` 폴더에 번호 순서대로 넣었다.
중복되는 단일 산출물 파일은 정리했고, 지금은 아래 파일들만 보면 된다.

- `00_FINAL_SUBMISSION_INDEX.md`
- `01_REQUIREMENTS_AND_COVERAGE.md`
- `02_DESIGN_DOCUMENT.md`
- `03_WBS_AND_GANTT.md`
- `04_SCREEN_DESIGN.md`
- `05_IMPLEMENTATION_RESULT.md`
- `06_TROUBLESHOOTING_AND_REMAINING_WORK.md`
- `07_EXTERNAL_API_USAGE.md`
- `08_FINAL_REPORT.md`
- `09_PRESENTATION_DRAFT.md`
