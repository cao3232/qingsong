package com.qingsong.ai.aspect;

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
 * 自定义注解扫描器，在 Spring 容器启动时扫描所有标记了 @MyTools 注解的类
 *
 * @author : caojiangjiang
 * @data : 2026/04/02 18:00
 */
@Slf4j
@Component
@Order(0)  // 提高优先级，确保在 Bean 初始化完成后立即扫描
public class MyToolAnnotationAspect {

    @Autowired
    private ApplicationContext applicationContext;

    public static final ConcurrentHashMap<String, Object> toolBeanMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, HashMap<String, String>> toolInfoMap = new ConcurrentHashMap<>();

    /**
     * 获取所有已注册的工具 Bean
     *
     * @return 工具 Bean Map
     */
    public ConcurrentHashMap<String, Object> getAllTools() {
        return toolBeanMap;
    }

    /**
     * 在 Spring 容器完全初始化后扫描标记了 @MyTools 注解的 Bean
     * 使用 ContextRefreshedEvent 避免 @PostConstruct 中的循环依赖问题
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
