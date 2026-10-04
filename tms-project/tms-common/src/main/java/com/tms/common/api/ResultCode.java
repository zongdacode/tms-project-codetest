package com.tms.common.api;

/**
 * 错误码分段约定。新增码必须落在对应段内，禁止跨段借用。
 *
 * <pre>
 * 0      成功
 * 1xxxx  框架/通用（参数格式、序列化、限流）
 * 2xxxx  参数与校验
 * 3xxxx  认证与鉴权
 * 4xxxx  业务规则
 * 5xxxx  外部系统与集成（WMS/上游/GPS）
 * 9xxxx  系统内部错误（未预期异常兜底）
 * </pre>
 */
public enum ResultCode {

    SUCCESS(0, "成功"),

    // ---- 1xxxx 框架/通用 ----
    BAD_REQUEST(10000, "请求格式错误"),
    METHOD_NOT_ALLOWED(10001, "请求方法不支持"),
    NOT_FOUND(10002, "资源不存在"),
    IDEMPOTENT_CONFLICT(10003, "重复请求"),

    // ---- 2xxxx 参数与校验 ----
    PARAM_INVALID(20000, "参数校验失败"),
    PARAM_MISSING(20001, "必填参数缺失"),

    // ---- 3xxxx 认证与鉴权 ----
    UNAUTHENTICATED(30000, "未登录或凭证已失效"),
    FORBIDDEN(30001, "无操作权限"),

    // ---- 4xxxx 业务规则 ----
    BIZ_RULE_REJECTED(40000, "业务规则拒绝"),
    /** 状态只前进（[ADR-013]）：收到旧版本或状态回退请求时返回。 */
    STATE_REGRESSION(40001, "状态不允许回退"),
    DUPLICATE_KEY(40002, "业务唯一键冲突"),

    // ---- 5xxxx 外部系统与集成 ----
    PEER_REJECTED(50000, "对方系统业务拒绝"),
    PEER_UNAVAILABLE(50001, "对方系统不可用"),

    // ---- 9xxxx 系统内部 ----
    INTERNAL_ERROR(90000, "系统内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
