package com.qingsong.ai.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.qingsong.ai.config.interceptor.ChatContextInterceptor;
import com.qingsong.ai.config.interceptor.UserContextInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class MvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private ChatContextInterceptor chatContextInterceptor;

    @Autowired
    private UserContextInterceptor userContextInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(true);
    }


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射 /uploads/** 到本地文件系统目录
        // file:uploads/ 表示当前项目根目录下的 uploads 文件夹。
        String uploadPath = "file:upload/";
        registry.addResourceHandler("/upload/**").addResourceLocations(uploadPath);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestOnlySaInterceptor(handler -> {
                    if ("OPTIONS".equalsIgnoreCase(SaHolder.getRequest().getMethod())) {
                        return;
                    }
                    SaRouter.match("/**")
                            .notMatch(
                                    "/user-config/login",
                                    "/user-config/register",
                                    "/user-config/session",
                                    "/api/dict/all",
                                    "/actuator/**",
                                    "/error",
                                    "/upload/**",
                                    "/swagger-ui.html",
                                    "/swagger-ui/**",
                                    "/v3/api-docs/**",
                                    "/webjars/**"
                            )
                            .check(route -> StpUtil.checkLogin());
                }))
                .addPathPatterns("/**");

        // 注册聊天上下文拦截器
        registry.addInterceptor(chatContextInterceptor)
                .addPathPatterns(List.of("/ai/**", "/message/**"))  // 只拦截AI相关接口
                .excludePathPatterns("/ai/refresh/roles"); // 排除不需要上下文的接口

        // 注册用户上下文拦截器
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**")  // 拦截所有接口
                .excludePathPatterns(
                        "/user-config/**",  // 排除用户配置管理接口（避免循环依赖）
                        "/actuator/**",     // 排除健康检查等系统接口
                        "/error"            // 排除错误页面
                );
    }
}
