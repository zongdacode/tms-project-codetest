package com.tms.framework.event;

import com.tms.framework.event.entity.InboxConsumed;
import com.tms.framework.event.mapper.InboxConsumedMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * 幂等消费（[06] §6、[ADR-011]）。
 *
 * <p><b>顺序是"先占坑，再执行"，不是"先查再执行"</b>：
 * <ol>
 *   <li>先 INSERT 幂等记录——重复投递在这里撞唯一键，业务逻辑根本没机会跑；</li>
 *   <li>再执行消费逻辑；</li>
 *   <li>最后回填结果摘要。</li>
 * </ol>
 * 若改成先 SELECT 判断再执行，两次投递并发时都会查到"没有"，两次都执行业务——
 * 唯一键这时已经晚了。并发安全只能靠 DB 约束来裁定，不能靠应用层查一把。
 *
 * <p>全流程在调用方事务内：消费逻辑抛异常 → 幂等记录一并回滚 → 事件在发送方 outbox
 * 继续重试（[06] §6-5）。这正是"消费失败不留幂等记录"的实现方式——不需要额外代码去补偿删除。
 */
@Component
public class InboxGuard {

    private final InboxConsumedMapper inboxConsumedMapper;

    public InboxGuard(InboxConsumedMapper inboxConsumedMapper) {
        this.inboxConsumedMapper = inboxConsumedMapper;
    }

    /**
     * 消费一个事件；重复事件直接返回首次结果，不执行业务逻辑。
     *
     * @param businessAction 消费逻辑，返回结果摘要（写入 {@code result_summary}）
     */
    public ConsumeOutcome consume(EventEnvelope envelope, Supplier<String> businessAction) {
        TransactionGuard.requireActiveTransaction("InboxGuard.consume");

        if (!tryClaim(envelope)) {
            InboxConsumed existing = findByDedupKey(envelope);
            String summary = existing == null ? null : existing.getResultSummary();
            return ConsumeOutcome.duplicate(summary);
        }

        String summary = businessAction.get();
        inboxConsumedMapper.updateSummary(
                envelope.eventId(), envelope.bizOrderNo(), envelope.lineNo(), envelope.eventType(), summary);
        return ConsumeOutcome.consumed(summary);
    }

    /** true = 本次占坑成功，可以执行消费逻辑。 */
    private boolean tryClaim(EventEnvelope envelope) {
        InboxConsumed record = new InboxConsumed();
        record.setEventId(envelope.eventId());
        record.setBizOrderNo(envelope.bizOrderNo());
        record.setLineNo(envelope.lineNo());
        record.setEventType(envelope.eventType());
        record.setConsumedAt(LocalDateTime.now());
        record.setResultSummary(InboxConsumed.PENDING_SUMMARY);
        try {
            inboxConsumedMapper.insert(record);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    private InboxConsumed findByDedupKey(EventEnvelope envelope) {
        return inboxConsumedMapper.findByDedupKey(
                envelope.eventId(), envelope.bizOrderNo(), envelope.lineNo(), envelope.eventType());
    }
}
