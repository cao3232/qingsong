package com.qingsong.ai.service.chat;

import com.qingsong.ai.entity.vo.chat.RedisMigrationSummary;

public interface LegacyRedisChatMigrationService {

    RedisMigrationSummary migrateAll();
}
