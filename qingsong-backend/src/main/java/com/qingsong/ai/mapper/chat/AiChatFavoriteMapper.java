package com.qingsong.ai.mapper.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingsong.ai.entity.po.chat.AiChatFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiChatFavoriteMapper extends BaseMapper<AiChatFavorite> {

    /**
     * 收藏列表游标分页：按收藏时间倒序，同时间戳用主键 id 递减作第二游标（重收藏语义同会话分页）。
     * LEFT JOIN ai_chat_session 计算 sessionAlive、LEFT JOIN ai_chat_message 计算 messageAlive：
     * 原会话/原消息删除后快照仍完整返回，仅"查看原文"跳转能力相应降级置灰。
     */
    @Select("""
            <script>
            SELECT f.id AS favoriteId,
                   f.message_no AS messageNo,
                   f.session_no AS sessionNo,
                   f.role_code AS roleCode,
                   f.message_type AS messageType,
                   f.content AS content,
                   f.session_title AS sessionTitle,
                   f.chat_model AS chatModel,
                   f.message_created_at AS messageCreatedAt,
                   f.created_at AS createdAt,
                   (s.id IS NOT NULL) AS sessionAlive,
                   (m.id IS NOT NULL) AS messageAlive
            FROM ai_chat_favorite f
            LEFT JOIN ai_chat_session s
                   ON s.session_no = f.session_no AND s.deleted = 0
            LEFT JOIN ai_chat_message m
                   ON m.message_no = f.message_no AND m.deleted = 0
            WHERE f.user_id = #{userId}
            <if test="keyword != null and keyword != ''">
              AND (f.content LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
                   OR f.session_title LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!')
            </if>
            <if test="roleCode != null and roleCode != ''">
              AND f.role_code = #{roleCode}
            </if>
            <if test="before != null and beforeId != null">
              AND (f.created_at &lt; #{before}
                   OR (f.created_at = #{before} AND f.id &lt; #{beforeId}))
            </if>
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> selectPageByCursor(@Param("userId") Long userId,
                                                 @Param("keyword") String keyword,
                                                 @Param("roleCode") String roleCode,
                                                 @Param("before") LocalDateTime before,
                                                 @Param("beforeId") Long beforeId,
                                                 @Param("limit") int limit);

    /**
     * 批量查询已收藏的 messageNo（聊天页星标回显）。
     */
    @Select("""
            <script>
            SELECT message_no AS messageNo
            FROM ai_chat_favorite
            WHERE user_id = #{userId}
              AND message_no IN
              <foreach collection="messageNos" item="no" open="(" separator="," close=")">
                  #{no}
              </foreach>
            </script>
            """)
    List<String> selectFavoritedMessageNos(@Param("userId") Long userId,
                                           @Param("messageNos") List<String> messageNos);
}
