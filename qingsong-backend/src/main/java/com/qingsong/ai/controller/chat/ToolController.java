package com.qingsong.ai.controller.chat;

import com.qingsong.ai.service.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具清单接口。
 *
 * <p>仅用于前端查看当前已注册的工具分组，不应把 Bean 实例当数据下发到生产前端
 * （调试用途，保留原始行为）。</p>
 *
 * @author caojiangjiang
 */
@RestController
@RequestMapping("/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolRegistry toolRegistry;

    @GetMapping("/object")
    public ConcurrentHashMap<String, Object> listTools() {
        // 直接返回所有已注册的工具 Bean Map
        System.out.println(toolRegistry.getAllTools());
        return toolRegistry.getAllTools();
    }

    @GetMapping("/name")
    public ConcurrentHashMap<String, HashMap<String, String>> listToolsName() {
        return toolRegistry.getToolInfoMap();
    }
}
