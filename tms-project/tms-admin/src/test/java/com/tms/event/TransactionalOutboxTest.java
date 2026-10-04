package com.tms.event;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tms.framework.event.OutboxRecorder;
import com.tms.framework.event.entity.OutboxEvent;
import com.tms.framework.event.mapper.OutboxEventMapper;
import com.tms.plan.entity.OrderPlan;
import com.tms.plan.mapper.OrderPlanMapper;
import com.tms.support.IntegrationTestBase;
import com.tms.support.OrderPlanFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 事务性发件箱（常驻测试，[12] §3.3；[ADR-011]）。
 *
 * <p>这条保证只有一句话：<b>业务成功 ⇔ 事件必达</b>。它由两个方向组成，
 * 缺任何一个这个模式都没意义：
 * <ul>
 *   <li>提交方向——业务数据与 outbox 记录一起提交（否则业务成功但事件丢了）；</li>
 *   <li>回滚方向——业务回滚时 outbox 记录一并消失（否则事件发了，业务却不存在，
 *       下游按事件去处理一个查无此单的业务）。</li>
 * </ul>
 * 回滚方向是最容易被忽略的：代码"看起来"只是往表里 insert 了一行，
 * 不跑一次真实回滚，永远不知道它到底有没有跟着回滚。
 */
class TransactionalOutboxTest extends IntegrationTestBase {

    @Autowired
    private OutboxRecorder outboxRecorder;

    @Autowired
    private OutboxEventMapper outboxEventMapper;

    @Autowired
    private OrderPlanMapper orderPlanMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private <T> T inTransaction(Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> action.get());
    }

    @Test
    @DisplayName("业务数据与事件一起提交，事件落为 PENDING 待投递")
    void businessDataAndEventCommitTogether() {
        OrderPlan plan = OrderPlanFixture.newPlan();

        OutboxEvent event = inTransaction(() -> {
            orderPlanMapper.insert(plan);
            return outboxRecorder.recordFact("SIGNED_COMPLETED", plan.getBizOrderNo(), 1, 1,
                    Map.of("waybillNo", "WB-001"), "WMS");
        });

        assertThat(orderPlanMapper.selectById(plan.getId())).isNotNull();
        OutboxEvent persisted = outboxEventMapper.selectById(event.getId());
        assertThat(persisted).as("事件未落库").isNotNull();
        assertThat(persisted.getStatus()).isEqualTo("PENDING");
        assertThat(persisted.getRetryCount()).isZero();
        assertThat(persisted.getNextRetryAt()).as("首次投递前不应有重试时间").isNull();
        assertThat(persisted.getCreatedAt()).as("created_at 应由 MetaObjectHandler 填充").isNotNull();
        assertThat(persisted.getSourceSystem()).isEqualTo("TMS");
        assertThat(persisted.getEventId())
                .as("event_id 必须已生成：投递重试要靠它保持稳定（[06] §3）")
                .isNotBlank();
    }

    @Test
    @DisplayName("业务回滚时事件也必须消失——否则会发出一个不存在的业务的事实")
    void businessRollbackAlsoRemovesEvent() {
        OrderPlan plan = OrderPlanFixture.newPlan();
        String bizOrderNo = plan.getBizOrderNo();

        assertThatThrownBy(() -> inTransaction(() -> {
            orderPlanMapper.insert(plan);
            outboxRecorder.recordFact("SIGNED_COMPLETED", bizOrderNo, 1, 1,
                    Map.of("waybillNo", "WB-002"), "WMS");
            throw new IllegalStateException("业务校验失败，本次事务作废");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(orderPlanMapper.selectById(plan.getId()))
                .as("业务数据未回滚")
                .isNull();
        assertThat(outboxEventMapper.selectCount(
                Wrappers.<OutboxEvent>lambdaQuery().eq(OutboxEvent::getBizOrderNo, bizOrderNo)))
                .as("业务回滚了，事件却留在发件箱里——下游会收到一个查无此单的事实")
                .isZero();
    }

    @Test
    @DisplayName("没有事务时记事件直接报错，而不是静默落库")
    void recordingOutsideTransactionIsRejected() {
        assertThatThrownBy(() -> outboxRecorder.recordFact("SIGNED_COMPLETED", "TMS-X-1", 1, 1,
                Map.of("k", "v"), "WMS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须在已开启的事务中调用");
    }

    @Test
    @DisplayName("targetSystem 为空直接拒绝：没有收件方的事件投不出去")
    void blankTargetSystemIsRejected() {
        OrderPlan plan = OrderPlanFixture.newPlan();

        assertThatThrownBy(() -> inTransaction(() -> {
            orderPlanMapper.insert(plan);
            return outboxRecorder.recordFact("SIGNED_COMPLETED", plan.getBizOrderNo(), 1, 1,
                    Map.of("k", "v"), "  ");
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetSystem");
    }
}
