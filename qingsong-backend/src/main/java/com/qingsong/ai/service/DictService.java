package com.qingsong.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qingsong.ai.entity.po.dict.SysDict;
import com.qingsong.ai.entity.vo.dict.DictItemVO;
import com.qingsong.ai.entity.vo.dict.DictResponse;

import java.util.List;

/**
 * 业务字典服务
 */
public interface DictService {

    /**
     * 下发全量启用字典（优先读 Redis，miss 则查库回填）
     */
    DictResponse getAll();

    /**
     * 下发单个字典类型的启用项
     */
    List<DictItemVO> getByCode(String dictCode);

    /**
     * 管理端分页查询（含停用项）
     */
    IPage<SysDict> page(long pageNum, long pageSize, String dictCode);

    /**
     * 新增字典项（写库后刷新缓存）
     */
    boolean add(SysDict dict);

    /**
     * 更新字典项（写库后刷新缓存）
     */
    boolean update(SysDict dict);

    /**
     * 删除字典项（写库后刷新缓存）
     */
    boolean delete(Long id);

    /**
     * 手动刷新缓存（重建 dict:all 并递增版本号）
     */
    void refreshCache();
}
