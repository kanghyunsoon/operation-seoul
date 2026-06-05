<template>
  <main class="admin-user-page">
    <header class="admin-hero">
      <div>
        <p>ADMIN CONSOLE</p>
        <h1>회원 관리</h1>
        <span>회원 권한, 상태, 닉네임, 프로필 이미지를 관리합니다.</span>
      </div>
      <div class="hero-actions">
        <button type="button" @click="router.push({ name: 'AdminReviews' })">리뷰 관리</button>
        <button type="button" @click="router.push({ name: 'EpisodeList' })">사건 목록</button>
      </div>
    </header>

    <section class="filters">
      <label>
        권한
        <select v-model="filters.role" @change="loadUsers">
          <option value="ALL">전체</option>
          <option value="ROLE_USER">사용자</option>
          <option value="ROLE_ADMIN">관리자</option>
        </select>
      </label>
      <label>
        상태
        <select v-model="filters.status" @change="loadUsers">
          <option value="ALL">전체</option>
          <option value="ACTIVE">활성</option>
          <option value="SUSPENDED">정지</option>
          <option value="DELETED">탈퇴</option>
        </select>
      </label>
      <label class="keyword">
        검색
        <input v-model.trim="filters.keyword" type="search" placeholder="이메일, 닉네임, 권한, 상태" @keyup.enter="loadUsers" />
      </label>
      <button type="button" @click="loadUsers">검색</button>
    </section>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <section v-if="loading" class="empty">회원 목록을 불러오는 중입니다.</section>
    <section v-else-if="users.length === 0" class="empty">조건에 맞는 회원이 없습니다.</section>
    <section v-else class="user-grid">
      <article v-for="user in users" :key="user.id" class="user-card" :class="user.status.toLowerCase()">
        <div class="card-head">
          <div class="avatar" :style="avatarStyle(user)">{{ avatarText(user) }}</div>
          <div>
            <strong>{{ user.nickname }}</strong>
            <span>{{ user.email }}</span>
          </div>
          <em>{{ user.status }}</em>
        </div>

        <div class="meta">
          <span>{{ roleLabel(user.role) }}</span>
          <span>가입 {{ formatDate(user.createdAt) }}</span>
          <span>수정 {{ formatDate(user.updatedAt) }}</span>
        </div>

        <div v-if="editingId === user.id" class="edit-panel">
          <label>
            닉네임
            <input v-model.trim="editForm.nickname" type="text" />
          </label>
          <label>
            프로필 이미지 URL
            <input v-model.trim="editForm.profileImageUrl" type="url" placeholder="https://..." />
          </label>
          <label>
            권한
            <select v-model="editForm.role">
              <option value="ROLE_USER">사용자</option>
              <option value="ROLE_ADMIN">관리자</option>
            </select>
          </label>
          <label>
            상태
            <select v-model="editForm.status">
              <option value="ACTIVE">활성</option>
              <option value="SUSPENDED">정지</option>
              <option value="DELETED">탈퇴</option>
            </select>
          </label>
          <div class="actions">
            <button type="button" @click="saveUser(user)">저장</button>
            <button type="button" class="ghost" @click="cancelEdit">취소</button>
          </div>
        </div>

        <div v-else class="actions">
          <button type="button" @click="startEdit(user)">수정</button>
          <button v-if="user.status === 'ACTIVE'" type="button" class="warn" @click="quickStatus(user, 'SUSPENDED')">정지</button>
          <button v-if="user.status === 'SUSPENDED'" type="button" @click="quickStatus(user, 'ACTIVE')">복구</button>
          <button v-if="user.status !== 'DELETED'" type="button" class="danger" @click="deleteUser(user)">탈퇴 처리</button>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { adminUserApi } from '@/api/adminUserApi';

const router = useRouter();
const users = ref([]);
const loading = ref(true);
const message = ref('');
const messageType = ref('success');
const editingId = ref(null);
const filters = reactive({ role: 'ALL', status: 'ALL', keyword: '' });
const editForm = reactive({ nickname: '', role: 'ROLE_USER', status: 'ACTIVE', profileImageUrl: '' });

onMounted(loadUsers);

async function loadUsers() {
  loading.value = true;
  try {
    users.value = await adminUserApi.getUsers(filters);
  } catch (error) {
    setMessage(error.userMessage || '회원 목록을 불러올 수 없습니다.', 'error');
  } finally {
    loading.value = false;
  }
}

function startEdit(user) {
  editingId.value = user.id;
  editForm.nickname = user.nickname || '';
  editForm.role = user.role || 'ROLE_USER';
  editForm.status = user.status || 'ACTIVE';
  editForm.profileImageUrl = user.profileImageUrl || '';
}

function cancelEdit() {
  editingId.value = null;
}

async function saveUser(user) {
  if (!confirm('회원 정보를 수정할까요?')) return;
  try {
    await adminUserApi.updateUser(user.id, { ...editForm });
    setMessage('회원 정보가 수정되었습니다.');
    editingId.value = null;
    await loadUsers();
  } catch (error) {
    setMessage(error.userMessage || '회원 정보를 수정할 수 없습니다.', 'error');
  }
}

async function quickStatus(user, status) {
  const label = status === 'SUSPENDED' ? '정지' : '복구';
  if (!confirm(`이 회원을 ${label} 처리할까요?`)) return;
  try {
    await adminUserApi.updateUser(user.id, {
      nickname: user.nickname,
      role: user.role,
      status,
      profileImageUrl: user.profileImageUrl
    });
    setMessage('회원 상태가 변경되었습니다.');
    await loadUsers();
  } catch (error) {
    setMessage(error.userMessage || '회원 상태를 변경할 수 없습니다.', 'error');
  }
}

async function deleteUser(user) {
  if (!confirm('이 회원을 탈퇴 상태로 변경할까요?')) return;
  try {
    await adminUserApi.deleteUser(user.id);
    setMessage('회원 상태가 변경되었습니다.');
    await loadUsers();
  } catch (error) {
    setMessage(error.userMessage || '회원 상태를 변경할 수 없습니다.', 'error');
  }
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}

function roleLabel(role) {
  return role === 'ROLE_ADMIN' ? '관리자' : '사용자';
}

function formatDate(value) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}

function avatarText(user) {
  return (user.nickname || user.email || '?').slice(0, 1).toUpperCase();
}

function avatarStyle(user) {
  if (!user.profileImageUrl) return {};
  return { backgroundImage: `url(${user.profileImageUrl})`, color: 'transparent' };
}
</script>

<style scoped>
.admin-user-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 70px; background: radial-gradient(circle at 85% 5%, rgba(20,184,166,.24), transparent 34%), linear-gradient(155deg, #0f172a, #111827 56%, #0b0b0b); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.admin-hero { width: min(100%, 1020px); margin: 0 auto 16px; display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; padding: 20px; border: 1px solid rgba(148,163,184,.2); border-radius: 20px; background: rgba(15,23,42,.72); }
.admin-hero p { margin: 0 0 6px; color: #14b8a6; font-size: .75rem; font-weight: 900; letter-spacing: .14em; }
.admin-hero h1 { margin: 0; font-size: clamp(1.8rem, 8vw, 3.2rem); }
.admin-hero span { display: block; margin-top: 8px; color: #cbd5e1; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 8px; }
button { min-height: 42px; border: 0; border-radius: 12px; background: #0f766e; color: white; font: inherit; font-weight: 900; padding: 0 14px; }
.filters { width: min(100%, 1020px); margin: 0 auto 14px; display: grid; grid-template-columns: 130px 130px 1fr auto; gap: 10px; padding: 14px; border: 1px solid rgba(148,163,184,.18); border-radius: 16px; background: rgba(248,250,252,.06); }
.filters label, .edit-panel label { display: grid; gap: 6px; color: #cbd5e1; font-size: .82rem; font-weight: 800; }
select, input { min-height: 42px; box-sizing: border-box; width: 100%; border: 1px solid rgba(148,163,184,.3); border-radius: 12px; background: rgba(15,23,42,.8); color: #f8fafc; padding: 0 12px; font: inherit; }
.message { width: min(100%, 1020px); margin: 0 auto 14px; padding: 12px 14px; border-radius: 14px; background: rgba(22,163,74,.14); color: #bbf7d0; }
.message.error { background: rgba(220,38,38,.16); color: #fecaca; }
.empty { width: min(100%, 1020px); margin: 0 auto; padding: 22px; border: 1px dashed rgba(148,163,184,.28); border-radius: 16px; color: #cbd5e1; text-align: center; }
.user-grid { width: min(100%, 1020px); margin: 0 auto; display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 12px; }
.user-card { padding: 18px; border: 1px solid rgba(248,250,252,.12); border-radius: 18px; background: rgba(15,23,42,.72); box-shadow: 0 18px 40px rgba(0,0,0,.18); }
.user-card.suspended { border-color: rgba(245,158,11,.36); }
.user-card.deleted { opacity: .62; border-color: rgba(248,113,113,.28); }
.card-head { display: grid; grid-template-columns: 48px 1fr auto; align-items: center; gap: 10px; }
.avatar { width: 48px; height: 48px; border-radius: 16px; display: grid; place-items: center; background: linear-gradient(135deg, #0f766e, #0f172a); background-size: cover; background-position: center; font-weight: 900; }
.card-head strong { display: block; font-size: 1.05rem; }
.card-head span { display: block; margin-top: 4px; color: #cbd5e1; font-size: .86rem; overflow-wrap: anywhere; }
em { font-style: normal; color: #99f6e4; font-weight: 900; font-size: .78rem; }
.meta { display: flex; flex-wrap: wrap; gap: 7px; margin: 14px 0; }
.meta span { border: 1px solid rgba(20,184,166,.28); border-radius: 999px; padding: 5px 9px; color: #ccfbf1; font-size: .75rem; }
.edit-panel { display: grid; gap: 10px; margin-top: 12px; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.actions .ghost { background: #334155; }
.actions .warn { background: #b45309; }
.actions .danger { background: #b91c1c; }
@media (max-width: 680px) {
  .admin-hero { align-items: stretch; flex-direction: column; }
  .filters { grid-template-columns: 1fr; }
  .hero-actions { display: grid; grid-template-columns: 1fr 1fr; }
}
</style>