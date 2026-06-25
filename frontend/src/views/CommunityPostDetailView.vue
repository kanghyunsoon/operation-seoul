<template>
  <main class="detail-page">
    <section v-if="loading" class="panel state">게시글을 불러오는 중입니다.</section>
    <section v-else-if="error" class="panel state error">{{ error }}</section>
    <section v-else class="panel">
      <div class="top-actions">
        <button type="button" class="ghost" @click="router.push({ name: 'CommunityHub' })">← 목록</button>
        <div v-if="question.mine && !editingPost" class="owner-actions">
          <button type="button" class="ghost" @click="startPostEdit">수정</button>
          <button type="button" class="danger compact" @click="deletePost">삭제</button>
        </div>
      </div>
      <div v-if="!editingPost" class="post-head">
        <span>{{ question.notice ? '공지사항' : regionName }}</span>
        <h1>{{ question.title }}</h1>
        <div class="author-menu">
          <button type="button" class="author" @click="authorMenuOpen = !authorMenuOpen">{{ question.authorNickname || '유저' }}</button>
          <div v-if="authorMenuOpen" class="author-popover">
            <button type="button" @click="openAuthorFeed">유저 정보 보기</button>
            <button v-if="question.userId !== sessionStore.userId" type="button" @click="toggleAuthorFollow">
              {{ question.authorFollowing ? '팔로우 해제' : '팔로우 하기' }}
            </button>
          </div>
        </div>
      </div>
      <form v-else class="edit-post-form" @submit.prevent="savePostEdit">
        <label>
          제목
          <input v-model.trim="postEditForm.title" type="text" maxlength="120" />
        </label>
        <label>
          내용
          <textarea v-model.trim="postEditForm.content" rows="8" maxlength="3000"></textarea>
        </label>
        <label v-if="sessionStore.isAdmin" class="check-row">
          <input v-model="postEditForm.notice" type="checkbox" />
          공지사항
        </label>
        <div class="actions">
          <button type="submit">수정 저장</button>
          <button type="button" class="ghost" @click="cancelPostEdit">취소</button>
        </div>
      </form>
      <p v-if="!editingPost" class="content">{{ question.content }}</p>
      <div v-if="!editingPost" class="actions">
        <button type="button" @click="toggleLike">추천 {{ question.likeCount || 0 }}</button>
      </div>

      <section class="comments">
        <h2>댓글 {{ (question.answers || []).length }}</h2>
        <article v-for="answer in question.answers || []" :key="answer.id">
          <strong>{{ answer.authorNickname || '유저' }}</strong>
          <template v-if="editingAnswerId === answer.id">
            <form class="answer-edit-form" @submit.prevent="saveAnswerEdit(answer)">
              <input v-model.trim="answerEditDraft" type="text" />
              <button type="submit">저장</button>
              <button type="button" class="ghost" @click="cancelAnswerEdit">취소</button>
            </form>
          </template>
          <template v-else>
            <p>{{ answer.content }}</p>
            <div v-if="answer.mine" class="comment-actions">
              <button type="button" class="ghost" @click="startAnswerEdit(answer)">수정</button>
              <button type="button" class="danger compact" @click="deleteAnswer(answer)">삭제</button>
            </div>
          </template>
        </article>
        <form @submit.prevent="submitAnswer">
          <input v-model.trim="answerDraft" type="text" placeholder="댓글을 입력하세요" />
          <button type="submit">등록</button>
        </form>
      </section>
      <p v-if="message" class="message" :class="messageType">{{ message }}</p>
    </section>
    <MainBottomNav />
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import MainBottomNav from '@/components/MainBottomNav.vue';
import { regionCommunityApi } from '@/api/regionCommunityApi';
import { userApi } from '@/api/userApi';
import { regionAreas } from '@/constants/regionAreas';
import { useSessionStore } from '@/stores/sessionStore';

const route = useRoute();
const router = useRouter();
const sessionStore = useSessionStore();
const loading = ref(true);
const error = ref('');
const question = ref({});
const answerDraft = ref('');
const message = ref('');
const messageType = ref('success');
const authorMenuOpen = ref(false);
const editingPost = ref(false);
const editingAnswerId = ref(null);
const answerEditDraft = ref('');
const postEditForm = reactive({
  title: '',
  content: '',
  notice: false
});

const regionId = computed(() => Number(question.value.regionId || route.params.regionId));
const questionId = computed(() => Number(route.params.questionId));
const regionName = computed(() => regionAreas.find((area) => area.regionId === regionId.value)?.label || '커뮤니티');

onMounted(loadQuestion);

async function loadQuestion() {
  loading.value = true;
  error.value = '';
  try {
    question.value = await regionCommunityApi.getQuestion(regionId.value, questionId.value);
    if (editingPost.value) hydratePostEditForm();
  } catch (err) {
    error.value = err.userMessage || '게시글을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function startPostEdit() {
  hydratePostEditForm();
  editingPost.value = true;
}

function hydratePostEditForm() {
  postEditForm.title = question.value.title || '';
  postEditForm.content = question.value.content || '';
  postEditForm.notice = Boolean(question.value.notice);
}

function cancelPostEdit() {
  editingPost.value = false;
}

async function savePostEdit() {
  if (!postEditForm.title || !postEditForm.content) {
    setMessage('제목과 내용을 입력해 주세요.', 'error');
    return;
  }
  try {
    await regionCommunityApi.updateQuestion(regionId.value, questionId.value, {
      title: postEditForm.title,
      content: postEditForm.content,
      notice: sessionStore.isAdmin ? postEditForm.notice : Boolean(question.value.notice)
    });
    editingPost.value = false;
    setMessage('게시글이 수정되었습니다.');
    await loadQuestion();
  } catch (err) {
    setMessage(err.userMessage || '게시글을 수정하지 못했습니다.', 'error');
  }
}

async function deletePost() {
  if (!confirm('게시글을 삭제할까요?')) return;
  try {
    await regionCommunityApi.deleteQuestion(regionId.value, questionId.value);
    router.push({ name: 'CommunityHub' });
  } catch (err) {
    setMessage(err.userMessage || '게시글을 삭제하지 못했습니다.', 'error');
  }
}

async function toggleLike() {
  try {
    await regionCommunityApi.toggleQuestionLike(regionId.value, questionId.value);
    await loadQuestion();
  } catch (err) {
    setMessage(err.userMessage || '추천을 처리하지 못했습니다.', 'error');
  }
}

async function submitAnswer() {
  if (!answerDraft.value) return;
  try {
    await regionCommunityApi.createAnswer(regionId.value, questionId.value, { content: answerDraft.value });
    answerDraft.value = '';
    await loadQuestion();
  } catch (err) {
    setMessage(err.userMessage || '댓글을 등록하지 못했습니다.', 'error');
  }
}

async function deleteAnswer(answer) {
  if (!confirm('댓글을 삭제할까요?')) return;
  try {
    await regionCommunityApi.deleteAnswer(regionId.value, questionId.value, answer.id);
    await loadQuestion();
  } catch (err) {
    setMessage(err.userMessage || '댓글을 삭제하지 못했습니다.', 'error');
  }
}

function startAnswerEdit(answer) {
  editingAnswerId.value = answer.id;
  answerEditDraft.value = answer.content || '';
}

function cancelAnswerEdit() {
  editingAnswerId.value = null;
  answerEditDraft.value = '';
}

async function saveAnswerEdit(answer) {
  if (!answerEditDraft.value) {
    setMessage('댓글 내용을 입력해 주세요.', 'error');
    return;
  }
  try {
    await regionCommunityApi.updateAnswer(regionId.value, questionId.value, answer.id, { content: answerEditDraft.value });
    cancelAnswerEdit();
    setMessage('댓글이 수정되었습니다.');
    await loadQuestion();
  } catch (err) {
    setMessage(err.userMessage || '댓글을 수정하지 못했습니다.', 'error');
  }
}

async function toggleAuthorFollow() {
  if (!question.value.userId || question.value.userId === sessionStore.userId) return;
  try {
    if (question.value.authorFollowing) {
      await userApi.unfollowUser(question.value.userId);
      setMessage('작성자 팔로우를 해제했습니다.');
    } else {
      await userApi.followUser(question.value.userId);
      setMessage('작성자를 팔로우했습니다.');
    }
    await loadQuestion();
  } catch (err) {
    setMessage(err.userMessage || '팔로우 상태를 변경하지 못했습니다.', 'error');
  }
}

function openAuthorFeed() {
  if (!question.value.userId) return;
  router.push({ name: 'UserFeed', params: { userId: question.value.userId } });
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}
</script>

<style scoped>
.detail-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 126px; background: #f8fbff; color: #172033; font-family: 'Noto Sans KR', system-ui, sans-serif; }
.panel { width: min(100%, 820px); box-sizing: border-box; margin: 0 auto; padding: 22px; border: 1px solid #d7e2ef; border-radius: 16px; background: #fff; }
button { min-height: 40px; border: 0; border-radius: 10px; background: #2563eb; color: #fff; font: inherit; font-weight: 900; padding: 0 12px; cursor: pointer; }
.ghost { border: 1px solid #cbd5e1; background: #fff; color: #334155; }
.top-actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.owner-actions { display: flex; gap: 8px; }
.post-head { margin-top: 18px; }
.post-head span { color: #2563eb; font-weight: 900; }
h1 { margin: 8px 0; font-size: clamp(1.8rem, 6vw, 3rem); }
.author-menu { position: relative; display: inline-block; }
.author { background: #eff6ff; color: #1d4ed8; }
.author-popover { position: absolute; z-index: 5; top: calc(100% + 8px); left: 0; display: grid; gap: 6px; min-width: 160px; padding: 8px; border: 1px solid #cbd5e1; border-radius: 10px; background: #fff; box-shadow: 0 18px 40px rgba(15,23,42,.16); }
.author-popover button { background: #fff; color: #172033; text-align: left; }
.content { padding: 18px 0; color: #334155; line-height: 1.7; white-space: pre-wrap; }
.actions, .comment-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.edit-post-form { display: grid; gap: 12px; margin-top: 18px; padding: 14px; border: 1px solid #e2e8f0; border-radius: 12px; background: #f8fafc; }
.edit-post-form label { display: grid; gap: 7px; color: #334155; font-weight: 900; }
.edit-post-form textarea { width: 100%; box-sizing: border-box; border: 1px solid #cbd5e1; border-radius: 10px; padding: 11px 12px; font: inherit; resize: vertical; line-height: 1.55; }
.check-row { grid-template-columns: auto 1fr !important; align-items: center; justify-content: start; }
.check-row input { width: 18px; min-height: 18px; }
.comments { margin-top: 22px; padding-top: 18px; border-top: 1px solid #d7e2ef; }
.comments h2 { margin: 0 0 12px; }
.comments article { padding: 12px; border: 1px solid #e2e8f0; border-radius: 10px; margin-bottom: 8px; }
.comments article p { margin: 6px 0 0; color: #475569; }
.comments form { display: grid; grid-template-columns: 1fr auto; gap: 8px; margin-top: 12px; }
.answer-edit-form { grid-template-columns: 1fr auto auto !important; }
input { min-height: 42px; box-sizing: border-box; border: 1px solid #cbd5e1; border-radius: 10px; padding: 0 12px; font: inherit; }
.danger { margin-top: 8px; background: #be123c; }
.compact { margin-top: 0; }
.message, .state { margin-top: 12px; padding: 12px; border-radius: 10px; background: #ecfdf5; color: #047857; }
.message.error, .state.error { background: #fff1f2; color: #be123c; }
@media (max-width: 560px) {
  .comments form { grid-template-columns: 1fr; }
}
</style>
