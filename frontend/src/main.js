import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 전역 상태(Pinia)와 화면 전환(router)은 mount 전에 등록해야 모든 view에서 사용할 수 있습니다.
app.use(createPinia())
app.use(router)

app.mount('#app')
