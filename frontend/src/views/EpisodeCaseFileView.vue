<template>
  <main class="case-file-page">
    <CaseFileTabMenu :episode-id="episodeId" active="case" />

    <section v-if="loading" class="state">사건파일을 불러오는 중입니다.</section>
    <section v-else-if="error" class="state error">{{ error }}</section>

    <template v-else-if="caseFile">
      <section class="cover dossier">
        <div class="cover-actions">
          <p class="stamp">CASE FILE</p>
          <div class="cover-buttons">
            <button type="button" class="ghost" @click="loadCaseFile">새로고침</button>
            <button type="button" @click="router.push({ name: 'EpisodeMap', params: { episodeId } })">다음: 조사 지도</button>
          </div>
        </div>
        <h1>{{ caseFile.title }}</h1>
        <h2>{{ caseFile.subtitle }}</h2>
        <div class="cover-grid">
          <span>장르 <strong>{{ caseFile.genre || '-' }}</strong></span>
          <span>난이도 <strong>{{ caseFile.difficulty || '-' }}</strong></span>
          <span>예상 시간 <strong>{{ caseFile.estimatedTime || '-' }}</strong></span>
          <span>예상 거리 <strong>{{ caseFile.estimatedDistance || '-' }}</strong></span>
          <span>권장 인원 <strong>{{ caseFile.recommendedPlayers || '-' }}</strong></span>
          <span>진행 상태 <strong>{{ statusLabel(caseFile.progressStatus) }}</strong></span>
        </div>
      </section>

      <section class="dossier overview" :class="{ unlocked: caseFile.overview?.storyUnlocked }">
        <p class="section-label">사건 개요</p>
        <div class="story-card-head">
          <h3>{{ caseFile.overview?.briefingTitle || '사건 브리핑' }}</h3>
          <span>{{ caseFile.overview?.storyUnlocked ? '스토리 기록 해금' : '시작 미션 필요' }}</span>
        </div>
        <p class="story-summary">{{ caseFile.overview?.summary }}</p>
        <div v-if="caseFile.overview?.storyUnlocked && caseFile.overview?.unlockedStoryClues?.length" class="story-clue-strip">
          <strong>해금된 스토리 단서</strong>
          <span v-for="(clue, index) in caseFile.overview.unlockedStoryClues" :key="`overview-story-${clue}`">{{ humanizeClue(clue, 'story', index) }}</span>
        </div>
        <p v-else class="story-locked-help">시작 장소의 현장 퍼즐을 해결하면 이 카드가 상세 스토리 기록으로 갱신됩니다.</p>
        <blockquote>{{ caseFile.finalQuestion }}</blockquote>
        <p class="goal">목표: {{ caseFile.overview?.goal }}</p>
      </section>

      <section class="dossier">
        <div class="section-head">
          <div>
            <p class="section-label">용의자 파일</p>
            <h3>{{ caseFile.progressSummary.unlockedSuspectCount }}/{{ caseFile.progressSummary.totalSuspectCount }}명 확인</h3>
            <p class="section-help">용의자는 반드시 범인이 아니라, 문서와 단서의 흐름을 왜곡했을 수 있는 관계자입니다. 관계, 의심 포인트, 알리바이를 증거 카드와 대조하세요.</p>
          </div>
        </div>
        <div class="card-grid">
          <article v-for="suspect in visibleSuspects" :key="suspect.suspectId" class="suspect-card" :class="{ locked: !suspect.unlocked }">
            <div class="portrait-frame">
              <img :src="suspectPortraitSrc(suspect)" alt="용의자 카드" />
            </div>
            <div>
              <em>{{ suspect.alias || '용의자' }}</em>
              <h4>{{ suspect.displayName || '이름 미확인 인물' }}</h4>
              <p>{{ suspect.shortDescription || '사건과 연결된 가능성이 있는 인물입니다. 단서를 모으면 관계와 알리바이가 더 명확해집니다.' }}</p>
            </div>
            <dl>
              <dt>관계</dt><dd>{{ suspect.relationToVictim || '관계 미확인' }}</dd>
              <dt>의심 포인트</dt><dd>{{ suspect.suspiciousPoint || '의심 포인트 미입력' }}</dd>
              <dt>알리바이</dt><dd>{{ suspect.alibiSummary || '알리바이 미확인' }}</dd>
            </dl>
            <span class="unlock-badge">{{ suspect.cleared ? '혐의 해소' : suspect.unlocked ? '상세 확인' : '기본 공개' }}</span>
          </article>
        </div>
      </section>

      <section class="dossier">
        <div class="section-head">
          <div>
            <p class="section-label">증거 / 메모 / 사진 카드</p>
            <h3>{{ caseFile.progressSummary.unlockedEvidenceCount }}/{{ caseFile.progressSummary.totalEvidenceCount }}개 해금</h3>
            <p class="section-help">각 카드는 정답 키워드, 장소 키워드, 용의자 진술 중 하나를 좁히는 근거입니다. 해금 순서와 관련 인물을 함께 보면 최종 추리 난도가 내려갑니다.</p>
          </div>
        </div>
        <div class="evidence-grid">
          <article v-for="evidence in caseFile.evidences" :key="evidence.evidenceId" class="evidence-card" :class="[String(evidence.type || '').toLowerCase(), { locked: !evidence.unlocked }]">
            <div class="evidence-art">
              <img v-if="evidence.unlocked" :src="evidenceImageSrc(evidence)" alt="사건자료 카드" />
              <span v-else>LOCKED</span>
            </div>
            <div>
              <span class="tag">{{ evidenceTypeLabel(evidence.type) }}</span>
              <h4>{{ evidence.unlocked ? evidence.title : '잠긴 사건자료' }}</h4>
              <p>{{ evidence.unlocked ? evidence.textSummary : '현장 퍼즐을 해결하면 이 자료가 사건파일에 추가됩니다.' }}</p>
              <div class="evidence-meta">
                <small v-if="evidence.relatedClueType">{{ clueTypeLabel(evidence.relatedClueType) }}</small>
                <small v-if="evidence.unlocked && evidence.relatedSuspectIds?.length">관련 인물 {{ suspectNames(evidence.relatedSuspectIds) }}</small>
                <small v-if="!evidence.unlocked">가방에 들어올 자료</small>
              </div>
            </div>
            <span v-if="evidence.unlocked" class="unlock-badge">해금됨</span>
            <span v-else class="lock">잠김</span>
          </article>
        </div>
      </section>

      <section class="dossier board">
        <p class="section-label">단서 요약</p>
        <div class="clue-column">
          <h4>정답 키워드</h4>
          <span v-for="(clue, index) in caseFile.clueSummary.answerClues" :key="`a-${clue}`">{{ humanizeClue(clue, 'answer', index) }}</span>
          <em v-if="!caseFile.clueSummary.answerClues.length">아직 없음</em>
        </div>
        <div class="clue-column purple">
          <h4>장소 키워드</h4>
          <span v-for="(clue, index) in caseFile.clueSummary.destinationClues" :key="`d-${clue}`">{{ humanizeClue(clue, 'destination', index) }}</span>
          <em v-if="!caseFile.clueSummary.destinationClues.length">아직 없음</em>
        </div>
        <div class="clue-column green">
          <h4>스토리 단서</h4>
          <span v-for="(clue, index) in caseFile.clueSummary.storyClues" :key="`s-${clue}`">{{ humanizeClue(clue, 'story', index) }}</span>
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

      <section v-if="caseFile.notices?.length" class="dossier notices">
        <p class="section-label">운영 주의사항</p>
        <ul>
          <li v-for="notice in cleanNotices" :key="notice">{{ notice }}</li>
        </ul>
        <p class="team">{{ caseFile.teamRoleGuide }}</p>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { caseFileApi } from '@/api/caseFileApi';
import CaseFileTabMenu from '@/components/episode/CaseFileTabMenu.vue';

const route = useRoute();
const router = useRouter();
const episodeId = route.params.episodeId;
const caseFile = ref(null);
const loading = ref(true);
const error = ref('');

const cleanNotices = computed(() => (caseFile.value?.notices || []).filter((notice) => !String(notice).includes('AI 초안 저장본')));
const visibleSuspects = computed(() => (caseFile.value?.suspects || []).filter((suspect) => suspect.displayName || suspect.alias));

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

const statusLabel = (status) => ({ NOT_STARTED: '시작 전', IN_PROGRESS: '조사 중', FINAL_READY: '최종 추리 가능', CLEARED: '클리어 완료', FAILED: '실패' }[status] || status);
const clueTypeLabel = (type) => ({ ANSWER_CLUE: '정답 키워드', DESTINATION_CLUE: '장소 키워드', STORY_CLUE: '스토리 단서', SUSPECT_CLUE: '용의자 단서' }[type] || type);
const evidenceTypeLabel = (type) => ({ PHOTO: '사진', MEMO: '메모', NOTE: '노트', DOCUMENT: '문서', EVIDENCE: '증거', SUSPECT_CLUE: '용의자 단서', POST_IT: '포스트잇', ANSWER_CLUE: '정답 키워드', DESTINATION_CLUE: '장소 키워드', STORY_CLUE: '스토리 단서' }[type] || type);

function suspectPortraitSrc(suspect) { return usableImageUrl(suspect.portraitImageUrl) || generatedSuspectPortraitDataUrl(suspect.displayName, suspect.alias, suspect.suspiciousPoint); }
function evidenceImageSrc(evidence) { return usableImageUrl(evidence.imageUrl) || generatedCaseCardDataUrl(evidence.title, evidence.type, evidence.textSummary); }
function usableImageUrl(value) { const url = String(value || '').trim(); return !url || url.includes('generated-case-card') ? '' : url; }
function suspectNames(ids = []) { return ids.map((id) => caseFile.value?.suspects?.find((suspect) => suspect.suspectId === id)?.displayName).filter(Boolean).join(', '); }
function humanizeClue(value, kind, index) {
  const text = String(value || '').trim();
  if (!/^(answer|destination|story)-clue-\d+$/i.test(text)) return text;
  const fallback = {
    answer: ['찢긴 가장자리', '빛에 탄 자국', '거꾸로 찍힌 그림자', '봉인 라벨'],
    destination: ['장소 표식', '닫힌 문', '굽은 골목'],
    story: ['첫 목격 기록', '엇갈린 동선', '남겨진 시간표']
  };
  return fallback[kind]?.[index % fallback[kind].length] || '미분류 단서';
}

function generatedSuspectPortraitDataUrl(name = '용의자', alias = 'SUSPECT', seedText = '') {
  const hash = hashString(`${name}-${alias}-${seedText}`);
  const skin = ['#f2c6a0', '#d9a77e', '#c68b6b', '#e4b98f'][hash % 4];
  const shirt = ['#93c5fd', '#86efac', '#fca5a5', '#c4b5fd', '#fde68a'][hash % 5];
  const hair = ['#111827', '#292524', '#3f3f46'][hash % 3];
  const safeName = escapeXml(name || '용의자');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="520" height="680" viewBox="0 0 520 680"><rect width="520" height="680" fill="#f8ead0"/><rect x="36" y="36" width="448" height="608" fill="#fff7ed" stroke="#94a3b8" stroke-width="3"/><text x="66" y="86" fill="#111827" font-family="Arial" font-size="34" font-weight="900">용의자 정보</text><rect x="86" y="124" width="348" height="350" fill="#e2e8f0" stroke="#475569" stroke-width="4"/><circle cx="260" cy="258" r="78" fill="${skin}"/><path d="M180 248 C176 160, 340 150, 340 250 C300 212, 236 230, 180 248Z" fill="${hair}"/><circle cx="232" cy="270" r="7" fill="#111827"/><circle cx="288" cy="270" r="7" fill="#111827"/><path d="M236 316 C254 332, 280 332, 298 316" fill="none" stroke="#7f1d1d" stroke-width="6" stroke-linecap="round"/><path d="M126 444 C150 370, 204 340, 260 340 C318 340, 372 372, 396 444" fill="${shirt}"/><path d="M122 514 H398 M122 558 H322" stroke="#94a3b8" stroke-width="10" stroke-linecap="round"/><text x="122" y="616" fill="#111827" font-family="Arial" font-size="28" font-weight="900">${safeName}</text></svg>`;
  return dataUrl(svg);
}
function generatedCaseCardDataUrl(title = '사건자료', type = 'EVIDENCE') { const safeTitle = escapeXml(title || '사건자료'); const safeType = escapeXml(evidenceTypeLabel(type)); const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="960" height="640" viewBox="0 0 960 640"><rect width="960" height="640" fill="#111827"/><rect x="72" y="54" width="816" height="532" rx="28" fill="#f8ead0"/><text x="116" y="104" fill="#b45309" font-family="Georgia" font-size="34" font-weight="900">OPERATION KOREA</text><text x="116" y="144" fill="#334155" font-family="Arial" font-size="20" font-weight="900">CASE MATERIAL · ${safeType}</text><rect x="136" y="190" width="420" height="240" rx="24" fill="#0f172a"/><path d="M190 290 H490 M190 345 H420" stroke="#f59e0b" stroke-width="18" stroke-linecap="round"/><circle cx="486" cy="238" r="42" fill="#fef3c7"/><path d="M470 238 L486 254 L520 214" fill="none" stroke="#78350f" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"/><rect x="108" y="506" width="744" height="54" rx="14" fill="#111827"/><text x="132" y="542" fill="#f8fafc" font-family="Arial" font-size="26" font-weight="900">${safeTitle}</text></svg>`; return dataUrl(svg); }
function dataUrl(svg) { return `data:image/svg+xml;base64,${btoa(unescape(encodeURIComponent(svg)))}`; }
function hashString(value) { return String(value || '').split('').reduce((hash, char) => ((hash << 5) - hash + char.charCodeAt(0)) >>> 0, 2166136261); }
function escapeXml(value) { return String(value || '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;'); }
</script>

<style scoped>
.case-file-page { min-height: 100vh; box-sizing: border-box; padding: 18px 16px 44px; background: radial-gradient(circle at 12% 8%, rgba(180,83,9,.24), transparent 32%), linear-gradient(180deg, #17110b, #111827 62%, #020617); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.dossier, .state { width: min(100%, 1080px); box-sizing: border-box; margin: 0 auto 12px; border: 1px solid rgba(245,158,11,.22); border-radius: 20px; background: rgba(15,23,42,.76); box-shadow: 0 18px 44px rgba(0,0,0,.22); }
.state { padding: 22px; color: #cbd5e1; text-align: center; }
.state.error { color: #fecaca; }
.cover { padding: 22px; background: linear-gradient(145deg, rgba(120,53,15,.44), rgba(15,23,42,.88)); }
.cover-actions { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.cover-buttons { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.cover-actions button { border: 1px solid rgba(245,158,11,.35); border-radius: 999px; background: #f59e0b; color: #111827; padding: 8px 11px; font-weight: 900; }
.cover-actions button.ghost { background: rgba(120,53,15,.3); color: #fde68a; }
.stamp, .section-label { margin: 0 0 8px; color: #f59e0b; font-size: .74rem; font-weight: 1000; letter-spacing: .14em; }
h1 { margin: 0; font-size: clamp(2rem, 6vw, 3.3rem); line-height: 1; }
h2 { margin: 10px 0 0; color: #fde68a; font-size: 1rem; }
h3, h4 { margin: 0; }
p { color: #dbeafe; line-height: 1.6; }
.cover-grid, .log-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-top: 18px; }
.cover-grid span, .log-grid span { display: grid; gap: 5px; padding: 11px; border-radius: 14px; background: rgba(2,6,23,.38); color: #94a3b8; font-size: .78rem; }
strong { color: #fff; }
.overview, .board, .log, .notices { padding: 18px; }
.overview { position: relative; overflow: hidden; }
.overview::before { content: ''; position: absolute; inset: 0; pointer-events: none; background: linear-gradient(135deg, rgba(71,85,105,.18), transparent 42%); }
.overview.unlocked { border-color: rgba(34,197,94,.38); background: linear-gradient(145deg, rgba(20,83,45,.28), rgba(15,23,42,.78)); }
.overview.unlocked::before { background: radial-gradient(circle at top right, rgba(34,197,94,.2), transparent 38%); }
.story-card-head { position: relative; display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.story-card-head span { flex: 0 0 auto; border-radius: 999px; padding: 5px 9px; background: rgba(100,116,139,.22); color: #cbd5e1; font-size: .72rem; font-weight: 1000; }
.overview.unlocked .story-card-head span { background: rgba(22,101,52,.72); color: #bbf7d0; }
.story-summary { position: relative; white-space: pre-line; }
.story-locked-help { position: relative; margin: 10px 0 0; color: #94a3b8; font-size: .86rem; }
.story-clue-strip { position: relative; display: flex; flex-wrap: wrap; gap: 7px; margin: 12px 0 0; padding: 12px; border-radius: 14px; background: rgba(2,6,23,.28); }
.story-clue-strip strong { width: 100%; color: #bbf7d0; font-size: .8rem; }
.story-clue-strip span { border-radius: 999px; padding: 5px 8px; background: rgba(21,128,61,.24); color: #bbf7d0; font-size: .74rem; font-weight: 900; }
blockquote { margin: 14px 0; padding: 14px; border-left: 4px solid #f97316; background: rgba(120,53,15,.22); color: #fff7ed; line-height: 1.6; }
.goal, .team, .resume { color: #fde68a; }
.section-head { display: flex; align-items: end; justify-content: space-between; gap: 10px; padding: 18px 18px 0; }
.section-help { margin: 6px 0 0; color: #cbd5e1; font-size: .84rem; line-height: 1.55; }
.card-grid, .evidence-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 10px; padding: 14px 18px 18px; }
.suspect-card, .evidence-card { position: relative; overflow: hidden; display: grid; grid-template-columns: 96px 1fr; gap: 12px; padding: 14px; border: 1px solid rgba(148,163,184,.18); border-radius: 16px; background: rgba(2,6,23,.34); }
.suspect-card.locked, .evidence-card.locked { filter: grayscale(.45); opacity: .72; }
.portrait-frame, .evidence-art { overflow: hidden; width: 96px; min-height: 116px; border-radius: 14px; display: grid; place-items: center; background: linear-gradient(135deg, #7c2d12, #0f172a); color: #fde68a; font-weight: 1000; font-size: .74rem; }
.portrait-frame img, .evidence-art img { width: 100%; height: 100%; object-fit: cover; display: block; }
.evidence-art { min-height: 82px; }
em { color: #94a3b8; font-style: normal; font-size: .78rem; }
dl { grid-column: 1 / -1; display: grid; gap: 4px; margin: 8px 0 0; }
dt { color: #fbbf24; font-size: .76rem; font-weight: 900; }
dd { margin: 0 0 6px; color: #cbd5e1; line-height: 1.5; }
.lock, .unlock-badge { position: absolute; right: 12px; top: 12px; border-radius: 999px; padding: 4px 8px; font-size: .72rem; font-weight: 900; }
.lock { background: rgba(15,23,42,.82); color: #f8fafc; }
.unlock-badge { background: rgba(22,101,52,.82); color: #bbf7d0; }
.tag, .clue-column span { display: inline-flex; width: fit-content; border-radius: 999px; padding: 5px 8px; background: rgba(245,158,11,.16); color: #fde68a; font-size: .74rem; font-weight: 900; }
.evidence-meta { display: flex; flex-wrap: wrap; gap: 6px; }
.evidence-card small { width: fit-content; border-radius: 999px; padding: 4px 7px; background: rgba(8,47,73,.42); color: #67e8f9; font-weight: 900; }
.clue-column { display: grid; gap: 8px; margin-top: 12px; padding: 13px; border-radius: 16px; background: rgba(2,6,23,.34); }
.clue-column.purple span { background: rgba(126,34,206,.24); color: #e9d5ff; }
.clue-column.green span { background: rgba(21,128,61,.24); color: #bbf7d0; }
.clue-column em { color: #94a3b8; }
ul { margin: 0; padding-left: 18px; color: #cbd5e1; line-height: 1.65; }
@media (max-width: 720px) { .cover-grid, .log-grid, .suspect-card, .evidence-card { grid-template-columns: 1fr; } .portrait-frame, .evidence-art { width: 100%; min-height: 160px; } }
</style>
