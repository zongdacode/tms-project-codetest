package com.tms.event;

import com.tms.framework.event.EventDeliveryException;
import com.tms.framework.event.EventEnvelope;
import com.tms.framework.event.EventPublisher;
import com.tms.framework.event.OutboxDispatcher;
import com.tms.framework.event.OutboxRecorder;
import com.tms.framework.event.entity.OutboxEvent;
import com.tms.framework.event.mapper.OutboxEventMapper;
import com.tms.support.IntegrationTestBase;
import com.tms.support.OrderPlanFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 发件箱投递器：退避、耗尽置 DEAD、配置缺失时的行为（[06] §8、[ADR-011]）。
 *
 * <p>退避时间在真实环境里要等 9 小时才能跑完一轮，所以这里不睡等，而是
 * <b>把"当前时间"作为参数传给 {@code dispatchOnce}</b>——投递器接受一个时间点，
 * 测试把它往后拨。这样几毫秒内就能验证完整的 1m→5m→30m→2h→6h→DEAD 阶梯，
 * 且断言的是精确值，不是"大概等了一会儿"。
 */
@TestPropertySource(properties = {
        // 关掉定时扫描：调度器会在上下文启动后立刻跑一次，之后每 30 秒一次，
        // 与测试手工驱动的 dispatchOnce 争抢同一批事件，造成偶发失败。
        "tms.event.outbox.scan-interval-ms=3600000"
})
// 必须显式 @Import：Spring 只扫描"声明 @SpringBootTest 的那个类"内部的嵌套配置类，
// 而 @SpringBootTest 写在父类 IntegrationTestBase 上，本类的嵌套类不在扫描范围内。
// 后果很隐蔽——投递实现没注册，事件被 selectDueEvents 正常选中却一条都投不出去，
// 测试看到的是"状态没变"，很像投递器坏了。
@Import(OutboxDispatcherTest.PublisherConfig.class)
class OutboxDispatcherTest extends IntegrationTestBase {

    /** 配置里 max-retries=5，即首次失败后再重试 5 次，共 6 次投递。 */
    private static final int TOTAL_ATTEMPTS_BEFORE_DEAD = 6;

    /** 有投递实现的目标系统（见 {@link PublisherConfig}）。 */
    private static final String DELIVERABLE_TARGET = "WMS";

    /** 没有投递实现的目标系统，用来验证"配置缺失"与"投递失败"被区别对待。 */
    private static final String UNWIRED_TARGET = "SAP";

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private OutboxRecorder outboxRecorder;

    @Autowired
    private OutboxEventMapper outboxEventMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetPublisher() {
        ControllablePublisher.FAILURE.set(null);
        ControllablePublisher.DELIVERED.clear();
    }

    @Test
    @DisplayName("投递成功：状态置 SENT，记录 sent_at，清空重试时间")
    void successfulDeliveryMarksSent() {
        OutboxEvent event = pendingEvent(DELIVERABLE_TARGET);

        int handled = dispatcher.dispatchOnce(LocalDateTime.now());

        assertThat(handled).isGreaterThanOrEqualTo(1);
        OutboxEvent after = outboxEventMapper.selectById(event.getId());
        assertThat(after.getStatus()).isEqualTo("SENT");
        assertThat(after.getSentAt()).as("缺少发送时间，事后无法判断这条事件什么时候到的").isNotNull();
        assertThat(after.getNextRetryAt()).isNull();
        assertThat(ControllablePublisher.DELIVERED)
                .anySatisfy(envelope -> assertThat(envelope.eventId()).isEqualTo(event.getEventId()));
    }

    @Test
    @DisplayName("首次投递失败：状态按序列退避，第一次等 1 分钟")
    void failedDeliverySchedulesFirstBackoffOfOneMinute() {
        OutboxEvent event = pendingEvent(DELIVERABLE_TARGET);
        ControllablePublisher.FAILURE.set(new EventDeliveryException("连接被拒绝"));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        dispatcher.dispatchOnce(now);

        OutboxEvent after = outboxEventMapper.selectById(event.getId());
        assertThat(after.getStatus()).isEqualTo("FAILED");
        assertThat(after.getRetryCount()).isEqualTo(1);
        assertThat(after.getNextRetryAt())
                .as("退避序列首项应为 1 分钟（[06] §8）")
                .isEqualTo(now.plusMinutes(1));
    }

    @Test
    @DisplayName("退避阶梯逐级推进：1m → 5m → 30m → 2h → 6h，第 6 次失败置 DEAD")
    void retriesWalkTheBackoffLadderThenGoDead() {
        OutboxEvent event = pendingEvent(DELIVERABLE_TARGET);
        ControllablePublisher.FAILURE.set(new EventDeliveryException("连接被拒绝"));

        // 每一轮都把"当前时间"往后拨一天，保证下一次重试时间已到
        LocalDateTime base = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        long[] expectedBackoffMinutes = {1, 5, 30, 120, 360};

        for (int attempt = 0; attempt < TOTAL_ATTEMPTS_BEFORE_DEAD - 1; attempt++) {
            LocalDateTime now = base.plusDays(attempt + 1);
            dispatcher.dispatchOnce(now);

            OutboxEvent after = outboxEventMapper.selectById(event.getId());
            assertThat(after.getStatus())
                    .as("第 %d 次失败后状态应为 FAILED（还有重试机会）", attempt + 1)
                    .isEqualTo("FAILED");
            assertThat(after.getRetryCount()).isEqualTo(attempt + 1);
            assertThat(after.getNextRetryAt())
                    .as("第 %d 次失败后的退避时长不符（[06] §8 序列）", attempt + 1)
                    .isEqualTo(now.plusMinutes(expectedBackoffMinutes[attempt]));
        }

        // 最后一次：重试次数用尽
        LocalDateTime lastRound = base.plusDays(TOTAL_ATTEMPTS_BEFORE_DEAD);
        dispatcher.dispatchOnce(lastRound);

        OutboxEvent dead = outboxEventMapper.selectById(event.getId());
        assertThat(dead.getStatus())
                .as("重试用尽后应置 DEAD：继续留在 FAILED 会被下一轮扫描无限重投")
                .isEqualTo("DEAD");
        assertThat(dead.getNextRetryAt()).as("DEAD 是终态，不应再有下次重试时间").isNull();
    }

    @Test
    @DisplayName("目标系统没有投递实现：事件保持原状，不被误判为投递失败")
    void targetWithoutPublisherLeavesEventUntouched() {
        OutboxEvent event = pendingEvent(UNWIRED_TARGET);

        dispatcher.dispatchOnce(LocalDateTime.now());

        OutboxEvent after = outboxEventMapper.selectById(event.getId());
        assertThat(after.getStatus())
                .as("配置疏漏被升级成了数据事故：有效事件被重试耗尽并置成 DEAD，只能人工恢复")
                .isEqualTo("PENDING");
        assertThat(after.getRetryCount()).isZero();
        assertThat(after.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("负载不是合法 JSON：一次就置 DEAD，不浪费 5 次重试配额")
    void corruptPayloadGoesDeadImmediately() {
        OutboxEvent broken = new OutboxEvent();
        broken.setEventId(UUID.randomUUID().toString());
        broken.setEventType("SIGNED_COMPLETED");
        broken.setSourceSystem("TMS");
        broken.setBizOrderNo(OrderPlanFixture.uniqueOrderNo("TMS-BROKEN"));
        broken.setLineNo(1);
        broken.setAggregateVersion(1);
        broken.setOccurredAt(LocalDateTime.now());
        broken.setPayload("这不是 JSON");
        broken.setTargetSystem(DELIVERABLE_TARGET);
        broken.setStatus("PENDING");
        broken.setRetryCount(0);
        outboxEventMapper.insert(broken);

        dispatcher.dispatchOnce(LocalDateTime.now());

        OutboxEvent after = outboxEventMapper.selectById(broken.getId());
        assertThat(after.getStatus())
                .as("坏负载重试一万次还是坏的，应直接 DEAD 等人工处理")
                .isEqualTo("DEAD");
    }

    private OutboxEvent pendingEvent(String targetSystem) {
        return inTransaction(() -> outboxRecorder.recordFact(
                "SIGNED_COMPLETED", OrderPlanFixture.uniqueOrderNo("TMS-DISP"), 1, 1,
                Map.of("waybillNo", "WB-TEST"), targetSystem));
    }

    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    /**
     * 可远程控制的投递实现：把"成功/失败"做成开关，测完再复位。
     *
     * <p>用静态状态是因为投递器在上下文启动时就拿到了 publisher 实例并缓存；
     * 让实例状态可变、而非换 bean，才能在不重建上下文的前提下切换行为。
     */
    @TestConfiguration
    static class PublisherConfig {

        @Bean
        EventPublisher controllableWmsPublisher() {
            return new ControllablePublisher();
        }
    }

    static class ControllablePublisher implements EventPublisher {

        static final AtomicReference<RuntimeException> FAILURE = new AtomicReference<>();
        static final List<EventEnvelope> DELIVERED = new CopyOnWriteArrayList<>();

        @Override
        public String targetSystem() {
            return DELIVERABLE_TARGET;
        }

        @Override
        public void publish(EventEnvelope envelope) {
            RuntimeException failure = FAILURE.get();
            if (failure != null) {
                throw failure;
            }
            DELIVERED.add(envelope);
        }
    }
}
