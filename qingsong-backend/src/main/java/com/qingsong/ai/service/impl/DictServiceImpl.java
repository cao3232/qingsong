package com.qingsong.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingsong.ai.constants.RedisConstants;
import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.entity.po.dict.SysDict;
import com.qingsong.ai.entity.vo.dict.DictItemVO;
import com.qingsong.ai.entity.vo.dict.DictResponse;
import com.qingsong.ai.mapper.dict.SysDictMapper;
import com.qingsong.ai.service.DictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务字典服务：DB 为数据源，Redis 做缓存（String 结构）。
 * 启动时全量加载写入 dict:all；写操作后 refreshCache 重建缓存并递增版本号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService, ApplicationRunner {

    private static final Duration CACHE_TTL = Duration.ofDays(1);

    private final SysDictMapper sysDictMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        loadAndBuild();
        ensureVersion();
        log.info("业务字典缓存初始化完成");
    }

    @Override
    public DictResponse getAll() {
        String json = stringRedisTemplate.opsForValue().get(RedisConstants.DICT_ALL_KEY.getRedisKey());
        Map<String, List<DictItemVO>> items = parseItems(json);
        if (items == null) {
            items = loadAndBuild();
            ensureVersion();
        }
        return buildResponse(items);
    }

    @Override
    public List<DictItemVO> getByCode(String dictCode) {
        Map<String, List<DictItemVO>> items = getAll().getItems();
        return items.getOrDefault(dictCode, List.of());
    }

    @Override
    public IPage<SysDict> page(long pageNum, long pageSize, String dictCode) {
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasText(dictCode)) {
            queryWrapper.like("dict_code", dictCode);
        }
        queryWrapper.orderByAsc("dict_code").orderByAsc("sort").orderByAsc("id");
        return sysDictMapper.selectPage(new Page<>(pageNum, pageSize), queryWrapper);
    }

    @Override
    public boolean add(SysDict dict) {
        checkUnique(dict.getDictCode(), dict.getItemKey(), null);
        dict.setId(null);
        if (dict.getStatus() == null) {
            dict.setStatus(1);
        }
        dict.setCreatedAt(LocalDateTime.now());
        dict.setUpdatedAt(LocalDateTime.now());
        boolean ok = sysDictMapper.insert(dict) > 0;
        if (ok) {
            refreshCache();
        }
        return ok;
    }

    @Override
    public boolean update(SysDict dict) {
        checkUnique(dict.getDictCode(), dict.getItemKey(), dict.getId());
        dict.setUpdatedAt(LocalDateTime.now());
        boolean ok = sysDictMapper.updateById(dict) > 0;
        if (ok) {
            refreshCache();
        }
        return ok;
    }

    @Override
    public boolean delete(Long id) {
        boolean ok = sysDictMapper.deleteById(id) > 0;
        if (ok) {
            refreshCache();
        }
        return ok;
    }

    @Override
    public void refreshCache() {
        loadAndBuild();
        stringRedisTemplate.opsForValue().increment(RedisConstants.DICT_VERSION_KEY.getRedisKey());
    }

    private void checkUnique(String dictCode, String itemKey, Long excludeId) {
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dict_code", dictCode).eq("item_key", itemKey);
        if (excludeId != null) {
            queryWrapper.ne("id", excludeId);
        }
        if (sysDictMapper.selectCount(queryWrapper) > 0) {
            throw new BusinessException("该字典下值已存在：" + itemKey);
        }
    }

    /**
     * 从 DB 全量加载启用项，分组排序后写入 dict:all。
     *
     * @return 分组建好的字典项
     */
    private Map<String, List<DictItemVO>> loadAndBuild() {
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1);
        queryWrapper.orderByAsc("dict_code").orderByAsc("sort").orderByAsc("id");
        List<SysDict> enabled = sysDictMapper.selectList(queryWrapper);

        Map<String, List<DictItemVO>> items = new LinkedHashMap<>();
        for (SysDict dict : enabled) {
            items.computeIfAbsent(dict.getDictCode(), k -> new ArrayList<>())
                    .add(toItem(dict));
        }
        stringRedisTemplate.opsForValue().set(
                RedisConstants.DICT_ALL_KEY.getRedisKey(),
                JSON.toJSONString(items),
                CACHE_TTL
        );
        return items;
    }

    private DictItemVO toItem(SysDict dict) {
        DictItemVO item = new DictItemVO();
        item.setKey(dict.getItemKey());
        item.setLabel(dict.getItemLabel());
        item.setExtra(dict.getItemExtra());
        item.setSort(dict.getSort());
        return item;
    }

    private Map<String, List<DictItemVO>> parseItems(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        return JSON.parseObject(json, new TypeReference<LinkedHashMap<String, List<DictItemVO>>>() {
        });
    }

    private DictResponse buildResponse(Map<String, List<DictItemVO>> items) {
        DictResponse response = new DictResponse();
        response.setItems(items);
        String version = stringRedisTemplate.opsForValue().get(RedisConstants.DICT_VERSION_KEY.getRedisKey());
        response.setVersion(version == null ? 0L : Long.parseLong(version));
        return response;
    }

    private void ensureVersion() {
        Boolean exists = stringRedisTemplate.hasKey(RedisConstants.DICT_VERSION_KEY.getRedisKey());
        if (Boolean.TRUE.equals(exists)) {
            return;
        }
        stringRedisTemplate.opsForValue().set(RedisConstants.DICT_VERSION_KEY.getRedisKey(), "1");
    }
}
