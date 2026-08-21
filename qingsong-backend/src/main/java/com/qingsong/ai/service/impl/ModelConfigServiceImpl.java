package com.qingsong.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.qingsong.ai.entity.dto.model.ModelBatchAddRequest;
import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.entity.po.model.ModelConfig;
import com.qingsong.ai.entity.po.model.ModelSource;
import com.qingsong.ai.entity.vo.ModelCheckResult;
import com.qingsong.ai.entity.vo.ModelDiscoverItem;
import com.qingsong.ai.mapper.model.ModelConfigMapper;
import com.qingsong.ai.mapper.model.ModelSourceMapper;
import com.qingsong.ai.service.ModelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型配置服务实现
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-01-26
 */
@Service
@Slf4j
public class ModelConfigServiceImpl extends ServiceImpl<ModelConfigMapper, ModelConfig> implements ModelConfigService {

    @Autowired
    private ModelConfigMapper modelConfigMapper;

    @Autowired
    private ModelSourceMapper modelSourceMapper;

    @Override
    @Cacheable(value = "modelConfigCache", key = "'active_models'")
    public List<ModelConfig> getActiveModelConfigs() {
        return modelConfigMapper.findByIsActiveTrue();
    }

    private final ExecutorService modelTestExecutor = Executors.newFixedThreadPool(5);


    @Override
    public List<ModelConfig> getActiveModelConfigsBySource(Integer modelSource) {
        // 多级排序：
        // 1. 先按 isActive 降序（true 在前，false 在后）
        // 2. 再按 modelOrder 升序
        return modelConfigMapper.findBySource(modelSource).stream()
                .sorted((o1, o2) -> {
                    // 第一优先级：按激活状态排序（激活的在前）
                    int activeCompare = Boolean.compare(o2.getIsActive(), o1.getIsActive());
                    if (activeCompare != 0) {
                        return activeCompare;
                    }
                    // 第二优先级：按 modelOrder 升序
                    return Integer.compare(o1.getModelOrder(), o2.getModelOrder());
                })
                .collect(Collectors.toList());
    }


    @Override
    @CacheEvict(value = "modelConfigCache", allEntries = true)
    public void active(Long id) {
        ModelConfig modelConfig = query().eq("id", id).one();
        if (modelConfig == null) {
            throw new BusinessException("配置不存在，无法激活");
        }
        if (modelConfig.getIsActive() && modelConfig.getIsTop()) {
            throw new BusinessException("该模型处于置顶状态，无法取消使用");
        }
        UpdateWrapper<ModelConfig> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id);
        updateWrapper.set("is_active", !modelConfig.getIsActive());
        int update = modelConfigMapper.update(updateWrapper);
        if (update <= 0) {
            throw new BusinessException("状态更新失败，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    @CacheEvict(value = "modelConfigCache", allEntries = true)
    public void top(Long id) {

        List<ModelConfig> list = modelConfigMapper.selectChangeTopList(id);
        if (CollectionUtils.isEmpty(list)) {
            throw new BusinessException("模型配置不存在");
        }
        if (list.size() == 1) {
            ModelConfig modelConfig = list.get(0);
            modelConfig.setIsTop(true);
            modelConfigMapper.updateById(modelConfig);
            return;
        }
        Map<Boolean, ModelConfig> mapByTop = list.stream().collect(Collectors.toMap(ModelConfig::getIsTop, Function.identity()));
        ModelConfig newEntity = mapByTop.get(false);
        if (newEntity == null || !Objects.equals(newEntity.getId(), id)) {
            throw new BusinessException("模型配置不存在");
        }
        newEntity.setIsTop(!newEntity.getIsTop());
        ModelConfig oldEntity = mapByTop.get(true);
        oldEntity.setIsTop(!oldEntity.getIsTop());

        boolean update = this.updateBatchById(Arrays.asList(newEntity, oldEntity));
        if (!update) {
            throw new BusinessException("更新失败");
        }
    }

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public boolean testModel(String sourceId) {
        String lockKey = "model:test:" + sourceId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.error("获取锁失败：{}", lockKey);
                throw new BusinessException("获取锁失败");
            }
            // 查找对应的来源信息
            ModelSource modelSource = modelSourceMapper.selectById(sourceId);
            if (modelSource == null) {
                log.error("来源不存在：{}", sourceId);
                throw new BusinessException("来源不存在");
            }
            List<ModelConfig> list = this.lambdaQuery()
                    .eq(ModelConfig::getModelSource, sourceId)
                    .list();
            if (CollectionUtils.isEmpty(list)) {
                log.error("来源下无模型：{}", sourceId);
                throw new BusinessException("来源下无模型");
            }
            // 调用接口测试
            this.testModelAvailable(modelSource, list);
            return true;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }

    @Override
    public ModelConfig updateModelConfig(ModelConfig modelConfig) {
        boolean result = this.updateById(modelConfig);
        if (!result) {
            throw new BusinessException("更新失败");
        }
        return baseMapper.selectById(modelConfig.getId());

    }

    @Override
    public String getInnerModel() {
        List<ModelSource> modelSources = modelSourceMapper.selectByMap(Collections.singletonMap("source_code", "inner"));
        if (CollectionUtils.isEmpty(modelSources)) {
            throw new BusinessException("未维护内部模型来源");
        }
        List<ModelConfig> modelConfigs = modelConfigMapper.selectByMap(Collections.singletonMap("model_source", modelSources.get(0).getId()));
        if (CollectionUtils.isEmpty(modelConfigs)) {
            throw new BusinessException("内部模型不存在");
        }
        return modelConfigs.get(0).getCode();
    }

    @Override
    public List<ModelDiscoverItem> discoverModels(String sourceId) {
        ModelSource modelSource = modelSourceMapper.selectById(sourceId);
        if (modelSource == null) {
            throw new BusinessException("来源不存在");
        }
        String baseUrl = modelSource.getApiBaseUrl();
        String apiKey = modelSource.getApiKey();
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException("来源未配置API地址");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("来源未配置API密钥");
        }
        // 去掉末尾斜杠，避免拼接出 //models
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl + "v1" : baseUrl + "/v1";

        // 拉取模型列表（仅获取列表与元信息，不做可用性检测）
        List<ModelDiscoverItem> items = fetchModels(normalizedBase, apiKey);
        if (items.isEmpty()) {
            throw new BusinessException("未从来源获取到任何模型");
        }
        items.sort(Comparator.comparing(ModelDiscoverItem::getModel));
        return items;
    }

    @Override
    public ModelCheckResult checkModel(String sourceId, String modelId) {
        ModelSource modelSource = modelSourceMapper.selectById(sourceId);
        if (modelSource == null) {
            throw new BusinessException("来源不存在");
        }
        String baseUrl = modelSource.getApiBaseUrl();
        String apiKey = modelSource.getApiKey();
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException("来源未配置API地址");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("来源未配置API密钥");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl + "v1" : baseUrl + "/v1";
        return checkModel(normalizedBase, apiKey, modelId);
    }

    /**
     * 调用来源的 /models 接口拉取模型列表（含元信息）
     */
    private List<ModelDiscoverItem> fetchModels(String baseUrl, String apiKey) {
        RestTemplate restTemplate = buildRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String fetchError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                ResponseEntity<String> resp = restTemplate.exchange(
                        baseUrl + "/models", HttpMethod.GET, entity, String.class);
                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    JSONObject body = JSON.parseObject(resp.getBody());
                    JSONArray data = body.getJSONArray("data");
                    List<ModelDiscoverItem> items = new ArrayList<>();
                    if (data != null) {
                        for (int i = 0; i < data.size(); i++) {
                            JSONObject obj = data.getJSONObject(i);
                            String id = obj.getString("id");
                            if (!StringUtils.hasText(id)) {
                                continue;
                            }
                            ModelDiscoverItem item = new ModelDiscoverItem();
                            item.setModel(id);
                            item.setOwnedBy(obj.getString("owned_by"));
                            JSONArray endpointTypes = obj.getJSONArray("supported_endpoint_types");
                            if (endpointTypes != null) {
                                List<String> types = new ArrayList<>();
                                for (int j = 0; j < endpointTypes.size(); j++) {
                                    types.add(endpointTypes.getString(j));
                                }
                                item.setSupportedEndpointTypes(types);
                            }
                            items.add(item);
                        }
                    }
                    return items;
                }
                fetchError = "HTTP " + resp.getStatusCode().value();
            } catch (Exception e) {
                fetchError = e.getMessage();
            }
            log.warn("/models 拉取失败（第 {} 次）：{}", attempt, fetchError);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new BusinessException("拉取模型列表失败：" + fetchError);
    }

    /**
     * 检测单个模型是否可用（模拟脚本逻辑：发 Hello 测试，429/超时重试）
     */
    private ModelCheckResult checkModel(String baseUrl, String apiKey, String modelId) {
        RestTemplate restTemplate = buildRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        boolean working = false;
        int httpStatus = 0;
        String note = "";

        int maxRetries = 2;
        for (int tryIdx = 0; tryIdx <= maxRetries; tryIdx++) {
            working = false;
            httpStatus = 0;
            note = "";
            boolean retryable = false;

            JSONObject payload = new JSONObject();
            payload.put("model", modelId);
            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", "Hello");
            messages.add(userMsg);
            payload.put("messages", messages);

            try {
                HttpEntity<String> request = new HttpEntity<>(payload.toJSONString(), headers);
                ResponseEntity<String> resp = restTemplate.exchange(
                        baseUrl + "/chat/completions", HttpMethod.POST, request, String.class);
                httpStatus = resp.getStatusCode().value();
                String body = resp.getBody();
                if (httpStatus == 200 && body != null) {
                    JSONObject respBody = JSON.parseObject(body);
                    JSONArray choices = respBody.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        working = true;
                        note = "OK";
                    } else {
                        note = "HTTP " + httpStatus + " / no choices";
                    }
                } else if (httpStatus == 429) {
                    retryable = true;
                    note = "Rate limited (429)";
                } else {
                    note = "HTTP " + httpStatus + " / no choices";
                }
            } catch (Exception e) {
                // 超时/网络错误
                httpStatus = 0;
                note = e.getMessage() != null ? e.getMessage() : "网络错误";
                retryable = true;
            }

            if (working || !retryable || tryIdx == maxRetries) {
                break;
            }
            try {
                Thread.sleep(1000L + (long) tryIdx * 2000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        ModelCheckResult result = new ModelCheckResult();
        result.setModel(modelId);
        result.setWorking(working);
        result.setHttpStatus(httpStatus);
        result.setNote(truncate(note));
        return result;
    }

    /**
     * 构建带超时与 UA 的 RestTemplate
     */
    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(45000);
        RestTemplate restTemplate = new RestTemplate(factory);
        return restTemplate;
    }

    /**
     * 截断过长的备注信息
     */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() > 200) {
            return text.substring(0, 200) + "...";
        }
        return text;
    }

    @Override
    public List<ModelCheckResult> batchAddModels(ModelBatchAddRequest request) {
        Long sourceId = request.getSourceId();
        List<String> modelIds = request.getModelIds();
        boolean check = request.getCheck() == null || request.getCheck();
        if (sourceId == null || modelIds == null || modelIds.isEmpty()) {
            throw new BusinessException("参数不完整");
        }
        ModelSource modelSource = modelSourceMapper.selectById(sourceId);
        if (modelSource == null) {
            throw new BusinessException("来源不存在");
        }
        String baseUrl = modelSource.getApiBaseUrl();
        String apiKey = modelSource.getApiKey();
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException("来源未配置API地址");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("来源未配置API密钥");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl + "v1" : baseUrl + "/v1";

        // 查询该来源下已存在的模型 code，去重
        List<ModelConfig> existing = this.lambdaQuery()
                .eq(ModelConfig::getModelSource, sourceId)
                .list();
        Set<String> existingCodes = existing.stream()
                .map(ModelConfig::getCode)
                .collect(Collectors.toSet());

        // 1. 并行检测选中的模型（check=false 时跳过检测，直接视为可用）
        List<CompletableFuture<ModelCheckResult>> futures = new ArrayList<>();
        for (String modelId : modelIds) {
            CompletableFuture<ModelCheckResult> future;
            if (check) {
                future = CompletableFuture.supplyAsync(
                        () -> checkModel(normalizedBase, apiKey, modelId),
                        modelTestExecutor
                ).exceptionally(ex -> {
                    ModelCheckResult result = new ModelCheckResult();
                    result.setModel(modelId);
                    result.setWorking(false);
                    result.setHttpStatus(0);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    result.setNote(truncate(cause.getMessage()));
                    return result;
                });
            } else {
                future = CompletableFuture.completedFuture(buildOkResult(modelId));
            }
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 2. 可用且未存在的模型入库
        List<ModelConfig> toSave = new ArrayList<>();
        List<ModelCheckResult> results = new ArrayList<>();
        for (CompletableFuture<ModelCheckResult> future : futures) {
            ModelCheckResult result = future.join();
            if (Boolean.TRUE.equals(result.getWorking()) && !existingCodes.contains(result.getModel())) {
                ModelConfig config = new ModelConfig();
                config.setName(result.getModel());
                config.setCode(result.getModel());
                config.setModelSource(sourceId);
                config.setType("大语言模型");
                config.setModelOrder(0);
                config.setIsActive(true);
                config.setIsTop(false);
                config.setCreateDate(LocalDateTime.now());
                config.setUpdateDate(LocalDateTime.now());
                toSave.add(config);
                result.setAdded(true);
            } else {
                result.setAdded(false);
                if (existingCodes.contains(result.getModel())) {
                    result.setNote("已存在，未重复添加");
                }
            }
            results.add(result);
        }
        if (!toSave.isEmpty()) {
            this.saveBatch(toSave);
        }
        return results.stream()
                .sorted((o1, o2) -> {
                    int addedCompare = Boolean.compare(o2.getAdded(), o1.getAdded());
                    if (addedCompare != 0) {
                        return addedCompare;
                    }
                    return o1.getModel().compareTo(o2.getModel());
                })
                .collect(Collectors.toList());
    }

    /**
     * 构建一个未检测、直接判定可用的结果（check=false 场景）
     */
    private ModelCheckResult buildOkResult(String modelId) {
        ModelCheckResult result = new ModelCheckResult();
        result.setModel(modelId);
        result.setWorking(true);
        result.setHttpStatus(200);
        result.setNote("未检测，直接添加");
        return result;
    }

    private void testModelAvailable(ModelSource modelSource, List<ModelConfig> list) {

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(modelSource.getApiBaseUrl())
                .apiKey(modelSource.getApiKey())
                .build();

        List<CompletableFuture> completableFutures = new ArrayList<CompletableFuture>();
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(1);

        // 分批执行
        int batchSize = 5;
        for (int i = 0; i < list.size(); i += batchSize) {
            List<ModelConfig> batch = list.subList(i, Math.min(i + batchSize, list.size()));
            batch.forEach(modelConfig -> {
                OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(openAiApi)
                        .defaultOptions(OpenAiChatOptions.builder()
                                .model(modelConfig.getCode())
                                .temperature(0.0)
                                .build())
                        .retryTemplate(RetryTemplate.builder().customPolicy(retryPolicy).build())
                        .build();

                CompletableFuture<String> future = null;
                future = CompletableFuture.supplyAsync(() -> {
                    String response = chatModel.call("仅仅回复Boolean类型success，例如success");
                    return response;
                }, modelTestExecutor).exceptionally(ex -> {
                    // ex 是 Throwable 类型，通常是 ExecutionException 的 cause
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    log.warn("模型 {} 调用失败: {}", modelConfig.getCode(), cause.getMessage());
                    // 返回一个默认值，代表“失败”，但不会中断主流程
                    // 注意：返回类型必须与 supplyAsync 的泛型一致 (String)
                    return "false";
                });

                completableFutures.add(future);
            });

        }

        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
                .join();


        for (int i = 0; i < completableFutures.size(); i++) {
            CompletableFuture<String> future = completableFutures.get(i);
            ModelConfig modelConfig = list.get(i);
            try {
                String response = future.get();
                log.info("模型 {} 测试结果：{}", modelConfig.getName(), response);
                if ("false".equals(response) || !response.contains("success")) {
                    this.lambdaUpdate().eq(ModelConfig::getId, modelConfig.getId())
                            .set(ModelConfig::getIsActive, false)
                            .update();
                } else {
                    this.lambdaUpdate().eq(ModelConfig::getId, modelConfig.getId())
                            .set(ModelConfig::getIsActive, true)
                            .update();
                }
            } catch (Exception e) {
                log.error("模型测试失败：{}", modelConfig.getName());
            }
        }


    }

    @Override
    @CacheEvict(value = "modelConfigCache", allEntries = true)
    public boolean save(ModelConfig entity) {
        entity.setCreateDate(LocalDateTime.now());
        entity.setUpdateDate(LocalDateTime.now());
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(ModelConfig entity) {
        entity.setUpdateDate(LocalDateTime.now());
        return super.updateById(entity);
    }

    /**
     * 根据名称生成模型ID
     *
     * @param name 模型名称
     * @return 生成的模型ID
     */
    private String generateModelId(String name) {
        if (name == null || name.isEmpty()) {
            return "model_" + System.currentTimeMillis();
        }
        // 将名称转换为适合用作ID的格式
        return name.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
}
