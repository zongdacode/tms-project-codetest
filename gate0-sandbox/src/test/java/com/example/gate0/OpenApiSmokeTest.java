package com.example.gate0;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gate 0 关键项：springdoc 3.1.1 在 Boot 4.1.1 上能生成 OpenAPI 文档。 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsGenerated() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Gate 0 沙箱 API"))
                .andExpect(jsonPath("$.paths['/gate0/ping']").exists());
    }
}
