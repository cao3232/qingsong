package com.qingsong.ai.mapper.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingsong.ai.entity.po.chat.AiChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
