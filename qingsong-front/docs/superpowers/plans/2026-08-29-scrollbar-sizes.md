# 滚动条三档尺寸系统实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立「大 / 中 / 小」三档滚动条尺寸体系（`.scrollbar-lg/md/sm`），按场景统一应用，并收敛 13 处重复的 `::-webkit-scrollbar` 样式块。

**Architecture:** 在 `common.scss` 定义尺寸令牌与三个工具类；工具类作用于元素自身及所有后代滚动容器，颜色主题感知（聊天区域读取 `--chat-scrollbar-*` 主题变量，其它区域回退中性色并适配暗色模式）；聊天主消息区用 `.scrollbar-lg`，面板/侧边栏/弹窗用 `.scrollbar-md`，表格/代码块/快捷短语用 `.scrollbar-sm`。尺寸由工具类令牌决定（`--chat-scrollbar-size` 不再作为尺寸来源），实现「按场景而非按主题」区分尺寸。

**Tech Stack:** Vue 3 + SCSS + Vite。

**关键规则（实现前必读）：**
- 工具类同时渲染 `&::-webkit-scrollbar` 与 `& *::-webkit-scrollbar`，嵌套子容器可用更小的档位类覆盖（子类特异性更高）。
- `var(--chat-bevel-light, transparent)` 回退为 `transparent`，因此非聊天区域（无聊天主题变量）不会出现 retro 斜角边框；聊天区域自动获得主题斜角与圆角。
- 全局 `main.ts` 需导入 `common.scss`，否则工具页面拿不到工具类样式。

---

## 文件清单

| 文件 | 操作 |
|------|------|
| `src/main.ts` | 修改：全局导入 common.scss |
| `src/shared/styles/common.scss` | 修改：令牌 + 工具类 + 全局默认 + 移动端 |
| `src/modules/chat/pages/AIChatPage.vue` | 修改：删 deep 滚动条块；根节点加 `scrollbar-md` |
| `src/modules/chat/components/ChatWorkspace.vue` | 修改：`.messages` 加 `scrollbar-lg`，删样式块 |
| `src/modules/chat/components/ChatComposer.vue` | 修改：textarea 加 `scrollbar-md`，删样式块 |
| `src/modules/chat/components/ChatReviewModal.vue` | 修改：`.chat-review` 加 `scrollbar-md`，删样式块 |
| `src/modules/chat/components/ChatWorkspaceHeader.vue` | 修改：三处加 `scrollbar-md`，删三处样式块 + 一处死代码 |
| `src/modules/chat/components/ConversationSidebar.vue` | 修改：`.chat-list`/`.message-list` 加 `scrollbar-md`，`.feature-cards` 加 `scrollbar-sm` |
| `src/modules/chat/components/RoleSidebar.vue` | 修改：`.list` 用 `scrollbar-md` 替换 `custom-scrollbar` |
| `src/modules/chat/components/QuickPhrasePanel.vue` | 修改：`.panel-content` 加 `scrollbar-sm`，删样式块 |
| `src/modules/chat/components/ChatMessage.vue` | 修改：代码块/表格用 `--scrollbar-size-sm` 覆盖 |
| `src/modules/tools/pages/ModelManagePage.vue` | 修改：表格容器加 `scrollbar-sm`，删样式块 |
| `src/modules/tools/pages/ModelSourcePage.vue` | 修改：表格容器加 `scrollbar-sm`，删样式块 |
| `src/modules/tools/pages/DictManagePage.vue` | 修改：表格容器加 `scrollbar-sm`，删样式块 |
| `src/modules/tools/pages/RoleManagePage.vue` | 修改：表格容器加 `scrollbar-sm`，删样式块 + 暗色规则 |
| `src/modules/tools/pages/ConfigView.vue` | 修改：删除无模板引用的死代码滚动条规则 |

---

### Task 1: 全局导入 common.scss

**Files:**
- Modify: `src/main.ts`

- [ ] **Step 1: 在 main.ts 中导入 common.scss**

在 `src/main.ts` 第 1 行 `import './assets/main.css'` 之后新增一行：

```ts
import './assets/main.css'
import './shared/styles/common.scss'
```

- [ ] **Step 2: 提交**

```bash
git add src/main.ts
git commit -m "refactor(scrollbar): 全局导入 common.scss，使工具类对所有页面可用"
```

---

### Task 2: common.scss 定义三档滚动条系统

**Files:**
- Modify: `src/shared/styles/common.scss`

- [ ] **Step 1: 在 `:root` 添加尺寸与颜色令牌**

将 `:root` 块（第 2-17 行）末尾、`--transition-normal: 0.2s;` 之后追加：

```scss
  --scrollbar-size-sm: 4px;
  --scrollbar-size-md: 8px;
  --scrollbar-size-lg: 14px;
  --scrollbar-thumb: rgba(0, 0, 0, 0.22);
  --scrollbar-thumb-hover: rgba(0, 0, 0, 0.35);
  --scrollbar-track: rgba(0, 0, 0, 0.04);
```

- [ ] **Step 2: 在 `.dark` 块添加暗色令牌**

将 `.dark` 块（第 19-28 行）末尾、`--card-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);` 之后追加：

```scss
  --scrollbar-thumb: rgba(255, 255, 255, 0.25);
  --scrollbar-thumb-hover: rgba(255, 255, 255, 0.4);
  --scrollbar-track: rgba(255, 255, 255, 0.06);
```

- [ ] **Step 3: 替换全局默认滚动条并新增工具类**

将第 49-65 行全局滚动条块：

```scss
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.05);
}

::-webkit-scrollbar-thumb {
  background: var(--primary-color);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--primary-color-light);
}
```

替换为：

```scss
::-webkit-scrollbar {
  width: var(--scrollbar-size-md);
  height: var(--scrollbar-size-md);
}

::-webkit-scrollbar-track {
  background: var(--scrollbar-track);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb {
  background: var(--scrollbar-thumb);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--scrollbar-thumb-hover);
}

/* 三档滚动条尺寸系统：小(表格/代码块) / 中(面板/侧边栏/弹窗) / 大(聊天长文区) */
.scrollbar-sm { --scrollbar-size: var(--scrollbar-size-sm); }
.scrollbar-md { --scrollbar-size: var(--scrollbar-size-md); }
.scrollbar-lg { --scrollbar-size: var(--scrollbar-size-lg); }

.scrollbar-sm,
.scrollbar-md,
.scrollbar-lg {
  scrollbar-width: thin;
  scrollbar-color: var(--chat-scrollbar-thumb, var(--scrollbar-thumb)) var(--chat-scrollbar-track, var(--scrollbar-track));

  &::-webkit-scrollbar,
  & *::-webkit-scrollbar {
    width: var(--scrollbar-size);
    height: var(--scrollbar-size);
  }

  &::-webkit-scrollbar-track,
  & *::-webkit-scrollbar-track {
    background: var(--chat-scrollbar-track, var(--scrollbar-track));
    border: 1px solid var(--chat-scrollbar-border, transparent);
    border-radius: var(--chat-radius, 4px);
  }

  &::-webkit-scrollbar-thumb,
  & *::-webkit-scrollbar-thumb {
    background: var(--chat-scrollbar-thumb, var(--scrollbar-thumb));
    border: 2px solid;
    border-color: var(--chat-bevel-light, transparent) var(--chat-bevel-shadow, transparent) var(--chat-bevel-shadow, transparent) var(--chat-bevel-light, transparent);
    border-radius: var(--chat-radius, 4px);
    min-height: 40px;

    &:hover {
      background: var(--chat-panel-hover, var(--scrollbar-thumb-hover));
    }

    &:active {
      border-color: var(--chat-bevel-shadow, transparent) var(--chat-bevel-light, transparent) var(--chat-bevel-light, transparent) var(--chat-bevel-shadow, transparent);
    }
  }
}
```

- [ ] **Step 4: 更新移动端媒体查询**

将第 136-139 行：

```scss
@media (max-width: 768px) {
  html { font-size: 14px; }
  ::-webkit-scrollbar { width: 4px; }
}
```

替换为：

```scss
@media (max-width: 768px) {
  html { font-size: 14px; }
  :root {
    --scrollbar-size-sm: 3px;
    --scrollbar-size-md: 4px;
    --scrollbar-size-lg: 8px;
  }
}
```

- [ ] **Step 5: 提交**

```bash
git add src/shared/styles/common.scss
git commit -m "feat(scrollbar): 新增大中小三档滚动条工具类与尺寸令牌"
```

---

### Task 3: 聊天主消息区使用大档

**Files:**
- Modify: `src/modules/chat/components/ChatWorkspace.vue`

- [ ] **Step 1: 模板 `.messages` 添加 `scrollbar-lg`**

第 41 行：

```html
      <div class="messages" ref="messagesRef">
```

改为：

```html
      <div class="messages scrollbar-lg" ref="messagesRef">
```

- [ ] **Step 2: 删除 `.messages` 的滚动条样式块**

删除第 306-323 行：

```scss
.messages::-webkit-scrollbar {
  width: var(--chat-scrollbar-size, 17px);
}

.messages::-webkit-scrollbar-track {
  background: var(--chat-scrollbar-track, repeating-conic-gradient(#c0c0c0 0% 25%, #ffffff 0% 50%) 50% / 2px 2px);
  border: 1px solid var(--chat-scrollbar-border, #808080);
}

.messages::-webkit-scrollbar-thumb {
  background: var(--chat-scrollbar-thumb, #c0c0c0);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);

  &:hover {
    background: var(--chat-panel-hover, #d4d4d4);
  }
}
```

- [ ] **Step 3: 提交**

```bash
git add src/modules/chat/components/ChatWorkspace.vue
git commit -m "refactor(scrollbar): 聊天消息区改用 scrollbar-lg 工具类"
```

---

### Task 4: AIChatPage 移除 deep 滚动条选择器

**Files:**
- Modify: `src/modules/chat/pages/AIChatPage.vue`

- [ ] **Step 1: 根节点添加 `scrollbar-md`**

第 3 行：

```html
    class="ai-chat-view"
```

改为：

```html
    class="ai-chat-view scrollbar-md"
```

- [ ] **Step 2: 删除 deep 滚动条样式块**

删除第 586-610 行（含 `/* Retro scrollbar override */` 注释）：

```scss
/* Retro scrollbar override */
.chat-container :deep(*::-webkit-scrollbar) {
  width: var(--chat-scrollbar-size, 17px);
  height: var(--chat-scrollbar-size, 17px);
}

.chat-container :deep(*::-webkit-scrollbar-track) {
  background: var(--chat-scrollbar-track, repeating-conic-gradient(#c0c0c0 0% 25%, #ffffff 0% 50%) 50% / 2px 2px);
  border: 1px solid var(--chat-scrollbar-border, #808080);
}

.chat-container :deep(*::-webkit-scrollbar-thumb) {
  background: var(--chat-scrollbar-thumb, #c0c0c0);
  border: 2px solid;
  border-color: var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080) var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff);
  border-radius: var(--chat-radius, 0);

  &:hover {
    background: var(--chat-panel-hover, #d4d4d4);
  }

  &:active {
    border-color: var(--chat-bevel-shadow, #808080) var(--chat-bevel-light, #ffffff) var(--chat-bevel-light, #ffffff) var(--chat-bevel-shadow, #808080);
  }
}
```

- [ ] **Step 3: 提交**

```bash
git add src/modules/chat/pages/AIChatPage.vue
git commit -m "refactor(scrollbar): AIChatPage 移除 deep 滚动条选择器，改用工具类"
```

---

### Task 5: 聊天侧边栏使用中档/小档

**Files:**
- Modify: `src/modules/chat/components/ConversationSidebar.vue`
- Modify: `src/modules/chat/components/RoleSidebar.vue`

- [ ] **Step 1: ConversationSidebar 模板添加类**

第 50 行：

```html
      <div class="chat-list">
```

改为：

```html
      <div class="chat-list scrollbar-md">
```

第 102 行：

```html
      <div class="feature-cards">
```

改为：

```html
      <div class="feature-cards scrollbar-sm">
```

第 150 行：

```html
        <div class="message-list">
```

改为：

```html
        <div class="message-list scrollbar-md">
```

- [ ] **Step 2: 删除 ConversationSidebar 两处滚动条样式块**

删除第 1222-1239 行 `.feature-cards` 内的 `&::-webkit-scrollbar` 块（含其下 `&::-webkit-scrollbar-track`、`&::-webkit-scrollbar-thumb` 及 hover 嵌套），并删除第 1419-1440 行 `.chat-list, .message-list` 内的 `&::-webkit-scrollbar` 块。

- [ ] **Step 3: RoleSidebar 模板替换类并删除样式块**

第 117 行：

```html
        <div class="list custom-scrollbar">
```

改为：

```html
        <div class="list scrollbar-md">
```

删除第 1104-1121 行 `.list` 内的 `&::-webkit-scrollbar` 块。

- [ ] **Step 4: 提交**

```bash
git add src/modules/chat/components/ConversationSidebar.vue src/modules/chat/components/RoleSidebar.vue
git commit -m "refactor(scrollbar): 侧边栏列表用中档、功能卡片用小档"
```

---

### Task 6: 输入框/面板/弹窗使用中档，快捷短语用小档

**Files:**
- Modify: `src/modules/chat/components/ChatComposer.vue`
- Modify: `src/modules/chat/components/ChatReviewModal.vue`
- Modify: `src/modules/chat/components/ChatWorkspaceHeader.vue`
- Modify: `src/modules/chat/components/QuickPhrasePanel.vue`

- [ ] **Step 1: ChatComposer textarea 添加 `scrollbar-md` 并删除样式块**

第 20 行：

```html
      <textarea :ref="inputRef" :value="draftMessage" class="message-input" :placeholder="placeholder" rows="1"
```

改为：

```html
      <textarea :ref="inputRef" :value="draftMessage" class="message-input scrollbar-md" :placeholder="placeholder" rows="1"
```

删除第 363-376 行 `.message-input` 内的 `&::-webkit-scrollbar` / track / thumb 嵌套块。

- [ ] **Step 2: ChatReviewModal 添加 `scrollbar-md` 并删除样式块**

第 10 行：

```html
    <div class="chat-review">
```

改为：

```html
    <div class="chat-review scrollbar-md">
```

删除第 331-344 行 `.chat-review` 内的 `&::-webkit-scrollbar` / track / thumb 嵌套块。

- [ ] **Step 3: ChatWorkspaceHeader 三处添加 `scrollbar-md` 并删除样式块**

第 113 行：

```html
          <div class="overflow-menu">
```

改为：

```html
          <div class="overflow-menu scrollbar-md">
```

删除第 955-972 行 `.overflow-menu` 内的 `&::-webkit-scrollbar` 嵌套块。

第 272 行：

```html
    <div class="role-description-modal">
```

改为：

```html
    <div class="role-description-modal scrollbar-md">
```

删除第 1421-1428 行 `.role-description-modal` 内的 `&::-webkit-scrollbar` 与 `&::-webkit-scrollbar-thumb` 嵌套块。

第 277 行：

```html
      <div v-if="roleDescription" class="modal-content">
```

改为：

```html
      <div v-if="roleDescription" class="modal-content scrollbar-md">
```

删除第 1459-1472 行 `.modal-content` 内的 `&::-webkit-scrollbar` / track / thumb 嵌套块。

- [ ] **Step 4: 删除 ChatWorkspaceHeader 死代码块**

第 1580-1593 行（`.modal-content` 第二个定义，无模板引用）内的 `&::-webkit-scrollbar` / track / thumb 嵌套块一并删除。

- [ ] **Step 5: QuickPhrasePanel 添加 `scrollbar-sm` 并删除样式块**

第 17 行：

```html
        <div class="panel-content">
```

改为：

```html
        <div class="panel-content scrollbar-sm">
```

删除第 301-314 行 `.panel-content` 内的 `&::-webkit-scrollbar` 嵌套块。

- [ ] **Step 6: 提交**

```bash
git add src/modules/chat/components/ChatComposer.vue src/modules/chat/components/ChatReviewModal.vue src/modules/chat/components/ChatWorkspaceHeader.vue src/modules/chat/components/QuickPhrasePanel.vue
git commit -m "refactor(scrollbar): 输入框/弹窗/面板用中档，快捷短语用小档"
```

---

### Task 7: 聊天消息内代码块使用小档

**Files:**
- Modify: `src/modules/chat/components/ChatMessage.vue`

- [ ] **Step 1: 替换代码块滚动条样式**

将第 2772-2792 行：

```scss
  /* 水平滚动条优化 */
  :deep(pre),
  :deep(.table-wrapper) {
    &::-webkit-scrollbar {
      height: 8px;
    }

    &::-webkit-scrollbar-track {
      background: #f1f5f9;
      border-radius: 4px;
    }

    &::-webkit-scrollbar-thumb {
      background: #cbd5e1;
      border-radius: 4px;

      &:hover {
        background: #94a3b8;
      }
    }
  }
```

替换为（利用自定义属性继承：`.messages` 的 `scrollbar-lg` 级联会给代码块宽/高与配色，此处仅把尺寸覆盖为小档）：

```scss
  /* 水平滚动条优化：代码块/表格用小档尺寸 */
  :deep(pre),
  :deep(.table-wrapper) {
    --scrollbar-size: var(--scrollbar-size-sm);
  }
```

- [ ] **Step 2: 提交**

```bash
git add src/modules/chat/components/ChatMessage.vue
git commit -m "refactor(scrollbar): 消息内代码块/表格滚动条用小档尺寸"
```

---

### Task 8: 工具页表格使用小档

**Files:**
- Modify: `src/modules/tools/pages/ModelManagePage.vue`
- Modify: `src/modules/tools/pages/ModelSourcePage.vue`
- Modify: `src/modules/tools/pages/DictManagePage.vue`
- Modify: `src/modules/tools/pages/RoleManagePage.vue`
- Modify: `src/modules/tools/pages/ConfigView.vue`

- [ ] **Step 1: ModelManagePage 表格容器**

第 105 行：

```html
          <div v-else class="model-table-container" ref="modelListRef" @scroll="handleModelListScroll">
```

改为：

```html
          <div v-else class="model-table-container scrollbar-sm" ref="modelListRef" @scroll="handleModelListScroll">
```

删除第 1200-1217 行 `.model-table-container::-webkit-scrollbar` 相关 4 条规则。

- [ ] **Step 2: ModelSourcePage 表格容器**

第 46 行：

```html
      <div v-else class="model-table-container">
```

改为：

```html
      <div v-else class="model-table-container scrollbar-sm">
```

删除第 609-623 行 `.model-table-container::-webkit-scrollbar` 相关 4 条规则。

- [ ] **Step 3: DictManagePage 表格容器**

第 56 行：

```html
      <div v-else class="model-table-container">
```

改为：

```html
      <div v-else class="model-table-container scrollbar-sm">
```

删除第 501-514 行 `.model-table-container::-webkit-scrollbar` 相关 3 条规则。

- [ ] **Step 4: RoleManagePage 表格容器**

第 60-64 行：

```html
        <div
          class="role-table-scroll"
          ref="roleListRef"
          @scroll="handleRoleListScroll"
        >
```

改为：

```html
        <div
          class="role-table-scroll scrollbar-sm"
          ref="roleListRef"
          @scroll="handleRoleListScroll"
        >
```

删除第 616-630 行 `.role-table-scroll::-webkit-scrollbar` 相关 4 条规则，并删除第 972-977 行暗色规则：

```scss
:global(.dark) .role-table-scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.18);
}
:global(.dark) .role-table-scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.3);
}
```

（暗色由 `.dark` 令牌自动处理。）

- [ ] **Step 5: ConfigView 删除死代码滚动条规则**

删除第 873-890 行（`.model-table-container` 在 ConfigView 模板中无引用，属遗留死代码）：

```scss
.model-table-container::-webkit-scrollbar {
  width: 6px; /* 纵向滚动条宽度 */
  height: 6px; /* 横向滚动条高度 */
}

.model-table-container::-webkit-scrollbar-track {
  background: var(--app-bg-secondary, #f1f1f1);
  border-radius: 3px;
}

.model-table-container::-webkit-scrollbar-thumb {
  background: var(--app-border-color, #c1c1c1);
  border-radius: 3px;
}

.model-table-container::-webkit-scrollbar-thumb:hover {
  background: var(--app-text-secondary, #a1a1a1);
}
```

- [ ] **Step 6: 提交**

```bash
git add src/modules/tools/pages/ModelManagePage.vue src/modules/tools/pages/ModelSourcePage.vue src/modules/tools/pages/DictManagePage.vue src/modules/tools/pages/RoleManagePage.vue src/modules/tools/pages/ConfigView.vue
git commit -m "refactor(scrollbar): 工具页表格统一使用小档 scrollbar-sm"
```

---

### Task 9: 验证

- [ ] **Step 1: 类型检查**

```bash
npm run type-check
```

预期：无错误退出。

- [ ] **Step 2: 构建**

```bash
npm run build
```

预期：构建成功。

- [ ] **Step 3: 残留扫描**

确认没有任何组件样式块再引用 `--chat-scrollbar-size` 之外的旧重复样式：

```bash
rg -n "::-webkit-scrollbar" src
```

预期：仅剩 `common.scss` 中的工具类/全局定义，以及 `cloudImmortal.scss` 的主题覆盖（保留，特异性更高，用于「云中仙」主题的定制滚动条）。

- [ ] **Step 4: 目测检查（npm run dev）**

逐项核对：
1. 聊天消息区滚动条：14px（大档），配色随主题变化，retro 主题保留斜角边框样式。
2. 聊天侧边栏/弹窗/输入框滚动条：8px（中档），配色随主题。
3. 快捷短语面板、功能卡片、代码块滚动条：4px（小档）。
4. 模型/角色/字典表格滚动条：4px（小档）中性灰。
5. 切换主题（含 retro 与云中仙）后聊天区滚动条配色不回归。
6. 窗口缩窄到 <768px 后滚动条整体变小。

- [ ] **Step 5: 提交（如目测有额外修复则先提交修复）**

```bash
git add -A
git commit -m "chore(scrollbar): 三档尺寸系统验证通过"
```
