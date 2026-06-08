<template>
  <main class="groups-page">
    <header class="hero">
      <div>
        <p>CREW BOARD</p>
        <h1>그룹</h1>
        <span>같이 플레이할 요원을 모으고 그룹 멤버를 확인합니다.</span>
      </div>
      <div class="hero-actions">
        <button type="button" @click="router.push({ name: 'EpisodeList' })">사건 파일</button>
        <button type="button" @click="router.push({ name: 'MyPage' })">마이페이지</button>
      </div>
    </header>

    <p v-if="message" class="toast" :class="messageType">{{ message }}</p>

    <section class="create-panel">
      <div>
        <p>NEW GROUP</p>
        <h2>팀 만들기</h2>
      </div>
      <input v-model.trim="form.name" maxlength="80" placeholder="그룹 이름" />
      <textarea v-model.trim="form.description" maxlength="500" placeholder="플레이 스타일, 모임 지역, 일정 메모"></textarea>
      <select v-model="form.visibility">
        <option value="PUBLIC">공개</option>
        <option value="PRIVATE">비공개</option>
      </select>
      <button type="button" :disabled="creating" @click="createGroup">그룹 생성</button>
    </section>

    <section v-if="loading" class="state">그룹을 불러오는 중입니다.</section>
    <section v-else-if="error" class="state error">{{ error }}</section>
    <section v-else class="content-grid">
      <article class="panel">
        <div class="panel-head">
          <div>
            <p>MY CREWS</p>
            <h2>내 그룹 {{ myGroups.length }}</h2>
          </div>
          <button type="button" @click="loadGroups">새로고침</button>
        </div>
        <p v-if="!myGroups.length" class="empty">아직 가입한 그룹이 없습니다.</p>
        <div v-for="group in myGroups" :key="`my-${group.id}`" class="group-card active">
          <div>
            <strong>{{ group.name }}</strong>
            <small>{{ group.ownerNickname }} · {{ group.memberCount }}명 · {{ roleLabel(group.myRole) }}</small>
          </div>
          <p>{{ group.description || '그룹 설명이 없습니다.' }}</p>
          <div class="card-actions">
            <button type="button" @click="selectGroup(group)">멤버 보기</button>
            <button v-if="group.myRole !== 'OWNER'" type="button" class="danger" @click="leave(group)">탈퇴</button>
          </div>
        </div>
      </article>

      <article class="panel">
        <div class="panel-head">
          <div>
            <p>PUBLIC CREWS</p>
            <h2>공개 그룹 {{ groups.length }}</h2>
          </div>
        </div>
        <p v-if="!groups.length" class="empty">공개 그룹이 아직 없습니다.</p>
        <div v-for="group in groups" :key="group.id" class="group-card">
          <div>
            <strong>{{ group.name }}</strong>
            <small>{{ group.ownerNickname }} · {{ group.memberCount }}명 · {{ visibilityLabel(group.visibility) }}</small>
          </div>
          <p>{{ group.description || '그룹 설명이 없습니다.' }}</p>
          <div class="card-actions">
            <button type="button" @click="selectGroup(group)">멤버 보기</button>
            <button v-if="!group.joined && group.visibility === 'PUBLIC'" type="button" @click="join(group)">가입</button>
            <button v-else-if="group.joined && group.myRole !== 'OWNER'" type="button" class="danger" @click="leave(group)">탈퇴</button>
          </div>
        </div>
      </article>
    </section>

    <section v-if="selectedGroup" class="members-panel">
      <div class="panel-head">
        <div>
          <p>MEMBERS</p>
          <h2>{{ selectedGroup.name }}</h2>
        </div>
        <button type="button" @click="selectedGroup = null">닫기</button>
      </div>
      <p v-if="membersLoading" class="empty">멤버를 불러오는 중입니다.</p>
      <div v-else class="member-list">
        <div v-for="member in members" :key="member.userId" class="member-row">
          <img :src="member.profileImageUrl || defaultProfile" alt="" />
          <div>
            <strong>{{ member.nickname || '요원' }}</strong>
            <small>{{ roleLabel(member.role) }}</small>
          </div>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { groupApi } from '@/api/groupApi';

const router = useRouter();
const groups = ref([]);
const myGroups = ref([]);
const members = ref([]);
const selectedGroup = ref(null);
const loading = ref(true);
const membersLoading = ref(false);
const creating = ref(false);
const error = ref('');
const message = ref('');
const messageType = ref('success');
const form = reactive({ name: '', description: '', visibility: 'PUBLIC' });
const defaultProfile = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2264%22 height=%2264%22 viewBox=%220 0 64 64%22%3E%3Crect width=%2264%22 height=%2264%22 rx=%2218%22 fill=%22%231348e%22/%3E%3Ccircle cx=%2232%22 cy=%2225%22 r=%2211%22 fill=%22%2399f6e4%22/%3E%3Cpath d=%22M14 56c3-12 12-18 18-18s15 6 18 18%22 fill=%22%2399f6e4%22/%3E%3C/svg%3E';

onMounted(loadGroups);

async function loadGroups() {
  loading.value = true;
  error.value = '';
  try {
    [groups.value, myGroups.value] = await Promise.all([
      groupApi.getGroups(),
      groupApi.getMyGroups()
    ]);
  } catch (err) {
    error.value = err.userMessage || '그룹을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

async function createGroup() {
  if (!form.name) {
    setMessage('그룹 이름을 입력하세요.', 'error');
    return;
  }
  creating.value = true;
  try {
    const created = await groupApi.createGroup({ ...form });
    form.name = '';
    form.description = '';
    form.visibility = 'PUBLIC';
    setMessage('그룹을 생성했습니다.');
    await loadGroups();
    await selectGroup(created);
  } catch (err) {
    setMessage(err.userMessage || '그룹을 생성하지 못했습니다.', 'error');
  } finally {
    creating.value = false;
  }
}

async function join(group) {
  try {
    await groupApi.joinGroup(group.id);
    setMessage('그룹에 가입했습니다.');
    await loadGroups();
  } catch (err) {
    setMessage(err.userMessage || '그룹에 가입하지 못했습니다.', 'error');
  }
}

async function leave(group) {
  try {
    await groupApi.leaveGroup(group.id);
    setMessage('그룹에서 탈퇴했습니다.');
    await loadGroups();
    if (selectedGroup.value?.id === group.id) {
      selectedGroup.value = null;
      members.value = [];
    }
  } catch (err) {
    setMessage(err.userMessage || '그룹에서 탈퇴하지 못했습니다.', 'error');
  }
}

async function selectGroup(group) {
  selectedGroup.value = group;
  membersLoading.value = true;
  try {
    members.value = await groupApi.getMembers(group.id);
  } catch (err) {
    setMessage(err.userMessage || '멤버를 불러오지 못했습니다.', 'error');
  } finally {
    membersLoading.value = false;
  }
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}

function roleLabel(role) {
  return role === 'OWNER' ? '그룹장' : '멤버';
}

function visibilityLabel(visibility) {
  return visibility === 'PRIVATE' ? '비공개' : '공개';
}
</script>

<style scoped>
.groups-page { min-height: 100vh; box-sizing: border-box; padding: 26px 16px 72px; background: radial-gradient(circle at 15% 0%, rgba(34,197,94,.18), transparent 32%), linear-gradient(145deg, #06130d, #111827 56%, #09090b); color: #f8fafc; font-family: 'Noto Serif KR', Georgia, serif; }
.hero, .create-panel, .content-grid, .members-panel, .state, .toast { width: min(100%, 960px); box-sizing: border-box; margin-left: auto; margin-right: auto; }
.hero { display: flex; justify-content: space-between; gap: 16px; align-items: end; margin-bottom: 14px; padding: 22px; border: 1px solid rgba(74,222,128,.22); border-radius: 24px; background: rgba(15,23,42,.6); }
.hero p, .create-panel p, .panel-head p { margin: 0 0 7px; color: #86efac; font-size: .74rem; font-weight: 1000; letter-spacing: .16em; }
h1 { margin: 0; font-size: clamp(2.2rem, 9vw, 4.4rem); line-height: .95; }
h2 { margin: 0; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.hero-actions, .card-actions { display: flex; flex-wrap: wrap; gap: 8px; }
button, input, textarea, select { font: inherit; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #15803d; color: #fff; font-weight: 900; padding: 0 14px; }
.danger { background: #7f1d1d; }
.create-panel { display: grid; grid-template-columns: 1fr 1.2fr; gap: 10px; margin-bottom: 14px; padding: 16px; border: 1px solid rgba(148,163,184,.18); border-radius: 20px; background: rgba(2,6,23,.42); }
input, textarea, select { width: 100%; box-sizing: border-box; border: 1px solid rgba(148,163,184,.22); border-radius: 13px; background: rgba(2,6,23,.55); color: #f8fafc; padding: 11px; }
textarea { min-height: 78px; resize: vertical; grid-column: span 2; }
.create-panel button { grid-column: span 2; }
.content-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 14px; }
.panel, .members-panel { padding: 16px; border: 1px solid rgba(148,163,184,.18); border-radius: 22px; background: rgba(15,23,42,.6); box-shadow: 0 22px 54px rgba(0,0,0,.2); }
.panel-head { display: flex; justify-content: space-between; gap: 12px; align-items: end; margin-bottom: 12px; }
.group-card { display: grid; gap: 10px; padding: 14px; border: 1px solid rgba(148,163,184,.14); border-radius: 16px; background: rgba(2,6,23,.38); margin-top: 10px; }
.group-card.active { border-color: rgba(74,222,128,.28); }
.group-card strong, .member-row strong { display: block; color: #fff7ed; }
.group-card small, .member-row small, .empty { color: #94a3b8; }
.group-card p { margin: 0; color: #cbd5e1; line-height: 1.5; }
.members-panel { margin-top: 14px; }
.member-list { display: grid; gap: 8px; }
.member-row { display: grid; grid-template-columns: 44px 1fr; gap: 10px; align-items: center; padding: 10px; border-radius: 14px; background: rgba(2,6,23,.35); }
.member-row img { width: 44px; height: 44px; border-radius: 15px; object-fit: cover; background: #134e4a; }
.state, .toast { padding: 16px; border: 1px dashed rgba(148,163,184,.34); border-radius: 16px; color: #cbd5e1; text-align: center; }
.toast { margin-bottom: 12px; border-style: solid; background: rgba(20,83,45,.2); color: #bbf7d0; }
.toast.error, .state.error { color: #fecaca; background: rgba(127,29,29,.18); }
@media (max-width: 720px) {
  .hero, .content-grid, .create-panel { display: grid; grid-template-columns: 1fr; }
  .hero-actions, .card-actions { display: grid; grid-template-columns: 1fr; }
  textarea, .create-panel button { grid-column: auto; }
}
</style>
