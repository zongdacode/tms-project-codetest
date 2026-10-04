package com.example.gate0;

import com.example.gate0.entity.OrderPlan;
import com.example.gate0.service.EventConsumeService;
import com.example.gate0.service.PlanCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Gate 0 关键项：
 * ① 业务写入 + outbox 落库同事务，失败一起回滚（事务性发件箱前提）；
 * ② 收件箱唯一约束去重 + 乐观锁更新可用。
 */
@SpringBootTest
class TransactionAndIdempotencyTest {

    @Autowired
    private PlanCommandService planCommandService;

    @Autowired
    private EventConsumeService eventConsumeService;

    @Test
    void planAndOutboxAreWrittenInSameTransaction() {
        String bizNo = "TMS-TEST-" + UUID.randomUUID();
        planCommandService.receiveDemand(bizNo, 1, "UPSTREAM", "SO-001", false);

        assertNotNull(planCommandService.findByKey(bizNo, 1), "计划表应落库");
        assertEquals(1L, planCommandService.countOutboxByBizNo(bizNo), "outbox 应同事务落库");
    }

    @Test
    void failureRollsBackBothPlanAndOutbox() {
        String bizNo = "TMS-TEST-ROLLBACK-" + UUID.randomUUID();
        assertThrows(IllegalStateException.class,
                () -> planCommandService.receiveDemand(bizNo, 1, "UPSTREAM", "SO-002", true));

        assertNull(planCommandService.findByKey(bizNo, 1), "异常应回滚计划表写入");
        assertEquals(0L, planCommandService.countOutboxByBizNo(bizNo), "异常应回滚 outbox 写入");
    }

    @Test
    void duplicateEventIsConsumedOnce() {
        String bizNo = "TMS-TEST-DEDUP-" + UUID.randomUUID();
        planCommandService.receiveDemand(bizNo, 1, "UPSTREAM", "SO-003", false);
        String eventId = UUID.randomUUID().toString();

        assertEquals("OK", eventConsumeService.consumeOutboundCompleted(eventId, bizNo, 1));
        assertEquals("DUPLICATE", eventConsumeService.consumeOutboundCompleted(eventId, bizNo, 1));

        OrderPlan plan = eventConsumeService.findPlan(bizNo, 1);
        assertNotNull(plan);
        assertEquals(1, plan.getOutboundStatus(), "出库状态应为已完成");
        assertEquals(1, plan.getVersion(), "乐观锁版本应只前进一次");
    }
}
