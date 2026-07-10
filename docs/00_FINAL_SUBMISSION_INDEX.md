# 문서 목록

작성일: 2026-07-10

중복 문서는 지우고 번호 문서만 남겼다. 제출하거나 이어서 볼 때는 아래 순서대로 보면 된다.

| 파일 | 내용 |
| --- | --- |
| `01_REQUIREMENTS_AND_COVERAGE.md` | 요구사항과 구현 상태 |
| `02_DESIGN_DOCUMENT.md` | 전체 구조, 주요 설계, ERD |
| `03_WBS_AND_GANTT.md` | 작업 일정과 WBS |
| `04_SCREEN_DESIGN.md` | 화면 경로와 화면별 기능 |
| `05_IMPLEMENTATION_RESULT.md` | 실제 구현한 내용 |
| `06_TROUBLESHOOTING_AND_REMAINING_WORK.md` | 해결한 문제와 남은 작업 |
| `07_EXTERNAL_API_USAGE.md` | 외부 API 사용 내용 |
| `08_FINAL_REPORT.md` | 최종 보고서 형태 정리 |
| `09_PRESENTATION_DRAFT.md` | 발표 원고 |

## 코드 위치

| 구분 | 위치 |
| --- | --- |
| 백엔드 | `backend/src/main/java/com/operation/seoul` |
| 백엔드 테스트 | `backend/src/test/java/com/operation/seoul` |
| DB 스키마 | `backend/src/main/resources/schema.sql` |
| 마이그레이션/seed | `backend/src/main/java/com/operation/seoul/global/config` |
| 프론트 | `frontend/src` |
| API client | `frontend/src/api` |
| 라우터 | `frontend/src/router/index.js` |
| 검증 스크립트 | `scripts` |

## 제출 전 확인

- 실제 API 키나 secret이 들어가 있지 않은지 확인한다.
- 운영에서는 `VITE_DEV_ARRIVAL=false`, `DEV_ARRIVAL_ENABLED=false`로 둔다.
- 백엔드 테스트와 프론트 빌드를 한 번씩 돌린다.
- 발표 자료는 `09_PRESENTATION_DRAFT.md`를 기준으로 만든다.
