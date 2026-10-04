package com.tms.framework.web.exception;

import com.tms.common.api.Result;
import com.tms.common.api.ResultCode;
import com.tms.common.exception.BizException;
import com.tms.framework.web.EventEndpoint;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 统一异常处理（仅作用于<b>命令式</b> HTTP API）。
 *
 * <p>三条硬规则：
 * <ol>
 *   <li><b>事件端点让出</b>——见 {@link EventEndpoint}，不套信封；</li>
 *   <li><b>5xx 不回显异常消息</b>——原始消息可能含 SQL、表名、内网地址。
 *       原始异常进日志，响应里只给 {@link ResultCode}；</li>
 *   <li><b>参数错误必须定位到字段</b>——"参数错误"对调用方无价值，
 *       要给出 {@code 字段名: 原因}。</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务可预期的拒绝：4xx，消息可回显（由业务自己保证不含敏感信息）。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException ex, HandlerMethod handlerMethod) {
        if (isEventEndpoint(handlerMethod)) {
            return rethrow(ex);
        }
        ResultCode code = ex.getResultCode();
        String message = ex.getMessage() == null ? code.message() : ex.getMessage();
        log.info("业务拒绝 code={} msg={}", code.code(), message);
        return ResponseEntity.status(statusOf(code))
                .body(Result.fail(code, message));
    }

    /**
     * 参数绑定/校验失败。
     *
     * <p>只需这一个处理器：{@code MethodArgumentNotValidException}（{@code @RequestBody} + {@code @Valid}）
     * 是 {@link BindException} 的子类，Spring 会选中最贴近的那个，而这里只有一个。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException ex, HandlerMethod handlerMethod) {
        if (isEventEndpoint(handlerMethod)) {
            return rethrow(ex);
        }
        return badRequest(ex.getBindingResult().getFieldErrors()
                .stream()
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining("; ")));
    }

    /** 方法参数上的 {@code @Validated} 校验失败（如 {@code @Min} 直接标在入参上）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex,
                                                                  HandlerMethod handlerMethod) {
        if (isEventEndpoint(handlerMethod)) {
            return rethrow(ex);
        }
        String detail = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining("; "));
        return badRequest(detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException ex,
                                                           HandlerMethod handlerMethod) {
        if (isEventEndpoint(handlerMethod)) {
            return rethrow(ex);
        }
        return badRequest("缺少必填参数: " + ex.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException ex,
                                                          HandlerMethod handlerMethod) {
        if (isEventEndpoint(handlerMethod)) {
            return rethrow(ex);
        }
        log.info("请求体无法解析: {}", ex.getMessage());
        return badRequest("请求体格式错误，无法解析");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                 HandlerMethod handlerMethod) {
        if (isEventEndpoint(handlerMethod)) {
            return rethrow(ex);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail(ResultCode.METHOD_NOT_ALLOWED, "不支持的请求方法: " + ex.getMethod()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex,
                                                         HandlerMethod handlerMethod) {
        if (isEventEndpoint(handlerMethod)) {
            return rethrow(ex);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(ResultCode.NOT_FOUND, "资源不存在: " + ex.getResourcePath()));
    }

    /**
     * 兜底。消息<b>不回显</b>——这里什么都可能进来，包括驱动异常、连接串、堆栈片段。
     * 排查靠日志，不靠响应体。
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Throwable ex, HandlerMethod handlerMethod) {
        if (isEventEndpoint(handlerMethod)) {
            return rethrow(ex);
        }
        log.error("未预期异常，uri 处理失败", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.INTERNAL_ERROR));
    }

    /**
     * 原样抛出（含受检异常），把错误表达权交还给事件端点自身。
     *
     * <p>泛型参数 {@code E} 在调用处被推断为非受检异常，因此调用方无需 {@code throws} 声明——
     * 这正是不想为 8 个处理器逐个挂 {@code throws} 的原因。
     */
    private static <E extends Throwable> ResponseEntity<Result<Void>> rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    private static boolean isEventEndpoint(HandlerMethod handlerMethod) {
        return handlerMethod != null
                && handlerMethod.getBeanType().isAnnotationPresent(EventEndpoint.class);
    }

    private static ResponseEntity<Result<Void>> badRequest(String detail) {
        return ResponseEntity.badRequest().body(Result.fail(ResultCode.PARAM_INVALID, detail));
    }

    private static String describe(FieldError error) {
        String message = error.getDefaultMessage();
        return error.getField() + ": " + (message == null ? "不合法" : message);
    }

    private static String describe(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }

    private static HttpStatus statusOf(ResultCode code) {
        int value = code.code();
        if (value == ResultCode.SUCCESS.code()) {
            return HttpStatus.OK;
        }
        return switch (value / 10000) {
            case 1, 2 -> HttpStatus.BAD_REQUEST;             // 1xxxx 框架 / 2xxxx 参数
            case 3 -> HttpStatus.UNAUTHORIZED;               // 3xxxx 认证
            // 4xxxx 业务拒绝。用 UNPROCESSABLE_CONTENT：Spring Framework 7 起
            // UNPROCESSABLE_ENTITY 已过时（RFC 9110 把 422 改为 Unprocessable Content），
            // 状态码数值仍是 422。
            case 4 -> HttpStatus.UNPROCESSABLE_CONTENT;
            case 5 -> HttpStatus.BAD_GATEWAY;                // 5xxxx 外部系统
            default -> HttpStatus.INTERNAL_SERVER_ERROR;     // 9xxxx 内部
        };
    }
}
