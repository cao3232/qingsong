// package com.qingsong.ai.config;
//
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
// import org.springframework.security.config.annotation.web.builders.HttpSecurity;
// import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
// import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// import org.springframework.security.crypto.password.PasswordEncoder;
// import org.springframework.security.web.SecurityFilterChain;
//
// /**
//  * description...
//  *
//  * @author : caojiangjiang
//  * @data : 2026/04/03 14:50
//  */
// @Configuration
// @EnableWebSecurity
// @EnableMethodSecurity // 开启方法级别的权限控制，如 @PreAuthorize
// public class SecurityConfig {
//
//     /**
//      * 核心安全过滤链配置
//      * 弃用 WebSecurityConfigurerAdapter，改用 Bean 注入
//      */
//     @Bean
//     public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//         http
//                 // 1. 禁用不必要的 CSRF（如果是纯 API 场景）
//                 .csrf(csrf -> csrf.disable())
//                 // 2. 配置请求授权
//                 .authorizeHttpRequests(auth -> auth
//                         .requestMatchers("/public/**", "/login").permitAll() // 白名单
//                         .anyRequest().authenticated()                       // 其余所有请求必须认证
//                 )
//                 // 3. 配置登录方式（表单登录）
//                 .formLogin(form -> form
//                         .defaultSuccessUrl("/dashboard", true)
//                 )
//                 // 4. 注销配置
//                 .logout(logout -> logout.logoutSuccessUrl("/"));
//
//         return http.build();
//     }
//
//     /**
//      * 必须配置密码编码器，禁止明文存储
//      */
//     @Bean
//     public PasswordEncoder passwordEncoder() {
//         return new BCryptPasswordEncoder();
//     }
// }
