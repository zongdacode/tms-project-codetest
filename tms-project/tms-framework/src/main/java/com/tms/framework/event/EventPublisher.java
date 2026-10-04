package com.tms.framework.event;

/**
 * 事件投递出口（SPI）。投递器不关心用 HTTP、MQ 还是别的通道，只要求实现方
 * <b>如实抛出失败</b>——把失败吞掉等于让 outbox 误判成功，事件静默丢失。
 *
 * <p>实现方约定：
 * <ul>
 *   <li>成功返回，失败抛 {@link EventDeliveryException} 或任意运行时异常；</li>
 *   <li><b>不得</b>在内部重试——重试与退避由 {@code OutboxDispatcher} 统一管，
 *       两层重试叠加会让退避时间变成两者之积，实际重试时机与 [06] §8 完全不符；</li>
 *   <li>收到 4xx 业务拒绝（仅命令类事件）时按 [06] §7 应<b>不重试</b>，
 *       但当前 outbox 状态机没有"永久失败"通道，故实现方需抛异常并置 DEAD——
 *       此处的处理待事件端点落地时细化（[06] §7 与 [05] §3.1 的衔接仍待定）。</li>
 * </ul>
 */
public interface EventPublisher {

    /**
     * 本实现负责投递的目标系统，取值需与 {@code outbox_event.target_system} 一致。
     *
     * <p>大小写与空白由投递器归一化后比对，实现方不必自己处理。
     */
    String targetSystem();

    void publish(EventEnvelope envelope);
}
