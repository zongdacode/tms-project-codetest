package com.example.gate0.service;

import com.example.gate0.entity.OrderPlan;
import com.example.gate0.entity.OutboxEvent;
import com.example.gate0.mapper.OrderPlanMapper;
import com.example.gate0.mapper.OutboxEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 事务性发件箱（[ADR-011]）最小实现：业务写入与 outbox 落库在同一本地事务。
 * Gate 0 验证点：跨 Mapper 的单事务写入在 Boot 4 + MP 3.5.17 下正常。
 */
@Service
public class PlanCommandService {

    private final OrderPlanMapper planMapper;
    private final OutboxEventMapper outboxMapper;

    public PlanCommandService(OrderPlanMapper planMapper, OutboxEventMapper outboxMapper) {
        this.planMapper = planMapper;
        this.outboxMapper = outboxMapper;
    }

    @Transactional
    public Long receiveDemand(String bizOrderNo, int lineNo, String sourceSystem, String sourceDocNo, boolean failAfterWrite) {
        OrderPlan plan = new OrderPlan();
        plan.setBizOrderNo(bizOrderNo);
        plan.setLineNo(lineNo);
        plan.setSourceSystem(sourceSystem);
        plan.setSourceDocNo(sourceDocNo);
        plan.setOutboundStatus(0);
        plan.setSignedStatus(0);
        plan.setCancelFlag(0);
        plan.setVersion(0);
        planMapper.insert(plan);

        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("ORDER_DISPATCHED");
        event.setBizOrderNo(bizOrderNo);
        event.setLineNo(lineNo);
        event.setPayload("{\"source_doc_no\":\"" + sourceDocNo + "\"}");
        event.setStatus("PENDING");
        event.setRetryCount(0);
        outboxMapper.insert(event);

        if (failAfterWrite) {
            throw new IllegalStateException("模拟写入后失败，验证事务回滚");
        }
        return plan.getId();
    }

    public OrderPlan findByKey(String bizOrderNo, int lineNo) {
        return planMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OrderPlan>()
                .eq("biz_order_no", bizOrderNo)
                .eq("line_no", lineNo));
    }

    public long countOutboxByBizNo(String bizOrderNo) {
        return outboxMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OutboxEvent>()
                .eq("biz_order_no", bizOrderNo));
    }
}
