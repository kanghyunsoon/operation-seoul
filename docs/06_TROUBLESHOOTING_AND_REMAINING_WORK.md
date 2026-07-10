# 트러블슈팅 / 남은 작업

## 1. 해결한 것

| 문제 | 처리 |
| --- | --- |
| 최종 장소가 지도 응답에서 노출될 수 있음 | 사용자 응답에서는 `publicMarkerType`만 쓰게 정리 |
| AI 초안이 정답을 너무 직접 말할 수 있음 | prompt, guardrail, validator 추가 |
| 미니게임 정답을 프론트만 믿는 문제 | `MG|TYPE|VALUE` proof를 서버에서 검증 |
| 퍼즐 오답 무한 제출 | 오답 제한 테이블과 cleanup job 추가 |
| secret이 파일에 남을 위험 | example/prod properties를 환경변수 기준으로 정리 |
| 공개 전 데이터 누락 | publish readiness 검사 추가 |
| 한글 깨짐 | UTF-8 설정과 mojibake repair 테스트 추가 |

## 2. 아직 남은 것

| 항목 | 이유 |
| --- | --- |
| 현장 검수 | 좌표, 운영시간, 보행 가능 여부는 실제로 봐야 함 |
| 제휴 쿠폰 지급 | 지금은 DB 구조와 관리자 수정만 있음 |
| OAuth 운영 등록 | 배포 주소가 확정되면 Google/Kakao 콘솔에 등록해야 함 |
| Tmap/GPS 실기기 테스트 | PC에서 다 확인하기 어려움 |
| AI 비용/쿼터 관리 | 운영하면 호출량 관리가 필요함 |

## 3. 확인 순서

1. 백엔드 `compileJava test`
2. 프론트 `npm run build`
3. `scripts/verify-release.ps1`
4. 관리자 AI 초안 저장 smoke
5. 모바일에서 지도, 도착, Tmap 확인
