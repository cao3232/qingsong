package com.qingsong.ai.controller.chat;

import com.qingsong.ai.aspect.MyToolAnnotationAspect;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/02/01 16:50
 */
@RestController
@RequestMapping("/tools")
@RequiredArgsConstructor
public class ToolController {

    private final MyToolAnnotationAspect aspect;

    @GetMapping("/object")
    public ConcurrentHashMap<String, Object> listTools() {
        // 直接返回所有已注册的工具 Bean Map
        System.out.println(aspect.getAllTools());
        System.out.println(MyToolAnnotationAspect.toolBeanMap);
        return aspect.getAllTools();
    }

    @GetMapping("/name")
    public ConcurrentHashMap<String, HashMap<String, String>> listToolsName() {
        return MyToolAnnotationAspect.toolInfoMap;
    }
}
