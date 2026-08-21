package com.qingsong.ai.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingsong.ai.entity.po.user.UserConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户配置表 Mapper 接口
 *
 * @author : caojiangjiang
 * @data : 2026/04/26
 */
@Mapper
public interface UserConfigMapper extends BaseMapper<UserConfig> {

}
