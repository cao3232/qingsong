CREATE TABLE IF NOT EXISTS sys_dict (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    dict_code   VARCHAR(64)  NOT NULL COMMENT '字典类型编码，如 chat_model',
    item_key    VARCHAR(255) NOT NULL COMMENT '字典项值，前端表单存这个',
    item_label  VARCHAR(128) NOT NULL COMMENT '展示文案',
    item_extra  VARCHAR(500) DEFAULT NULL COMMENT '附加JSON(可选)',
    sort        INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 1 COMMENT '1启用/0停用',
    remark      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_dict_code_item_key (dict_code, item_key),
    KEY idx_code_status_sort (dict_code, status, sort)
) COMMENT='业务字典表';

-- 已建过表的老库执行下面这句扩容（值留足余量）
ALTER TABLE sys_dict MODIFY COLUMN item_key VARCHAR(255) NOT NULL COMMENT '字典项值，前端表单存这个';
