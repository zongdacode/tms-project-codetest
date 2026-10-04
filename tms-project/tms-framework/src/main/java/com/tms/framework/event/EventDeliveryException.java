package com.tms.framework.event;

/**
 * 事件投递失败：网络不可达、超时、对方 5xx、响应无法解析等<b>可重试</b>场景。
 *
 * <p>与"信封本身有问题"要分开：后者重试一万次结果一样，应在投递器里直接置 DEAD。
 */
public class EventDeliveryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 供告警与排障判断是否值得重试；null 表示未分类。 */
    private final Integer httpStatus;

    public EventDeliveryException(String message) {
        this(message, null, null);
    }

    public EventDeliveryException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public EventDeliveryException(String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
