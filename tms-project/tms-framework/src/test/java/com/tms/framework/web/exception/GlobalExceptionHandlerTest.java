package com.tms.framework.web.exception;

import com.tms.common.api.Result;
import com.tms.common.api.ResultCode;
import com.tms.common.exception.BizException;
import com.tms.framework.web.EventEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统一异常处理的三条硬规则（[06] §7、[10]）。
 *
 * <p>用 standalone MockMvc 而不是整应用上下文：本类的作用是把异常翻译成 HTTP 响应，
 * 只依赖 MVC 那一层。起整个应用会把 DB、事件框架全拖进来，测的东西反而模糊。
 *
 * <p>重点在两条容易被当成"细节"的规则上：
 * <ul>
 *   <li><b>5xx 不回显异常消息</b>——这是信息泄露防线。数据库连接串、表名、
 *       内网地址经常就夹在异常消息里，一旦回显给调用方，等于把内网拓扑送出去；</li>
 *   <li><b>事件端点让出</b>——事实类接口不能返回业务拒绝信封（[06] §7）。
 *       被包装成 {@code Result} 之后，接收方会当成"已受理"，事实静默丢失。</li>
 * </ul>
 */
class GlobalExceptionHandlerTest {

    private static MockMvc commandApi() {
        return MockMvcBuilders.standaloneSetup(new CommandFixture())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockMvc eventApi() {
        return MockMvcBuilders.standaloneSetup(new EventFixture())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("命令类接口：异常翻译成统一信封")
    class CommandApi {

        @Test
        @DisplayName("业务拒绝 → 422 + 业务错误码，消息原样回显（业务自己保证不含敏感信息）")
        void bizExceptionBecomesUnprocessableContent() throws Exception {
            commandApi().perform(get("/command/reject"))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.code").value(ResultCode.STATE_REGRESSION.code()))
                    .andExpect(jsonPath("$.message").value("运单已签收，不能重复签收"));
        }

        @Test
        @DisplayName("未预期异常 → 500 + 内部错误码，且响应体里不含原始异常消息")
        void unexpectedExceptionDoesNotLeakMessage() throws Exception {
            String body = commandApi().perform(get("/command/boom"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(ResultCode.INTERNAL_ERROR.code()))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body)
                    .as("响应体回显了原始异常消息：数据库连接串、表名等内网信息会随之外泄")
                    .doesNotContain("jdbc:mysql")
                    .doesNotContain("10.0.0.5")
                    .doesNotContain("outbox_event");
            assertThat(body)
                    .as("内部错误只给错误码，不给任何细节文案")
                    .doesNotContain("连接失败");
        }

        @Test
        @DisplayName("缺少必填参数 → 400，且报错点名到具体参数")
        void missingParameterNamesTheParameter() throws Exception {
            commandApi().perform(get("/command/need-param"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ResultCode.PARAM_INVALID.code()))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("waybillNo")));
        }

        @Test
        @DisplayName("消息为 null 的业务异常回退到错误码自带文案，而不是回一个空 message")
        void bizExceptionWithoutMessageFallsBackToCodeMessage() {
            GlobalExceptionHandler handler = new GlobalExceptionHandler();
            BizException bare = new BizException(ResultCode.BIZ_RULE_REJECTED);

            ResponseEntity<Result<Void>> response = handler.handleBizException(bare, null);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage())
                    .as("回空 message 的响应调用方无从判断原因")
                    .isEqualTo(ResultCode.BIZ_RULE_REJECTED.message());
        }
    }

    @Nested
    @DisplayName("事件类接口：抛出去，不套信封")
    class EventApi {

        @Test
        @DisplayName("事件端点上的业务异常原样抛出，不返回 Result 信封")
        void eventEndpointExceptionPropagates() {
            Throwable thrown = catchThrowable(() -> eventApi().perform(post("/events/outbound")));

            assertThat(thrown)
                    .as("异常没抛出来：说明被兜底处理器接走并包成了 Result 信封，"
                            + "接收方会把这当成'已受理'，而事实其实被丢弃了")
                    .isNotNull();
            assertThat(causesOf(thrown))
                    .as("抛出的异常链里应能找到原本的业务异常")
                    .anyMatch(BizException.class::isInstance);
        }

        @Test
        @DisplayName("事件端点正常受理时返回原生响应，不被包装")
        void eventEndpointResponseIsNotWrapped() throws Exception {
            eventApi().perform(post("/events/accepted"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ACCEPTED"));
        }
    }

    /** 异常可能被容器包过一层，判断类型时要把整条因果链都看一遍。 */
    private static List<Throwable> causesOf(Throwable thrown) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            chain.add(current);
        }
        return chain;
    }

    @RestController
    static class CommandFixture {

        @GetMapping("/command/reject")
        Result<Void> reject() {
            throw new BizException(ResultCode.STATE_REGRESSION, "运单已签收，不能重复签收");
        }

        @GetMapping("/command/boom")
        Result<Void> boom() {
            throw new IllegalStateException(
                    "连接失败: jdbc:mysql://10.0.0.5:3306/tms，表 outbox_event 不存在");
        }

        @GetMapping("/command/need-param")
        Result<Void> needParam(@RequestParam("waybillNo") String waybillNo) {
            return Result.ok();
        }
    }

    /**
     * {@link EventEndpoint} 标在<b>类</b>上——{@code GlobalExceptionHandler} 是按处理该请求的
     * bean 类型判断的，标在方法上不起作用。
     */
    @RestController
    @EventEndpoint
    static class EventFixture {

        @PostMapping("/events/outbound")
        String outbound() {
            throw new BizException(ResultCode.STATE_REGRESSION, "运单已签收，不能重复签收");
        }

        @PostMapping("/events/accepted")
        String accepted() {
            return "ACCEPTED";
        }
    }
}
