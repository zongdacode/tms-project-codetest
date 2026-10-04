package com.tms.framework.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tms.framework.event.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    /**
     * 取到期待投递事件。
     *
     * <p>{@code next_retry_at IS NULL} 是首次投递（刚由业务事务落库，还没排过期），
     * 漏掉这个条件会让所有新事件永远投不出去。
     *
     * <p><b>多实例下的并发投递</b>：本查询<b>没有</b>加锁语义，两个实例会扫到同一批。
     * 当前部署是单实例，故 Batch 0 不做抢占；多实例前必须先定抢占方案
     * （见 {@code OutboxDispatcher} 类注释）。
     */
    @Select("""
            SELECT * FROM outbox_event
             WHERE status IN ('PENDING', 'FAILED')
               AND (next_retry_at IS NULL OR next_retry_at <= #{now})
             ORDER BY next_retry_at, id
             LIMIT #{limit}
            """)
    List<OutboxEvent> selectDueEvents(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE outbox_event
               SET status = 'SENT', sent_at = #{sentAt}, next_retry_at = NULL
             WHERE id = #{id}
            """)
    int markSent(@Param("id") Long id, @Param("sentAt") LocalDateTime sentAt);

    /**
     * 标记一次投递失败。
     *
     * <p>带 {@code retry_count = #{expectedRetryCount}} 条件：只有"我读到的那个版本"还能改，
     * 避免两个实例同时失败时把重试次数各加一次（那会让重试次数虚高、提前进 DEAD）。
     */
    @Update("""
            UPDATE outbox_event
               SET status = #{status},
                   retry_count = #{newRetryCount},
                   next_retry_at = #{nextRetryAt}
             WHERE id = #{id} AND retry_count = #{expectedRetryCount}
            """)
    int markFailure(@Param("id") Long id,
                    @Param("status") String status,
                    @Param("expectedRetryCount") int expectedRetryCount,
                    @Param("newRetryCount") int newRetryCount,
                    @Param("nextRetryAt") LocalDateTime nextRetryAt);

    /** 人工重推：DEAD/FAILED 回到待投递，{@code event_id} 不变（依赖接收方幂等，[06] §8）。 */
    @Update("""
            UPDATE outbox_event
               SET status = 'PENDING', retry_count = 0, next_retry_at = NULL
             WHERE id = #{id} AND status IN ('DEAD', 'FAILED')
            """)
    int requeue(@Param("id") Long id);
}
