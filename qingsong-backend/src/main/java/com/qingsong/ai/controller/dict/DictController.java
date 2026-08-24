package com.qingsong.ai.controller.dict;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qingsong.ai.entity.po.dict.SysDict;
import com.qingsong.ai.entity.vo.Result;
import com.qingsong.ai.entity.vo.dict.DictItemVO;
import com.qingsong.ai.entity.vo.dict.DictResponse;
import com.qingsong.ai.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 业务字典控制器：下发接口公开，管理接口需登录
 */
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    /**
     * 下发全量启用字典（前端启动时调用）
     */
    @GetMapping("/all")
    public Result<DictResponse> getAll() {
        return Result.ok(dictService.getAll());
    }

    /**
     * 下发单个字典类型的启用项
     */
    @GetMapping("/{code}")
    public Result<List<DictItemVO>> getByCode(@PathVariable String code) {
        return Result.ok(dictService.getByCode(code));
    }

    /**
     * 管理端分页查询（含停用项）
     */
    @GetMapping("/admin/page")
    public Result<IPage<SysDict>> page(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize,
                                       @RequestParam(required = false) String dictCode) {
        return Result.ok(dictService.page(pageNum, pageSize, dictCode));
    }

    /**
     * 新增字典项
     */
    @PostMapping("/admin")
    public Result<SysDict> add(@RequestBody SysDict dict) {
        boolean ok = dictService.add(dict);
        return ok ? Result.ok(dict) : Result.fail("新增失败");
    }

    /**
     * 更新字典项
     */
    @PutMapping("/admin/{id}")
    public Result<SysDict> update(@PathVariable Long id, @RequestBody SysDict dict) {
        dict.setId(id);
        boolean ok = dictService.update(dict);
        return ok ? Result.ok(dict) : Result.fail("更新失败");
    }

    /**
     * 删除字典项
     */
    @DeleteMapping("/admin/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean ok = dictService.delete(id);
        return ok ? Result.ok() : Result.fail("删除失败");
    }

    /**
     * 手动刷新缓存（重建 dict:all 并递增版本号）
     */
    @PostMapping("/admin/reload")
    public Result<Void> reload() {
        dictService.refreshCache();
        return Result.ok();
    }
}
