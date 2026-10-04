package com.tms.framework.event;

/**
 * 一次事件消费的结果。
 *
 * @param duplicate     true = 该事件此前已消费过，本次<b>未执行</b>业务逻辑
 * @param resultSummary 处理结果摘要；重复事件原样返回首次的摘要（[06] §6-3）
 */
public record ConsumeOutcome(boolean duplicate, String resultSummary) {

    public static ConsumeOutcome consumed(String resultSummary) {
        return new ConsumeOutcome(false, resultSummary);
    }

    public static ConsumeOutcome duplicate(String firstResultSummary) {
        return new ConsumeOutcome(true, firstResultSummary);
    }
}
