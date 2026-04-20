<template>
  <div class="briefing-container">
    <div class="monitor-overlay"></div>
    <div class="text-area">
      <span class="cmd-label">COMMANDER:</span>
      <p class="typewriter">{{ displayedText }}<span class="cursor">_</span></p>
    </div>

    <button v-if="isFinished" @click="goToMap" class="mission-btn">
      확인. 작전 지역으로 이동한다.
    </button>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';

const fullText = "작전 지역 '중명전' 진입을 확인했다. 이곳은 고종 황제가 을사늑약의 부당함을 알리기 위해 헤이그 특사를 파견했던 비밀 거점이다. 요원, 현판의 암호를 해독하고 작전의 실마리를 찾아라.";
const displayedText = ref('');
const isFinished = ref(false);
const router = useRouter();

const typeText = () => {
  let i = 0;
  const interval = setInterval(() => {
    if (i < fullText.length) {
      displayedText.value += fullText[i];
      i++;
    } else {
      clearInterval(interval);
      isFinished.value = true;
    }
  }, 50);
};

onMounted(() => typeText());
const goToMap = () => router.push('/map');
</script>

<style scoped>
.briefing-container { background: #000; height: 100vh; padding: 40px; font-family: 'Courier New', monospace; color: #00ffcc; }
.cmd-label { font-weight: bold; display: block; margin-bottom: 10px; color: #ff4444; }
.typewriter { line-height: 1.8; font-size: 1.2rem; }
.cursor { animation: blink 1s infinite; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
.mission-btn { margin-top: 50px; background: none; border: 1px solid #00ffcc; color: #00ffcc; padding: 15px 30px; cursor: pointer; transition: 0.3s; }
.mission-btn:hover { background: #00ffcc; color: #000; }
</style>