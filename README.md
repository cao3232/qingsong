# qingsong-chat

青松 AI 对话(chat)功能的独立开源版本。

从个人项目 `qingsong`(后端 Spring Boot + 前端 Vue 3)中按「代码不变、只提取」原则拆出的对话模块,
含角色、模型配置、会话历史、消息流式输出(SSE)、TTS、对话复盘等能力。

> ⚠️ 本仓库为**独立拆分产物**,不含原项目其它业务模块(工作流/知识库/心理/格言/思路追踪等)。

## 仓库结构

```
qingsong-chat-extract/
├─ qingsong-front/     # Vue 3 + Vite + Naive UI 前端(chat 模块)
├─ qingsong-backend/   # Spring Boot 3 后端(chat 链路接口)
└─ sql/                # 数据库备份(schema + 通用配置数据导出脚本)
```

## 前后端一致性

前端 `src/modules/chat/services/` 的所有 API 调用,在后端均有对应接口。见各端 README 的接口对照表。

## 数据库

- **通用配置表**(可导数据,无隐私):`role`、`role_phrases`、`model_source`、`model_config`
- **会话/消息表**(仅 schema,不导真实数据):`ai_chat_session`、`ai_chat_message`、`chat_review`

详见 [`sql/README.md`](sql/README.md)。

## 运行

```bash
# 后端
cd qingsong-backend && cp secrets.example.yml secrets.yml && mvn spring-boot:run   # :8088

# 前端
cd qingsong-front && npm install && npm run dev                                     # :80
```

## License

[MIT](LICENSE)
