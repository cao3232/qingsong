-- ============================================================
-- qingsong-chat 通用配置表结构(schema + 说明)
-- 来源:qingsong-backend 实体类(Role/RolePhrases/ModelSource/ModelConfig)
-- 说明:这些是【通用配置表,不涉及隐私】,可导出数据作为种子。
--       角色默认种子其实在 Java 配置类 MyRolesConfig 中(随后端代码提取),
--       role 表仅承载管理端(/admin/roles)的增删改与排序收藏。
--       数据导出请运行 backup-data.ps1 / backup-data.sh,勿提交真实数据到 git。
-- ============================================================

-- 角色表(管理端 CRUD;对话侧默认角色来自 MyRolesConfig 配置类)
CREATE TABLE IF NOT EXISTS role
(
    id          VARCHAR(64)  PRIMARY KEY COMMENT '角色ID',
    name        VARCHAR(255) NOT NULL COMMENT '角色名称',
    value       VARCHAR(255) NULL COMMENT '角色值/系统提示词引用',
    value_en    VARCHAR(255) NULL COMMENT '角色英文值',
    favor       VARCHAR(8)   NULL DEFAULT '0' COMMENT '是否收藏(1=收藏)',
    temperature DOUBLE       NULL COMMENT '采样温度',
    sort        BIGINT       NULL DEFAULT 1000000 COMMENT '排序权重',
    create_date DATETIME     NULL COMMENT '创建时间',
    update_date DATETIME     NULL COMMENT '更新时间',
    description TEXT         NULL COMMENT '角色描述'
) COMMENT = 'AI角色表';

-- 角色快捷短语表
CREATE TABLE IF NOT EXISTS role_phrases
(
    id      BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    role_id BIGINT       NULL COMMENT '角色ID',
    phrase  VARCHAR(255) NOT NULL COMMENT '快捷短语'
) COMMENT = '角色快捷短语表';

-- 模型来源表(API 服务商)
CREATE TABLE IF NOT EXISTS model_source
(
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '来源唯一标识',
    source_code  VARCHAR(64)  NOT NULL COMMENT '来源编码,如 OPENAI/AZURE/LOCAL',
    source_name  VARCHAR(128) NOT NULL COMMENT '来源名称',
    api_base_url VARCHAR(512) NULL COMMENT 'API基础地址',
    api_key      VARCHAR(512) NULL COMMENT 'API密钥(导库时注意脱敏)',
    is_active    TINYINT(1)   NULL DEFAULT 1 COMMENT '是否启用',
    sort_order   INT          NULL DEFAULT 0 COMMENT '排序,数值越小越靠前',
    create_date  DATETIME     NULL COMMENT '创建时间',
    update_date  DATETIME     NULL COMMENT '更新时间'
) COMMENT = '模型来源表';

-- 模型配置表(具体模型项,关联 model_source)
CREATE TABLE IF NOT EXISTS model_config
(
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '模型配置唯一标识',
    name        VARCHAR(128) NOT NULL COMMENT '模型名称',
    code        VARCHAR(128) NOT NULL COMMENT '模型值/配置信息',
    type        VARCHAR(64)  NULL COMMENT '模型类型,如 LLM/EMBEDDING',
    model_source BIGINT       NULL COMMENT '模型来源ID,关联model_source.id',
    model_order INT          NULL DEFAULT 0 COMMENT '排序,数值越小优先级越高',
    is_active   TINYINT(1)   NULL DEFAULT 1 COMMENT '是否激活',
    created_by  VARCHAR(64)  NULL COMMENT '创建人',
    updated_by  VARCHAR(64)  NULL COMMENT '更新人',
    create_date DATETIME     NULL COMMENT '创建时间',
    update_date DATETIME     NULL COMMENT '更新时间',
    is_top      TINYINT(1)   NULL DEFAULT 0 COMMENT '是否默认置顶'
) COMMENT = '模型配置表';
