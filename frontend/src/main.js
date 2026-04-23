import { createApp } from 'vue'
import { createPinia } from 'pinia' // 📌 Pinia 생성 함수 가져오기
import App from './App.vue'
import router from './router'       // 📌 우리가 만든 라우터 가져오기 (index.js는 생략 가능)

const app = createApp(App)

// 앱에 Pinia와 Router를 장착합니다! (순서 주의: mount 보다 먼저 해야 합니다)
app.use(createPinia())
app.use(router)

app.mount('#app')