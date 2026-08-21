package com.qingsong.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.entity.po.model.ModelConfig;
import com.qingsong.ai.entity.po.model.ModelSource;
import com.qingsong.ai.entity.vo.ModelSourceInfoVo;
import com.qingsong.ai.mapper.model.ModelConfigMapper;
import com.qingsong.ai.mapper.model.ModelSourceMapper;
import com.qingsong.ai.service.ModelSourceService;
import com.qingsong.ai.service.factorys.ChatClientFactory;
import com.qingsong.ai.utils.DynamicDesensitize;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 模型来源服务实现
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-02-16
 */
@Service
public class ModelSourceServiceImpl extends ServiceImpl<ModelSourceMapper, ModelSource> implements ModelSourceService {

    @Autowired
    private ModelSourceMapper modelSourceMapper;

    @Autowired
    private ModelConfigMapper modelConfigMapper;

    @Autowired
    private ChatClientFactory chatClientFactory;

    @Override
    public ModelSource getBySourceCode(String sourceCode) {
        return modelSourceMapper.findBySourceCode(sourceCode);
    }

    @Override
    public List<ModelSource> getActiveModelSources() {
        return modelSourceMapper.findByIsActiveTrue();
    }

    @Override
    public List<ModelSource> getActiveModelSourcesOrderBySort() {
        return modelSourceMapper.findByIsActiveTrueOrderBySortOrderAsc();
    }

    @Override
    public Long countActiveModelSources() {
        return modelSourceMapper.countByIsActiveTrue();
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    @CacheEvict(value = "modelConfigCache", allEntries = true)
    public void toggleActive(Long id) {
        ModelSource modelSource = query().eq("id", id).one();
        if (modelSource == null) {
            throw new BusinessException("模型来源不存在，无法切换状态");
        }
        if (modelSource.getIsActive()) {
            throw new BusinessException("该模型来源处于启动状态，无法禁用");
        }

        ModelSource active = query().eq("is_active", Boolean.TRUE).one();
        ArrayList<ModelSource> updateList = new ArrayList<>();
        if (active != null && !Objects.equals(active.getId(), id)) {
            active.setIsActive(false);
            updateList.add(active);
        }
        modelSource.setIsActive(true);
        updateList.add(modelSource);
        modelSourceMapper.updateById(updateList);

        this.switchAIApi(modelSource);
    }


    @Override
    public List<ModelSourceInfoVo> getAllModelSourceInfo() {
        List<ModelSource> list = this.list();
        // 方式1: MyBatis Plus 分组统计
        Map<Long, Long> countMap = modelConfigMapper.selectMaps(
                        new QueryWrapper<ModelConfig>()
                                .select("model_source", "COUNT(*) as count")
                                .groupBy("model_source")
                ).stream()
                .collect(Collectors.toMap(
                        m -> Long.valueOf((Integer) m.get("model_source")),
                        m -> (Long) m.get("count")
                ));
        List<ModelSourceInfoVo> infoVos = new ArrayList<>();
        list.forEach(modelSource -> {
            ModelSourceInfoVo modelSourceInfoVo = new ModelSourceInfoVo();
            BeanUtils.copyProperties(modelSource, modelSourceInfoVo);
            String maskApiKey = DynamicDesensitize.maskMiddle(modelSource.getApiKey(), 10, 10);
            modelSourceInfoVo.setMaskApiKey(maskApiKey);
            modelSourceInfoVo.setCount(countMap.getOrDefault(modelSource.getId(), 0L));
            infoVos.add(modelSourceInfoVo);
        });

        return infoVos;
    }

    @Override
    public ModelSource getCurrentModelSource() {
        QueryWrapper<ModelSource> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("is_active", true);
        ModelSource modelSource = this.baseMapper.selectOne(queryWrapper);
        return modelSource;
    }

    @Override
    public ModelSource updateModelSource(ModelSource modelSource) {
        boolean result = this.updateById(modelSource);
        if (!result) {
            throw new BusinessException("更新模型来源失败");
        }
        chatClientFactory.refreshChatClient(modelSource);

        return this.getBaseMapper().selectById(modelSource.getId());
    }

    @Override
    @Transactional
    public boolean removeSourceById(Long id) {
        ModelSource modelSource = this.modelSourceMapper.selectById(id);
        if (modelSource == null) {
            throw new BusinessException("模型来源不存在");
        }
        if (modelSource.getIsActive()) {
            throw new BusinessException("模型来源正在使用中，无法删除");
        }
        // 1、删除下面的模型配置
        QueryWrapper<ModelConfig> modelSourceWrapper = new QueryWrapper<ModelConfig>()
                .eq("model_source", id);
        modelConfigMapper.delete(modelSourceWrapper);
        // 2、删除模型来源
        boolean sourceDelete = this.removeById(id);
        if (!sourceDelete) {
            throw new BusinessException("删除来源失败");
        }
        // 3、移除缓存客户端（清理副作用，失败不影响主流程）
        chatClientFactory.removeChatClient(modelSource.getSourceCode());
        return true;
    }

    private void switchAIApi(ModelSource source) {
        chatClientFactory.setDefault(source.getSourceCode());
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public boolean save(ModelSource entity) {
        entity.setCreateDate(LocalDateTime.now());
        entity.setUpdateDate(LocalDateTime.now());
        entity.setIsActive(false);
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        boolean save = super.save(entity);

        if (save) {
            // 添加默认模型
            createDefaultModelConfig(entity);
            chatClientFactory.addChatClient(entity);
        }

        return save;
    }

    /**
     * 创建默认模型配置
     *
     * @param modelSource 模型来源对象
     */
    private void createDefaultModelConfig(ModelSource modelSource) {
        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setName("测试模型");
        modelConfig.setCode("test");
        modelConfig.setType("大模型");
        modelConfig.setModelSource(modelSource.getId());
        modelConfig.setModelOrder(0);
        modelConfig.setIsActive(false);
        modelConfig.setCreatedBy("root");
        modelConfig.setUpdatedBy("root");
        modelConfig.setCreateDate(LocalDateTime.now());
        modelConfig.setUpdateDate(LocalDateTime.now());
        modelConfig.setIsTop(false);

        int result = modelConfigMapper.insert(modelConfig);
        if (result <= 0) {
            throw new BusinessException("创建默认模型配置失败");
        }
    }

    @Override
    public boolean updateById(ModelSource entity) {
        entity.setUpdateDate(LocalDateTime.now());
        return super.updateById(entity);
    }
}
