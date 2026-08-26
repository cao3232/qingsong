<div align="center">

# 🍃 qingsong-chat

**开箱即用的开源 AI 对话平台 —— 对话 / 知识 / 工具 / 复盘一体化,前后端完整,一条命令启动全栈**

![version](https://img.shields.io/badge/version-1.0.0-10b981)
![license](https://img.shields.io/badge/license-MIT-blue)
![frontend](https://img.shields.io/badge/frontend-Vue%203%20%2F%20Vite-42b883)
![backend](https://img.shields.io/badge/backend-Spring%20Boot%203-6db33f)
![build](https://img.shields.io/badge/build-passing-brightgreen)
[![LINUX DO](https://img.shields.io/badge/社区-LINUX%20DO-0ea5e9)](https://linux.do)

多角色对话 · SSE 流式输出 · TTS 语音 · PDF 阅读 · RAG 知识库 · AI 工具调用 · MCP 工具 · 会话复盘 · 模型管理

**🤝 本项目已链接并认可 LINUX DO 社区**

</div>

---



## ✨ 功能特性

| 能力 | 说明 |
|------|------|
| 💬 **多角色对话** | 内置多套角色人设,支持自定义、收藏、搜索、拖拽排序与常用语快捷插入 |
| ⚡ **流式输出** | 打字机式实时呈现,随时可中断,网络波动自动续接不丢消息 |
| 🧵 **会话管理** | 历史保存/改名/删除、全文搜索定位;消息支持编辑、复制、重发、删除,可导出 HTML 邮件 |
| 🎨 **富内容呈现** | Markdown、代码高亮、LaTeX 公式、Mermaid 图表、Emoji 表情一站式渲染 |
| 🔊 **语音朗读 TTS** | 流式朗读对话内容,预置音色 / 音色克隆 / 音色设计,语速 0.75x~1.5x 可调 |
| 📄 **PDF 阅读器** | 内置本地 PDF 阅读 + 大纲跳转,与朗读无缝衔接,无需上传边看边听 |
| 🧠 **知识库问答** | 挂载知识库做检索增强回答,上传文档即问即答 |
| 🛠 **工具调用可视化** | AI 实时调用工具,执行过程渲染成步骤卡片,推理不再黑盒 |
| 🔌 **MCP 扩展** | 内置 MCP 连接框架,可接入任意第三方 MCP 服务器扩展 AI 能力 |
| 🎛 **模型管理** | 多来源多模型(OpenAI/DeepSeek/Qwen/任意兼容中转)自由切换,即配即测 |
| 📊 **对话复盘** | 按日生成角色/会话/消息统计与 AI 解读报告 |
| 🪞 **深度换肤** | 9 套聊天皮肤、60 种页面背景、面板与代码配色,深浅色一键切换 |
| ⚙️ **配置中心** | 账号、模型来源、角色管理、字典(音色预设)、表情快捷键一体化管理 |

## 🛠️ 内置 AI 工具

工具通过注册式框架装配,调用全程以步骤卡片实时呈现。

| 工具 | 说明 |
|------|------|
| ✏️ **保存提示词** | 对话中把需求直接沉淀为角色模板(名称 + 中英文提示词 + 温度 + 说明) |
| 🔍 **查询提示词** | 按名称检索已保存的角色模板,辅助优化当前角色 |
| 🧠 **会话记忆** | 对话关键内容存档,AI 拥有角色级长期记忆,跨会话不重复产出 |
| 🔌 **MCP 接入** | 面向任何第三方 MCP 服务器开放,一条龙扩展 AI 工具集 |

## 🎨 支持的主题

| 类型 | 数量 | 内容 |
|------|------|------|
| 聊天皮肤 | 9 套 | 复古 Win95、明亮现代、暗色现代、纸墨阅读、翡翠绿、终端黑、珊瑚晚霞、国风水墨、云海仙门 |
| 页面背景 | 60 套 | 基础、自然风光、缤纷色彩、游戏主题、国风雅韵、其他六大类 |
| 聊天面板背景 | 16 套 | 纯净 / 温暖 / 清新 / 宁静 / 优雅 / 柔和 / 深色、透明玻璃及多款渐变 |
| 面板玻璃背景 | 5 套 | 明亮玻璃、深邃玻璃、清澈、朦胧、深沉 |
| 行内代码背景 | 8 套 | 浅灰、淡黄、淡蓝、淡紫、淡绿、淡橙、淡粉、淡青 |
| 自定义 | 无限 | 全场配色可调,支持云雾 / 纸面 / 翠玉等纹理素材 |

## 🚀 项目亮点

- **一键全栈启动**:Docker Compose 编排 MySQL / Redis / MinIO / pgvector + 前后端,种子数据直接起跑
- **工具调用看得见**:AI 调用工具时,执行过程同步渲染为步骤卡片,过程透明可追溯
- **读听一体化**:PDF 阅读 + TTS 朗读无缝衔接,可读可听
- **个性化到极致**:9 套复古/现代皮肤、60 种背景配色、音色克隆与设计的自由组合
- **开放可扩展**:注册式工具 + MCP 接入,对话、知识、工具、复盘全链路开源可二次开发

## 🛠 完整技术栈

| 端 | 技术 |
|----|------|
| 🖥 前端 | Vue 3 · TypeScript · Vite · Vue Router · Pinia · Naive UI · Sass |
| | 渲染:marked · highlight.js · KaTeX · Mermaid · DOMPurify · 五套 Emoji 表情 |
| | PDF/TTS:pdfjs-dist · Web Audio API(PCM 流式播放) |
| | 组件:@heroicons/vue · @tanstack/vue-virtual(虚拟列表)· @vueuse/core · vuedraggable · html-to-image |
| ⚙️ 后端 | Spring Boot 3.5 · Java 17 · Reactor(SSE 流式)· Spring AI(OpenAI 兼容模型接入) |
| | 向量检索:PostgreSQL + pgvector · 文档解析:spring-ai-pdf-document-reader |
| | MCP:spring-ai-starter-mcp-server / mcp-client-webflux |
| | 持久层:MyBatis-Plus · 鉴权:Sa-Token(会话持久化到 Redis) |
| | 缓存与锁:Redis · Redisson(分布式会话锁)· Caffeine |
| | 导出:EasyExcel / POI · flexmark-all · Thymeleaf · 邮件推送 |
| | 文档:SpringDoc OpenAPI(Swagger)· 观测:Actuator · Spring Retry / AOP / Validation |
| 🗄 存储 | MySQL 8(主库)· Redis(缓存 / 会话 / 锁)· PostgreSQL + pgvector(向量库)· MinIO(对象存储) |
| 🎙 语音 | MiMo TTS(前端直连,`pcm16` SSE 流式 + Web Audio 实时播放,key 可配置) |

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
