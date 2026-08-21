# qingsong-chat-backend

青松 AI 对话(chat)后端,从个人项目按「代码不变、只提取」原则拆分。

技术栈:**Spring Boot 3 + Java 17 + Maven + MyBatis-Plus + Sa-Token + Spring AI + MySQL + Redis + pgvector + MinIO**。

## 运行

```bash
cp secrets.example.yml secrets.yml   # 填入你自己的密钥
mvn spring-boot:run                  # :8088
```

> 依赖中间件:本地 MySQL(`big_event`)、Redis(:6379)、pgvector(:5432)、MinIO(:9032)。
> 未配置时 chat 主流程(对话/历史/角色)仍可用,文件/RAG 相关能力受限。

## 提取范围

仅包含 chat 链路相关的包:

| 包 | 内容 |
|----|------|
| `controller/chat` | ChatController(`/ai/chat` SSE)、ChatHistoryController(`/ai/history`)、ChatReviewController(`/ai/stats`)、MessageController(`/message/send-email-html`)、ToolController(`/tools/name`) |
| `controller/model` | ModelSourceController(`/api/model-sources`)、ModelConfigController(`/api/model-configs`) |
| `controller/role` | RolesController(`/roles`、`/admin/roles`)、RolePhrasesController(`/api/quick-phrases`) |
| `controller/knowledge` | KnowledgeController(`/api/knowledge/bases` RAG 知识库列表) |
| `controller/user` | UserConfigController(`/user-config` 登录/注册/会话校验) |
| `service` / `service/impl` | ChatService、ChatPersistenceService、ChatHistoryService、ChatReviewService、Role/Model/Email/Export/Rag 服务 |
| `entity` | `po/chat`、`po/role`、`po/model`、`po/knowledge`、`po/user` + 相关 dto/vo |
| `mapper` | chat(3)、role(2)、model(2)、knowledge(2)、user(1) |
| `config` / `aspect` / `advice` / `utils` 等 | Sa-Token、MyBatis-Plus、Redis、MinIO、pgvector 等基础设施 |

**未包含**:workflow / topic / motto / code-snippet / interview / flowable / rocketmq 等无关业务,
以及原项目中 `/ai/game`、`/ai/service`、`/ai/pdf/*`、`/api/tts`、`/audio` 等场景接口。

## 数据库

表结构与数据备份见 `../sql/`:
- 通用配置表(可导数据):`role`、`role_phrases`、`model_source`、`model_config`
- 会话/消息表(仅 schema):`ai_chat_session`、`ai_chat_message`、`chat_review`

角色默认种子在 `config/MyRolesConfig.java`(代码配置)。

## 配置

- `application.yaml` / `ai.yml`:通过 `${VAR}` 占位符引用密钥,由 `secrets.yml` 注入
  (`spring.config.import: optional:file:./secrets.yml`)。
- `secrets.example.yml`:模板,复制为 `secrets.yml` 填写真实值。真实密钥不入库。

## 接口对照

前端调用点集中在 `qingsong-front/src/modules/chat/services/`(`chatService.js` / `roleService.js` /
`rolePhrasesService.js` / `ragService.js` / `ttsService.js`),与本后端一一对应。
