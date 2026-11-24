<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Grid, Lock, User } from '@element-plus/icons-vue'
import { useSessionStore } from '../stores/session'
import { ApiError } from '../api/http'

const router = useRouter()
const session = useSessionStore()
const loading = ref(false)
const errorMessage = ref('')
const form = reactive({ username: '', displayName: '', password: '' })

const columns = [
  { title: '待处理', tasks: ['整理迭代需求', '补充验收标准'] },
  { title: '进行中', tasks: ['完善项目看板', '优化任务详情'] },
  { title: '已完成', tasks: ['确定迭代目标', '建立项目空间'] },
]

async function submit() {
  if (loading.value) return
  errorMessage.value = ''
  if (!/^[a-zA-Z0-9_]{3,32}$/.test(form.username)) {
    errorMessage.value = '用户名须为 3–32 位字母、数字或下划线'
    return
  }
  if (form.password.length < 8) {
    errorMessage.value = '密码至少需要 8 位'
    return
  }

  loading.value = true
  try {
    await session.register(form.username, form.password, form.displayName)
    await router.push('/join')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '注册失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-page">
    <header class="register-header">
      <router-link class="brand" to="/login">
        <span class="brand-icon"><el-icon aria-hidden="true"><Grid /></el-icon></span>
        Team-Work
      </router-link>
    </header>

    <main class="register-card">
      <section class="form-panel" aria-labelledby="register-title">
        <h1 id="register-title">创建 Team-Work 账号</h1>
        <p class="intro">注册后即可创建或加入团队，和同事一起推进项目。</p>

        <form class="register-form" @submit.prevent="submit">
          <div class="field">
            <label for="register-username">用户名 <span aria-hidden="true">*</span></label>
            <el-input id="register-username" v-model="form.username" :prefix-icon="User"
              autocomplete="username" placeholder="3–32 位字母、数字或下划线" maxlength="32" required />
          </div>
          <div class="field">
            <label for="register-display-name">显示名</label>
            <el-input id="register-display-name" v-model="form.displayName"
              autocomplete="nickname" placeholder="输入团队成员看到的名字" />
          </div>
          <div class="field">
            <label for="register-password">密码 <span aria-hidden="true">*</span></label>
            <el-input id="register-password" v-model="form.password" :prefix-icon="Lock"
              type="password" show-password autocomplete="new-password" placeholder="至少 8 位" required />
          </div>

          <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
          <el-button class="submit-button" type="primary" native-type="submit" :loading="loading">
            注册并登录
            <el-icon v-if="!loading" aria-hidden="true"><ArrowRight /></el-icon>
          </el-button>
        </form>

        <p class="sign-in">已有账号？ <router-link to="/login">立即登录</router-link></p>
      </section>

      <aside class="preview" aria-labelledby="preview-title">
        <h2 id="preview-title">让团队看清每项任务的进度</h2>
        <p>在同一张看板上规划迭代、跟进任务和查看交付进度。</p>

        <div class="board">
          <div class="board-heading">
            <el-icon aria-hidden="true"><Grid /></el-icon>
            <strong>迭代任务看板</strong>
            <span>示例</span>
          </div>
          <div class="board-columns">
            <section v-for="column in columns" :key="column.title" class="board-column">
              <h3><i aria-hidden="true"></i>{{ column.title }}</h3>
              <ul>
                <li v-for="task in column.tasks" :key="task">{{ task }}</li>
              </ul>
            </section>
          </div>
        </div>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  display: grid;
  grid-template-rows: auto 1fr;
  color: #172036;
  background:
    radial-gradient(ellipse at 22% 22%, #e9e8ff, transparent 48%),
    radial-gradient(ellipse at 82% 77%, #e2efff, transparent 47%),
    #f7f9fc;
}
.register-header {
  padding: 20px clamp(20px, 4vw, 56px);
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #172036;
  font-size: 18px;
  font-weight: 700;
  text-decoration: none;
}
.brand-icon {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 10px;
  background: #4847cf;
  color: white;
}
.register-card {
  box-sizing: border-box;
  align-self: center;
  justify-self: center;
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: min(1120px, calc(100% - 48px));
  margin: 16px 0 52px;
  overflow: hidden;
  border: 1px solid #fff;
  border-radius: 24px;
  background: #fff;
  box-shadow: 0 24px 64px #352b7620;
}
.form-panel,
.preview {
  min-width: 0;
  padding: clamp(26px, 4vw, 56px);
}
h1, h2, h3, p { margin: 0; }
h1 {
  font-size: clamp(24px, 3vw, 34px);
  line-height: 1.25;
}
.intro,
.preview > p {
  margin-top: 10px;
  color: #515d75;
  font-size: 14px;
  line-height: 1.65;
}
.register-form {
  display: grid;
  gap: 17px;
  margin-top: 28px;
}
.field {
  display: grid;
  gap: 7px;
}
.field label {
  font-size: 14px;
  font-weight: 600;
}
.field label span,
.form-error { color: #a92336; }
.field :deep(.el-input) {
  --el-input-height: 44px;
  --el-input-border-radius: 9px;
  --el-input-focus-border-color: #4847cf;
  --el-input-placeholder-color: #68738a;
}
.submit-button {
  width: 100%;
  min-height: 46px;
  border-radius: 9px;
  font-weight: 600;
  --el-button-bg-color: #4847cf;
  --el-button-border-color: #4847cf;
  --el-button-hover-bg-color: #3b39b8;
  --el-button-hover-border-color: #3b39b8;
}
.submit-button .el-icon { margin-left: 8px; }
.form-error, .sign-in { font-size: 14px; }
.sign-in {
  margin-top: 20px;
  color: #515d75;
  text-align: center;
}
.sign-in a { color: #4142bd; font-weight: 600; }
.preview {
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: linear-gradient(145deg, #eeedff, #e9f2ff);
}
.preview h2 { font-size: clamp(20px, 2vw, 26px); }
.board {
  margin-top: 24px;
  padding: 16px;
  border: 1px solid #fff;
  border-radius: 16px;
  background: #ffffffc9;
  box-shadow: 0 12px 32px #352b7614;
}
.board-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-size: 14px;
}
.board-heading .el-icon { color: #4847cf; }
.board-heading span {
  margin-left: auto;
  color: #515d75;
  font-size: 12px;
}
.board-columns {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.board-column {
  padding: 8px;
  border-radius: 9px;
  background: #eff2f9;
}
.board-column h3 {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
}
.board-column i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #6e7891;
}
.board-column:nth-child(2) i { background: #5855d9; }
.board-column:nth-child(3) i { background: #25815d; }
.board-column ul {
  display: grid;
  gap: 6px;
  margin: 9px 0 0;
  padding: 0;
  list-style: none;
}
.board-column li {
  padding: 8px 5px;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  overflow-wrap: anywhere;
}
@media (max-width: 760px) {
  .register-card {
    grid-template-columns: minmax(0, 1fr);
    width: min(480px, calc(100% - 32px));
    margin: 12px 0 32px;
  }
  .preview { display: none; }
}
</style>
