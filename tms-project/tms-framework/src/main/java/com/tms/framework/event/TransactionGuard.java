package com.tms.framework.event;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * "必须在事务内调用"的显式断言。
 *
 * <p>事务性发件箱与幂等消费的正确性完全建立在"业务写入 + outbox/inbox 记录同事务提交"之上。
 * 少了事务，代码照样跑通、测试照样绿——直到某次业务回滚而事件已发出，
 * 或者消费记录先落库而业务没执行，才以重复执行/事件丢失的形式暴露。
 *
 * <p>所以宁可在这里<b>当场炸掉</b>：把一条静默的数据不一致，换成一行明确的启动/调用期错误。
 */
public final class TransactionGuard {

    private TransactionGuard() {
    }

    public static void requireActiveTransaction(String operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    operation + " 必须在已开启的事务中调用："
                            + "否则业务写入与事件/幂等记录无法同事务提交，"
                            + "会出现「业务回滚但事件已发出」或「记录了已消费但业务未执行」。");
        }
    }
}
