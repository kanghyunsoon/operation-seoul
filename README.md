# Operation KOREA

Operation KOREA는 TourAPI/Kakao Local 기반 장소를 사건 현장으로 재구성하고, 사용자가 실제 장소를 이동하며 퍼즐, 단서, 사건파일, 최종 추리 채팅을 통해 사건을 해결하는 모바일 야외 방탈출 웹앱입니다.

현재 방향은 단순 관광 미션 앱이 아니라 오프라인 사건파일 키트를 모바일 앱으로 디지털화하는 것입니다.

## 현재 MVP 목표

샘플 또는 관리자 생성 사건파일 1개가 다음 흐름으로 실제 플레이 가능해야 합니다.

1. 로그인
2. 에피소드 목록에서 사건파일 선택
3. 사건 브리핑 확인
4. 지도 화면 진입
5. 전체 조사 장소를 처음부터 지도에 표시
6. 역할별 마커 색상 표시
7. 장소 선택 후 바텀시트 확인
8. Tmap 내비 시작
9. GPS 또는 개발 모드 도착 판정
10. 도착한 장소의 퍼즐 열기
11. 퍼즐 정답 제출
12. reward_payload 기반 단서/증거/용의자/메모 해금
13. 사건파일 탭에서 해금 자료 확인
14. 실제 최종 장소 도착
15. 바다거북 스프식 최종 추리 채팅
16. 최종 정답 제출
17. CLEARED 처리
18. 클리어 리포트와 실제 역사 해설 확인
19. CLEARED 사용자만 리뷰 작성

## 핵심 구현 상태

| 영역 | 상태 | 비고 |
| --- | --- | --- |
| 인증/JWT/현재 사용자 | 구현됨 | ACTIVE 아닌 사용자 로그인/API 차단, 관리자 권한 구분 |
| 관리자 회원 관리 | 구현됨 | soft delete/status/role 관리 |
| episode/mission_spot/puzzle/progress | 구현됨 | 공개 API는 PUBLISHED만 접근 가능 |
| EP.01 seed | 구현됨 | 사건파일형 샘플 데이터 |
| 지도 전체 장소 표시 | 구현됨 | Kakao Map SDK 동적 로드, publicMarkerType만 사용 |
| Tmap 내비 | 구현됨 | `VITE_TMAP_APP_KEY` 필요 |
| 도착 판정 | 구현됨 | `app.dev-mode.arrival-enabled`와 `VITE_DEV_ARRIVAL`로 개발 모드 분리 |
| 퍼즐 제출 | 구현됨 | 정답/오답 메시지, reward_payload 적용 |
| 사건파일 탭 | 구현됨 | 용의자/증거/메모/단서/조사 기록 표시 |
| 최종 추리 | MVP 구현됨 | 규칙 기반 제한 답변, Gemini 고도화는 남음 |
| 최종 정답/CLEARED | 구현됨 | 점수 계산, 클리어 리포트 연결 |
| 리뷰 | 구현됨 | CLEARED + cleared_at 기준, 1인 1리뷰 |
| 관리자 사건파일 생성 | 구현 중 | TourAPI 기준 장소 + Kakao 주변 후보 + 초안 생성/검증/저장 |
| AI 생성 | MVP 구현 | 규칙 기반/Gemini draft 구조, 운영 검수 필수 |

## 주요 사용자 API

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/episodes` | 공개 사건파일 목록 |
| `GET` | `/api/v1/episodes/{episodeId}` | 사건파일 상세 |
| `POST` | `/api/v1/episodes/{episodeId}/start` | 플레이 시작/이어하기 |
| `GET` | `/api/v1/episodes/{episodeId}/map` | 전체 장소 지도 데이터. `isFinalPlace` 절대 미노출 |
| `POST` | `/api/v1/episodes/{episodeId}/spots/{spotId}/arrive` | GPS 도착 판정 |
| `GET` | `/api/v1/spots/{spotId}/puzzle` | 도착한 장소의 퍼즐 조회 |
| `POST` | `/api/v1/puzzles/{puzzleId}/submit` | 퍼즐 정답 제출 및 보상 적용 |
| `GET` | `/api/v1/episodes/{episodeId}/clue-board` | 단서 보드 조회 |
| `GET` | `/api/v1/episodes/{episodeId}/case-file` | 사건파일 탭 데이터 |
| `POST` | `/api/v1/episodes/{episodeId}/deduction/start` | 최종 추리 세션 시작 |
| `POST` | `/api/v1/deduction/{sessionId}/ask` | 제한형 AI 질문 |
| `GET` | `/api/v1/deduction/{sessionId}/questions` | 질문 기록 |
| `POST` | `/api/v1/episodes/{episodeId}/final-answer` | 최종 정답 제출 |
| `GET` | `/api/v1/episodes/{episodeId}/clear-report` | 클리어 리포트 |

## 주요 관리자 API

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/admin/episodes` | 사건파일 목록 |
| `GET` | `/api/v1/admin/episodes/{episodeId}` | 관리자 상세 |
| `GET` | `/api/v1/admin/episodes/place-candidates` | TourAPI 기준 후보 |
| `GET` | `/api/v1/admin/episodes/place-candidates/nearby` | Kakao Local 주변 후보 |
| `POST` | `/api/v1/admin/episodes/ai-draft` | 규칙 기반 사건파일 초안 |
| `POST` | `/api/v1/admin/episodes/ai-draft/gemini` | Gemini 초안 |
| `POST` | `/api/v1/admin/episodes/ai-draft/validate` | 초안 검증 |
| `POST` | `/api/v1/admin/episodes/ai-draft/save` | DRAFT 저장 |
| `GET` | `/api/v1/admin/episodes/{episodeId}/publish-readiness` | 공개 준비도 점검 |
| `PUT` | `/api/v1/admin/episodes/{episodeId}` | 사건파일 핵심 정보 수정/공개 전환 |
| `PUT` | `/api/v1/admin/episodes/{episodeId}/spots/{spotId}` | 장소 수정 |
| `PUT` | `/api/v1/admin/episodes/{episodeId}/puzzles/{puzzleId}` | 퍼즐/힌트/reward_payload 수정 |

## 주요 화면

| 경로 | 화면 | 설명 |
| --- | --- | --- |
| `/intro` | 로그인/회원가입 | 이메일/비밀번호 로그인 |
| `/episodes` | 사건파일 목록 | 공개 에피소드 목록, 관리자 진입 버튼 |
| `/episodes/:episodeId/briefing` | 사건 브리핑 | 시작 전 사건 개요 |
| `/episodes/:episodeId/map` | 지도 | 전체 장소 마커, Tmap 내비, 도착 판정, 퍼즐 |
| `/episodes/:episodeId/case-file` | 사건파일 | 수사자료, 용의자, 증거, 단서, 조사 기록 |
| `/episodes/:episodeId/deduction` | 최종 추리 | 제한형 질문/최종 정답 제출 |
| `/episodes/:episodeId/clear-report` | 클리어 리포트 | 진실 파일, 실제 역사 해설, 리뷰 |
| `/admin/episodes` | 관리자 사건파일 관리 | 생성/검수/공개 관리 |
| `/admin/users` | 관리자 회원 관리 | 권한/상태/soft delete |
| `/admin/reviews` | 관리자 리뷰 관리 | 숨김/복구/삭제 |

## 실행 방법

### Backend

```powershell
cd backend
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain bootRun
```

검증:

```powershell
cd backend
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain compileJava
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test
```

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

검증:

```powershell
cd frontend
npm run build
```

## 환경 변수

### frontend/.env

```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_KAKAO_MAP_KEY=KAKAO_JAVASCRIPT_KEY
VITE_TMAP_APP_KEY=TMAP_APP_KEY
VITE_DEV_ARRIVAL=true
```

### backend application properties

```properties
app.dev-mode.arrival-enabled=true
kakao.rest.api.key=KAKAO_REST_API_KEY
gemini.api.key=GEMINI_API_KEY
jwt.secret=CHANGE_ME
```

운영 모드에서는 `app.dev-mode.arrival-enabled=false`, `VITE_DEV_ARRIVAL=false`로 둡니다.

## 중요한 보안/게임 규칙

- `GET /episodes/{episodeId}/map` 응답에 `isFinalPlace`, 내부 최종 장소 정보는 절대 포함하지 않습니다.
- 프론트는 `publicMarkerType`만 보고 마커를 표시합니다.
- 실제 최종 장소는 서버 내부 `is_final_place`로만 판정합니다.
- 잘못된 조사 후보에서는 최종 추리를 시작할 수 없습니다.
- 실제 최종 장소에 도착하면 단서가 적어도 최종 추리를 시도할 수 있지만 점수 페널티가 적용됩니다.
- 최종 추리 AI는 정답이나 실제 최종 장소를 직접 말하면 안 됩니다.
- AI 생성 퍼즐은 실제 현장 간판, 숫자, 계단 수, 조형물을 임의로 만들어서는 안 됩니다.
- OAuth를 실제 구현하지 않았으므로 구글/네이버 로그인 버튼을 만들지 않습니다.

## 이어받기 문서

다른 Codex가 이어서 작업할 때는 아래 문서를 먼저 읽으세요.

- `docs/MVP_STATUS.md`
- `docs/CODEX_HANDOFF_PROMPT.md`