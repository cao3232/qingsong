package com.qingsong.ai.entity.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 收藏列表游标分页结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatFavoritePageVO {
    private List<ChatFavoriteItemVO> list;
    private boolean hasMore;
}
