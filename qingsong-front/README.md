<div align="center">

# 🖥 qingsong-chat-front

**AI 对话应用前端 —— 复古 OS 风格聊天界面 · SSE 流式渲染 · TTS 朗读 · PDF 阅读 · RAG 知识库**

![version](https://img.shields.io/badge/version-1.0.0-10b981)
![vue](https://img.shields.io/badge/Vue-3.4-42b883)
![vite](https://img.shields.io/badge/Vite-6-646cff)
![ui](https://img.shields.io/badge/UI-Naive%20UI-18a058)

</div>

---

## ✨ 功能

- 💬 **AI 对话**(`/chat`)— 多角色、SSE 流式输出、会话历史、消息跳转/分享、复盘日报、TTS 朗读、RAG 知识库
- 📄 **PDF 阅读**(`/pdf-reader`)— 本地 PDF 阅读 + TTS 朗读
- 📚 **知识库管理**(`/knowledge-base`)— 创建/维护知识库、上传文档,供对话 RAG 检索
- 🎛 **系统配置**(`/config/*`)— 系统主题、模型来源、模型管理、角色管理、用户配置
- 🔐 **登录 / 注册**(`/login` `/register`)

## 🚀 快速开始

```bash
npm install
npm run dev          # http://localhost (端口 80,需管理员权限)
npm run build        # 生产构建 → dist/ (含 type-check)
```

开发环境后端地址由 `vite.config.js` 代理到 `http://localhost:8088`;
也可通过 `.env` 的 `VITE_API_BASE_URL` 指向远程后端。

## 🗺 路由

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` | HomePage | 应用入口 |
| `/login` `/register` | BiophilicAuthPage | 登录 / 注册 |
| `/chat` | AIChatPage | 核心 AI 对话 |
| `/pdf-reader` | PDFReaderPage | PDF 阅读 + TTS |
| `/knowledge-base` | KnowledgeBasePage | 知识库列表/管理 |
| `/knowledge-base/:id` | KnowledgeBaseDetailPage | 知识库文档管理 |
| `/config/*` | SystemSetting / ModelSource / ModelManage / RoleManage / UserConfig | 系统配置 |

## 📁 目录结构

```
src/
├─ modules/chat/          # 对话核心(页面 / 组件 / 组合逻辑 / 服务)
├─ modules/knowledge-base/ # 知识库管理(文档上传 / RAG)
├─ modules/tools/pages/   # 系统配置页(模型 / 角色管理)
├─ modules/agent-lab/     # agentApi(对话复盘依赖)
├─ app/                   # 路由 / 布局 / 首页
├─ config/ env.js         # 运行期配置
├─ services/ stores/      # 鉴权 / 主题 / 表情
└─ shared/                # 公共样式与头像工具
```

## 🧪 测试

```bash
npm run test:chat-sse          # SSE 解析
npm run test:pdf-reader        # PDF 文本抽取
npm run test:virtual-list      # 虚拟列表
```

## ⚙️ 配置

| 配置项 | 位置 | 说明 |
|--------|------|------|
| `API_BASE_URL` | `.env` → `VITE_API_BASE_URL` | 后端地址(空则走同源代理) |
| TTS 密钥 | `.env` → `VITE_MIMO_TTS_DEFAULT_KEY` | 或运行时 `localStorage('mimo-tts-api-key')` |

> 📌 `public/emoji*` 表情资源(5 套 provider)**已随仓库提交**,开箱即用。
> 如需自定义,可执行 `npm run copy:emoji` 重新生成;
> `src/assets/chat-themes/` 云隐主题背景图已提交,`npm run build:theme-cloud-immortal` 可重新生成。
