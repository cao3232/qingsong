package com.qingsong.ai.service;

import com.qingsong.ai.aspect.MyTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具分组注册表。
 *
 * <p>替代旧的 {@code MyToolAnnotationAspect}：扫描逻辑与注册时机保持不变，
 * 但把 {@code static} 全局 map 收敛为实例字段，由 Spring 容器管理，
 * 便于注入、测试与生命周期控制（聊天工具执行观测特性的地基）。</p>
 *
 * <h3>特殊用法（扫描时的坑，勿随意改动）：</h3>
 * <ol>
 *   <li>用 {@link ContextRefreshedEvent} 而非 {@code @PostConstruct} 触发扫描，
 *       避免 Bean 初始化阶段的循环依赖（扫描需要访问容器内其它 Bean）；</li>
 *   <li>用 {@link AopUtils#getTargetClass} 取真实类型，避免拿到 CGLIB 代理类导致
 *       反射扫不到 {@code @Tool} 方法；</li>
 *   <li>用 {@link ApplicationContext#findAnnotationOnBean} 取类级 {@link MyTools} 注解，
 *       因为目标 Bean 可能是代理，直接 {@code getAnnotation} 会拿不到。</li>
 * </ol>
 *
 * @author caojiangjiang
 */
@Slf4j
@Component
@Order(0)
public class ToolRegistry {

    /**
     * 工具分组 key → 工具 Bean 实例。
     */
    private final ConcurrentHashMap<String, Object> toolBeanMap = new ConcurrentHashMap<>();

    /**
     * 工具分组 key → （工具名 → 工具描述），供前端工具清单/调试使用。
     */
    private final ConcurrentHashMap<String, HashMap<String, String>> toolInfoMap = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 按分组 key 获取工具 Bean（未注册返回 null）。
     */
    public Object getToolGroup(String key) {
        return toolBeanMap.get(key);
    }

    /**
     * 全部已注册的工具分组（key → Bean 实例）。
     */
    public ConcurrentHashMap<String, Object> getAllTools() {
        return toolBeanMap;
    }

    /**
     * 全部已注册的工具元信息（key → 工具名 → 描述）。
     */
    public ConcurrentHashMap<String, HashMap<String, String>> getToolInfoMap() {
        return toolInfoMap;
    }

    /**
     * 在 Spring 容器完全初始化后扫描标记了 {@code @MyTools} 注解的 Bean。
     * 每次全量重建（clear + repopulate），保证与容器当前 Bean 集合一致。
     */
    @EventListener(ContextRefreshedEvent.class)
    public void scanAnnotatedBeans() {
        log.info("========================================");
        log.info("🔍 开始扫描标记了 @MyTools 注解的 Bean...");

        toolBeanMap.clear();
        toolInfoMap.clear();

        // 获取所有标记了 @MyTools 注解的 Bean
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(MyTools.class);

        log.info("beansWithAnnotation size: {}", beansWithAnnotation.size());

        if (beansWithAnnotation.isEmpty()) {
            log.info("⚠️  未找到任何标记了 @MyTools 注解的 Bean");
        } else {
            log.info("✅ 共发现 {} 个标记了 @MyTools 注解的 Bean:", beansWithAnnotation.size());

            // 遍历并打印每个 Bean 的信息
            beansWithAnnotation.forEach((beanName, beanInstance) -> {
                Class<?> beanClass = AopUtils.getTargetClass(beanInstance);
                log.info("----------------------------------------");
                log.info("Bean 名称：{}", beanName);
                log.info("Bean 类名：{}", beanClass.getName());
                log.info("Bean 类型：{}", beanClass.getSimpleName());
                log.info("所有注解：{}", Arrays.toString(beanClass.getAnnotations()));
                log.info("包路径：{}", beanClass.getPackage().getName());

                // 使用 applicationContext.findAnnotationOnBean 来获取注解，避免 CGLIB 代理问题
                MyTools annotation = applicationContext.findAnnotationOnBean(beanName, MyTools.class);
                log.info("annotation is null: {}", annotation == null);
                if (annotation != null) {
                    log.info("📝 注解信息：name={}, description={}", annotation.name(), annotation.description());
                    toolBeanMap.put(beanName, beanInstance);
                    log.info("✅ 已添加工具 Bean: {}", beanName);

                    Method[] declaredMethods = beanClass.getDeclaredMethods();
                    HashMap<String, String> toolNameMapByObject = new HashMap<>();
                    for (Method method : declaredMethods) {
                        Tool annotation1 = method.getAnnotation(Tool.class);
                        if (annotation1 != null) {
                            log.info("📝 方法注解信息：name={}, description={}", annotation1.name(), annotation1.description());
                            String toolName = annotation1.name().isBlank() ? method.getName() : annotation1.name();
                            toolNameMapByObject.put(toolName, annotation1.description());
                        }
                    }
                    toolInfoMap.put(beanName, toolNameMapByObject);

                }
            });
        }

        log.info("🎉 @MyTools 注解扫描完成!");
        log.info("toolBeanMap size: {}", toolBeanMap.size());
        log.info("========================================");
    }

}
