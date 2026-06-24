<template>
  <main class="challenges-page">
    <header class="hero">
      <div>
        <p>FIELD CHALLENGES</p>
        <h1>챌린지</h1>
        <span>사건 파일 클리어 기록을 기준으로 자동 진행되는 목표입니다.</span>
      </div>
      <div class="hero-actions">
        <button type="button" @click="router.push({ name: 'EpisodeList' })">사건 파일</button>
      </div>
    </header>

    <p v-if="message" class="toast" :class="messageType">{{ message }}</p>

    <section v-if="loading" class="state">챌린지를 불러오는 중입니다.</section>
    <section v-else-if="error" class="state error">{{ error }}</section>
    <section v-else class="challenge-grid">
      <article class="panel">
        <div class="panel-head">
          <div>
            <p>AVAILABLE</p>
            <h2>다음 챌린지</h2>
          </div>
          <button type="button" @click="loadChallenges">새로고침</button>
        </div>
        <p v-if="!availableChallenges.length" class="empty">참가 가능한 다음 챌린지가 없습니다.</p>
        <div v-for="challenge in availableChallenges" :key="challenge.id" class="challenge-card" :class="{ completed: challenge.completed }">
          <div class="card-top">
            <strong>{{ challenge.title }}</strong>
            <span>{{ challenge.completed ? '완료' : challenge.joined ? '참가 중' : '참가 가능' }}</span>
          </div>
          <p>{{ challenge.description }}</p>
          <div class="progress-track">
            <i :style="{ width: progressPercent(challenge) + '%' }"></i>
          </div>
          <small>{{ challenge.progressCount || 0 }} / {{ challenge.targetCount }} 클리어</small>
          <button v-if="!challenge.joined" type="button" @click="join(challenge)">참가</button>
          <button v-else type="button" class="ghost" @click="loadChallenges">클리어!</button>
        </div>
      </article>

      <article class="panel">
        <div class="panel-head">
          <div>
            <p>MY RUNS</p>
            <h2>진행 중 챌린지</h2>
          </div>
          <span>{{ activeMyChallenges.length }}개</span>
        </div>
        <p v-if="!activeMyChallenges.length" class="empty">진행 중인 챌린지가 없습니다.</p>
        <div v-for="challenge in activeMyChallenges" :key="`mine-${challenge.id}`" class="mini-card">
          <strong>{{ challenge.title }}</strong>
          <span>{{ challenge.completed ? '완료' : '진행 중' }}</span>
          <div class="progress-track">
            <i :style="{ width: progressPercent(challenge) + '%' }"></i>
          </div>
          <small>{{ challenge.progressCount || 0 }} / {{ challenge.targetCount }}</small>
        </div>
      </article>

      <article class="panel completed-panel">
        <div class="panel-head">
          <div>
            <p>CLEARED</p>
            <h2>달성한 챌린지</h2>
          </div>
          <span>{{ completedMyChallenges.length }}개</span>
        </div>
        <p v-if="!completedMyChallenges.length" class="empty">아직 달성한 챌린지가 없습니다.</p>
        <div v-for="challenge in completedMyChallenges" :key="`completed-${challenge.id}`" class="mini-card completed">
          <strong>{{ challenge.title }}</strong>
          <span>클리어!</span>
          <div class="progress-track">
            <i :style="{ width: progressPercent(challenge) + '%' }"></i>
          </div>
          <small>{{ challenge.progressCount || 0 }} / {{ challenge.targetCount }}</small>
        </div>
      </article>
    </section>
    <MainBottomNav />
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { challengeApi } from '@/api/challengeApi';

const router = useRouter();
const challenges = ref([]);
const myChallenges = ref([]);
const loading = ref(true);
const error = ref('');
const message = ref('');
const messageType = ref('success');
const activeMyChallenges = computed(() => myChallenges.value.filter((challenge) => !challenge.completed && challenge.entryStatus !== 'COMPLETED'));
const completedMyChallenges = computed(() => myChallenges.value.filter((challenge) => challenge.completed || challenge.entryStatus === 'COMPLETED'));
const availableChallenges = computed(() => challenges.value.filter((challenge) => !challenge.completed && challenge.entryStatus !== 'COMPLETED'));

onMounted(loadChallenges);

async function loadChallenges() {
  loading.value = true;
  error.value = '';
  try {
    [challenges.value, myChallenges.value] = await Promise.all([
      challengeApi.getChallenges(),
      challengeApi.getMyChallenges()
    ]);
  } catch (err) {
    error.value = err.userMessage || '챌린지를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

async function join(challenge) {
  try {
    await challengeApi.joinChallenge(challenge.id);
    setMessage('챌린지에 참가했습니다.');
    await loadChallenges();
  } catch (err) {
    setMessage(err.userMessage || '챌린지에 참가하지 못했습니다.', 'error');
  }
}

function progressPercent(challenge) {
  const target = Math.max(1, challenge.targetCount || 1);
  const progress = Math.max(0, challenge.progressCount || 0);
  return Math.min(100, Math.round((progress / target) * 100));
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}
</script>

<style scoped>
.challenges-page { min-height: 100vh; box-sizing: border-box; padding: 26px 16px 126px; background: radial-gradient(circle at 75% 0%, rgba(248,113,113,.22), transparent 32%), linear-gradient(150deg, #1f1111, #111827 58%, #030712); color: #f8fafc; font-family: 'Noto Serif KR', Georgia, serif; }
.hero, .challenge-grid, .state, .toast { width: min(100%, 1040px); box-sizing: border-box; margin-left: auto; margin-right: auto; }
.hero { display: flex; justify-content: space-between; align-items: end; gap: 16px; margin-bottom: 16px; padding: 22px; border: 1px solid rgba(248,113,113,.24); border-radius: 24px; background: rgba(15,23,42,.66); }
.hero p, .panel-head p { margin: 0 0 8px; color: #fca5a5; font-size: .74rem; font-weight: 1000; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(2.3rem, 9vw, 4.8rem); line-height: .94; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 8px; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #b91c1c; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; }
.ghost { background: #334155; }
.challenge-grid { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(300px, .75fr); gap: 14px; }
.panel { padding: 18px; border: 1px solid rgba(148,163,184,.18); border-radius: 24px; background: rgba(15,23,42,.68); box-shadow: 0 24px 64px rgba(0,0,0,.24); }
.completed-panel { grid-column: 1 / -1; }
.panel-head { display: flex; justify-content: space-between; align-items: end; gap: 12px; margin-bottom: 12px; }
.panel-head h2 { margin: 0; }
.panel-head span { color: #fecaca; font-weight: 900; }
.challenge-card, .mini-card { display: grid; gap: 10px; margin-top: 10px; padding: 16px; border: 1px solid rgba(148,163,184,.16); border-radius: 18px; background: rgba(2,6,23,.42); }
.challenge-card.completed { border-color: rgba(74,222,128,.3); }
.card-top { display: flex; justify-content: space-between; gap: 12px; align-items: center; }
.card-top strong, .mini-card strong { color: #fff7ed; font-size: 1.08rem; }
.card-top span, .mini-card span { color: #fecaca; font-weight: 900; }
.challenge-card.completed .card-top span { color: #86efac; }
p { margin: 0; color: #cbd5e1; line-height: 1.55; }
small, .empty { color: #94a3b8; }
.progress-track { overflow: hidden; height: 10px; border-radius: 999px; background: rgba(148,163,184,.18); }
.progress-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #f87171, #facc15); }
.state, .toast { padding: 16px; border: 1px dashed rgba(148,163,184,.34); border-radius: 16px; color: #cbd5e1; text-align: center; }
.toast { margin-bottom: 12px; border-style: solid; background: rgba(20,83,45,.2); color: #bbf7d0; }
.toast.error, .state.error { color: #fecaca; background: rgba(127,29,29,.18); }
@media (max-width: 820px) {
  .hero, .challenge-grid { display: grid; grid-template-columns: 1fr; }
  .hero-actions { display: grid; grid-template-columns: 1fr; }
}
</style>
