# Operation: SEOUL Frontend

Vue 3 + Vite 기반 프론트엔드입니다. 전체 온보딩 문서는 루트 `README.md`를 기준으로 보고, 이 문서는 프론트만 빠르게 실행하거나 구조를 확인할 때 사용합니다.

## 핵심 구조

| 경로 | 설명 |
| --- | --- |
| `src/api/axiosInstance.js` | 백엔드 기본 URL, JWT Authorization 헤더, 401/403 공통 처리 |
| `src/stores/sessionStore.js` | 로그인 토큰과 사용자 정보를 `localStorage`에 보존하는 Pinia store |
| `src/router/index.js` | Intro, Home, Briefing, Map, Chat, Clear 라우트와 로그인 가드 |
| `src/views/HomeView.vue` | 권역 선택, 관리자 후보지 스캔, 작전 카드 목록 |
| `src/views/MapView.vue` | Kakao 지도, GPS 추적, 사진 인증, 최종 목적지 해금 |
| `src/views/AiChatView.vue` | Gemini 스트리밍 채팅, 최종 정답 입력, 단서 패널 |
| `src/views/ClearView.vue` | 클리어 리포트, 점수, 단서별 역사 해설 |

## Project Setup

```sh
npm install
```

### 개발 서버

```sh
npm run dev
```

### 프로덕션 빌드

```sh
npm run build
```

## 환경 변수

`.env.example`을 `.env`로 복사한 뒤 값을 채웁니다.

| Key | 설명 |
| --- | --- |
| `VITE_API_BASE_URL` | 백엔드 API 기본 주소 |
| `VITE_KAKAO_MAP_KEY` | Kakao Maps JavaScript 키 |
| `VITE_TMAP_APP_KEY` | Tmap App Key |
