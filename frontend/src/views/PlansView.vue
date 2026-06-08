<template>
  <main class="plans-page">
    <header class="hero">
      <div>
        <p>FIELD SCHEDULE</p>
        <h1>내 일정</h1>
        <span>플레이할 사건 파일을 날짜별로 정리합니다.</span>
      </div>
      <div class="actions">
        <button type="button" @click="router.push({ name: 'EpisodeList' })">사건 파일 찾기</button>
        <button type="button" @click="router.push({ name: 'MyPage' })">마이페이지</button>
      </div>
    </header>

    <p v-if="message" class="toast" :class="messageType">{{ message }}</p>
    <section v-if="loading" class="state">일정을 불러오는 중입니다.</section>
    <section v-else-if="error" class="state error">{{ error }}</section>
    <section v-else-if="!plans.length" class="state">아직 등록된 일정이 없습니다. 사건 파일 상세에서 일정을 추가하세요.</section>
    <section v-else class="plan-list">
      <article v-for="plan in plans" :key="plan.id" class="plan-card" :class="statusClass(plan.status)">
        <div class="date-tile">
          <strong>{{ dateParts(plan.plannedAt).day }}</strong>
          <span>{{ dateParts(plan.plannedAt).month }}</span>
        </div>
        <div class="plan-body">
          <div class="plan-top">
            <span>{{ statusLabel(plan.status) }}</span>
            <small>{{ formatDate(plan.plannedAt) }}</small>
          </div>
          <h2>{{ plan.episodeTitle }}</h2>
          <p>{{ plan.episodeSubtitle }}</p>
          <div class="meta">
            <span>{{ plan.era }}</span>
            <span>{{ plan.genre }}</span>
            <span>{{ plan.estimatedTime }}</span>
          </div>
          <textarea v-model="drafts[plan.id].memo" maxlength="500" placeholder="준비물, 약속 장소, 같이 할 메모"></textarea>
          <input v-model="drafts[plan.id].plannedAt" type="datetime-local" />
          <div class="card-actions">
            <button type="button" @click="save(plan, plan.status)">저장</button>
            <button type="button" @click="save(plan, plan.status === 'DONE' ? 'PLANNED' : 'DONE')">
              {{ plan.status === 'DONE' ? '예정으로 변경' : '완료 처리' }}
            </button>
            <button type="button" class="ghost" @click="openEpisode(plan)">상세</button>
            <button type="button" class="danger" @click="remove(plan)">삭제</button>
          </div>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { planApi } from '@/api/planApi';

const router = useRouter();
const plans = ref([]);
const drafts = reactive({});
const loading = ref(true);
const error = ref('');
const message = ref('');
const messageType = ref('success');

onMounted(loadPlans);

async function loadPlans() {
  loading.value = true;
  error.value = '';
  try {
    plans.value = await planApi.getMyPlans();
    plans.value.forEach((plan) => {
      drafts[plan.id] = {
        memo: plan.memo || '',
        plannedAt: toDatetimeLocal(plan.plannedAt)
      };
    });
  } catch (err) {
    error.value = err.userMessage || '일정을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

async function save(plan, status) {
  try {
    await planApi.updatePlan(plan.id, {
      episodeId: plan.episodeId,
      plannedAt: drafts[plan.id].plannedAt,
      memo: drafts[plan.id].memo,
      status
    });
    setMessage('일정을 저장했습니다.');
    await loadPlans();
  } catch (err) {
    setMessage(err.userMessage || '일정을 저장하지 못했습니다.', 'error');
  }
}

async function remove(plan) {
  try {
    await planApi.deletePlan(plan.id);
    setMessage('일정을 삭제했습니다.');
    plans.value = plans.value.filter((item) => item.id !== plan.id);
    delete drafts[plan.id];
  } catch (err) {
    setMessage(err.userMessage || '일정을 삭제하지 못했습니다.', 'error');
  }
}

function openEpisode(plan) {
  router.push({ name: 'EpisodeDetail', params: { episodeId: plan.episodeId } });
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}

function statusLabel(status) {
  return { PLANNED: '예정', DONE: '완료', CANCELLED: '취소' }[status] || status;
}

function statusClass(status) {
  return String(status || '').toLowerCase();
}

function formatDate(value) {
  if (!value) return '일정 미정';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function dateParts(value) {
  const date = value ? new Date(value) : new Date();
  return {
    day: String(date.getDate()).padStart(2, '0'),
    month: `${date.getMonth() + 1}월`
  };
}

function toDatetimeLocal(value) {
  const date = value ? new Date(value) : new Date(Date.now() + 24 * 60 * 60 * 1000);
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}
</script>

<style scoped>
.plans-page { min-height: 100vh; box-sizing: border-box; padding: 26px 16px 70px; background: radial-gradient(circle at 10% 0%, rgba(20,184,166,.22), transparent 30%), linear-gradient(145deg, #07111f, #111827 58%, #09090b); color: #f8fafc; font-family: 'Noto Serif KR', Georgia, serif; }
.hero, .state, .toast, .plan-list { width: min(100%, 900px); margin-left: auto; margin-right: auto; box-sizing: border-box; }
.hero { display: flex; justify-content: space-between; align-items: end; gap: 16px; margin-bottom: 16px; padding: 22px; border: 1px solid rgba(45,212,191,.22); border-radius: 22px; background: rgba(15,23,42,.62); }
.hero p { margin: 0 0 8px; color: #5eead4; font-size: .76rem; font-weight: 1000; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(2.2rem, 9vw, 4.2rem); line-height: .95; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; }
button, input, textarea { font: inherit; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #0f766e; color: #fff; font-weight: 900; padding: 0 14px; }
.plan-list { display: grid; gap: 14px; }
.plan-card { display: grid; grid-template-columns: 86px 1fr; gap: 14px; padding: 16px; border: 1px solid rgba(148,163,184,.17); border-radius: 22px; background: linear-gradient(135deg, rgba(20,184,166,.12), rgba(2,6,23,.72)); box-shadow: 0 24px 58px rgba(0,0,0,.2); }
.plan-card.done { opacity: .72; }
.date-tile { display: grid; place-content: center; min-height: 86px; border-radius: 20px; background: #134e4a; color: #ccfbf1; text-align: center; }
.date-tile strong { font-size: 2rem; line-height: 1; }
.date-tile span { margin-top: 6px; font-weight: 900; }
.plan-top { display: flex; justify-content: space-between; gap: 12px; color: #99f6e4; font-weight: 900; }
.plan-top small { color: #94a3b8; font-weight: 700; }
h2 { margin: 10px 0 6px; color: #fff7ed; }
p { margin: 0; color: #cbd5e1; line-height: 1.55; }
.meta { display: flex; flex-wrap: wrap; gap: 7px; margin: 12px 0; }
.meta span { border: 1px solid rgba(94,234,212,.24); border-radius: 999px; padding: 5px 9px; color: #ccfbf1; font-size: .78rem; }
textarea, input { width: 100%; box-sizing: border-box; margin-top: 8px; border: 1px solid rgba(148,163,184,.2); border-radius: 13px; background: rgba(2,6,23,.48); color: #f8fafc; padding: 11px; }
textarea { min-height: 74px; resize: vertical; }
.card-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.ghost { background: #334155; }
.danger { background: #7f1d1d; }
.state, .toast { padding: 16px; border: 1px dashed rgba(148,163,184,.34); border-radius: 16px; color: #cbd5e1; text-align: center; }
.toast { margin-bottom: 12px; border-style: solid; background: rgba(20,83,45,.2); color: #bbf7d0; }
.toast.error, .state.error { color: #fecaca; background: rgba(127,29,29,.18); }
@media (max-width: 620px) {
  .hero { display: grid; }
  .actions { display: grid; grid-template-columns: 1fr; }
  .plan-card { grid-template-columns: 1fr; }
  .date-tile { min-height: 70px; }
  .card-actions { display: grid; }
}
</style>
