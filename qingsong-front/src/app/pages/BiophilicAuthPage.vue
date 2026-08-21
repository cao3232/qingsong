<template>
  <main class="auth-page" :class="feedback.type ? `is-${feedback.type}` : ''">
    <div class="auth-background" aria-hidden="true">
      <span class="bg-orb orb-green"></span>
      <span class="bg-orb orb-blue"></span>
      <span class="bg-orb orb-orange"></span>
      <span class="pine-line pine-line-one"></span>
      <span class="pine-line pine-line-two"></span>
    </div>

    <section class="brand-panel" aria-label="青松登录介绍">
      <router-link class="brand-link" to="/">
        <svg class="brand-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 2L4 8L12 14L20 8L12 2Z" fill="currentColor" opacity="0.9" />
          <path d="M12 8L4 14L12 20L20 14L12 8Z" fill="currentColor" opacity="0.7" />
          <path d="M12 14L6 18.5L12 23L18 18.5L12 14Z" fill="currentColor" opacity="0.5" />
        </svg>
        <div>
          <strong>青松</strong>
          <span>让人生更轻松</span>
        </div>
      </router-link>

      <p class="eyebrow">Account Gateway</p>
      <h1>进入 AI 工作台</h1>

      <div class="feature-list">
        <span>让人生更轻松</span>
        <span>账号密码登录</span>
      </div>
    </section>

    <section class="auth-card" aria-label="账号登录注册表单">
      <div class="mode-tabs" role="tablist" aria-label="登录注册切换">
        <button type="button" role="tab" :aria-selected="mode === 'login'" :class="{ active: mode === 'login' }"
          @click="switchMode('login')">
          登录
        </button>
        <button type="button" role="tab" :aria-selected="mode === 'register'" :class="{ active: mode === 'register' }"
          @click="switchMode('register')">
          注册
        </button>
      </div>

      <header class="form-header">
        <div class="seed-icon" aria-hidden="true"></div>
        <div>
          <p>{{ mode === 'login' ? 'Welcome Back' : 'Create Account' }}</p>
          <h2>{{ mode === 'login' ? '账号密码登录' : '注册青松账号' }}</h2>
        </div>
      </header>

      <form class="auth-form" novalidate @submit.prevent="handleSubmit">
        <label class="field" :class="{ focused: focusedField === 'account' }">
          <span>Account</span>
          <input v-model.trim="form.account" name="account" type="text" autocomplete="username" placeholder="请输入账号名"
            :disabled="isSubmitting" @focus="focusedField = 'account'" @blur="focusedField = ''" />
        </label>

        <label class="field" :class="{ focused: focusedField === 'password' }">
          <span>Password</span>
          <input v-model="form.password" name="password" type="password"
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="请输入密码"
            :disabled="isSubmitting" @focus="focusedField = 'password'" @blur="focusedField = ''" />
        </label>

        <label v-if="mode === 'register'" class="field" :class="{ focused: focusedField === 'confirmPassword' }">
          <span>Confirm Password</span>
          <input v-model="form.confirmPassword" name="confirmPassword" type="password" autocomplete="new-password"
            placeholder="请再次输入密码" :disabled="isSubmitting" @focus="focusedField = 'confirmPassword'"
            @blur="focusedField = ''" />
        </label>

        <div class="form-actions-row">
          <label class="remember-me">
            <input v-model="form.remember" type="checkbox" :disabled="isSubmitting" />
            <span>记住登录状态</span>
          </label>
        </div>

        <p v-if="feedback.message" class="feedback" :class="feedback.type" aria-live="polite">
          {{ feedback.message }}
        </p>

        <button class="submit-button" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? '处理中...' : mode === 'login' ? '登录' : '注册' }}
        </button>
      </form>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { authService } from '@/services/authService'

const route = useRoute()
const router = useRouter()

const mode = ref(route.name === 'Register' ? 'register' : 'login')
const focusedField = ref('')
const isSubmitting = ref(false)
const form = reactive({
  account: '',
  password: '',
  confirmPassword: '',
  remember: true
})
const feedback = reactive({
  type: '',
  message: ''
})

watch(
  () => route.name,
  routeName => {
    mode.value = routeName === 'Register' ? 'register' : 'login'
    clearFeedback()
  }
)

const clearFeedback = () => {
  feedback.type = ''
  feedback.message = ''
}

const switchMode = nextMode => {
  if (mode.value === nextMode) return
  router.push(nextMode === 'login' ? '/login' : '/register')
}

const getLoginRedirect = () => {
  const redirect = route.query.redirect

  if (typeof redirect !== 'string' || !redirect.startsWith('/')) return '/'
  if (redirect.startsWith('/login') || redirect.startsWith('/register')) return '/'

  return redirect
}

const validateForm = () => {
  if (!form.account) {
    return '请输入账号'
  }

  if (form.account.length < 3) {
    return '账号长度至少 3 个字符'
  }

  if (!form.password) {
    return '请输入密码'
  }

  if (form.password.length < 6) {
    return '密码长度至少 6 位'
  }

  if (mode.value === 'register' && form.password !== form.confirmPassword) {
    return '两次输入的密码不一致'
  }

  return ''
}

const getErrorMessage = error => {
  if (error instanceof Error && error.message) return error.message
  return '请求失败，请确认后端服务已启动且接口地址正确。'
}

const handleSubmit = async () => {
  const validationMessage = validateForm()

  if (validationMessage) {
    feedback.type = 'error'
    feedback.message = validationMessage
    return
  }

  isSubmitting.value = true
  clearFeedback()

  try {
    if (mode.value === 'login') {
      const data = await authService.login({
        account: form.account,
        password: form.password
      })
      if (data.ok === 1) {
        authService.saveSession(data.data.token, form.remember)
        feedback.type = 'success'
        feedback.message = '登录成功，正在进入…'
        router.push(getLoginRedirect())
      } else {
        feedback.type = 'error'
        feedback.message = '登录失败，账号或密码错误'
      }
      return
    } else if (mode.value === 'register') {
      const data = await authService.register({
        account: form.account,
        password: form.password,
        rePassword: form.confirmPassword
      })

      if (data.ok === 1) {
        feedback.type = 'success'
        feedback.message = '注册成功，正在进入…'
        router.push(getLoginRedirect())
      } else {
        feedback.type = 'error'
        feedback.message = '注册失败，该账号可能已存在'
      }
    }
  } catch (error) {
    feedback.type = 'error'
    feedback.message = getErrorMessage(error)
  } finally {
    isSubmitting.value = false
  }
}
</script>

<style scoped lang="scss">
.auth-page {
  position: relative;
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(340px, 430px);
  align-items: center;
  gap: clamp(2rem, 6vw, 5rem);
  min-height: 100%;
  padding: clamp(1.5rem, 5vw, 4.5rem);
  overflow: hidden;
  color: var(--text-color, #1f2937);
  background:
    radial-gradient(circle at 14% 18%, rgba(16, 185, 129, 0.18), transparent 28rem),
    radial-gradient(circle at 86% 14%, rgba(14, 165, 233, 0.16), transparent 24rem),
    linear-gradient(135deg, rgba(224, 247, 250, 0.72), rgba(232, 245, 233, 0.9));
  font-family: var(--app-font-family);
}

.auth-background {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.bg-orb {
  position: absolute;
  border-radius: 999px;
  filter: blur(2px);
  opacity: 0.78;
  animation: floatOrb 8s ease-in-out infinite;
}

.orb-green {
  width: 22rem;
  height: 22rem;
  left: -7rem;
  bottom: -8rem;
  background: rgba(16, 185, 129, 0.2);
}

.orb-blue {
  width: 18rem;
  height: 18rem;
  right: 10%;
  top: -7rem;
  background: rgba(14, 165, 233, 0.18);
  animation-delay: -3s;
}

.orb-orange {
  width: 14rem;
  height: 14rem;
  right: -4rem;
  bottom: 14%;
  background: rgba(249, 115, 22, 0.14);
  animation-delay: -5s;
}

.pine-line {
  position: absolute;
  width: 20rem;
  height: 20rem;
  border: 1px solid rgba(5, 150, 105, 0.14);
  border-radius: 36% 64% 42% 58% / 54% 34% 66% 46%;
}

.pine-line-one {
  left: 8%;
  top: 10%;
}

.pine-line-two {
  right: 8%;
  bottom: 8%;
  transform: rotate(18deg);
}

.brand-panel,
.auth-card {
  position: relative;
  z-index: 1;
}

.brand-panel {
  max-width: 620px;
}

.brand-link {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
  color: inherit;
  text-decoration: none;
}

.brand-icon {
  width: 3rem;
  height: 3rem;
  color: #059669;
  filter: drop-shadow(0 0.5rem 0.9rem rgba(16, 185, 129, 0.28));
}

.brand-link strong,
.brand-link span {
  display: block;
}

.brand-link strong {
  font-size: 1.8rem;
  line-height: 1;
  letter-spacing: 0.08em;
  background: linear-gradient(135deg, #059669 0%, #10b981 52%, #34d399 100%);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.brand-link span {
  margin-top: 0.22rem;
  color: rgba(26, 26, 26, 0.62);
  font-size: 0.92rem;
}

.eyebrow {
  margin: 3rem 0 0.85rem;
  color: #059669;
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.brand-panel h1 {
  max-width: 11em;
  margin: 0;
  color: #172033;
  font-size: clamp(2.5rem, 6vw, 5.6rem);
  line-height: 1.02;
  letter-spacing: -0.06em;
}


.feature-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 1.8rem;
}

.feature-list span {
  padding: 0.58rem 0.85rem;
  border: 1px solid rgba(5, 150, 105, 0.16);
  border-radius: 999px;
  color: #047857;
  background: rgba(255, 255, 255, 0.58);
  font-size: 0.88rem;
  font-weight: 700;
  box-shadow: 0 0.8rem 1.8rem rgba(15, 23, 42, 0.05);
}

.auth-card {
  width: min(100%, 430px);
  justify-self: end;
  padding: clamp(1.35rem, 4vw, 2rem);
  border: 1px solid rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.28);
  border-radius: 24px;
  background: rgba(var(--app-panel-background-rgb, 255, 255, 255), 0.95);
  box-shadow: 0 22px 60px rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(18px);
}

.mode-tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.5rem;
  padding: 0.35rem;
  border-radius: 18px;
  background: rgba(5, 150, 105, 0.08);
}

.mode-tabs button,
.submit-button {
  border: 0;
  font: inherit;
  cursor: pointer;
}

.mode-tabs button {
  min-height: 2.8rem;
  border-radius: 14px;
  color: #64748b;
  background: transparent;
  font-weight: 800;
  transition: all 0.24s ease;
}

.mode-tabs button:hover {
  color: #059669;
}

.mode-tabs button.active {
  color: #ffffff;
  background: linear-gradient(135deg, #059669 0%, #10b981 100%);
  box-shadow: 0 0.7rem 1.4rem rgba(16, 185, 129, 0.26);
}

.form-header {
  display: flex;
  align-items: center;
  gap: 0.9rem;
  margin: 1.7rem 0 1.4rem;
}

.seed-icon {
  width: 3rem;
  height: 3rem;
  border-radius: 18px;
  background:
    radial-gradient(circle at 28% 24%, rgba(255, 255, 255, 0.9), transparent 26%),
    linear-gradient(135deg, #84fab0 0%, #8fd3f4 100%);
  box-shadow: 0 0.75rem 1.4rem rgba(16, 185, 129, 0.24);
}

.form-header p,
.form-header h2 {
  margin: 0;
}

.form-header p {
  color: #059669;
  font-size: 0.82rem;
  font-weight: 900;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.form-header h2 {
  margin-top: 0.18rem;
  color: #111827;
  font-size: 1.65rem;
  letter-spacing: -0.04em;
}

.auth-form {
  display: grid;
  gap: 1rem;
}

.field {
  display: grid;
  gap: 0.48rem;
  padding: 0.9rem 1rem;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  transition: border-color 0.24s ease, box-shadow 0.24s ease, transform 0.24s ease;
}

.field.focused {
  border-color: rgba(16, 185, 129, 0.72);
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.1), 0 0.85rem 1.6rem rgba(15, 23, 42, 0.06);
  transform: translateY(-1px);
}

.field span {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.field input {
  width: 100%;
  border: 0;
  outline: 0;
  color: #111827;
  background: transparent;
  font: inherit;
}

.field input::placeholder {
  color: rgba(100, 116, 139, 0.58);
}

.form-actions-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  color: #64748b;
  font-size: 0.9rem;
}

.remember-me {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  cursor: pointer;
}

.remember-me input {
  accent-color: #10b981;
}

.form-actions-row a {
  color: #f97316;
  font-weight: 800;
  text-decoration: none;
}

.feedback {
  margin: 0;
  padding: 0.75rem 0.9rem;
  border-radius: 14px;
  font-size: 0.92rem;
  line-height: 1.55;
}

.feedback.success {
  color: #047857;
  background: rgba(16, 185, 129, 0.12);
}

.feedback.error {
  color: #c2410c;
  background: rgba(249, 115, 22, 0.12);
}

.submit-button {
  min-height: 3.25rem;
  border-radius: 16px;
  color: #ffffff;
  background: linear-gradient(135deg, #059669 0%, #10b981 56%, #34d399 100%);
  box-shadow: 0 0.9rem 1.8rem rgba(16, 185, 129, 0.25);
  font-weight: 900;
  letter-spacing: 0.06em;
  transition: transform 0.24s ease, box-shadow 0.24s ease, filter 0.24s ease;
}

.submit-button:hover {
  filter: brightness(1.04);
  transform: translateY(-2px);
  box-shadow: 0 1.1rem 2rem rgba(16, 185, 129, 0.32);
}

.submit-button:active {
  transform: translateY(0);
}

.is-success .auth-card {
  animation: successPulse 0.55s ease both;
}

.is-error .auth-card {
  animation: errorShake 0.32s ease both;
}

@keyframes floatOrb {

  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }

  50% {
    transform: translate3d(0.8rem, -0.9rem, 0) scale(1.04);
  }
}

@keyframes successPulse {
  50% {
    box-shadow: 0 24px 64px rgba(16, 185, 129, 0.22);
  }
}

@keyframes errorShake {
  30% {
    transform: translateX(-0.35rem);
  }

  70% {
    transform: translateX(0.35rem);
  }
}

@media (max-width: 920px) {
  .auth-page {
    grid-template-columns: 1fr;
    align-content: start;
  }

  .auth-card {
    justify-self: stretch;
    width: min(100%, 520px);
  }
}

@media (max-width: 560px) {
  .auth-page {
    padding: 1rem;
  }

  .brand-panel h1 {
    font-size: clamp(2.1rem, 12vw, 3.5rem);
  }

  .form-actions-row {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {

  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
