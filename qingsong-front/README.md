# qingsong-chat-front

AI 对话应用前端。**版本 1.0.0**。

技术栈:**Vue 3 + TypeScript + Vite + Naive UI + Pinia + Vue Router**。

## 功能

- **AI 对话**(`/chat`):多角色、SSE 流式输出、会话历史、消息跳转/分享、复盘日报、TTS 朗读、RAG 知识库
- **PDF 阅读**(`/pdf-reader`):本地 PDF 阅读 + TTS 朗读
- **系统配置**(`/config/*`):系统主题、模型来源、模型管理、角色管理、用户配置
- **登录/注册**(`/login` `/register`)

## 运行

```bash
npm install
npm run dev      # http://localhost (端口 80,需管理员权限)
```

后端地址:开发环境走 `vite.config.js` 代理到 `http://localhost:8088`;
也可通过 `.env` 的 `VITE_API_BASE_URL` 指向远程后端。

生产构建:

```bash
npm run build    # 产物在 dist/
```

## 目录

```
src/
├─ modules/chat/         # 对话核心(页面/组件/组合逻辑/服务)
├─ modules/tools/pages/  # 系统配置页(模型/角色管理)
├─ modules/agent-lab/    # ChatReviewModal 依赖的 agentApi(对话复盘)
├─ app/                  # 路由 / 布局 / 首页
├─ config/ env.js        # 运行期配置
├─ services/ stores/     # 鉴权 / 主题 / 表情
└─ shared/               # 头像等公共工具
```

## 静态资源

- `public/emoji*` 与 `src/assets/chat-themes/cloud-immortal/*.webp` 为生成资源(已 gitignore),
  本地执行 `npm run copy:emoji` 与 `npm run build:theme-cloud-immortal` 重新生成。
- PDF 渲染使用 `pdfjs-dist`(动态加载)。

## 测试

```bash
npm run test:chat-sse
npm run test:pdf-reader
npm run test:virtual-list
```

## 配置

- `API_BASE_URL`:`.env` 的 `VITE_API_BASE_URL`(空则走同源代理)
- TTS 密钥:`.env` 的 `VITE_MIMO_TTS_DEFAULT_KEY`,或运行时 `localStorage('mimo-tts-api-key')`
