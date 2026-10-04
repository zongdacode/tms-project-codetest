package com.tms.framework.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记"事件语义"接口（外部系统推送事实、或 TMS 对外推送事实的回执端点）。
 *
 * <p>这类接口与命令式 API 的契约不同（[docs/05] § 事件语义接口）：
 * <ul>
 *   <li>不返回 {@code Result} 信封——事实只有"收到/没收到"，没有业务拒绝码；</li>
 *   <li>业务上不接受的事件<b>不报错</b>，而是记录并忽略（幂等/时序原因）；</li>
 *   <li>错误响应体由该接口自己定义，不能套用统一异常处理器的格式。</li>
 * </ul>
 *
 * <p>因此 {@link com.tms.framework.web.exception.GlobalExceptionHandler} 遇到本注解标注的
 * 控制器时会<b>放弃包装、原样抛出</b>，把错误表达权交还给端点自身。
 *
 * <p>忘记标注的后果：事件端点返回了 {@code Result} 信封，调用方按信封解析，
 * 而 TMS 按事实语义实现——两边对"失败"的理解从此不一致。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventEndpoint {
}
