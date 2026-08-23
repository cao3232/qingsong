<div align="center">

# 🍃 qingsong-chat

**一个开源的 AI 对话应用 · 前后端一体 · 即开即用**

![version](https://img.shields.io/badge/version-1.0.0-10b981)
![license](https://img.shields.io/badge/license-MIT-blue)
![frontend](https://img.shields.io/badge/frontend-Vue%203%20%2F%20Vite-42b883)
![backend](https://img.shields.io/badge/backend-Spring%20Boot%203-6db33f)
![build](https://img.shields.io/badge/build-passing-brightgreen)
[![LINUX DO](https://img.shields.io/badge/社区-LINUX%20DO-0ea5e9)](https://linux.do)

多角色对话 · SSE 流式输出 · 会话历史 · TTS 语音 · PDF 阅读 · RAG 知识库 · AI 工具调用 · 模型管理

**🤝 本项目已链接并认可 LINUX DO 社区**

</div>

---



## ✨ 功能特性

| 能力 | 说明 |
|------|------|
| 💬 **多角色对话** | 内置多套角色人设,支持收藏、排序、自定义管理 |
| ⚡ **SSE 流式输出** | 打字机式实时返回,支持中断/取消、Token 用量统计 |
| 🗂 **会话历史** | 会话列表、改名、删除、消息搜索跳转、导出与邮件发送 |
| 🔊 **语音合成 TTS** | 流式朗读对话内容,多音色可选 |
| 📄 **PDF 阅读器** | 本地 PDF 阅读 + TTS 朗读,无需上传 |
| 🧠 **RAG 知识库增强** | 对话时可挂载知识库做检索增强回答 |
| 🛠 **AI 工具调用** | 对话中可调用注册工具,支持快捷短语 |
| 🎛 **模型管理** | 多来源(OpenAI/DeepSeek/Qwen/任意 OpenAI 兼容中转)+ 多模型配置与一键切换 |
| 📊 **对话复盘** | 按日聚合角色/会话/消息统计,生成解读报告 |

## 🛠 技术栈

| 端 | 技术 |
|----|------|
| 🖥 前端 | Vue 3 · TypeScript · Vite · Naive UI · Pinia · Vue Router · pdfjs-dist |
| ⚙️ 后端 | Spring Boot 3.5 · Java 17 · Maven · MyBatis-Plus · Sa-Token · Spring AI · Reactor |
| 🗄 存储 | MySQL(主库) · Redis(缓存) · PostgreSQL + pgvector(RAG 向量库) · MinIO(对象存储) |

## 📁 目录结构

```
├─ qingsong-front/        # 前端(Vue 3)
├─ qingsong-backend/      # 后端(Spring Boot)
├─ sql/                   # 数据库表结构 + 配置数据备份脚本
└─ env/                   # Docker Compose 编排(基础设施 + 前后端)
```

## 🚀 快速开始

### 方式一:Docker Compose(推荐)

```bash
cd env
cp .env.example .env        # 填写 AI_OPENAI_API_KEY 等必填项
docker compose up -d        # 一键启动 MySQL/Redis/pgvector/MinIO + 前后端
```

- 🖥 前端:`http://localhost`
- ⚙️ 后端 API:`http://localhost:8088` · Swagger:`http://localhost:8088/swagger-ui.html`

### 方式二:本地运行

依赖:JDK 17、Maven、Node 20+、MySQL、Redis、MinIO、PostgreSQL(pgvector)。

```bash
# 1️⃣ 数据库:创建 big_event 库,导入表结构与种子数据
#    mysql < sql/chat-schema.sql
#    mysql < sql/config-schema.sql
#    mysql < sql/seed-data.sql
#    (docker compose 首次启动会自动执行,无需手动导入)

# 2️⃣ 后端
cd qingsong-backend
cp src/main/resources/secrets.example.yml src/main/resources/secrets.yml   # 填密钥
mvn spring-boot:run                                                        # :8088

# 3️⃣ 前端
cd qingsong-front
npm install
npm run dev                                                                # :80
```

> 🎫 **默认账号**:`admin / admin123`(登录后请尽快修改)。
> ⚙️ 首次使用请到「系统配置 → 模型来源」填入你的 API Key(种子数据中留空,直接填 Key 即可用)。
> 🛠 **后端必须用 JDK 17 构建**(推荐),JDK 过新会触发 Lombok 编译报错(`JCImport qualid`),详见后端 README。

## 🗄 数据库

| 表 | 类型 | 说明 |
|----|------|------|
| `role` / `role_phrases` | 通用配置 | 角色与快捷短语(含种子数据,chat 的角色唯一来源) |
| `model_source` / `model_config` | 通用配置 | 模型来源与模型配置(含种子,Key 待填) |
| `user_config` | 通用配置 | 登录账号(含默认账号 `admin/admin123`) |
| `knowledge_base` | 通用配置 | RAG 知识库 |
| `ai_chat_session` / `ai_chat_message` | 会话数据 | 会话与消息(仅结构,不含真实数据) |
| `chat_review` | 会话数据 | 对话复盘(仅结构) |

详见 [`sql/README.md`](sql/README.md)。

## 📖 子项目说明

- [前端 `qingsong-front`](qingsong-front/README.md) — 页面、组件、服务、配置
- [后端 `qingsong-backend`](qingsong-backend/README.md) — 接口、模块、配置

## 📌 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2026-08-21 | 首个开源版本 |

## 📄 License

[MIT](LICENSE)

## 社区交流

> **真诚、友善、团结、专业** —— LINUX DO 社区

[学AI就来Linux DO](https://linux.do/)
