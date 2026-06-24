<template>
  <aside class="clue-board" :class="{ open }">
    <header>
      <div>
        <p>DEDUCTION BOARD</p>
        <h2>수집한 추리 단서</h2>
      </div>
      <button type="button" @click="$emit('close')">닫기</button>
    </header>

    <p class="guide">8개의 서로 다른 정보를 종합해 범인, 흉기, 동기, 사인을 추리하세요. 정답 값은 단서에 직접 표시되지 않습니다.</p>

    <section v-for="slot in clueSlots" :key="slot.id" :class="`slot-${slot.id.toLowerCase()}`">
      <h3>{{ slot.label }} <span>{{ slot.clues.length }}</span></h3>
      <ul><li v-for="clue in slot.clues" :key="clue">{{ clue }}</li></ul>
      <p v-if="!slot.clues.length" class="empty">{{ slot.empty }}</p>
    </section>

    <section>
      <h3>사건파일 해금</h3>
      <p class="empty">증거 {{ board?.unlockedEvidenceIds?.length || 0 }}개 · 용의자 {{ board?.unlockedSuspectIds?.length || 0 }}명</p>
    </section>
  </aside>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({ board: { type: Object, default: null }, open: { type: Boolean, default: false } });
defineEmits(['close']);

const clueSlots = computed(() => {
  return [
    slot('CULPRIT', '범인 추리', props.board?.culpritClues, '범인의 접근 권한과 알리바이를 좁힐 단서가 아직 없습니다.'),
    slot('WEAPON', '흉기 추리', props.board?.weaponClues, '범행에 사용된 물질이나 도구를 좁힐 단서가 아직 없습니다.'),
    slot('MOTIVE', '동기 추리', props.board?.motiveClues, '범행 동기와 이익 관계를 좁힐 단서가 아직 없습니다.'),
    slot('METHOD', '사인 추리', props.board?.methodClues, '피해자의 직접 사인을 좁힐 단서가 아직 없습니다.')
  ];
});

function slot(id, label, explicit, empty) {
  const clues = displayClues(explicit);
  return { id, label, clues, empty };
}

function displayClues(clues = []) {
  return [...new Set((clues || []).map((clue) => String(clue || '').trim()).filter(Boolean))];
}
</script>

<style scoped>
.clue-board { position: fixed; inset: auto 0 0 0; z-index: 45; max-height: 82vh; transform: translateY(105%); transition: transform .28s ease; box-sizing: border-box; width: min(100%, 430px); margin: 0 auto; padding: 18px; border-radius: 22px 22px 0 0; background: #f8f1df; color: #24180d; box-shadow: 0 -18px 45px rgba(0,0,0,.35); overflow: auto; }
.clue-board.open { transform: translateY(0); }
header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; border-bottom: 2px solid rgba(36,24,13,.2); padding-bottom: 12px; }
p { margin: 0; }
header p { font-size: .72rem; font-weight: 900; letter-spacing: .12em; color: #9a3412; }
h2 { margin: 2px 0 0; font-size: 1.25rem; }
button { border: 1px solid rgba(36,24,13,.25); border-radius: 999px; background: transparent; padding: 7px 10px; font-weight: 800; }
.guide { margin-top: 12px; color: #57534e; font-size: .82rem; line-height: 1.5; }
section { margin-top: 12px; padding: 13px; border: 1px solid rgba(36,24,13,.16); border-left: 5px solid #78716c; border-radius: 14px; background: rgba(255,255,255,.42); }
.slot-culprit { border-left-color: #b91c1c; }
.slot-weapon { border-left-color: #1d4ed8; }
.slot-motive { border-left-color: #a16207; }
.slot-method { border-left-color: #047857; }
h3 { display: flex; justify-content: space-between; margin: 0 0 8px; font-size: .95rem; }
span { color: #9a3412; }
ul { display: grid; gap: 7px; margin: 0; padding-left: 18px; }
li { line-height: 1.45; font-weight: 750; }
.empty { color: #78716c; font-size: .84rem; line-height: 1.5; }
</style>
