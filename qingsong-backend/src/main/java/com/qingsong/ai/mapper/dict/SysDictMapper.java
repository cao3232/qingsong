package com.qingsong.ai.mapper.dict;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingsong.ai.entity.po.dict.SysDict;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

/**
 * 业务字典数据访问接口
 */
@Mapper
@Repository
public interface SysDictMapper extends BaseMapper<SysDict> {
}
