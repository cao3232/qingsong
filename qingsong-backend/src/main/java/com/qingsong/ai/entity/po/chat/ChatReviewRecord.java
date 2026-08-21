package com.qingsong.ai.entity.po.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对话复盘记录：按天持久化统计数据快照与 AI 解读全文。
 */
@Data
@TableName("chat_review")
public class ChatReviewRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 复盘日期 */
    private LocalDate reviewDate;

    /** 当日统计数据快照（summary/leaderboard/sessions） */
    private String dataJson;

    /** AI 随笔（季羡林风格） */
    private String essay;

    /** 会话总结 JSON 数组 */
    private String sessionSummariesJson;

    /** 角色小结 JSON 数组 */
    private String roleSummariesJson;

    /** 生成模型名 */
    private String model;

    /** 状态：PENDING/DONE/FAILED */
    private String status;

    /** 失败原因 */
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
