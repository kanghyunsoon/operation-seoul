<template>
  <aside v-if="spot" class="spot-sheet">
    <div class="sheet-bar"></div>

    <div class="spot-head">
      <div>
        <p class="role" :class="spot.publicMarkerType">{{ roleLabel(spot.publicMarkerType) }}</p>
        <h2>{{ spot.placeName }}</h2>
      </div>
      <span class="state" :class="{ done: spot.completed, visited: spot.visited }">
        {{ spot.completed ? '완료' : spot.visited ? '도착 확인' : '미방문' }}
      </span>
    </div>

    <p class="story">{{ spot.storyText || '이 장소의 사건 문구가 아직 없습니다. 관리자 화면에서 현장 메모를 보강하세요.' }}</p>
    <p class="address">{{ spot.address || '주소 정보 없음' }}</p>

    <div v-if="arrivalResult" class="arrival-message" :class="{ success: arrivalResult.arrived, final: arrivalResult.canStartDeduction }">
      {{ arrivalResult.canStartDeduction ? '이 장소에서 최종 추리를 시작할 수 있습니다. 수집한 단서를 먼저 확인하세요.' : arrivalResult.message }}
    </div>
    <p v-else-if="spot.publicMarkerType === 'FINAL_CANDIDATE'" class="candidate-note">
      이 지점은 단서와 함께 비교해 볼 조사 지점입니다. 단서 보드와 사건자료를 확인하며 이동 여부를 판단하세요.
    </p>

    <div class="actions">
      <button type="button" class="nav" @click="$emit('navigate', spot)">지도에 경로 표시</button>
      <button type="button" @click="$emit('arrive', spot)">도착 확인</button>
      <button type="button" :disabled="!spot.visited" @click="$emit(puzzleOpen ? 'close-puzzle' : 'open-puzzle', spot)">
        {{ puzzleOpen ? '퍼즐 닫기' : '퍼즐 열기' }}
      </button>
      <button type="button" class="deduction" :disabled="!arrivalResult?.canStartDeduction" @click="$emit('start-deduction')">최종 추리</button>
    </div>
  </aside>
</template>

<script setup>
defineProps({
  spot: { type: Object, default: null },
  arrivalResult: { type: Object, default: null },
  puzzleOpen: { type: Boolean, default: false }
});

defineEmits(['navigate', 'arrive', 'open-puzzle', 'close-puzzle', 'start-deduction']);

const roleLabel = (type) => ({
  START: '시작 장소',
  KEYWORD_1: '조사 미션',
  KEYWORD_2: '조사 미션',
  KEYWORD_3: '조사 미션',
  FINAL: '최종 정답 입력 장소',
  FINAL_DESTINATION: '최종 정답 입력 장소',
  ANSWER_HINT: '조사 미션',
  DESTINATION_HINT: '조사 미션',
  STORY: '사건 기록',
  FINAL_CANDIDATE: '조사 지점'
}[type] || '조사 지점');
</script>

<style scoped>
.spot-sheet { position: fixed; left: 50%; bottom: 0; z-index: 30; width: min(100%, 430px); transform: translateX(-50%); box-sizing: border-box; padding: 12px 16px 18px; border: 1px solid rgba(148, 163, 184, .22); border-radius: 22px 22px 0 0; background: rgba(15, 23, 42, .97); color: #e5edf8; box-shadow: 0 -18px 40px rgba(0,0,0,.38); }
.sheet-bar { width: 44px; height: 4px; margin: 0 auto 12px; border-radius: 999px; background: #475569; }
.spot-head { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; }
h2 { margin: 3px 0 0; color: #fff; font-size: 1.15rem; }
.role { margin: 0; font-size: .74rem; font-weight: 900; }
.role.START { color: #60a5fa; }
.role.KEYWORD_1, .role.ANSWER_HINT, .role.KEYWORD_2, .role.KEYWORD_3, .role.DESTINATION_HINT { color: #fb923c; }
.role.FINAL, .role.FINAL_DESTINATION { color: #e5e7eb; }
.role.STORY { color: #4ade80; }
.role.FINAL_CANDIDATE { color: #cbd5e1; }
.state { flex: 0 0 auto; border: 1px solid rgba(148,163,184,.28); border-radius: 999px; padding: 5px 8px; color: #94a3b8; font-size: .74rem; font-weight: 800; }
.state.visited { border-color: rgba(56,189,248,.45); color: #7dd3fc; }
.state.done { border-color: rgba(34,197,94,.45); color: #86efac; }
.story { margin: 12px 0 8px; color: #dbeafe; line-height: 1.55; }
.address { margin: 0; color: #94a3b8; font-size: .82rem; }
.arrival-message, .candidate-note { margin-top: 10px; padding: 10px; border-radius: 10px; background: rgba(71,85,105,.22); color: #cbd5e1; font-size: .84rem; line-height: 1.45; }
.arrival-message.success { background: rgba(14,116,144,.26); color: #a5f3fc; }
.arrival-message.final { background: rgba(127,29,29,.34); color: #fecaca; }
.candidate-note { border: 1px dashed rgba(148,163,184,.28); }
.actions { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 8px; margin-top: 14px; }
button { min-height: 42px; border: 1px solid rgba(148,163,184,.28); border-radius: 12px; background: rgba(30,41,59,.88); color: #f8fafc; font: inherit; font-weight: 850; }
button:disabled { opacity: .45; }
.nav { border-color: rgba(59,130,246,.5); background: rgba(30,64,175,.55); }
.deduction { border-color: rgba(248,113,113,.42); background: rgba(127,29,29,.3); }
@media (min-width: 900px) {
  .spot-sheet { left: auto; right: 24px; bottom: 24px; transform: none; width: 380px; max-height: calc(100vh - 120px); overflow: auto; border-radius: 22px; box-shadow: 0 24px 70px rgba(0,0,0,.45); }
  .sheet-bar { display: none; }
}
@media (max-width: 370px) { .actions { grid-template-columns: 1fr; } .spot-sheet { max-height: 52vh; overflow: auto; } }
</style>
