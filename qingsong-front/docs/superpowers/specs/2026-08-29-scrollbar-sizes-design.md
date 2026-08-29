# 滚动条三档尺寸系统设计

## 背景

当前滚动条样式散落各处，缺乏统一规范：

- `common.scss` 定义全局 6px 默认滚动条
- 聊天区域通过 `--chat-scrollbar-*` 主题变量驱动（retro 17px / 其余主题 8-9px），并在多个组件中重复定义
- 模型/角色/字典表格各自硬编码 6px 滚动条

目标：建立「大 / 中 / 小」三档滚动条尺寸体系，按场景统一应用，收敛重复代码。

## 尺寸令牌

定义在 `src/shared/styles/common.scss` 的 `:root`：

| 变量 | 尺寸 | 对应档位 |
|------|------|---------|
| `--scrollbar-size-sm` | 4px | 小 |
| `--scrollbar-size-md` | 8px | 中 |
| `--scrollbar-size-lg` | 14px | 大 |

## 工具类

`common.scss` 中定义三个工具类：`.scrollbar-sm` / `.scrollbar-md` / `.scrollbar-lg`。

行为：

- 加在容器元素上，同时作用于元素自身及所有后代滚动容器（`&::-webkit-scrollbar` 与 `& *::-webkit-scrollbar`）
- 尺寸取自 `--scrollbar-size-*` 令牌
- 颜色主题感知：优先使用 `--chat-scrollbar-thumb` / `--chat-scrollbar-track` / `--chat-scrollbar-border`（聊天区域保持各主题配色），否则回退到中性默认色并适配暗色模式
- 兼容 Firefox：`scrollbar-width` + `scrollbar-color`
- 移动端（≤768px）下，工具类尺寸整体减半

## 场景映射

| 档位 | 尺寸 | 场景 | 应用位置 |
|------|------|------|---------|
| 大 | 14px | 聊天消息区等长文阅读区 | `.chat-container`、`.messages` |
| 中 | 8px | 侧边栏、弹窗、面板、页面主体滚动 | 全局默认、`.conversation-sidebar`、`.role-sidebar`、`.chat-review-modal` 等 |
| 小 | 4px | 表格（模型/角色/字典）、代码块、快捷短语面板 | `.model-table-container`、`.role-table-scroll`、`.quick-phrase-panel` 等 |

## 重构范围

将以下组件中散落的 `::-webkit-scrollbar` 样式块替换为对应工具类：

- `src/modules/tools/pages/ConfigView.vue`
- `src/modules/tools/pages/ModelManagePage.vue`
- `src/modules/tools/pages/ModelSourcePage.vue`
- `src/modules/tools/pages/DictManagePage.vue`
- `src/modules/tools/pages/RoleManagePage.vue`
- `src/modules/chat/components/ChatWorkspace.vue`
- `src/modules/chat/components/ChatComposer.vue`
- `src/modules/chat/components/ChatWorkspaceHeader.vue`
- `src/modules/chat/components/ChatReviewModal.vue`
- `src/modules/chat/components/ConversationSidebar.vue`
- `src/modules/chat/components/RoleSidebar.vue`
- `src/modules/chat/components/QuickPhrasePanel.vue`
- `src/modules/chat/pages/AIChatPage.vue`

同时：

- `common.scss` 全局默认滚动条由 6px 改为 8px（中档）
- 移动端媒体查询保持 4px，并作用于新令牌
- 聊天主题变量 `--chat-scrollbar-size` 保留，作为聊天区域尺寸的最终来源；`--chat-scrollbar-*` 颜色变量继续由 `themeStore` 提供

## 验证

- 运行 `npm run type-check` 确认无类型错误
- 本地 `npm run dev` 目测检查：聊天区、各表格、侧边栏、弹窗的滚动条尺寸与配色正确，主题切换（retro / 暗色 / 其它主题）下聊天滚动条配色不回归
