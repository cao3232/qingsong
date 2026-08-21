package com.qingsong.ai.controller.role;

import com.qingsong.ai.entity.po.role.RolePhrases;
import com.qingsong.ai.entity.vo.Result;
import com.qingsong.ai.service.RolePhrasesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/role-phrases")
@RequiredArgsConstructor
public class RolePhrasesController {
    private final RolePhrasesService rolePhrasesService;

    /**
     * 根据角色ID获取角色短语列表
     */
    @GetMapping("/{roleId}")
    public Result<List<RolePhrases>> rolePhrases(@PathVariable String roleId) {
        return Result.ok(rolePhrasesService.getRolePhrasesByRoleId(roleId));
    }

    /**
     * 添加角色短语
     */
    @PostMapping
    public Result<Boolean> addRolePhrase(@RequestBody RolePhrases rolePhrase) {
        boolean result = rolePhrasesService.saveRolePhrase(rolePhrase);
        return result ? Result.ok(true) : Result.fail("添加失败");
    }

    /**
     * 更新角色短语
     */
    @PutMapping
    public Result<Boolean> updateRolePhrase(@RequestBody RolePhrases rolePhrase) {
        boolean result = rolePhrasesService.updateRolePhrase(rolePhrase);
        return result ? Result.ok(true) : Result.fail("更新失败");
    }

    /**
     * 删除角色短语
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteRolePhrase(@PathVariable Long id) {
        boolean result = rolePhrasesService.deleteRolePhrase(id);
        return result ? Result.ok(true) : Result.fail("删除失败");
    }
}
