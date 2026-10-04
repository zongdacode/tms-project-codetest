package com.tms.support;

import com.tms.plan.entity.OrderPlan;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 造计划表测试数据的工具。
 *
 * <p>每行都带唯一 {@code biz_order_no}（表上有唯一键 {@code uk_plan}），
 * 这样各测试共用一个 H2 库也不会互相撞键——省掉了清理逻辑，也就省掉了
 * "清理没写全导致测试顺序相关"这类难查的偶发失败。
 */
public final class OrderPlanFixture {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private OrderPlanFixture() {
    }

    /** 以给定前缀生成一支唯一的业务单号。 */
    public static String uniqueOrderNo(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "-" + SEQ.incrementAndGet();
    }

    /** 建档即"已下发"（[05-data-model] §2：dispatch_status 建档即 1）。 */
    public static OrderPlan newPlan(String bizOrderNo) {
        OrderPlan plan = new OrderPlan();
        plan.setBizOrderNo(bizOrderNo);
        plan.setLineNo(1);
        plan.setSourceSystem("UPSTREAM");
        plan.setSourceDocNo("SRC-" + bizOrderNo);
        plan.setOrderType("TRANSFER");
        plan.setShipFromCode("WH-001");
        plan.setShipToCode("WH-002");
        plan.setPlanQty(new BigDecimal("100.0000"));
        plan.setDispatchStatus(1);
        plan.setDispatchedAt(java.time.LocalDateTime.now());
        plan.setOutboundStatus(0);
        plan.setInboundStatus(0);
        plan.setSignedStatus(0);
        plan.setCancelFlag(0);
        plan.setReverseFlag(0);
        return plan;
    }

    public static OrderPlan newPlan() {
        return newPlan(uniqueOrderNo("TMS-TEST"));
    }
}
