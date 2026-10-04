package com.tms.framework.event;

import com.tms.framework.event.entity.OutboxEvent;
import com.tms.framework.event.mapper.OutboxEventMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发件箱投递器：扫描到期事件、按退避序列重试、耗尽置 DEAD（[06] §8、[ADR-011]）。
 *
 * <p><b>本类不做多实例抢占</b>：{@code selectDueEvents} 没有锁语义，两个实例会扫到同一批并各投一次。
 * 当前部署是单实例，所以先不做。真要上多实例之前，必须先定下抢占方案
 * （{@code SELECT ... FOR UPDATE SKIP LOCKED}、或 Redis 分布式锁、或给状态机加 SENDING 中间态），
 * 这几种对现有状态机的改动量差别很大，属于要写 ADR 的决定，不能顺手改。
 *
 * <p><b>投递调用不在数据库事务里</b>：一次 HTTP 投递可能耗时数秒，
 * 放进事务会长时间占着连接和行锁。这里刻意不加 {@code @Transactional}，
 * 投递完再用单条 UPDATE 落状态——代价是"投递成功但状态没落库"时会对同一事件重投一次，
 * 而接收方幂等正是为此准备的（[06] §6）。
 */
@Component
@ConditionalOnProperty(prefix = "tms.event.outbox", name = "dispatcher-enabled",
        havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;
    private final Map<String, EventPublisher> publishersByTarget = new HashMap<>();

    /** 已就"缺少投递实现"告警过的目标系统，避免每个扫描周期刷屏。 */
    private final Set<String> warnedMissingPublisher = ConcurrentHashMap.newKeySet();

    public OutboxDispatcher(OutboxEventMapper outboxEventMapper,
                            ObjectMapper objectMapper,
                            OutboxProperties properties,
                            List<EventPublisher> publishers) {
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
        for (EventPublisher publisher : publishers) {
            String key = normalize(publisher.targetSystem());
            EventPublisher previous = publishersByTarget.put(key, publisher);
            if (previous != null) {
                throw new IllegalStateException(
                        "目标系统 " + publisher.targetSystem() + " 存在多个投递实现："
                                + previous.getClass().getName() + " 与 " + publisher.getClass().getName()
                                + "。同时注册两个会随机挑一个用，投递结果不可预期。");
            }
        }
    }

    @PostConstruct
    void validateConfiguration() {
        List<Duration> backoff = properties.getBackoff();
        if (backoff == null || backoff.size() != properties.getMaxRetries()) {
            throw new IllegalStateException(String.format(
                    "退避序列长度(%s)必须等于最大重试次数(%d)："
                            + "否则要么最后一个退避永远用不上，要么重试到一半没有等待时长可用。",
                    backoff == null ? "null" : String.valueOf(backoff.size()), properties.getMaxRetries()));
        }
    }

    @Scheduled(fixedDelayString = "${tms.event.outbox.scan-interval-ms:30000}")
    public void dispatchDueEvents() {
        try {
            int handled = dispatchOnce(LocalDateTime.now());
            if (handled > 0) {
                log.debug("本轮投递处理 {} 条事件", handled);
            }
        } catch (RuntimeException ex) {
            // 定时任务里抛出的异常会静默终止后续调度，必须自己吞掉并记录
            log.error("发件箱扫描失败，下轮将重试", ex);
        }
    }

    /**
     * 执行一轮扫描，返回本轮处理的事件条数。
     *
     * <p>单独暴露出来便于测试直接驱动，不必等调度器触发。
     */
    public int dispatchOnce(LocalDateTime now) {
        List<OutboxEvent> due = outboxEventMapper.selectDueEvents(now, properties.getScanBatchSize());
        int handled = 0;
        for (OutboxEvent event : due) {
            deliver(event, now);
            handled++;
        }
        return handled;
    }

    private void deliver(OutboxEvent event, LocalDateTime now) {
        EventPublisher publisher = publishersByTarget.get(normalize(event.getTargetSystem()));
        if (publisher == null) {
            // 没配投递实现是装配问题，不是"投递失败"。
            // 若当成失败去重试，5 次后会把这批有效事件置成 DEAD——把配置疏漏升级成数据事故。
            if (warnedMissingPublisher.add(normalize(event.getTargetSystem()))) {
                log.warn("目标系统 {} 没有注册 EventPublisher，事件保持原状不投递："
                                + "该事件会一直积压在 outbox_event 里，补齐实现后自动开始投递",
                        event.getTargetSystem());
            }
            return;
        }

        EventEnvelope envelope;
        try {
            envelope = toEnvelope(event);
        } catch (RuntimeException ex) {
            // 负载坏了：重试一万次还是坏的，直接 DEAD 等人工处理，别占用重试配额
            log.error("事件负载无法还原，置 DEAD。id={} event_id={} type={}",
                    event.getId(), event.getEventId(), event.getEventType(), ex);
            outboxEventMapper.markFailure(event.getId(), OutboxStatus.DEAD.name(),
                    retryCountOf(event), retryCountOf(event), null);
            return;
        }

        try {
            publisher.publish(envelope);
            outboxEventMapper.markSent(event.getId(), LocalDateTime.now());
        } catch (RuntimeException ex) {
            handleDeliveryFailure(event, now, ex);
        }
    }

    private void handleDeliveryFailure(OutboxEvent event, LocalDateTime now, RuntimeException cause) {
        int expected = retryCountOf(event);
        int nextRetryCount = expected + 1;
        if (nextRetryCount > properties.getMaxRetries()) {
            log.error("事件投递重试耗尽，置 DEAD 待人工介入。id={} event_id={} type={} target={} 已重试={}次",
                    event.getId(), event.getEventId(), event.getEventType(),
                    event.getTargetSystem(), expected, cause);
            outboxEventMapper.markFailure(event.getId(), OutboxStatus.DEAD.name(), expected, nextRetryCount, null);
            return;
        }

        Duration wait = properties.getBackoff().get(nextRetryCount - 1);
        LocalDateTime nextAttemptAt = now.plus(wait);
        int updated = outboxEventMapper.markFailure(event.getId(), OutboxStatus.FAILED.name(),
                expected, nextRetryCount, nextAttemptAt);
        if (updated == 0) {
            log.warn("事件重试次数已被其他进程改动，本次跳过。id={} 期望 retry_count={}",
                    event.getId(), expected);
            return;
        }
        log.warn("事件投递失败，将在 {} 后重试（第 {}/{} 次）。id={} event_id={}",
                wait, nextRetryCount, properties.getMaxRetries(), event.getId(), event.getEventId(), cause);
    }

    /** 由 outbox 行还原完整信封（[06] §3）。 */
    EventEnvelope toEnvelope(OutboxEvent event) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(event.getPayload());
        } catch (JacksonException ex) {
            throw new IllegalStateException("payload 不是合法 JSON", ex);
        }
        try {
            return new EventEnvelope(
                    event.getEventId(),
                    event.getEventType(),
                    event.getOccurredAt(),
                    event.getSourceSystem(),
                    event.getBizOrderNo(),
                    event.getLineNo(),
                    event.getAggregateVersion(),
                    payload,
                    event.getTraceId());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("outbox 行缺字段，无法还原信封: " + ex.getMessage(), ex);
        }
    }

    private static int retryCountOf(OutboxEvent event) {
        return event.getRetryCount() == null ? 0 : event.getRetryCount();
    }

    private static String normalize(String targetSystem) {
        return targetSystem == null ? "" : targetSystem.trim().toUpperCase(Locale.ROOT);
    }
}
