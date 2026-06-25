<template>
  <main class="post-write-page">
    <section class="panel">
      <button type="button" class="ghost" @click="router.push({ name: 'CommunityHub' })">← 목록</button>
      <p>NEW POST</p>
      <h1>게시글 작성</h1>
      <form @submit.prevent="submitPost">
        <label>
          지역
          <select v-model.number="form.regionId">
            <option v-for="area in regionAreas" :key="area.regionId" :value="area.regionId">{{ area.label }}</option>
          </select>
        </label>
        <label v-if="sessionStore.isAdmin" class="check-row">
          <input v-model="form.notice" type="checkbox" />
          공지사항으로 등록
        </label>
        <label>
          제목
          <input v-model.trim="form.title" type="text" maxlength="120" placeholder="제목을 입력하세요" />
        </label>
        <label>
          내용
          <textarea v-model.trim="form.content" rows="10" maxlength="3000" placeholder="내용을 입력하세요"></textarea>
        </label>
        <p v-if="message" class="message" :class="messageType">{{ message }}</p>
        <button type="submit">등록</button>
      </form>
    </section>
    <MainBottomNav />
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { regionAreas } from '@/constants/regionAreas';
import { regionCommunityApi } from '@/api/regionCommunityApi';
import { useSessionStore } from '@/stores/sessionStore';

const router = useRouter();
const sessionStore = useSessionStore();
const message = ref('');
const messageType = ref('success');
const form = reactive({
  regionId: regionAreas[0]?.regionId || 1,
  notice: false,
  title: '',
  content: ''
});

async function submitPost() {
  if (!form.title || !form.content) {
    setMessage('제목과 내용을 입력해 주세요.', 'error');
    return;
  }
  try {
    const created = await regionCommunityApi.createQuestion(form.regionId, {
      title: form.title,
      content: form.content,
      notice: sessionStore.isAdmin ? form.notice : false
    });
    router.push({ name: 'CommunityPostDetail', params: { regionId: form.regionId, questionId: created.id } });
  } catch (error) {
    setMessage(error.userMessage || '게시글을 등록하지 못했습니다.', 'error');
  }
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}
</script>

<style scoped>
.post-write-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 126px; background: #f8fbff; color: #172033; font-family: 'Noto Sans KR', system-ui, sans-serif; }
.panel { width: min(100%, 760px); box-sizing: border-box; margin: 0 auto; padding: 22px; border: 1px solid #d7e2ef; border-radius: 16px; background: #fff; }
.panel > p { margin: 18px 0 8px; color: #2563eb; font-weight: 900; letter-spacing: .14em; font-size: .75rem; }
h1 { margin: 0 0 18px; font-size: clamp(2rem, 6vw, 3rem); }
form { display: grid; gap: 14px; }
label { display: grid; gap: 7px; color: #334155; font-weight: 900; }
input, textarea, select { width: 100%; box-sizing: border-box; border: 1px solid #cbd5e1; border-radius: 10px; font: inherit; padding: 11px 12px; }
textarea { resize: vertical; line-height: 1.55; }
button { min-height: 42px; border: 0; border-radius: 10px; background: #2563eb; color: #fff; font: inherit; font-weight: 900; padding: 0 14px; cursor: pointer; }
.ghost { border: 1px solid #cbd5e1; background: #fff; color: #334155; }
.check-row { grid-template-columns: auto 1fr; align-items: center; justify-content: start; }
.check-row input { width: 18px; }
.message { margin: 0; padding: 12px; border-radius: 10px; background: #ecfdf5; color: #047857; }
.message.error { background: #fff1f2; color: #be123c; }
</style>
