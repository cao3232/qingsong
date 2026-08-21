package com.qingsong.ai.entity.vo.chat;

import lombok.Data;

@Data
public class RedisMigrationSummary {

    private int scannedSessionCount;

    private int migratedSessionCount;

    private int skippedSessionCount;

    private int migratedMessageCount;
}
