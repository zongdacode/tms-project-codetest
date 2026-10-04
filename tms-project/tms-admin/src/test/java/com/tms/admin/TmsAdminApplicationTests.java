package com.tms.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 批 0 验收：tms-admin 能真正启动，并且健康检查可用（[12] §3.4）。
 *
 * <p>与其余集成测试的区别在于 {@code webEnvironment = RANDOM_PORT}：
 * 这里起的是<b>真实的 Servlet 容器和监听端口</b>，用的是真实的 HTTP 客户端，
 * 而不是 MockMvc 的内存调用。它验证的是"打出来的包能不能跑起来"，
 * 而不是"处理器映射对不对"——两者会坏在不同的地方。
 *
 * <p>端口通过 {@code local.server.port} 读取，而不是 {@code @LocalServerPort}：
 * 后者在 Boot 4 里换了包，而前者是稳定的属性名，不受包调整影响。
 *
 * <p>这里不复用 {@link com.tms.support.IntegrationTestBase}：那两处注解都要重新声明属性，
 * 而"父类注解 + 子类注解"的合并结果由 Spring 的属性合并规则决定，
 * 多一层推断就多一种"以为连的是 H2、其实不是"的可能。三行注解写清楚更省事。
 */
@SpringBootTest(classes = TmsAdminApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
class TmsAdminApplicationTests {

    @Value("${local.server.port}")
    private int port;

    @Test
    @DisplayName("健康检查可用：/actuator/health 返回 200 且状态为 UP")
    void healthEndpointReportsUp() throws Exception {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 断言消息里带上响应体：健康检查失败时，光知道"不是 200"没有排查价值，
            // 需要看到的恰恰是"哪个组件 DOWN"。
            assertThat(response.statusCode())
                    .as("健康检查不是 200，响应体: %s", response.body())
                    .isEqualTo(200);
            assertThat(response.body())
                    .as("健康检查返回的报文不对，实际为: %s", response.body())
                    .contains("\"status\":\"UP\"");
        }
    }
}
