<template>
  <div class="page-container">
    <!-- 页面标题区 -->
    <div class="page-header">
      <h2 class="page-title">角色配置</h2>
      <p class="page-desc">管理当前对话角色的基础信息与模型参数</p>
    </div>

    <!-- 表单主体：双卡片布局 -->
    <n-grid :cols="2" :x-gap="20" :y-gap="20" responsive="screen"
            :screen-s="1" :screen-m="2">
      <!-- 左：基本信息 -->
      <n-grid-item>
        <n-card :bordered="true" size="small" class="setting-card">
          <template #header>
            <span class="card-header-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
              基本信息
            </span>
          </template>
          <n-form
              ref="formRef"
              :model="form"
              :disabled="disabled"
              label-placement="top"
              :show-label="true"
              label-align="left"
              :rules="rules"
              require-mark-placement="right-hanging"
          >
            <n-form-item label="用户姓名" path="userName">
              <n-auto-complete
                  v-model:value="form.userName"
                  :options="nameOptions"
                  clearable
                  :input-props="{ autocomplete: 'off' }"
                  :disabled="disabled"
                  placeholder="输入用户姓名"
              />
            </n-form-item>

            <n-form-item label="接收邮箱" path="email">
              <n-auto-complete
                  v-model:value="form.email"
                  :options="emailOptions"
                  clearable
                  :input-props="{ autocomplete: 'off' }"
                  :disabled="disabled"
                  placeholder="输入邮箱地址"
              />
            </n-form-item>

            <n-form-item label="上次对话角色" path="lastRole">
              <n-auto-complete
                  v-model:value="form.lastRole"
                  :options="roleOptions"
                  clearable
                  :input-props="{ autocomplete: 'off' }"
                  :disabled="disabled"
                  placeholder="选择或输入角色"
              />
            </n-form-item>
          </n-form>
        </n-card>
      </n-grid-item>

      <!-- 右：参数配置 -->
      <n-grid-item>
        <n-card :bordered="true" size="small" class="setting-card">
          <template #header>
            <span class="card-header-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                stroke-linecap="round" stroke-linejoin="round">
                <line x1="4" y1="21" x2="4" y2="14" />
                <line x1="4" y1="10" x2="4" y2="3" />
                <line x1="12" y1="21" x2="12" y2="12" />
                <line x1="12" y1="8" x2="12" y2="3" />
                <line x1="20" y1="21" x2="20" y2="16" />
                <line x1="20" y1="12" x2="20" y2="3" />
                <line x1="1" y1="14" x2="7" y2="14" />
                <line x1="9" y1="8" x2="15" y2="8" />
                <line x1="17" y1="16" x2="23" y2="16" />
              </svg>
              模型参数
            </span>
          </template>
          <n-form
              :model="form"
              :disabled="disabled"
              label-placement="top"
              :show-label="true"
              label-align="left"
          >
            <n-form-item label="上次登录时间" path="lastLogin">
              <n-input v-model:value="form.lastLogin" clearable
                       :disabled="disabled" placeholder="例如 2026-07-04 21:00" />
            </n-form-item>

            <n-form-item label="Temperature" path="temperature">
              <n-input-number v-model:value="temperatureNum"
                              :min="0" :max="2" :step="0.1"
                              :disabled="disabled" clearable
                              placeholder="0.0 - 2.0"
                              style="width: 100%;" />
            </n-form-item>

            <n-form-item label="Top-K" path="topKey">
              <n-input-number v-model:value="topKNum"
                              :min="1" :max="100" :step="1"
                              :disabled="disabled" clearable
                              placeholder="1 - 100"
                              style="width: 100%;" />
            </n-form-item>
          </n-form>
          <!-- 操作按钮：放在右侧卡片底部，避免脱离面板 -->
          <template #footer>
            <n-space justify="center">
              <n-button @click="handleEdit" :disabled="!disabled"
                        secondary type="info" size="medium" icon-placement="left" class="btn-secondary-warm">
                编辑
              </n-button>
              <n-button @click="handleSave" :disabled="disabled"
                        type="primary" size="medium" icon-placement="left" class="btn-primary-save">
                保存
              </n-button>
              <n-button @click="handleReset" :disabled="disabled"
                        quaternary size="medium">重置</n-button>
            </n-space>
          </template>
        </n-card>
      </n-grid-item>
    </n-grid>
  </div>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import {
  NForm, NFormItem, NAutoComplete, NInput, NInputNumber,
  NButton, NSpace, NCard, NGrid, NGridItem,
  useMessage
} from "naive-ui";
// import { Edit16Regular as EditIcon, Save16Regular as SaveIcon } from "@vicons/fluent";

const message = useMessage();

// ---- 状态 ----
const disabled = ref(true);
const formRef = ref(null);

const form = ref({
  userName: "nihao",
  email: "nihao@163.com",
  lastRole: "开发者",
  lastLogin: "2026-04-26 20:00",
  temperature: "0.9",
  topKey: "1"
});

// 输入框与数字控件双向绑定
const temperatureNum = ref(0.9);
const topKNum = ref(1);

watch(temperatureNum, v => { form.value.temperature = String(v); });
watch(topKNum, v => { form.value.topKey = String(v); });
watch(() => form.value.temperature, v => { temperatureNum.value = Number(v) || 0; });
watch(() => form.value.topKey, v => { topKNum.value = Number(v) || 1; });

// ---- 数据源 ----
const allUsers     = ["张三", "李四", "王五", "nihao"];
const emailDomains = ["@gmail.com", "@163.com", "@qq.com", "@outlook.com"];
const allRoles     = ["管理员", "普通用户", "客服", "开发者"];

// ---- 自动补全过滤 ----
const nameOptions = computed(() => {
  const q = form.value.userName?.trim().toLowerCase();
  return q
      ? allUsers.filter(u => u.toLowerCase().includes(q)).map(u => ({ label: u, value: u }))
      : [];
});

const emailOptions = computed(() => {
  const val = form.value.email?.trim();
  if (!val) return [];
  if (!val.includes('@')) {
    return emailDomains.map(d => ({ label: val + d, value: val + d }));
  }
  const [prefix, domainPart] = val.split('@');
  return emailDomains
      .filter(d => d.startsWith('@' + (domainPart || '')))
      .map(d => ({ label: prefix + d, value: prefix + d }));
});

const roleOptions = computed(() => {
  const q = form.value.lastRole?.trim();
  return q
      ? allRoles.filter(r => r.includes(q)).map(r => ({ label: r, value: r }))
      : [];
});

// ---- 表单校验规则 ----
const rules = {
  userName: [{ required: true, message: "请输入用户姓名", trigger: "blur" }],
  email: [
    { required: true, message: "请输入接收邮箱", trigger: "blur" },
    { type: "email", message: "邮箱格式不正确", trigger: "blur" }
  ],
  lastRole: [{ required: true, message: "请输入或选择角色", trigger: "blur" }]
};

// ---- 操作方法 ----
function handleEdit() {
  disabled.value = false;
}

async function handleSave() {
  try {
    await formRef.value?.validate();
    // 同步数字字段
    form.value.temperature = String(temperatureNum.value);
    form.value.topKey = String(topKNum.value);
    // TODO: POST /api/config
    message.success("✅ 配置保存成功");
    disabled.value = true;
  } catch (e) {
    message.warning("⚠️ 请检查表单中的错误提示");
  }
}

function handleReset() {
  form.value = {
    userName: "nihao",
    email: "nihao@163.com",
    lastRole: "开发者",
    lastLogin: "2026-04-26 20:00",
    temperature: "0.9",
    topKey: "1"
  };
  temperatureNum.value = 0.9;
  topKNum.value = 1;
  message.info("已恢复默认值");
}
</script>

<style scoped>
.page-container {
  max-width: none;
  padding: 0 2px 8px;
}

.page-header {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.08));
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--app-text-primary, #374151);
  margin: 0 0 4px 0;
  line-height: 1.3;
}

.page-desc {
  margin: 0;
  color: var(--app-text-secondary, #6b7280);
  font-size: 0.9rem;
}

/* 卡片：暖色圆角，与配置页整体风格统一 */
.setting-card {
  --n-card-border-radius: 18px;
  --n-card-border-color: rgba(255, 230, 214, 0.85);
  --n-card-color: rgba(255, 252, 248, 0.94);
  --n-card-title-text-color: #4b3d66;
  border-radius: 18px;
  box-shadow: 0 12px 28px rgba(120, 87, 61, 0.08);
  height: 100%;
}
.setting-card :deep(.n-card-header) {
  font-weight: 700;
  font-size: 15px;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(251, 191, 36, 0.18);
}
.card-header-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #4b3d66;
}
.card-header-title svg {
  width: 16px;
  height: 16px;
  color: #f59e0b;
  flex-shrink: 0;
}
.setting-card :deep(.n-card__content) {
  padding: 18px 18px 22px;
}
.setting-card :deep(.n-form-item-label__text) {
  font-weight: 600;
  color: var(--app-text-primary, #374151);
}

/* 暖色按钮：编辑为描边、保存为主渐变 */
.btn-secondary-warm {
  border-color: rgba(245, 158, 11, 0.55) !important;
  color: #b45309 !important;
  background: rgba(255, 247, 237, 0.7) !important;
}
.btn-secondary-warm:hover:not(:disabled) {
  border-color: #f59e0b !important;
  background: rgba(255, 237, 213, 0.9) !important;
}
.btn-primary-save {
  background: linear-gradient(135deg, #ffb86c 0%, #fb7185 100%) !important;
  border-color: transparent !important;
  box-shadow: 0 12px 22px rgba(244, 114, 182, 0.22);
}
.btn-primary-save:hover:not(:disabled) {
  background: linear-gradient(135deg, #ffae52 0%, #f43f5e 100%) !important;
}

/* 暗色模式 */
:global(.dark) .setting-card {
  --n-card-color: rgba(30, 41, 59, 0.9);
  --n-card-border-color: rgba(255, 255, 255, 0.08);
  --n-card-title-text-color: #e5e7eb;
  box-shadow: none;
}
:global(.dark) .setting-card :deep(.n-card-header) {
  border-bottom-color: rgba(255, 255, 255, 0.08);
}
:global(.dark) .card-header-title {
  color: #e5e7eb;
}
:global(.dark) .card-header-title svg {
  color: #fbbf24;
}
:global(.dark) .btn-secondary-warm {
  background: rgba(255, 255, 255, 0.06) !important;
  color: #fbbf24 !important;
  border-color: rgba(251, 191, 36, 0.4) !important;
}

/* 小屏适配 */
@media (max-width: 639px) {
  .page-container { padding: 12px 4px 24px; }
  .page-title { font-size: 20px; }
}
</style>
