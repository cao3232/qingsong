# 🗄 数据库备份说明

本目录存放 chat 功能的数据库备份与结构。

## 表分类

| 表 | 类型 | 备份策略 |
|----|------|----------|
| `role` | 通用配置(无隐私) | schema 已提交;数据建议导出(`--data-only`) |
| `role_phrases` | 通用配置(无隐私) | schema 已提交;数据建议导出 |
| `model_source` | 通用配置(无隐私) | schema 已提交;数据建议导出(注意 `api_key` 脱敏) |
| `model_config` | 通用配置(无隐私) | schema 已提交;数据建议导出 |
| `ai_chat_session` | 用户会话 | **仅 schema,禁止导数据** |
| `ai_chat_message` | 用户消息 | **仅 schema,禁止导数据** |
| `chat_review` | 对话复盘(含真实对话聚合) | **仅 schema,禁止导数据** |

## 文件

- `chat-schema.sql` — 会话/消息/复盘表结构(来源 `qingsong-backend/src/main/resources/db/chat-schema.sql`)
- `config-schema.sql` — 角色/短语/模型来源/模型配置表结构(来源实体类)
- `backup-data.ps1` / `backup-data.sh` — 导出**通用配置表数据**的脚本(产物 `data-backup.sql` 已被 .gitignore 忽略,不会提交)

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
