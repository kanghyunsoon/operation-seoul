# Operation KOREA Codex Handoff Prompt

작업 위치:
`C:\Users\khsoo\Desktop\operation-seoul`

다른 컴퓨터에서 이어받을 때 이 파일을 먼저 읽고 시작한다.

## 기본 작업 규칙

- 작업 전 `git status`를 확인한다. 현재 환경에서는 `git` 명령이 PATH에 없을 수 있으므로, 안 되면 변경 파일을 직접 확인한다.
- 기존 사용자 변경을 되돌리지 않는다.
- 코드 수정 전 관련 흐름을 검색하고, 최소 범위만 수정한다.
- Gemini/API 호출은 남발하지 않는다. 호출 결과가 의도와 다르면 즉시 재호출하지 말고 코드/프롬프트/검증을 고친 뒤 다시 1회 호출한다.
- PowerShell 콘솔 출력에서 한글이 깨져 보여도 파일 자체가 깨진 것은 아닐 수 있다. 실제 파일 확인은 `node -e "fs.readFileSync(path,'utf8')"` 방식으로 한다.
- BOM, 깨진 글자, `\uXXXX` 이스케이프가 들어가지 않게 한다.
- 작업이 끝나면 항상 다음 작업을 짧게 제안한다.

## 현재 개편 목표

Operation KOREA의 야외 방탈출/추리 미션 자동 생성 기능을 기존 장소 힌트 기반 구조에서 다음 구조로 개편 중이다.

- 장르는 범죄 스릴러/범죄 미스터리로 고정한다.
- 최종 정답 키워드는 항상 4개로 고정한다.
  - `CULPRIT` / 범인
  - `WEAPON` / 흉기
  - `MOTIVE` / 동기
  - `METHOD` / 방법
- 장소 힌트 시스템은 제거한다.
- 최종 장소는 플레이어가 추리하는 정답이 아니다.
- 최종 장소는 시작 장소를 제외한 조사 장소 8개를 모두 클리어하면 자동 공개된다.
- 최종 장소는 TourAPI/장소 후보에서 가져온 장소를 사용한다.
- TourAPI/외부 문서는 배경 모티브/RAG context로만 사용한다.
- 실제 장소에서 실제 범죄가 일어난 것처럼 쓰면 안 된다.
- 생성되는 스토리는 허구 사건이어야 한다.
- 플레이어가 수집하는 8개 단서는 단순 분위기 묘사가 아니라 범인/흉기/동기/방법을 추론하게 하는 직접 단서여야 한다.
- 각 단서는 최종 정답 value를 직접 노출하지 않아야 한다.
- 모든 단서는 서로 다른 정보를 제공해야 한다.
- 관리자/플레이어 문구에 `TourAPI`, `RAG`, `실제 장소`, `가상의 용의자`, `관리자 검수` 같은 몰입 방해 표현이 나오면 안 된다.

## 사용자가 원하는 사건 구조

생성 결과는 아래 스타일을 목표로 한다.

- 사건 개요: 피해자가 특정 장소/공간에서 의문사한다.
- 사망 원인, 잠긴 문, 외부 침입 흔적 없음 같은 핵심 미스터리 조건이 있다.
- 용의자는 3명이다.
- 각 용의자는 알리바이와 의심 포인트를 가진다.
- 최종 정답은 범인/흉기/동기/방법 4개다.
- 8개 단서는 단서 체인으로 하나의 진실에 수렴해야 한다.
- 내부 슬롯은 현재 2개씩 배분되어 있지만, 사용자/관리자/프롬프트에는 “범인 2개, 흉기 2개...”처럼 기계적으로 보이면 안 된다.
- 표현은 자연스러운 증거 체인으로 보여야 한다.

## 현재 완료된 주요 변경

실제 코드 기준으로 다음 흐름이 구현/검증되었다.

- Gemini 초안 생성 프롬프트가 범죄 미스터리 고정 구조로 개편됨.
- 최종 정답 슬롯은 `CULPRIT`, `WEAPON`, `MOTIVE`, `METHOD` 4개로 고정됨.
- 장소 힌트/최종 장소 추리 구조가 제거되고, 최종 장소는 자동 공개 대상이 됨.
- 에피소드 구조는 `START 1개 + 조사 미션 8개 + FINAL 1개`.
- 플레이어 맵에서는 최종 장소가 8개 조사 미션 완료 전 잠김.
- 8개 조사 완료 후 최종 장소가 자동 공개됨.
- 최종 정답 제출 및 클리어 리포트는 4개 정답 슬롯 기준으로 동작함.
- 관리자 UI의 슬롯 라벨은 한국어로 표시됨.
- 관리자 로그/검증 메시지 일부도 한국어화됨.
- Gemini 결과를 저장할 때 정답 value와 용의자 이름이 조사 단서에 직접 노출되지 않도록 후처리됨.
- `관련 인물`, `기록 속 인물`, `문서에 언급된 인물는` 같은 어색한 표현을 잡고 보정하는 후처리/검증이 추가됨.
- 저장 후처리에서 슬롯 문맥 기반 익명 표현을 사용함.
- 저장 후처리에서 다시 자연화하여 플레이어 문구를 더 자연스럽게 만듦.

최근 자연화 예시:

- `기록 속 인물` -> `메모의 대상자`
- CCTV 문맥 -> `동일 인물`
- 문자 문맥 -> `문자 발신자`
- 유언장 문맥 -> `해당 당사자`
- 온라인 구매 문맥 -> `동선 기록과 연결된 계정`

## 최근 검증된 상태

마지막 전체 파이프라인 검증:

- Gemini 추가 호출 없이 기존 `tmp-gemini-draft-response.json`을 사용
- 저장된 최신 검증 에피소드: `episodeId 163`
- 제목: `시간의 틈새 56719`
- 결과: 전체 파이프라인 통과

통과한 검증:

- 백엔드 테스트
- 저장 검증
- 관리자 UI 데이터 검증
- 플레이어 플로우 검증
- 플레이어 문구 품질/정답 누출 검증
- 최종 장소 잠금/8개 조사 완료 후 자동 공개
- 최종 정답 제출/클리어 리포트
- `??`, `\uXXXX`, `문서에 언급된 인물는` 잔존 없음

최근 실행한 주요 명령:

```powershell
cd C:\Users\khsoo\Desktop\operation-seoul\backend
.\gradlew.bat test --tests AdminEpisodeGeminiServiceTest --tests AdminEpisodeServiceAiDraftSaveTest
```

```powershell
cd C:\Users\khsoo\Desktop\operation-seoul
node scripts\smoke-ai-episode-pipeline.mjs
```

## 최근 수정 파일

핵심 backend:

- `backend/src/main/java/com/operation/seoul/admin/episode/service/AdminEpisodeGeminiService.java`
- `backend/src/main/java/com/operation/seoul/admin/episode/service/AdminEpisodeService.java`
- `backend/src/main/java/com/operation/seoul/episode/service/EpisodePlayService.java`
- `backend/src/main/java/com/operation/seoul/admin/episode/controller/AdminEpisodeController.java`

핵심 frontend:

- `frontend/src/views/AdminEpisodesView.vue`
- `frontend/src/components/PuzzleCard.vue`

검증 scripts:

- `scripts/smoke-gemini-draft.mjs`
- `scripts/validate-ai-draft.mjs`
- `scripts/save-gemini-draft.mjs`
- `scripts/smoke-admin-episode-ui-data.mjs`
- `scripts/smoke-player-flow.mjs`
- `scripts/smoke-player-content-quality.mjs`
- `scripts/smoke-ai-episode-pipeline.mjs`

임시 검증 결과 파일:

- `tmp-gemini-draft-response.json`
- `tmp-gemini-draft-save-response.json`
- `tmp-ai-episode-pipeline-smoke-response.json`
- `tmp-admin-ui-data-smoke-response.json`
- `tmp-player-flow-smoke-response.json`
- `tmp-player-content-quality-response.json`

## 주요 서비스 흐름

AI 생성:

1. 프론트 `AdminEpisodesView.vue`에서 AI 생성 요청 payload 전송
2. 백엔드 `AdminEpisodeGeminiService`가 프롬프트 구성
3. Gemini 응답 JSON을 `AiEpisodeDraftResponse` 계열 DTO로 파싱
4. guardrail/repair/post-process 적용
5. 프론트에서 초안 확인

AI 저장:

1. `AdminEpisodeService`가 AI draft 저장
2. Episode, MissionSpot, Puzzle, PuzzleHint, CaseSuspect, CaseEvidence, PartnerReward 저장
3. 조사 단서의 정답 value/용의자 이름 노출 방지
4. 슬롯별 단서 타입 유지
5. 최종 장소는 `FINAL_PLACE`, `finalPlace=true`, public marker는 `ANSWER_HINT`로 관리

플레이:

1. 플레이어는 시작 장소와 조사 장소만 우선 본다.
2. 최종 장소는 8개 조사 미션 완료 전 잠김.
3. 조사 8개 완료 후 최종 장소 자동 공개.
4. 최종 장소 도착 후 최종 정답 입력 가능.
5. 정답은 범인/흉기/동기/방법 4개 기준으로 판정.
6. 클리어 리포트는 수집 단서와 타입별 단서 카운트를 보여준다.

## 주의할 지점

- `finalAnswerKeywordItems`와 `finalAnswerKeywords`는 둘 다 호환 흐름이 남아 있다. 수정 시 둘 모두 확인한다.
- 내부적으로 단서 슬롯은 현재 `CULPRIT/WEAPON/MOTIVE/METHOD` 각 2개씩 배정되어 있다. 다만 프롬프트/관리자 문구에서는 “2개씩”을 노출하지 말고 자연스러운 8개 증거 체인으로 설명해야 한다.
- `finalPlace`는 정답이 아니다. 장소명/장소 힌트로 최종 장소를 맞히는 구조를 되살리면 안 된다.
- `publicMarkerType`은 프론트 표시용이다. 내부 최종 장소 판정은 서버 필드로 해야 한다.
- 새 Gemini 호출은 꼭 필요할 때만 1회 수행한다.
- 호출 결과가 나쁘면 재호출하지 말고 프롬프트/후처리/검증을 고친다.
- PowerShell `Get-Content`에서 한글이 깨져도 즉시 파일 손상으로 판단하지 말고 Node UTF-8 읽기로 확인한다.

## 검증 명령

Backend 단위 테스트:

```powershell
cd C:\Users\khsoo\Desktop\operation-seoul\backend
.\gradlew.bat test --tests AdminEpisodeGeminiServiceTest --tests AdminEpisodeServiceAiDraftSaveTest
```

Frontend 빌드:

```powershell
cd C:\Users\khsoo\Desktop\operation-seoul\frontend
npm.cmd run build
```

AI draft 로컬 검증:

```powershell
cd C:\Users\khsoo\Desktop\operation-seoul
node scripts\validate-ai-draft.mjs tmp-gemini-draft-response.json
```

기존 Gemini 결과 저장/관리자/플레이어 전체 파이프라인:

```powershell
cd C:\Users\khsoo\Desktop\operation-seoul
node scripts\smoke-ai-episode-pipeline.mjs
```

Gemini 신규 호출은 신중하게 1회:

```powershell
cd C:\Users\khsoo\Desktop\operation-seoul
$env:FORCE_GEMINI_CALL='true'
node scripts\smoke-gemini-draft.mjs
```

인코딩/BOM/`\uXXXX` 확인 예시:

```powershell
node -e "const fs=require('fs'); for (const p of ['backend/src/main/java/com/operation/seoul/admin/episode/service/AdminEpisodeService.java']) { const s=fs.readFileSync(p,'utf8'); if (s.charCodeAt(0)===0xfeff) throw new Error('BOM '+p); if (/\\u[0-9a-fA-F]{4}/.test(s)) throw new Error('unicode escape '+p); } console.log('encoding checks passed');"
```

## 다음에 이어서 할 일

권장 다음 최소 단위:

1. 새 Gemini 호출을 1회만 실행한다.
2. 새로 생성된 에피소드가 기존 예시처럼 매번 다른 사건/용의자/단서 체인으로 나오는지 확인한다.
3. 결과가 의도와 다르면 재호출하지 말고 프롬프트/후처리/검증만 수정한다.
4. 특히 다음을 확인한다.
   - 최종 정답이 범인/흉기/동기/방법 4개인지
   - 용의자 3명의 알리바이/의심 포인트가 충분한지
   - 8개 단서가 서로 다른 정보인지
   - 단서가 정답 value를 직접 노출하지 않는지
   - 최종 장소가 정답 추리 대상이 아닌지
   - TourAPI/외부 문서 정보가 배경 모티브로만 쓰였는지
   - 실제 장소에서 실제 범죄가 난 것처럼 보이지 않는지
   - 플레이어 문구에 `TourAPI`, `RAG`, `실제 장소`, `가상의 용의자`, `관리자 검수`, 깨진 글자, `\uXXXX`가 없는지

## 장기 남은 과제

- Gemini 실시간 최종 추리 고도화
- TourAPI 외부 문서/RAG 보강 소스 품질 개선
- 실제 모바일 Kakao Map/Tmap/GPS 현장 QA
- PUBLISHED 전환 전 운영 승인/현장 검수 정책 정리
- 추천/코칭의 Gemini 기반 개인화 고도화
- 실제 제휴 리워드/쿠폰 지급 흐름
