package com.qingsong.ai;

import cn.dev33.satoken.SaManager;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.PostConstruct;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan(basePackages = {"com.qingsong.ai.mapper"})
@SpringBootApplication()
@EnableRetry
@EnableScheduling
public class QingsongApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(QingsongApplication.class, args);
        System.out.println("启动成功，Sa-Token 配置如下：" + SaManager.getConfig());
    }

}
