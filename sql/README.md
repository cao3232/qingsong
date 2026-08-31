# 🗄 数据库备份说明

本目录存放 chat 功能的数据库备份与结构。

## 表分类

| 表 | 类型 | 备份策略 |
|----|------|----------|
| `role` | 通用配置(无隐私) | schema 已提交;含种子数据(seed-data.sql) |
| `role_phrases` | 通用配置(无隐私) | schema 已提交;含种子数据 |
| `model_source` | 通用配置(无隐私) | schema 已提交;含种子(api_key 留空待填) |
| `model_config` | 通用配置(无隐私) | schema 已提交;含种子 |
| `user_config` | 通用配置(无隐私) | schema 已提交;含默认账号(admin/admin123) |
| `knowledge_base` | 通用配置(无隐私) | schema 已提交 |
| `sys_dict` | 通用配置(无隐私) | schema 已提交(业务字典,音色设计预设等) |
| `ai_chat_session` | 用户会话 | **仅 schema,禁止导数据** |
| `ai_chat_message` | 用户消息 | **仅 schema,禁止导数据** |
| `ai_chat_favorite` | 消息收藏(快照) | **仅 schema,禁止导数据** |
| `chat_review` | 对话复盘(含真实对话聚合) | **仅 schema,禁止导数据** |

## 文件

- `chat-schema.sql` — 会话/消息/复盘表结构(来源 `qingsong-backend/src/main/resources/db/chat-schema.sql`)
- `chat-favorite-schema.sql` — 消息收藏表结构(来源 `qingsong-backend/src/main/resources/db/chat-favorite-schema.sql`)
- `config-schema.sql` — 角色/短语/模型来源/模型配置 + `user_config`(登录) + `knowledge_base`(RAG)表结构
- `sys-dict-schema.sql` — `sys_dict` 业务字典表结构(来源 `qingsong-backend/src/main/resources/db/sys-dict-schema.sql`)
- `seed-data.sql` — **初始化种子数据(开箱即用)**:4 个角色 + 快捷短语 + 模型来源/配置 + 默认账号 `admin / admin123`
- `backup-data.ps1` / `backup-data.sh` — 导出**通用配置表数据**的脚本(产物 `data-backup.sql` 已被 .gitignore 忽略,不会提交)

## 初始化(首次部署)

```bash
# 方式一:本地 MySQL
mysql < sql/chat-schema.sql
mysql < sql/chat-favorite-schema.sql
mysql < sql/config-schema.sql
mysql < sql/seed-data.sql
mysql < sql/sys-dict-schema.sql

# 方式二:Docker Compose
# 已自动挂载到 /docker-entrypoint-initdb.d/,首次启动自动执行 01→02→03→04→05
```

> 种子数据后请到「系统配置 → 模型来源」填入你的 API Key,并尽快修改默认账号密码。

## 如何导出配置数据

PowerShell(Windows):

```powershell
.\sql\backup-data.ps1 -User root -Password '你的密码' -Database big_event
```

Bash(macOS/Linux):

```bash
./sql/backup-data.sh -u root -p '你的密码' big_event
```

## 隐私红线

1. `ai_chat_session` / `ai_chat_message` / `chat_review` 只保留建表语句,任何情况下不导出真实行。
2. `model_source.api_key` 若确需导出,请先脱敏再提交。
3. `data-backup.sql` 已加入 `.gitignore`,不要用 `git add -f` 强制提交。
