package com.qingsong.ai.controller.model;

import cn.hutool.crypto.KeyUtil;
import cn.hutool.crypto.symmetric.AES;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingsong.ai.entity.po.model.ModelSource;
import com.qingsong.ai.entity.vo.ModelSourceInfoVo;
import com.qingsong.ai.entity.vo.Result;
import com.qingsong.ai.service.ModelSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import javax.validation.Valid;
import java.util.List;

/**
 * 模型来源控制器
 * 提供完整的CRUD接口和业务操作接口
 *
 * @author caojiangjiang
 * @version 1.0
 * @since 2026-02-16
 */
@RestController
@RequestMapping("/api/model-sources")
public class ModelSourceController {

    private final ModelSourceService modelSourceService;

    @Autowired
    public ModelSourceController(ModelSourceService modelSourceService) {
        this.modelSourceService = modelSourceService;
    }

    /**
     * 获取所有模型来源
     *
     * @return 所有模型来源
     */
    @GetMapping
    public Result<List<ModelSource>> getAllModelSources() {
        return Result.ok(modelSourceService.list());
    }

    /**
     * 创建模型来源
     *
     * @param modelSource 模型来源
     * @return 创建后的模型来源
     */
    @PostMapping
    public Result<ModelSource> createModelSource(@Valid @RequestBody ModelSource modelSource) {
        boolean result = modelSourceService.save(modelSource);
        if (result) {
            return Result.ok(modelSource);
        } else {
            return Result.fail("创建失败");
        }
    }

    /**
     * 更新模型来源
     *
     * @param id          模型来源ID
     * @param modelSource 模型来源
     * @return 更新后的模型来源
     */
    @PutMapping("/{id}")
    public Result<ModelSource> updateModelSource(@PathVariable Long id, @Valid @RequestBody ModelSource modelSource) {
        modelSource.setId(id);
        return Result.ok(modelSourceService.updateModelSource(modelSource));
    }

    /**
     * 删除模型来源
     *
     * @param id 模型来源ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteModelSource(@PathVariable Long id) {
        return Result.ok(modelSourceService.removeSourceById(id));
    }


    /**
     * 获取所有模型来源（支持分页）
     *
     * @param page 页码
     * @param size 每页大小
     * @return 模型来源分页结果
     */
    @GetMapping("/page")
    public ResponseEntity<IPage<ModelSource>> getAllModelSources(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        IPage<ModelSource> pageInfo = new Page<>(page, size);
        IPage<ModelSource> result = modelSourceService.page(pageInfo);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有启用的模型来源
     *
     * @return 启用的模型来源列表
     */
    @GetMapping("/active")
    public Result<List<ModelSource>> getActiveModelSources() {
        List<ModelSource> sources = modelSourceService.getActiveModelSources();
        return Result.ok(sources);
    }


    /**
     * 启用并禁用模型来源
     *
     * @param id 模型来源ID
     * @return 操作结果
     */
    @PostMapping("/{id}/toggle-active")
    public Result<Void> toggleActive(@PathVariable Long id) {
        modelSourceService.toggleActive(id);
        return Result.ok();
    }


    /**
     * 获取所有模型来源-附带配置信息
     *
     * @return 所有模型来源
     */
    @GetMapping("/info")
    public Result<List<ModelSourceInfoVo>> getAllModelSourceInfo() {
        return Result.ok(modelSourceService.getAllModelSourceInfo());
    }


    public static void main(String[] args) {
        SecretKey aesKey = KeyUtil.generateKey("AES", 128);
        AES aes = new AES(aesKey.getEncoded());
        String s = aes.encryptHex("123");
        System.out.println(aes.encryptHex("123"));
        System.out.println(aes.decryptStr(s));
    }


}
