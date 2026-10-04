package com.tms.framework.event;

/**
 * 发件箱投递状态（[05-data-model] §3.1）。
 *
 * <pre>
 * PENDING ──投递成功──▶ SENT
 *    │
 *    └─投递失败─▶ FAILED ──重试成功──▶ SENT
 *                   │
 *                   └─重试耗尽─▶ DEAD（需人工介入，可重推，event_id 不变）
 * </pre>
 *
 * <p>落库用字符串而非序号：这张表会被人工直接查、直接改（重推 DEAD 事件），
 * 存 {@code 3} 还是 {@code DEAD} 决定了排障时要不要翻代码。
 */
public enum OutboxStatus {

    /** 待投递。业务事务内落库时的初始状态。 */
    PENDING,

    /** 投递成功。终态。 */
    SENT,

    /** 投递失败且有剩余重试次数，等待 {@code next_retry_at}。 */
    FAILED,

    /** 重试耗尽。终态，需人工介入。 */
    DEAD
}
