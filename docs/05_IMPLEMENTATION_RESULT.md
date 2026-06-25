# 구현 결과물 정리

## 1. Back-End

| 항목 | 내용 |
| --- | --- |
| Framework | Spring Boot |
| Security | Spring Security, JWT |
| DB Access | MyBatis |
| DB | MySQL |
| Entry Point | `backend/src/main/java/com/operation/seoul/OperationSeoulApplication.java` |
| Schema | `backend/src/main/resources/schema.sql` |

### 주요 패키지

| 패키지 | 역할 |
| --- | --- |
| `auth` | 회원가입, 로그인, JWT, 현재 사용자 인증 |
| `user` | 프로필, 팔로우, 피드, 관리자 회원 관리 |
| `episode` | 에피소드 목록, 지도, 퍼즐, 최종 추리, 클리어 |
| `casefile` | 단서/증거/용의자 미션 파일 조회 |
| `review` | 에피소드 리뷰와 관리자 리뷰 관리 |
| `community` | 권역 게시판, 답변, 좋아요 |
| `ranking` | 클리어 랭킹 |
| `challenge` | 챌린지 |
| `admin.episode` | 관리자 에피소드 관리/검수 |
| `global` | 공통 응답, 예외, 보안/스키마 마이그레이션 |

## 2. Front-End

| 항목 | 내용 |
| --- | --- |
| Framework | Vue 3 |
| State | Pinia |
| Router | Vue Router |
| HTTP | Axios |
| Map | Kakao Maps JavaScript |
| Navigation | Tmap 링크/길찾기 |

### 주요 위치

| 위치 | 역할 |
| --- | --- |
| `frontend/src/views` | 라우트 화면 |
| `frontend/src/components` | 공통 컴포넌트/미니게임 |
| `frontend/src/api` | API Client |
| `frontend/src/stores/sessionStore.js` | 로그인 세션 |
| `frontend/src/router/index.js` | 라우팅과 인증 가드 |
| `frontend/src/constants/regionAreas.js` | 권역 지도 메타 |

## 3. DB Schema 및 활용 데이터셋

| 데이터 | 위치/생성 방식 | 설명 |
| --- | --- | --- |
| 기본 스키마 | `schema.sql` | 사용자, 지역, 에피소드, 장소, 퍼즐, 커뮤니티 등 |
| 에피소드 Seed | `EpisodeSchemaMigration` | EP.01 기본 플레이 데이터 |
| 커뮤니티 권역 Seed | `RegionSchemaMigration` | 권역 커뮤니티 기본 region 데이터 |
| 챌린지 Seed | `CommunitySchemaMigration` | 기본 챌린지 |
| 외부 후보 데이터 | TourAPI/Kakao Local 응답 | 관리자 검수 후 에피소드 후보로 활용 |

## 4. 실행 명령

### Backend

```powershell
cd backend
$env:JAVA_HOME='C:\Users\Administrator\.jdks\ms-17.0.19'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat bootRun
```

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

## 5. 검증 명령

```powershell
cd backend
$env:JAVA_HOME='C:\Users\Administrator\.jdks\ms-17.0.19'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat compileJava
```

```powershell
cd frontend
npm run build
```

