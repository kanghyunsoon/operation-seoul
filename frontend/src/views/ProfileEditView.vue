<template>
  <main class="profile-edit-page">
    <header class="hero">
      <button type="button" class="back" @click="router.push({ name: 'MyPage' })">뒤로가기</button>
      <p>PROFILE SETTINGS</p>
      <h1>내 정보 수정</h1>
      <span>닉네임, 프로필 사진, 상태메시지, 비밀번호를 관리합니다.</span>
    </header>

    <p v-if="message" class="toast" :class="messageType">{{ message }}</p>

    <section class="settings-grid">
      <form class="panel" @submit.prevent="saveProfile">
        <div class="panel-head">
          <div>
            <p>ACCOUNT</p>
            <h2>프로필 정보</h2>
          </div>
          <img :src="profilePreview" alt="프로필 미리보기" />
        </div>

        <label>
          <span>이메일</span>
          <input v-model="email" type="email" disabled />
        </label>
        <label>
          <span>닉네임</span>
          <input v-model.trim="profileForm.nickname" type="text" maxlength="30" autocomplete="nickname" />
        </label>
        <label>
          <span>상태메시지</span>
          <textarea
            v-model.trim="profileForm.statusMessage"
            maxlength="120"
            rows="3"
            placeholder="프로필에 보여줄 짧은 상태메시지를 입력하세요."
          />
          <small>{{ statusMessageLength }}/120</small>
        </label>
        <label class="check-row">
          <input v-model="profileForm.profilePublic" type="checkbox" />
          <span>프로필 정보 공개</span>
        </label>
        <label>
          <span>프로필 사진 URL</span>
          <input v-model.trim="profileForm.profileImageUrl" type="url" placeholder="https://..." />
        </label>

        <div class="photo-tools">
          <label class="file-button">
            사진 파일 선택
            <input ref="fileInput" type="file" accept="image/*" @change="handleProfileImageFile" />
          </label>
          <button type="button" class="ghost" @click="clearProfileImage">사진 제거</button>
        </div>
        <p v-if="profileImageFileName" class="file-name">{{ profileImageFileName }}</p>

        <div class="actions">
          <button type="button" class="ghost" @click="resetProfile">초기화</button>
          <button type="submit" :disabled="profileSaving">저장</button>
        </div>
      </form>

      <form class="panel" @submit.prevent="changePassword">
        <div class="panel-head compact">
          <div>
            <p>SECURITY</p>
            <h2>비밀번호 변경</h2>
          </div>
        </div>

        <label>
          <span>현재 비밀번호</span>
          <input v-model="passwordForm.currentPassword" type="password" autocomplete="current-password" />
        </label>
        <label>
          <span>새 비밀번호</span>
          <input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" />
        </label>
        <label>
          <span>새 비밀번호 확인</span>
          <input v-model="passwordConfirm" type="password" autocomplete="new-password" />
        </label>

        <div class="actions">
          <button type="button" class="ghost" @click="resetPassword">초기화</button>
          <button type="submit" :disabled="passwordSaving">비밀번호 변경</button>
        </div>
      </form>
    </section>

    <MainBottomNav />
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { useSessionStore } from '@/stores/sessionStore';

const MAX_PROFILE_IMAGE_BYTES = 700 * 1024;

const router = useRouter();
const sessionStore = useSessionStore();
const message = ref('');
const messageType = ref('success');
const email = ref('');
const profileSaving = ref(false);
const passwordSaving = ref(false);
const passwordConfirm = ref('');
const profileImageFileName = ref('');
const fileInput = ref(null);
const defaultProfile = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2264%22 height=%2264%22 viewBox=%220 0 64 64%22%3E%3Crect width=%2264%22 height=%2264%22 rx=%2218%22 fill=%22%23334155%22/%3E%3Ccircle cx=%2232%22 cy=%2225%22 r=%2211%22 fill=%22%23cbd5e1%22/%3E%3Cpath d=%22M14 56c3-12 12-18 18-18s15 6 18 18%22 fill=%22%23cbd5e1%22/%3E%3C/svg%3E';

const profileForm = reactive({
  nickname: '',
  profileImageUrl: '',
  statusMessage: '',
  profilePublic: true
});
const passwordForm = reactive({
  currentPassword: '',
  newPassword: ''
});

const profilePreview = computed(() => profileForm.profileImageUrl || defaultProfile);
const statusMessageLength = computed(() => profileForm.statusMessage.length);

onMounted(async () => {
  const user = await sessionStore.ensureInitialized();
  hydrate(user || sessionStore.currentUser);
});

function hydrate(user) {
  email.value = user?.email || '';
  profileForm.nickname = user?.nickname || '';
  profileForm.profileImageUrl = user?.profileImageUrl || '';
  profileForm.statusMessage = user?.statusMessage || '';
  profileForm.profilePublic = user?.profilePublic !== false;
  profileImageFileName.value = '';
  if (fileInput.value) fileInput.value.value = '';
}

function resetProfile() {
  hydrate(sessionStore.currentUser);
  setMessage('프로필 입력값을 되돌렸습니다.');
}

function clearProfileImage() {
  profileForm.profileImageUrl = '';
  profileImageFileName.value = '';
  if (fileInput.value) fileInput.value.value = '';
}

function handleProfileImageFile(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  if (!file.type.startsWith('image/')) {
    setMessage('이미지 파일만 선택할 수 있습니다.', 'error');
    event.target.value = '';
    return;
  }
  if (file.size > MAX_PROFILE_IMAGE_BYTES) {
    setMessage('프로필 사진은 700KB 이하 이미지를 사용해 주세요.', 'error');
    event.target.value = '';
    return;
  }

  const reader = new FileReader();
  reader.onload = () => {
    profileForm.profileImageUrl = String(reader.result || '');
    profileImageFileName.value = file.name;
  };
  reader.onerror = () => {
    setMessage('사진 파일을 읽지 못했습니다.', 'error');
    event.target.value = '';
  };
  reader.readAsDataURL(file);
}

async function saveProfile() {
  if (!profileForm.nickname) {
    setMessage('닉네임을 입력하세요.', 'error');
    return;
  }
  profileSaving.value = true;
  try {
    const { userApi } = await import('@/api/userApi');
    const updated = await userApi.updateMe({
      nickname: profileForm.nickname,
      profileImageUrl: profileForm.profileImageUrl,
      statusMessage: profileForm.statusMessage,
      profilePublic: profileForm.profilePublic
    });
    sessionStore.setCurrentUser(updated);
    hydrate(updated);
    setMessage('내 정보가 수정되었습니다.');
  } catch (err) {
    setMessage(err.userMessage || '내 정보를 수정하지 못했습니다.', 'error');
  } finally {
    profileSaving.value = false;
  }
}

function resetPassword() {
  passwordForm.currentPassword = '';
  passwordForm.newPassword = '';
  passwordConfirm.value = '';
  setMessage('비밀번호 입력값을 비웠습니다.');
}

async function changePassword() {
  if (!passwordForm.currentPassword || !passwordForm.newPassword) {
    setMessage('현재 비밀번호와 새 비밀번호를 모두 입력하세요.', 'error');
    return;
  }
  if (passwordForm.newPassword !== passwordConfirm.value) {
    setMessage('새 비밀번호 확인이 일치하지 않습니다.', 'error');
    return;
  }
  passwordSaving.value = true;
  try {
    const { userApi } = await import('@/api/userApi');
    await userApi.changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword
    });
    sessionStore.logout();
    router.push({ name: 'Intro' });
  } catch (err) {
    setMessage(err.userMessage || '비밀번호를 변경하지 못했습니다.', 'error');
  } finally {
    passwordSaving.value = false;
  }
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}
</script>

<style scoped>
.profile-edit-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 126px; background: radial-gradient(circle at 80% 0%, rgba(245,158,11,.2), transparent 34%), linear-gradient(160deg, #0f172a, #111827 60%, #050505); color: #f8fafc; font-family: Georgia, 'Noto Sans KR', serif; }
.hero, .settings-grid, .toast { width: min(100%, 880px); box-sizing: border-box; margin-left: auto; margin-right: auto; }
.hero { margin-bottom: 18px; padding: 22px; border: 1px solid rgba(245,158,11,.24); border-radius: 20px; background: rgba(15,23,42,.58); }
.back { min-height: 38px; border: 1px solid rgba(148,163,184,.28); border-radius: 999px; background: transparent; color: #cbd5e1; padding: 0 14px; }
.hero p, .panel p { margin: 18px 0 8px; color: #f59e0b; font-weight: 900; letter-spacing: .16em; font-size: .78rem; }
.panel p { margin-top: 0; }
h1 { margin: 0; font-size: clamp(2rem, 9vw, 3.5rem); line-height: 1; }
.hero span { display: block; margin-top: 10px; color: #cbd5e1; }
.settings-grid { display: grid; grid-template-columns: 1.1fr .9fr; gap: 14px; }
.panel { padding: 18px; border: 1px solid rgba(248,250,252,.13); border-radius: 18px; background: rgba(15,23,42,.66); box-shadow: 0 20px 52px rgba(0,0,0,.2); }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 14px; }
.panel-head.compact { display: block; }
.panel-head img { width: 72px; height: 72px; border-radius: 18px; object-fit: cover; background: #334155; border: 1px solid rgba(248,250,252,.14); }
h2 { margin: 0; font-size: 1.35rem; }
label { display: grid; gap: 7px; margin-top: 12px; }
label span { color: #cbd5e1; font-size: .86rem; font-weight: 900; }
.check-row { grid-template-columns: auto 1fr; align-items: center; gap: 10px; }
.check-row input { width: 18px; min-height: 18px; }
input, textarea { width: 100%; box-sizing: border-box; border: 1px solid rgba(148,163,184,.28); border-radius: 12px; background: rgba(2,6,23,.42); color: #f8fafc; font: inherit; padding: 0 12px; }
input { min-height: 42px; }
textarea { min-height: 88px; padding-top: 11px; resize: vertical; line-height: 1.5; }
input:disabled { opacity: .72; }
small { justify-self: end; color: #94a3b8; font-size: .78rem; }
.photo-tools { display: flex; gap: 8px; margin-top: 12px; align-items: center; flex-wrap: wrap; }
.file-button { display: inline-flex; align-items: center; justify-content: center; min-height: 40px; margin-top: 0; border-radius: 12px; background: #475569; color: #fff; font-weight: 900; padding: 0 14px; cursor: pointer; }
.file-button input { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.file-name { margin: 8px 0 0; color: #cbd5e1; letter-spacing: 0; font-size: .84rem; word-break: break-all; }
.actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
button { min-height: 40px; border: 0; border-radius: 12px; background: #b45309; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; cursor: pointer; }
button:disabled { opacity: .6; cursor: default; }
.ghost { background: #334155; }
.toast { margin-bottom: 12px; padding: 16px; border: 1px solid rgba(148,163,184,.34); border-radius: 16px; background: rgba(22,101,52,.18); color: #bbf7d0; text-align: center; }
.toast.error { color: #fecaca; background: rgba(127,29,29,.18); }
@media (max-width: 720px) {
  .settings-grid { grid-template-columns: 1fr; }
  .actions { display: grid; grid-template-columns: 1fr; }
  .photo-tools { display: grid; grid-template-columns: 1fr; }
}
</style>
