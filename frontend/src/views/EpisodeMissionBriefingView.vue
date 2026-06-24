<template>
  <main class="briefing-page">
    <section v-if="loading" class="terminal-card">미션 브리핑을 연결하는 중입니다.</section>
    <section v-else-if="error" class="terminal-card error">
      <p class="eyebrow">BRIEFING FAILED</p>
      <h1>브리핑을 열 수 없습니다</h1>
      <p>{{ error }}</p>
      <button type="button" @click="goCaseFile">사건 파일로 이동</button>
    </section>

    <section v-else class="terminal-card">
      <p class="eyebrow">MISSION BRIEFING</p>
      <h1>{{ episode?.title || '사건 브리핑' }}</h1>
      <p class="subtitle">{{ episode?.subtitle }}</p>

      <div class="terminal-window">
        <div class="terminal-head">
          <span></span><span></span><span></span>
          <strong>CASE OVERVIEW</strong>
        </div>
        <p class="typing-text">{{ displayedBriefing }}<span v-if="typingBuffer.isTyping.value" class="cursor">▌</span></p>
      </div>

      <div class="actions">
        <button type="button" class="ghost" @click="skipTyping">브리핑 즉시 표시</button>
        <button type="button" @click="goCaseFile">{{ typingBuffer.isTyping.value ? '사건 파일로 이동' : '조사 시작' }}</button>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { episodeApi } from '@/api/episodeApi';
import { useTypingBuffer } from '@/composables/useTypingBuffer';

const route = useRoute();
const router = useRouter();
const episodeId = route.params.episodeId;
const episode = ref(null);
const loading = ref(true);
const error = ref('');
const typingBuffer = useTypingBuffer(24);
const displayedBriefing = computed(() => typingBuffer.displayedText.value);

onMounted(loadBriefing);

async function loadBriefing() {
  loading.value = true;
  error.value = '';
  try {
    episode.value = await episodeApi.getEpisode(episodeId);
    const briefing = [
      `[작전명] ${episode.value?.title || '미확인 사건'}`,
      episode.value?.subtitle ? `[상황] ${episode.value.subtitle}` : '',
      '',
      episode.value?.fictionSynopsis || episode.value?.missionDescription || '사건 개요가 아직 등록되지 않았습니다.',
      '',
      '요원은 현장 단서를 수집해 범인, 흉기, 동기, 사인을 확정해야 합니다.'
    ].filter(Boolean).join('\n');
    typingBuffer.reset();
    typingBuffer.addChunk(briefing);
    typingBuffer.finishTyping();
  } catch (err) {
    error.value = err.userMessage || '미션 브리핑을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function skipTyping() {
  typingBuffer.skipTyping();
}

function goCaseFile() {
  router.push({ name: 'EpisodeCaseFile', params: { episodeId } });
}
</script>

<style scoped>
.briefing-page { min-height: 100vh; box-sizing: border-box; padding: 28px 16px; display: grid; place-items: center; background: radial-gradient(circle at 20% 12%, rgba(245,158,11,.22), transparent 34%), linear-gradient(145deg, #09090b, #111827 60%, #1c1917); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.terminal-card { width: min(100%, 880px); box-sizing: border-box; border: 1px solid rgba(245,158,11,.28); border-radius: 26px; padding: 24px; background: rgba(2,6,23,.82); box-shadow: 0 26px 70px rgba(0,0,0,.38); }
.terminal-card.error { color: #fecaca; }
.eyebrow { margin: 0 0 10px; color: #f59e0b; font-size: .78rem; font-weight: 1000; letter-spacing: .18em; }
h1 { margin: 0; font-size: clamp(2rem, 7vw, 4rem); line-height: 1; }
.subtitle { color: #fde68a; font-weight: 800; }
.terminal-window { margin: 22px 0; overflow: hidden; border: 1px solid rgba(148,163,184,.24); border-radius: 18px; background: #020617; }
.terminal-head { display: flex; align-items: center; gap: 7px; padding: 12px 14px; border-bottom: 1px solid rgba(148,163,184,.18); color: #94a3b8; font-size: .75rem; letter-spacing: .12em; }
.terminal-head span { width: 10px; height: 10px; border-radius: 999px; background: #f97316; }
.terminal-head span:nth-child(2) { background: #facc15; }
.terminal-head span:nth-child(3) { background: #22c55e; }
.terminal-head strong { margin-left: 8px; }
.typing-text { min-height: 280px; margin: 0; padding: 22px; color: #dbeafe; white-space: pre-line; line-height: 1.85; font-size: 1.02rem; }
.cursor { color: #fbbf24; animation: blink 1s step-end infinite; }
.actions { display: flex; gap: 10px; justify-content: flex-end; flex-wrap: wrap; }
button { min-height: 48px; border: 0; border-radius: 14px; padding: 0 18px; background: #b45309; color: #fff7ed; font-weight: 1000; font: inherit; }
button.ghost { border: 1px solid rgba(245,158,11,.28); background: transparent; color: #fde68a; }
@keyframes blink { 50% { opacity: 0; } }
@media (max-width: 560px) { .terminal-card { padding: 18px; } .actions { display: grid; } }
</style>
