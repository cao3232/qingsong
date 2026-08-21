# qingsong-chat-front

青松 AI 对话(chat)前端模块,从个人项目按「代码不变、只提取」原则拆分。

技术栈:**Vue 3 + TypeScript + Vite + Naive UI + Pinia + Vue Router**。

## 路由

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` | HomePage | 应用入口(3 个卡片) |
| `/login` `/register` | BiophilicAuthPage | 登录 / 注册 |
| `/chat` | AIChatPage | **核心 AI 对话**(SSE 流式、角色、会话历史、TTS、复盘) |
| `/pdf-reader` | PDFReaderPage | PDF 阅读 + TTS 朗读 |
| `/config/*` | 系统配置 | 系统/模型来源/模型管理/角色管理/用户配置 |

> 原项目中的 `/game`、`/customer-service`、`/chat-pdf` 三个死路由页面及其 composables
> (GameChatPage/CustomerServicePage/ChatPDFPage)未提取,新项目不注册。

## 运行

```bash
npm install
npm run dev      # http://localhost (端口 80,需管理员权限)
```

后端地址通过 `vite.config.js` 代理到 `http://localhost:8088`;也可用 `.env` 的
`VITE_API_BASE_URL` 指向远程后端。

## 静态资源说明

- `public/emoji*`(表情 svg)与 `src/assets/chat-themes/cloud-immortal/*.webp` 为生成资源,已 gitignore。
  本地执行以下脚本重新生成:
  ```bash
  npm run copy:emoji                  # openmoji + fluent + trend 表情
  npm run build:theme-cloud-immortal  # 云隐主题背景图
  ```
- PDF 渲染使用 `pdfjs-dist`(动态加载),`public/pdf.worker.js` 已包含。

## 测试

```bash
npm run test:chat-sse
npm run test:pdf-reader
npm run test:virtual-list
```

## 与后端对应

所有 API 调用位于 `src/modules/chat/services/`,与 `qingsong-chat-extract/qingsong-backend` 接口一一对应。
详见根目录 README 的接口对照。

## 安全说明

- 已移除原项目 `src/config/env.js` 中硬编码的 MiMo TTS API Key,改为通过
  `VITE_MIMO_TTS_DEFAULT_KEY` 环境变量或 `localStorage('mimo-tts-api-key')` 注入。
- 未包含任何真实密钥;`.env*` 已在 `.gitignore` 中忽略。
