<template>
  <div class="scanner-container">
    <div class="scanner-overlay">
      <div class="scan-frame">
        <div class="radar-scan-line"></div>

        <div class="corner top-left"></div>
        <div class="corner top-right"></div>
        <div class="corner bottom-left"></div>
        <div class="corner bottom-right"></div>
      </div>
      <p class="scan-guide">단서를 중앙에 맞추고 캡처하십시오.</p>
    </div>

    <div class="controls">
      <input
          type="file"
          accept="image/*"
          capture="environment"
          ref="fileInput"
          @change="processFile"
          style="display: none;"
      />

      <button @click="triggerFileInput" class="capture-btn">
        [ 📷 단서 스캔 (CAPTURE) ]
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const emit = defineEmits(['capture']);
const fileInput = ref(null);

const triggerFileInput = () => {
  fileInput.value.click();
};

const processFile = (event) => {
  const file = event.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = (e) => {
    emit('capture', e.target.result);
  };
  reader.readAsDataURL(file);
};
</script>

<style scoped>
.scanner-container {
  position: relative; width: 100%; height: 100vh;
  background-color: rgba(0, 0, 0, 0.9);
  display: flex; flex-direction: column;
}

.scanner-overlay {
  flex: 1; display: flex; flex-direction: column;
  justify-content: center; align-items: center;
}

/* 스캔 영역 프레임 */
.scan-frame {
  position: relative; width: 85%; height: 60%;
  border: 1px dashed rgba(0, 255, 204, 0.3);
  margin-bottom: 20px; overflow: hidden; /* 스캔 라인이 밖으로 안 나가게 */
}

/* 💡 부활한 레이더 스캔 애니메이션 */
.radar-scan-line {
  position: absolute; top: 0; left: 0;
  width: 100%; height: 4px;
  background-color: #00ffcc;
  box-shadow: 0 0 20px 5px rgba(0, 255, 204, 0.5);
  animation: scan-down 2.5s ease-in-out infinite;
}

@keyframes scan-down {
  0% { top: -10px; opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { top: 100%; opacity: 0; }
}

/* 모서리 디자인 */
.corner { position: absolute; width: 30px; height: 30px; border-color: #00ffcc; border-style: solid; }
.top-left { top: -2px; left: -2px; border-width: 3px 0 0 3px; }
.top-right { top: -2px; right: -2px; border-width: 3px 3px 0 0; }
.bottom-left { bottom: -2px; left: -2px; border-width: 0 0 3px 3px; }
.bottom-right { bottom: -2px; right: -2px; border-width: 0 3px 3px 0; }

.scan-guide {
  color: #00ffcc; font-family: 'Share Tech Mono', monospace;
  animation: blink 1.5s infinite; text-shadow: 0 0 8px rgba(0,255,204,0.5);
}
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }

.controls { padding: 30px; display: flex; justify-content: center; }

.capture-btn {
  background: rgba(0, 0, 0, 0.7); color: #00ffcc;
  border: 2px solid #00ffcc; padding: 15px 40px;
  font-size: 1.2rem; font-weight: bold; font-family: inherit;
  border-radius: 8px; cursor: pointer; transition: 0.2s;
}
.capture-btn:hover { background: #00ffcc; color: #000; box-shadow: 0 0 20px #00ffcc; }
</style>