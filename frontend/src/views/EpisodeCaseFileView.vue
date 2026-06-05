<template>
  <main class="case-file-page">
    <CaseFileTabMenu :episode-id="episodeId" active="case" />

    <section v-if="loading" class="state">사건파일을 불러오는 중입니다.</section>
    <section v-else-if="error" class="state error">{{ error }}</section>

    <template v-else-if="caseFile">
      <section class="cover dossier">
        <div class="cover-actions">
          <p class="stamp">CASE FILE</p>
          <button type="button" @click="loadCaseFile">새로고침</button>
        </div>
        <h1>{{ caseFile.title }}</h1>
        <h2>{{ caseFile.subtitle }}</h2>
        <div class="cover-grid">
          <span>장르 <strong>{{ caseFile.genre }}</strong></span>
          <span>난이도 <strong>{{ caseFile.difficulty }}</strong></span>
          <span>예상 시간 <strong>{{ caseFile.estimatedTime }}</strong></span>
          <span>예상 거리 <strong>{{ caseFile.estimatedDistance }}</strong></span>
          <span>권장 인원 <strong>{{ caseFile.recommendedPlayers }}</strong></span>
          <span>진행 상태 <strong>{{ statusLabel(caseFile.progressStatus) }}</strong></span>
        </div>
      </section>

      <section class="dossier overview">
        <p class="section-label">사건 개요</p>
        <h3>{{ caseFile.overview.briefingTitle }}</h3>
        <p>{{ caseFile.overview.summary }}</p>
        <blockquote>{{ caseFile.finalQuestion }}</blockquote>
        <p class="goal">목표: {{ caseFile.overview.goal }}</p>
      </section>

      <section class="dossier">
        <div class="section-head">
          <div>
            <p class="section-label">용의자 파일</p>
            <h3>{{ caseFile.progressSummary.unlockedSuspectCount }}/{{ caseFile.progressSummary.totalSuspectCount }}명 확인</h3>
          </div>
        </div>
        <div class="card-grid">
          <article v-for="suspect in caseFile.suspects" :key="suspect.suspectId" class="suspect-card" :class="{ locked: !suspect.unlocked }">
            <div class="portrait">{{ suspect.unlocked ? suspect.displayName.slice(0, 1) : '?' }}</div>
            <div>
              <em>{{ suspect.alias }}</em>
              <h4>{{ suspect.displayName }}</h4>
              <p>{{ suspect.shortDescription }}</p>
            </div>
            <dl v-if="suspect.unlocked">
              <dt>관계</dt><dd>{{ suspect.relationToVictim }}</dd>
              <dt>의심 포인트</dt><dd>{{ suspect.suspiciousPoint }}</dd>
              <dt>알리바이</dt><dd>{{ suspect.alibiSummary }}</dd>
            </dl>
            <span v-else class="lock">잠김</span>
          </article>
        </div>
      </section>

      <section class="dossier">
        <div class="section-head">
          <div>
            <p class="section-label">증거 / 메모 / 사진 카드</p>
            <h3>{{ caseFile.progressSummary.unlockedEvidenceCount }}/{{ caseFile.progressSummary.totalEvidenceCount }}개 해금</h3>
          </div>
        </div>
        <div class="evidence-grid">
          <article v-for="evidence in caseFile.evidences" :key="evidence.evidenceId" class="evidence-card" :class="[String(evidence.type || '').toLowerCase(), { locked: !evidence.unlocked }]">
            <div class="photo-placeholder">{{ evidence.unlocked ? evidenceTypeLabel(evidence.type) : 'LOCKED' }}</div>
            <div>
              <span class="tag">{{ evidenceTypeLabel(evidence.type) }}</span>
              <h4>{{ evidence.title }}</h4>
              <p>{{ evidence.textSummary }}</p>
              <small v-if="evidence.relatedClueType">{{ clueTypeLabel(evidence.relatedClueType) }}</small>
            </div>
          </article>
        </div>
      </section>

      <section class="dossier board">
        <p class="section-label">단서 요약</p>
        <div class="clue-column">
          <h4>정답 힌트</h4>
          <span v-for="clue in caseFile.clueSummary.answerClues" :key="`a-${clue}`">{{ clue }}</span>
          <em v-if="!caseFile.clueSummary.answerClues.length">아직 없음</em>
        </div>
        <div class="clue-column purple">
          <h4>목적지 힌트</h4>
          <span v-for="clue in caseFile.clueSummary.destinationClues" :key="`d-${clue}`">{{ clue }}</span>
          <em v-if="!caseFile.clueSummary.destinationClues.length">아직 없음</em>
        </div>
        <div class="clue-column green">
          <h4>스토리 단서</h4>
          <span v-for="clue in caseFile.clueSummary.storyClues" :key="`s-${clue}`">{{ clue }}</span>
          <em v-if="!caseFile.clueSummary.storyClues.length">아직 없음</em>
        </div>
      </section>

      <section class="dossier log">
        <p class="section-label">답안지 / 조사 기록</p>
        <div class="log-grid">
          <span>방문 장소 <strong>{{ caseFile.progressSummary.visitedSpotCount }}/{{ caseFile.progressSummary.totalSpotCount }}</strong></span>
          <span>완료 장소 <strong>{{ caseFile.progressSummary.completedSpotCount }}/{{ caseFile.progressSummary.totalSpotCount }}</strong></span>
          <span>힌트 사용 <strong>{{ caseFile.progressSummary.hintUsedCount }}</strong></span>
          <span>오답 횟수 <strong>{{ caseFile.progressSummary.wrongAnswerCount }}</strong></span>
          <span>추리 질문 <strong>{{ caseFile.progressSummary.deductionQuestionCount }}</strong></span>
          <span>점수 <strong>{{ caseFile.progressSummary.score ?? '-' }}</strong></span>
        </div>
        <p class="resume">시작: {{ caseFile.answerLog.startedAt || '아직 시작 전' }} / 마지막 접속: {{ caseFile.answerLog.lastPlayedAt || '-' }}</p>
      </section>

      <section class="dossier notices">
        <p class="section-label">운영 주의사항</p>
        <ul>
          <li v-for="notice in caseFile.notices" :key="notice">{{ notice }}</li>
        </ul>
        <p class="team">{{ caseFile.teamRoleGuide }}</p>
      </section>

      <section class="dossier rewards">
        <p class="section-label">지역 리워드</p>
        <article v-for="reward in caseFile.partnerRewards" :key="reward.title" class="reward-card">
          <strong>{{ reward.title }}</strong>
          <span>{{ reward.status }}</span>
          <p>{{ reward.description }}</p>
        </article>
      </section>
    </template>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { caseFileApi } from '@/api/caseFileApi';
import CaseFileTabMenu from '@/components/episode/CaseFileTabMenu.vue';

const route = useRoute();
const episodeId = route.params.episodeId;
const caseFile = ref(null);
const loading = ref(true);
const error = ref('');

onMounted(loadCaseFile);

async function loadCaseFile() {
  loading.value = true;
  error.value = '';
  try {
    caseFile.value = await caseFileApi.getCaseFile(episodeId);
  } catch (err) {
    error.value = err.userMessage || '사건파일을 불러올 수 없습니다.';
  } finally {
    loading.value = false;
  }
}

const statusLabel = (status) => ({
  NOT_STARTED: '시작 전',
  IN_PROGRESS: '조사 중',
  FINAL_READY: '최종 추리 가능',
  CLEARED: '클리어 완료',
  FAILED: '실패'
}[status] || status);

const clueTypeLabel = (type) => ({
  ANSWER_CLUE: '정답 힌트',
  DESTINATION_CLUE: '목적지 힌트',
  STORY_CLUE: '스토리 단서',
  SUSPECT_CLUE: '용의자 단서'
}[type] || type);

const evidenceTypeLabel = (type) => ({
  PHOTO: '사진',
  MEMO: '메모',
  NOTE: '노트',
  DOCUMENT: '문서',
  EVIDENCE: '증거',
  SUSPECT_CLUE: '용의자 단서',
  POST_IT: '포스트잇',
  ANSWER_CLUE: '정답 힌트',
  DESTINATION_CLUE: '목적지 힌트',
  STORY_CLUE: '스토리 단서'
}[type] || type);
</script>

<style scoped>
.case-file-page { min-height: 100vh; box-sizing: border-box; padding: 14px 12px 44px; background: radial-gradient(circle at 12% 8%, rgba(180,83,9,.24), transparent 32%), linear-gradient(180deg, #17110b, #111827 62%, #020617); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.dossier, .state { width: min(100%, 430px); box-sizing: border-box; margin: 0 auto 12px; border: 1px solid rgba(245,158,11,.22); border-radius: 20px; background: rgba(15,23,42,.76); box-shadow: 0 18px 44px rgba(0,0,0,.22); }
.state { padding: 22px; color: #cbd5e1; text-align: center; }
.state.error { color: #fecaca; }
.cover { padding: 22px; background: linear-gradient(145deg, rgba(120,53,15,.44), rgba(15,23,42,.88)); }
.cover-actions { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.cover-actions button { border: 1px solid rgba(245,158,11,.35); border-radius: 999px; background: rgba(120,53,15,.3); color: #fde68a; padding: 8px 11px; font-weight: 900; }
.stamp, .section-label { margin: 0 0 8px; color: #f59e0b; font-size: .74rem; font-weight: 1000; letter-spacing: .14em; }
h1 { margin: 0; font-size: clamp(2rem, 10vw, 3.3rem); line-height: .98; }
h2 { margin: 10px 0 0; color: #fde68a; font-size: 1rem; }
h3, h4 { margin: 0; }
p { color: #dbeafe; line-height: 1.6; }
.cover-grid, .log-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 18px; }
.cover-grid span, .log-grid span { display: grid; gap: 5px; padding: 11px; border-radius: 14px; background: rgba(2,6,23,.38); color: #94a3b8; font-size: .78rem; }
strong { color: #fff; }
.overview, .board, .log, .notices, .rewards { padding: 18px; }
blockquote { margin: 14px 0; padding: 14px; border-left: 4px solid #f97316; background: rgba(120,53,15,.22); color: #fff7ed; line-height: 1.6; }
.goal, .team, .resume { color: #fde68a; }
.section-head { display: flex; align-items: end; justify-content: space-between; gap: 10px; padding: 18px 18px 0; }
.card-grid, .evidence-grid { display: grid; gap: 10px; padding: 14px 18px 18px; }
.suspect-card, .evidence-card { position: relative; overflow: hidden; display: grid; grid-template-columns: 58px 1fr; gap: 12px; padding: 14px; border: 1px solid rgba(148,163,184,.18); border-radius: 16px; background: rgba(2,6,23,.34); }
.suspect-card.locked, .evidence-card.locked { filter: grayscale(.6); opacity: .7; }
.portrait { width: 58px; height: 58px; border-radius: 18px; display: grid; place-items: center; background: linear-gradient(135deg, #7c2d12, #0f172a); font-weight: 1000; font-size: 1.4rem; }
em { color: #94a3b8; font-style: normal; font-size: .78rem; }
dl { grid-column: 1 / -1; display: grid; gap: 4px; margin: 8px 0 0; }
dt { color: #fbbf24; font-size: .76rem; font-weight: 900; }
dd { margin: 0 0 6px; color: #cbd5e1; line-height: 1.5; }
.lock { position: absolute; right: 12px; top: 12px; border-radius: 999px; padding: 4px 8px; background: rgba(15,23,42,.82); color: #f8fafc; font-size: .72rem; font-weight: 900; }
.photo-placeholder { min-height: 74px; border-radius: 14px; display: grid; place-items: center; background: linear-gradient(135deg, rgba(245,158,11,.28), rgba(15,23,42,.9)); color: #fde68a; font-size: .75rem; font-weight: 1000; }
.tag, .clue-column span { display: inline-flex; width: fit-content; border-radius: 999px; padding: 5px 8px; background: rgba(245,158,11,.16); color: #fde68a; font-size: .74rem; font-weight: 900; }
.evidence-card small { color: #67e8f9; font-weight: 900; }
.clue-column { display: grid; gap: 8px; margin-top: 12px; padding: 13px; border-radius: 16px; background: rgba(2,6,23,.34); }
.clue-column.purple span { background: rgba(126,34,206,.24); color: #e9d5ff; }
.clue-column.green span { background: rgba(21,128,61,.24); color: #bbf7d0; }
.clue-column em { color: #94a3b8; }
ul { margin: 0; padding-left: 18px; color: #cbd5e1; line-height: 1.65; }
.reward-card { padding: 14px; border: 1px dashed rgba(148,163,184,.34); border-radius: 16px; background: rgba(2,6,23,.28); }
.reward-card span { margin-left: 8px; border-radius: 999px; padding: 4px 8px; background: #334155; color: #cbd5e1; font-size: .75rem; }
@media (max-width: 370px) { .cover-grid, .log-grid { grid-template-columns: 1fr; } }
</style>
