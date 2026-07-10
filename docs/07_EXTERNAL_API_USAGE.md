# 외부 API 사용 정리

## TourAPI

관리자 화면에서 장소 후보를 가져올 때 사용한다.
필요한 값은 `TOURAPI_SERVICE_KEY`다.

관련 코드:

- `TourApiService`
- `AdminEpisodeController`

## Kakao

| API | 사용한 곳 | 설정 |
| --- | --- | --- |
| Kakao Local | 좌표 주변 장소 후보 조회 | `KAKAO_REST_API_KEY` |
| Kakao Map JS | 프론트 지도 표시 | `VITE_KAKAO_MAP_KEY` |
| Kakao OAuth | 소셜 로그인 | `KAKAO_OAUTH_CLIENT_ID`, `KAKAO_OAUTH_REDIRECT_URI` |

운영할 때는 Kakao 개발자 콘솔에 실제 도메인과 redirect URI를 등록해야 한다.

## Tmap

미션 장소 길찾기 버튼에 사용한다.

- 프론트: `VITE_TMAP_APP_KEY`
- 백엔드/공통 설정: `TMAP_APP_KEY`

모바일에서 앱이 열리는 방식은 실기기로 다시 봐야 한다.

## OpenAI / Gemini

AI 초안, 답안 계획, 검증 쪽에 사용한다.

사용 설정:

- `AI_PROVIDER`
- `OPENAI_API_KEY`
- `OPENAI_MODEL`
- `GEMINI_API_KEY`
- `GEMINI_MODEL`

AI가 실패하거나 이상한 값을 만들 수 있어서 validator와 fallback을 같이 뒀다.

## Google Vision

예전 관광 미션의 사진 인증 쪽에서 사용한다. 설정은 `GOOGLE_VISION_KEY`다.

## Wikipedia

AI 초안 만들 때 장소 설명을 보강하기 위해 사용한다.

- `EXTERNAL_RESEARCH_WIKIPEDIA_ENABLED`
- `EXTERNAL_RESEARCH_WIKIPEDIA_ENDPOINT`
