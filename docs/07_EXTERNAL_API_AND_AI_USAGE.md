# 외부 API 및 생성형 AI 활용 정리

## 1. TourAPI

| 항목 | 내용 |
| --- | --- |
| 목적 | 역사/관광 장소 후보 수집 |
| 사용 위치 | `TourApiService`, 관리자 에피소드 후보 조회 |
| 활용 방식 | 권역/좌표 기반 후보를 가져와 관리자 검수 대상으로 표시 |
| 보안 | `TOURAPI_SERVICE_KEY` 환경변수 사용 |
| 예외 처리 | 키 누락 시 `TOURAPI_SERVICE_KEY_MISSING` 반환 |

## 2. Kakao Local API

| 항목 | 내용 |
| --- | --- |
| 목적 | 특정 좌표 주변 장소 후보 조회 |
| 사용 위치 | `KakaoLocalCandidateService` |
| 활용 방식 | TourAPI 후보 주변의 실제 장소 보강 |
| 보안 | `KAKAO_REST_API_KEY` 환경변수 사용 |
| 예외 처리 | 키 누락 시 `KAKAO_REST_API_KEY_MISSING` 반환 |

## 3. Kakao Maps JavaScript

| 항목 | 내용 |
| --- | --- |
| 목적 | 사용자 지도 화면 표시 |
| 사용 위치 | `EpisodeMapView.vue` |
| 활용 방식 | 조사 장소 마커, 현재 위치, 이동 대상 표시 |
| 보안 | `VITE_KAKAO_MAP_KEY` 사용, 허용 도메인 등록 필요 |

## 4. Tmap

| 항목 | 내용 |
| --- | --- |
| 목적 | 목적지 길찾기/내비게이션 연결 |
| 사용 위치 | 지도 화면 |
| 활용 방식 | 조사 장소까지 이동을 돕는 외부 내비 링크 |
| 보안 | `VITE_TMAP_APP_KEY` 사용 |

## 5. Gemini 생성형 AI

| 항목 | 내용 |
| --- | --- |
| 목적 | 에피소드 초안, 단서/퍼즐/서사 초안 생성 |
| 사용 위치 | `AdminEpisodeGeminiService`, `GeminiDraftGenerator` |
| 활용 방식 | 관리자가 입력한 권역/장소 후보를 바탕으로 DRAFT 초안 생성 |
| 검증 | `AiEpisodeDraftValidator`, `Draft*Guardrail`, publish readiness |
| 보안 | `GEMINI_API_KEY`, `GEMINI_MODEL` 환경변수 사용 |
| 운영 원칙 | AI 결과는 즉시 공개하지 않고 관리자 검수 후 공개 |

## 6. AI 활용 윤리/안전 기준

- 실제 장소에 없는 간판, 숫자, 조형물, 계단 수를 AI가 임의로 만들지 않도록 제한한다.
- 최종 정답, 최종 장소, 핵심 반전은 사용자 응답에서 직접 노출하지 않는다.
- AI 초안은 DRAFT 상태로만 저장하고 PUBLISHED 전 검수한다.
- 외부 API와 AI 응답은 저작권/정확성/현장 접근성을 관리자 검수 대상으로 둔다.

