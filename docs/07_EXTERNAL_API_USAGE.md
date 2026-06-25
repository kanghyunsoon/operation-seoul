# 외부 API 활용 정리

## 1. TourAPI

| 항목 | 내용 |
| --- | --- |
| 목적 | 관광지/장소 후보 조회 |
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
| 목적 | 목적지 길찾기 내비게이션 연결 |
| 사용 위치 | 지도 화면 |
| 활용 방식 | 조사 장소까지 이동을 돕는 외부 내비 링크 |
| 보안 | `VITE_TMAP_APP_KEY` 사용 |

## 5. 운영 안전 기준

- 최종 정답과 최종 장소는 사용자 플레이 API에서 조기 노출하지 않는다.
- 외부 API 응답은 정확성, 현장 접근성, 운영 가능성을 관리자 검수 대상으로 둔다.
