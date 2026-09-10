package com.shanyangcode.aiservice;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

//@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class}, scanBasePackages = {"com.shanyangcode.gateway", "com.shanyangcode.common"})
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class}, scanBasePackages = {"com.shanyangcode.aiservice", "com.shanyangcode.common"})
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }

}