package com.tms.common.context;

import java.util.Optional;

/**
 * 当前登录用户上下文（线程级）。
 *
 * <p>由框架层过滤/拦截器在请求进入时写入、结束时清理；业务代码只读。
 * 审计字段自动填充依赖它取操作人。
 *
 * <p><b>阶段 2 注意</b>：拆服务后本上下文只对新服务自身的请求有效；
 * 跨服务调用需通过 HTTP Header 透传（Feign 拦截器），届时此处不变、透传层新增。
 */
public final class UserContext {

    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserPrincipal principal) {
        HOLDER.set(principal);
    }

    public static Optional<UserPrincipal> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    /** 取当前用户 ID；未登录（如定时任务、系统间调用）返回 {@code null}。 */
    public static Long currentUserId() {
        UserPrincipal principal = HOLDER.get();
        return principal == null ? null : principal.userId();
    }

    public static String currentUserName() {
        UserPrincipal principal = HOLDER.get();
        return principal == null ? null : principal.userName();
    }

    /**
     * 必须清理。容器线程池会复用线程，残留上下文会串号到下一个请求。
     */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * @param userId   用户主键
     * @param userName 展示名
     * @param tenantId 当前生效工厂；多工厂隔离未启用期间可为 {@code null}
     */
    public record UserPrincipal(Long userId, String userName, Long tenantId) {
    }
}
