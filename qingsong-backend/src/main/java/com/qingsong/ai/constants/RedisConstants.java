package com.qingsong.ai.constants;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2025/12/07 21:39
 */
public enum RedisConstants {


    USER_ROLE_HISTORY_KEY("user_role_history:%s:%s"),
    USER_ROLE_HISTORY_MESSAGE_KEY("user_role_history_chat:%s:%s"),
    INNER_PROMPT_INCR_KEY("inner_prompt_incr_key"),

    /** 角色总榜（zset：member=roleId，score=累计使用次数） */
    ROLE_USAGE_TOTAL_KEY("role:usage:total"),
    /** 角色今日榜（zset，%s=yyyyMMdd，当日过期自动重置） */
    ROLE_USAGE_TODAY_KEY("role:usage:today:%s"),
    /** 最近对话角色（string：roleId，每次发起对话请求时覆盖） */
    ROLE_USAGE_LAST_KEY("role:usage:last"),

    /** 业务字典全量缓存（string：JSON，Map<dictCode, List<DictItemVO>>） */
    DICT_ALL_KEY("dict:all"),
    /** 业务字典版本号（string：整数，写操作后 INCR） */
    DICT_VERSION_KEY("dict:version");


    private String redisKey;

    RedisConstants(String redisKey) {
        this.redisKey = redisKey;
    }

    public String getRedisKey() {
        return redisKey;
    }
}
