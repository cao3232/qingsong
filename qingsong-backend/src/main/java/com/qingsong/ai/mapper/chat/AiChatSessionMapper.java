package com.qingsong.ai.mapper.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingsong.ai.entity.po.chat.AiChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {

    /**
     * 按互斥时间窗口统计会话与消息数。
     * 每个会话只落入一个桶：今日 / 近7天 / 近30天 / 更早 / 全部。
     * 除“全部”外，各桶左闭右开，互不重叠，因此数字之和 = 全部。
     */
    @Select("""
            SELECT '今日' AS label,
                   COUNT(*) AS session_count,
                   COALESCE(SUM(message_count), 0) AS message_count,
                   MIN(created_at) AS first_chat_at,
                   MAX(last_message_at) AS last_chat_at
            FROM ai_chat_session
            WHERE deleted = 0 AND biz_type = #{bizType} AND role_code = #{roleCode}
              AND last_message_at >= CURDATE()
              AND last_message_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)

            UNION ALL
            SELECT '近7天',
                   COUNT(*),
                   COALESCE(SUM(message_count), 0),
                   MIN(created_at),
                   MAX(last_message_at)
            FROM ai_chat_session
            WHERE deleted = 0 AND biz_type = #{bizType} AND role_code = #{roleCode}
              AND last_message_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
              AND last_message_at < CURDATE()

            UNION ALL
            SELECT '近30天',
                   COUNT(*),
                   COALESCE(SUM(message_count), 0),
                   MIN(created_at),
                   MAX(last_message_at)
            FROM ai_chat_session
            WHERE deleted = 0 AND biz_type = #{bizType} AND role_code = #{roleCode}
              AND last_message_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
              AND last_message_at < DATE_SUB(CURDATE(), INTERVAL 7 DAY)

            UNION ALL
            SELECT '更早',
                   COUNT(*),
                   COALESCE(SUM(message_count), 0),
                   MIN(created_at),
                   MAX(last_message_at)
            FROM ai_chat_session
            WHERE deleted = 0 AND biz_type = #{bizType} AND role_code = #{roleCode}
              AND (last_message_at < DATE_SUB(CURDATE(), INTERVAL 30 DAY) OR last_message_at IS NULL)

            UNION ALL
            SELECT '全部',
                   COUNT(*),
                   COALESCE(SUM(message_count), 0),
                   MIN(created_at),
                   MAX(last_message_at)
            FROM ai_chat_session
            WHERE deleted = 0 AND biz_type = #{bizType} AND role_code = #{roleCode}
            """)
    List<Map<String, Object>> statByRange(@Param("bizType") String bizType, @Param("roleCode") String roleCode);

    /**
     * 游标分页查询会话列表。
     * 排序键为 COALESCE(last_message_at, created_at)（/chat/pre 预建的空会话 last_message_at 为 NULL，回退 created_at），
     * 同时间戳用主键 id 递减作第二游标，保证翻页稳定不重不漏。
     */
    @Select("""
            <script>
            SELECT s.id AS sessionDbId,
                   s.session_no AS id,
                   s.title AS title,
                   s.role_code AS role,
                   s.biz_type AS bizType,
                   s.message_count AS messageCount,
                   s.last_user_message_no AS lastUserMessageNo,
                   s.created_at AS createdAt,
                   s.last_message_at AS lastMessageAt
            FROM ai_chat_session s
            WHERE s.biz_type = #{bizType} AND s.role_code = #{roleCode} AND s.deleted = 0
            <if test="keyword != null and keyword != ''">
              AND s.title LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
            </if>
            <if test="start != null">
              AND COALESCE(s.last_message_at, s.created_at) &gt;= #{start}
            </if>
            <if test="end != null">
              AND COALESCE(s.last_message_at, s.created_at) &lt; #{end}
            </if>
            <if test="before != null and beforeId != null">
              AND (COALESCE(s.last_message_at, s.created_at) &lt; #{before}
                   OR (COALESCE(s.last_message_at, s.created_at) = #{before} AND s.id &lt; #{beforeId}))
            </if>
            ORDER BY COALESCE(s.last_message_at, s.created_at) DESC, s.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> selectPageByCursor(@Param("bizType") String bizType,
                                                 @Param("roleCode") String roleCode,
                                                 @Param("keyword") String keyword,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end,
                                                 @Param("before") LocalDateTime before,
                                                 @Param("beforeId") Long beforeId,
                                                 @Param("limit") int limit);

    /**
     * 有会话记录的日期集合（yyyy-MM-dd 降序），供前端日历高亮/禁用。
     * COALESCE 与分页口径一致：预建空会话按 created_at 计。
     */
    @Select("""
            SELECT DISTINCT DATE_FORMAT(COALESCE(last_message_at, created_at), '%Y-%m-%d') AS d
            FROM ai_chat_session
            WHERE biz_type = #{bizType} AND role_code = #{roleCode} AND deleted = 0
            ORDER BY d DESC
            """)
    List<String> selectActiveDates(@Param("bizType") String bizType, @Param("roleCode") String roleCode);
}
