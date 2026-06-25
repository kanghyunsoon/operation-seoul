# Operation Seoul Final Submission Index

작성일: 2026-06-26

## 제출 산출물 구성

| 구분 | 파일 | 포함 내용 |
| --- | --- | --- |
| 요구사항/검증 | `01_REQUIREMENTS_AND_COVERAGE.md` | 요구사항 정의서, 기능 범위, 수용 기준, 구현 추적표 |
| 설계 문서 | `02_DESIGN_DOCUMENT.md` | 시스템 구조, Use-Case Diagram, ER Diagram, 핵심 설계 |
| 일정 문서 | `03_WBS_AND_GANTT.md` | WBS, Gantt Chart, 역할/작업 분해 |
| 화면 설계 | `04_SCREEN_DESIGN.md` | 주요 화면 IA, 사용자 흐름, 화면별 구성 |
| 구현 결과 | `05_IMPLEMENTATION_RESULT.md` | Back-End, Front-End, DB Schema, 데이터셋 정리 |
| 이슈/개선 | `06_TROUBLESHOOTING_AND_REMAINING_WORK.md` | 해결 이슈, 검증 결과, 남은 리스크 |
| 외부 API/AI | `07_EXTERNAL_API_AND_AI_USAGE.md` | TourAPI, Kakao, Tmap, Gemini 활용 및 보안 |
| AI 필수 활용 증빙 | `10_AI_REQUIRED_USAGE_EVIDENCE.md` | 주요 프롬프트, LLM 활용 전후 비교, AI 기능 구현 코드 캡처 대상 |
| 최종 보고서 | `08_FINAL_REPORT.md` | 프로젝트 개요, 구현 성과, 한계와 향후 계획 |
| 발표 자료 | `09_PRESENTATION_DRAFT.md` | PPT/PDF 제작용 슬라이드 원고 |
| 발표 PPT | `OperationSeoul_Final_Presentation.pptx` | 최종 발표용 PowerPoint |
| 발표 PDF | `OperationSeoul_Final_Presentation.pdf` | 최종 발표용 PDF |
| 발표 HTML | `OperationSeoul_Final_Presentation.html` | PDF 재생성/브라우저 발표용 HTML |

## 코드 산출물 위치

| 산출물 | 위치 |
| --- | --- |
| Back-End Spring Boot 소스 | `backend/src/main/java/com/operation/seoul` |
| Back-End 테스트 | `backend/src/test/java/com/operation/seoul` |
| DB Schema.sql | `backend/src/main/resources/schema.sql` |
| DB 마이그레이션/Seed | `backend/src/main/java/com/operation/seoul/global/config` |
| Front-End Vue.js 소스 | `frontend/src` |
| API Client | `frontend/src/api` |
| Router | `frontend/src/router/index.js` |
| 화면 컴포넌트 | `frontend/src/views`, `frontend/src/components` |
| 실행/검증 스크립트 | `scripts`, `backend/gradlew.bat`, `frontend/package.json` |

## 제출 전 확인 항목

- `backend/src/main/resources/schema.sql` 포함 여부 확인
- `backend/src/main/resources/application-example.properties`에는 실제 키를 넣지 않음
- `frontend/.env`는 제출 시 예시값만 제공
- 운영 비밀값은 환경변수로 주입
- 발표용 PPT/PDF는 `09_PRESENTATION_DRAFT.md`를 기준으로 제작
- 발표용 PPT/PDF 재생성은 `python scripts/create-final-presentation.py` 실행 후 Edge headless PDF 출력
- 최종 보고서 본문은 `08_FINAL_REPORT.md` 사용
