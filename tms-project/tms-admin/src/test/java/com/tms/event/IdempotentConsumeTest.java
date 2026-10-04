package com.tms.event;

import com.tms.framework.event.ConsumeOutcome;
import com.tms.framework.event.EventEnvelope;
import com.tms.framework.event.InboxGuard;
import com.tms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 幂等消费（常驻测试，[12] §3.3；[06] §6、[ADR-011]）。
 *
 * <p>至少一次投递意味着<b>重复必然发生</b>：网络抖动、投递成功后状态没落库、
 * 人工重推 DEAD 事件，都会让同一条事件再来一遍。幂等不是优化，是正确性前提。
 *
 * <p>这里验证三件事，每件都对应用一种真实的线上故障：
 * <ol>
 *   <li>重复事件不重复执行业务，且返回首次的结果摘要（否则调用方拿到两套答案）；</li>
 *   <li>业务失败时不留下消费记录（否则事件永远补不回来，只能人工修数据）；</li>
 *   <li>不在事务内消费直接报错（否则消费记录先落库、业务没执行，比不消费更糟）。</li>
 * </ol>
 */
class IdempotentConsumeTest extends IntegrationTestBase {

    @Autowired
    private InboxGuard inboxGuard;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private EventEnvelope envelope(String eventId, String bizOrderNo) {
        return new EventEnvelope(eventId, "OUTBOUND_COMPLETED", LocalDateTime.now(),
                "WMS", bizOrderNo, 1, 1,
                objectMapper.valueToTree(Map.of("qty", 10)), null);
    }

    @Test
    @DisplayName("同一事件投递两次：业务只执行一次，第二次返回首次的结果摘要")
    void duplicateEventExecutesBusinessOnlyOnce() {
        EventEnvelope envelope = envelope(UUID.randomUUID().toString(), "TMS-IDEM-1");
        AtomicInteger executions = new AtomicInteger();

        ConsumeOutcome first = inTransaction(() -> inboxGuard.consume(envelope, () -> {
            executions.incrementAndGet();
            return "出库完成已登记";
        }));
        ConsumeOutcome second = inTransaction(() -> inboxGuard.consume(envelope, () -> {
            executions.incrementAndGet();
            return "不该被执行到";
        }));

        assertThat(first.duplicate()).isFalse();
        assertThat(first.resultSummary()).isEqualTo("出库完成已登记");

        assertThat(second.duplicate()).as("第二次必须被识别为重复").isTrue();
        assertThat(second.resultSummary())
                .as("重复事件应返回首次结果摘要，而不是本次（未执行的）结果")
                .isEqualTo("出库完成已登记");
        assertThat(executions.get())
                .as("业务逻辑被执行了 %d 次：幂等失效，状态会被重复推进", executions.get())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("去重键包含单号+行号+事件类型：同 event_id 但不同行号是两个事件")
    void dedupKeyIncludesOrderAndLineAndType() {
        String eventId = UUID.randomUUID().toString();
        AtomicInteger executions = new AtomicInteger();

        inTransaction(() -> inboxGuard.consume(envelope(eventId, "TMS-IDEM-2A"), () -> {
            executions.incrementAndGet();
            return "行 1 完成";
        }));
        ConsumeOutcome otherLine = inTransaction(() -> inboxGuard.consume(
                new EventEnvelope(eventId, "OUTBOUND_COMPLETED", LocalDateTime.now(),
                        "WMS", "TMS-IDEM-2A", 2, 1,
                        objectMapper.valueToTree(Map.of("qty", 3)), null),
                () -> {
                    executions.incrementAndGet();
                    return "行 2 完成";
                }));

        assertThat(otherLine.duplicate())
                .as("行号不同却被当成重复：同一单据的其它行会永远不被处理")
                .isFalse();
        assertThat(executions.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("消费失败不留幂等记录，事件可被发送方重投")
    void businessFailureLeavesNoInboxRecordSoEventCanBeRetried() {
        EventEnvelope envelope = envelope(UUID.randomUUID().toString(), "TMS-IDEM-3");

        assertThatThrownBy(() -> inTransaction(() -> inboxGuard.consume(envelope, () -> {
            throw new IllegalStateException("写计划表失败");
        }))).isInstanceOf(IllegalStateException.class);

        ConsumeOutcome retried = inTransaction(() -> inboxGuard.consume(envelope, () -> "重试后成功"));

        assertThat(retried.duplicate())
                .as("失败时留下了幂等记录：事件重投会被当成已消费，这笔业务永远补不回来")
                .isFalse();
        assertThat(retried.resultSummary()).isEqualTo("重试后成功");
    }

    @Test
    @DisplayName("没有事务时消费直接报错——幂等记录与业务必须同事务")
    void consumeOutsideTransactionIsRejected() {
        EventEnvelope envelope = envelope(UUID.randomUUID().toString(), "TMS-IDEM-4");

        assertThatThrownBy(() -> inboxGuard.consume(envelope, () -> "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须在已开启的事务中调用");
    }
}
