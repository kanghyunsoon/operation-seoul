<template>
  <main class="debrief-page">
    <section v-if="loading" class="debrief-card">사건 전말 파일을 복호화하는 중입니다.</section>
    <section v-else-if="error" class="debrief-card error">
      <p class="eyebrow">DEBRIEFING FAILED</p>
      <h1>사건 전말을 열 수 없습니다</h1>
      <p>{{ error }}</p>
      <button type="button" @click="goReport">클리어 리포트로 이동</button>
    </section>

    <section v-else class="debrief-card">
      <p class="stamp">CASE CLOSED</p>
      <p class="eyebrow">DEBRIEFING</p>
      <h1>사건의 전말</h1>
      <p class="lead">{{ report?.title }}</p>

      <article class="truth-file">
        <p>{{ displayedDebrief }}<span v-if="typingBuffer.isTyping.value" class="cursor">▌</span></p>
      </article>

      <div class="actions">
        <button type="button" class="ghost" @click="typingBuffer.skipTyping()">전말 즉시 표시</button>
        <button type="button" @click="goReport">클리어 리포트 확인</button>
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
const report = ref(null);
const loading = ref(true);
const error = ref('');
const typingBuffer = useTypingBuffer(20);
const displayedDebrief = computed(() => typingBuffer.displayedText.value);

onMounted(loadDebriefing);

async function loadDebriefing() {
  loading.value = true;
  error.value = '';
  try {
    report.value = await episodeApi.getClearReport(episodeId);
    const debrief = [
      '[사건의 전말]',
      formatReadableText(cleanSection(report.value?.finalTruthSummary)) || '사건의 전말이 아직 등록되지 않았습니다.',
      '',
      '[배경 해설]',
      formatReadableText(cleanSection(report.value?.actualHistorySummary)) || '실제 장소 배경 해설이 아직 등록되지 않았습니다.'
    ].join('\n\n');
    typingBuffer.reset();
    typingBuffer.addChunk(debrief);
    typingBuffer.finishTyping();
  } catch (err) {
    error.value = err.userMessage || '클리어한 에피소드의 디브리핑만 확인할 수 있습니다.';
  } finally {
    loading.value = false;
  }
}

function cleanSection(value) {
  return String(value || '')
    .replace(/^\s*\d+\.\s*[^\n]+/gm, '')
    .replace(/픽션과\s*역사의\s*매칭\s*\(디브리핑\)/g, '')
    .trim();
}

function formatReadableText(value) {
  return String(value || '')
    .replace(/\r\n?/g, '\n')
    .replace(/[ \t]+/g, ' ')
    .replace(/\s*([.!?。！？])\s*/g, '$1\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function goReport() {
  router.push({ name: 'EpisodeClearReport', params: { episodeId } });
}
</script>

<style scoped>
.debrief-page { min-height: 100vh; box-sizing: border-box; padding: 26px 14px; display: grid; place-items: center; background: radial-gradient(circle at 82% 8%, rgba(185,28,28,.18), transparent 32%), linear-gradient(160deg, #1c1917, #020617 70%); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.debrief-card { width: min(100%, 900px); box-sizing: border-box; border: 1px solid rgba(248,113,113,.24); border-radius: 26px; padding: 24px; background: rgba(15,23,42,.86); box-shadow: 0 26px 80px rgba(0,0,0,.42); }
.debrief-card.error { color: #fecaca; }
.stamp { display: inline-block; margin: 0 0 12px; transform: rotate(-4deg); border: 3px solid #ef4444; color: #fecaca; padding: 6px 10px; font-weight: 1000; letter-spacing: .12em; }
.eyebrow { margin: 0 0 8px; color: #fca5a5; font-size: .78rem; font-weight: 1000; letter-spacing: .18em; }
h1 { margin: 0; font-size: clamp(2.2rem, 8vw, 4.4rem); line-height: 1; }
.lead { color: #fde68a; font-weight: 900; }
.truth-file { margin: 22px 0; border: 1px solid rgba(251,191,36,.24); border-radius: 20px; background: linear-gradient(180deg, rgba(254,243,199,.08), rgba(2,6,23,.72)); }
.truth-file p { min-height: 380px; margin: 0; padding: 30px; color: #f3f4f6; white-space: pre-wrap; word-break: keep-all; overflow-wrap: anywhere; line-height: 1.95; font-size: clamp(1.13rem, 2.4vw, 1.3rem); font-weight: 600; }
.cursor { color: #fbbf24; animation: blink 1s step-end infinite; }
.actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; }
button { min-height: 48px; border: 0; border-radius: 14px; padding: 0 18px; background: #b91c1c; color: #fff; font-weight: 1000; font: inherit; }
button.ghost { border: 1px solid rgba(248,113,113,.32); background: transparent; color: #fecaca; }
@keyframes blink { 50% { opacity: 0; } }
@media (max-width: 560px) { .debrief-card { padding: 18px; } .truth-file p { min-height: 320px; padding: 20px; font-size: 1.08rem; line-height: 1.85; } .actions { display: grid; } }
</style>
