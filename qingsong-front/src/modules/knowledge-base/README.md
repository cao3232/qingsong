# knowledge-base

知识库业务域。
当前阶段先完成页面归位，包括：

- `KnowledgeBaseView.vue`
- `KnowledgeBaseDetailView.vue`

后续再逐步补齐：

- routes
- services
- components
- composables

迁移原则：

- 先目录归位
- 再逐步把域内依赖收回本域

使用约定：

- 域外优先通过 `src/modules/knowledge-base/index.js` 使用页面导出入口
