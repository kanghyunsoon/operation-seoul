# Episode Minigames

퍼즐 미니게임은 이 폴더에 있는 컴포넌트만 사용한다.
AI가 임의로 UI를 만들게 하면 검증이 어려워져서, 백엔드는 설정값만 만들고 프론트는 정해진 타입만 렌더링한다.

## 공통 props

- `config`: 타입별 설정값
- `localSolution`: 화면 상호작용에만 쓰는 값
- `basis`: 라벨에 보여줄 짧은 근거 문구

## 공통 emit

- `solved-change`: 내부 풀이 상태
- `proof-change`: 서버로 보낼 `MG|TYPE|VALUE` proof

정답 여부는 서버 제출 전에는 사용자에게 확정해서 보여주지 않는다.
`PuzzleCard.vue`가 proof를 제출하고, 서버가 다시 검증한 뒤 퍼즐 완료 여부를 결정한다.

## 지원 타입

| Type | 내용 |
| --- | --- |
| `NUMBER_LOCK` | 숫자 자물쇠 |
| `WORD_COMPOSE` | 단어 조합 |
| `MEMORY_CARD` | 카드 매칭 |
| `PATTERN_LOCK` | 3x3 패턴 |
| `RAPID_TAP` | 제한 시간 탭 |
| `DIRECTION_SEQUENCE` | 방향 순서 |
| `UP_DOWN_TIMER` | 업다운 숫자 맞히기 |
| `NUMBER_BASEBALL` | 숫자 야구 |
| `NUMBER_SEQUENCE_TAP` | 숫자 순서 탭 |

## 백엔드 쪽

- `AdminEpisodeService.buildPuzzleInteraction()`에서 미니게임 타입과 설정을 만든다.
- 사건 파일 보상은 `rewardPayload.rewards`에 둔다.
- `EpisodePlayService.validateMinigameProof()`에서 proof를 검증한다.
