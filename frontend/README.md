# Operation KOREA Frontend

Vue 3 + Vite로 만든 프론트엔드다. 전체 실행 방법은 루트 `README.md`에 있고, 이 파일은 프론트만 볼 때 참고하면 된다.

## 실행

```sh
npm install
npm run dev
```

빌드:

```sh
npm run build
```

## 환경 변수

`.env`에 아래 값을 넣는다.

| Key | 내용 |
| --- | --- |
| `VITE_API_BASE_URL` | 백엔드 API 주소. 예: `http://localhost:8080/api` |
| `VITE_KAKAO_MAP_KEY` | Kakao 지도 JS 키 |
| `VITE_TMAP_APP_KEY` | Tmap 길찾기 키 |
| `VITE_DEV_ARRIVAL` | 개발용 도착 버튼 사용 여부 |

운영에서는 `VITE_DEV_ARRIVAL=false`로 둔다.

## 폴더

| 위치 | 내용 |
| --- | --- |
| `src/main.js` | 앱 시작 |
| `src/router/index.js` | 라우트와 로그인/관리자 가드 |
| `src/stores/sessionStore.js` | 로그인 토큰과 현재 사용자 |
| `src/api` | API 호출 함수 |
| `src/views` | 화면 컴포넌트 |
| `src/components` | 공통 컴포넌트 |
| `src/components/episode` | 에피소드 플레이 관련 컴포넌트 |
| `src/components/episode/minigames` | 퍼즐 미니게임 |
| `src/styles/operation-korea-design-system.css` | 공통 스타일 |

## 라우트

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
| `/admin/users`, `/admin/reviews`, `/admin/episodes` | 관리자 |

예전 경로(`/home`, `/briefing`, `/map`, `/chat/:sessionId`, `/clear/:missionId`)는 새 에피소드 흐름으로 리다이렉트한다.
