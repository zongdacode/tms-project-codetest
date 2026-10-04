package com.example.gate0.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.gate0.entity.InboxConsumed;
import com.example.gate0.entity.OrderPlan;
import com.example.gate0.mapper.InboxConsumedMapper;
import com.example.gate0.mapper.OrderPlanMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 幂等消费（[ADR-011] / [06-api-contracts] §6）：消费逻辑与幂等记录同一本地事务。
 * Gate 0 验证点：唯一约束去重 + 乐观锁 @Version 在 Boot 4 下行为正常。
 */
@Service
public class EventConsumeService {

    private final InboxConsumedMapper inboxMapper;
    private final OrderPlanMapper planMapper;

    public EventConsumeService(InboxConsumedMapper inboxMapper, OrderPlanMapper planMapper) {
        this.inboxMapper = inboxMapper;
        this.planMapper = planMapper;
    }

    /**
     * @return 首次消费返回 "OK"；重复事件返回 "DUPLICATE"。
     */
    @Transactional
    public String consumeOutboundCompleted(String eventId, String bizOrderNo, int lineNo) {
        InboxConsumed record = new InboxConsumed();
        record.setEventId(eventId);
        record.setBizOrderNo(bizOrderNo);
        record.setLineNo(lineNo);
        record.setEventType("OUTBOUND_COMPLETED");
        record.setResultSummary("plan marked outbound");
        try {
            inboxMapper.insert(record);
        } catch (DuplicateKeyException e) {
            return "DUPLICATE";
        }

        OrderPlan plan = planMapper.selectOne(new QueryWrapper<OrderPlan>()
                .eq("biz_order_no", bizOrderNo)
                .eq("line_no", lineNo));
        if (plan != null && plan.getOutboundStatus() != null && plan.getOutboundStatus() == 1) {
            return "DUPLICATE";
        }
        if (plan != null) {
            plan.setOutboundStatus(1);
            planMapper.updateById(plan);
        }
        return "OK";
    }

    public OrderPlan findPlan(String bizOrderNo, int lineNo) {
        return planMapper.selectOne(new QueryWrapper<OrderPlan>()
                .eq("biz_order_no", bizOrderNo)
                .eq("line_no", lineNo));
    }
}
