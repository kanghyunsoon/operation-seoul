<template>
  <aside class="clue-board" :class="{ open }">
    <header>
      <div>
        <p>CLUE BOARD</p>
        <h2>수집한 단서</h2>
      </div>
      <button type="button" @click="$emit('close')">닫기</button>
    </header>

    <section>
      <h3>관련자 힌트 <span>{{ relatedPersonClues.length }}/3</span></h3>
      <ul><li v-for="clue in displayClues(relatedPersonClues, 'related')" :key="clue">{{ clue }}</li></ul>
      <p v-if="!relatedPersonClues.length" class="empty">관련자를 좁혀 줄 단서가 아직 없습니다.</p>
    </section>

    <section>
      <h3>핵심 단서 힌트 <span>{{ coreClues.length }}/3</span></h3>
      <ul><li v-for="clue in displayClues(coreClues, 'core')" :key="clue">{{ clue }}</li></ul>
      <p v-if="!coreClues.length" class="empty">핵심 단서의 형태를 좁혀 줄 단서가 아직 없습니다.</p>
    </section>

    <section>
      <h3>장소 힌트 <span>{{ board?.destinationClueCount || 0 }}/3</span></h3>
      <ul><li v-for="clue in displayClues(board?.destinationClues, 'destination')" :key="clue">{{ clue }}</li></ul>
      <p v-if="!(board?.destinationClues || []).length" class="empty">최종 목적지를 열기 위한 장소 힌트가 아직 없습니다.</p>
    </section>

    <section>
      <h3>스토리 단서</h3>
      <ul><li v-for="clue in displayClues(board?.storyClues, 'story')" :key="clue">{{ clue }}</li></ul>
      <p v-if="!(board?.storyClues || []).length" class="empty">용의자 진술과 사건 배경을 해석할 보조 단서가 아직 없습니다.</p>
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

const relatedPersonClues = computed(() => {
  const explicit = props.board?.relatedPersonClues || [];
  if (explicit.length) return explicit;
  return legacyAnswerClues().filter((_, index) => index % 2 === 0);
});

const coreClues = computed(() => {
  const explicit = props.board?.coreClues || [];
  if (explicit.length) return explicit;
  return legacyAnswerClues().filter((_, index) => index % 2 === 1);
});

function legacyAnswerClues() {
  return props.board?.answerClues || [];
}

function displayClues(clues = [], kind) {
  return (clues || []).map((clue, index) => humanizeClue(clue, kind, index));
}

function humanizeClue(value, kind, index) {
  const text = String(value || '').trim();
  if (!/^(answer|destination|story)-clue-\d+$/i.test(text)) return text;
  const fallback = {
    related: ['엇갈린 진술', '남겨진 서명', '목격 기록', '알리바이 틈'],
    core: ['찢긴 가장자리', '빛에 탄 자국', '거꾸로 찍힌 그림자', '봉인 라벨'],
    answer: ['찢긴 가장자리', '빛에 탄 자국', '거꾸로 찍힌 그림자', '봉인 라벨'],
    destination: ['장소 표식', '닫힌 문', '굽은 골목'],
    story: ['첫 목격 기록', '엇갈린 동선', '남겨진 시간표']
  };
  return fallback[kind]?.[index % fallback[kind].length] || '미분류 단서';
}
</script>

<style scoped>
.clue-board { position: fixed; inset: auto 0 0 0; z-index: 45; max-height: 78vh; transform: translateY(105%); transition: transform .28s ease; box-sizing: border-box; width: min(100%, 430px); margin: 0 auto; padding: 18px; border-radius: 22px 22px 0 0; background: #f8f1df; color: #24180d; box-shadow: 0 -18px 45px rgba(0,0,0,.35); overflow: auto; }
.clue-board.open { transform: translateY(0); }
header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; border-bottom: 2px solid rgba(36,24,13,.2); padding-bottom: 12px; }
p { margin: 0; }
header p { font-size: .72rem; font-weight: 900; letter-spacing: .12em; color: #9a3412; }
h2 { margin: 2px 0 0; font-size: 1.25rem; }
button { border: 1px solid rgba(36,24,13,.25); border-radius: 999px; background: transparent; padding: 7px 10px; font-weight: 800; }
section { margin-top: 16px; padding: 13px; border: 1px solid rgba(36,24,13,.16); border-radius: 14px; background: rgba(255,255,255,.42); }
h3 { display: flex; justify-content: space-between; margin: 0 0 8px; font-size: .95rem; }
span { color: #9a3412; }
ul { display: grid; gap: 7px; margin: 0; padding-left: 18px; }
li { line-height: 1.45; font-weight: 750; }
.empty { color: #78716c; font-size: .84rem; line-height: 1.5; }
</style>
