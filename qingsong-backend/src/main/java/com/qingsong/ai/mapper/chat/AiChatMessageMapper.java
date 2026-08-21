package com.qingsong.ai.mapper.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingsong.ai.entity.po.chat.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {

    /**
     * 按角色统计消息数（含用户消息数）与会话数，按消息数降序
     */
    @Select("""
            SELECT s.role_code AS role_code,
                   COUNT(*) AS messages,
                   SUM(CASE WHEN m.message_type = 'USER' THEN 1 ELSE 0 END) AS user_messages,
                   COUNT(DISTINCT s.id) AS rounds
            FROM ai_chat_message m
            INNER JOIN ai_chat_session s ON m.session_id = s.id
            WHERE m.deleted = 0 AND s.deleted = 0 AND s.biz_type = 'chat'
              AND m.created_at >= #{start} AND m.created_at < #{end}
            GROUP BY s.role_code
            ORDER BY messages DESC
            """)
    List<Map<String, Object>> selectRoleStats(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /**
     * 按小时统计消息数（活跃时段分布）
     */
    @Select("""
            SELECT HOUR(m.created_at) AS hour, COUNT(*) AS messages
            FROM ai_chat_message m
            INNER JOIN ai_chat_session s ON m.session_id = s.id
            WHERE m.deleted = 0 AND s.deleted = 0 AND s.biz_type = 'chat'
              AND m.created_at >= #{start} AND m.created_at < #{end}
            GROUP BY HOUR(m.created_at)
            """)
    List<Map<String, Object>> selectHourStats(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /**
     * 当日会话列表（按最近活跃时间降序，供会话总结工作流取数）
     */
    @Select("""
            SELECT s.role_code AS role_code,
                   s.session_no AS session_no,
                   s.title AS title,
                   s.message_count AS message_count
            FROM ai_chat_session s
            WHERE s.biz_type = 'chat' AND s.deleted = 0
              AND s.last_message_at >= #{start} AND s.last_message_at < #{end}
            ORDER BY s.last_message_at DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectSessions(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("limit") int limit);

    /**
     * 指定会话最近 N 条用户消息（倒序，供会话总结工作流取数；SQL 直接过滤仅用户消息）
     */
    @Select("""
            SELECT m.seq_no AS seq_no,
                   m.message_type AS message_type,
                   m.content AS content
            FROM ai_chat_message m
            INNER JOIN ai_chat_session s ON m.session_id = s.id
            WHERE s.session_no = #{sessionNo} AND m.deleted = 0 AND m.message_type = 'USER'
            ORDER BY m.seq_no DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectSessionRecentMessages(@Param("sessionNo") String sessionNo,
                                                          @Param("limit") int limit);
}
