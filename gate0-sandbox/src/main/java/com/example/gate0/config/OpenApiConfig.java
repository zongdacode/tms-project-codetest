package com.example.gate0.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gate 0 验证点：springdoc-openapi 3.1.1 在 Boot 4 下能否自动装配并暴露 /v3/api-docs。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gate0OpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Gate 0 沙箱 API")
                .description("技术栈可行性验证，非交付代码")
                .version("0.0.1"));
    }
}
