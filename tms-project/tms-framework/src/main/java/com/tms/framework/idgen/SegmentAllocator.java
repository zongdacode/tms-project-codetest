package com.tms.framework.idgen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 号段分配器：从 DB 号段表取一段连续 ID 区间（[ADR-016] 主键 = 号段模式）。
 *
 * <p><b>为什么单独成 bean</b>：{@code @Transactional(REQUIRES_NEW)} 在同类内部自调用时不走
 * Spring 代理，注解会静默失效、号段分配被并进外层业务事务——那会让长业务事务持有
 * {@code sys_id_segment} 行锁，成为写热点。拆成独立 bean 才能保证独立事务真的生效。
 *
 * <p>并发正确性依赖 {@code SELECT ... FOR UPDATE}：同一 biz_tag 的取号在 DB 层串行，
 * 多实例部署下不会拿到重叠区间。
 */
@Component
public class SegmentAllocator {

    private static final String SQL_ENSURE_ROW =
            "INSERT INTO sys_id_segment(biz_tag, max_id, step) VALUES (?, 0, ?)";
    private static final String SQL_COUNT_ROW =
            "SELECT COUNT(1) FROM sys_id_segment WHERE biz_tag = ?";
    private static final String SQL_LOCK_ROW =
            "SELECT max_id FROM sys_id_segment WHERE biz_tag = ? FOR UPDATE";
    /**
     * 推进号段，并把<b>本次实际使用的</b> step 写回。
     *
     * <p>早期版本写的是 {@code max_id = max_id + step}（用表里存的 step），而返回区间用的却是
     * 入参 step。两者不一致时——例如测试用 step=10、生产用 1000，命中同一 biz_tag——
     * 返回的区间与库里的推进量就对不上，会发出<b>重叠 ID</b>，且没有任何报错。
     * 现在推进量与返回区间同源同值，并把 step 落库备查。
     */
    private static final String SQL_STEP_FORWARD =
            "UPDATE sys_id_segment SET max_id = max_id + ?, step = ? WHERE biz_tag = ?";

    private final JdbcTemplate jdbcTemplate;

    public SegmentAllocator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param bizTag 业务标签（一段一锁，不同标签互不阻塞）
     * @param step   号段长度
     * @return {@code {起始值(含), 上界(不含)}}——上界是 {@code from + step + 1}，
     *         使本段恰好放出 {@code step} 个 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long[] allocate(String bizTag, int step) {
        Integer exists = jdbcTemplate.queryForObject(SQL_COUNT_ROW, Integer.class, bizTag);
        if (exists == null || exists == 0) {
            jdbcTemplate.update(SQL_ENSURE_ROW, bizTag, step);
        }
        Long currentMaxId = jdbcTemplate.queryForObject(SQL_LOCK_ROW, Long.class, bizTag);
        long from = currentMaxId == null ? 0L : currentMaxId;
        jdbcTemplate.update(SQL_STEP_FORWARD, step, step, bizTag);
        // +1 不能省：上界是不含的，写成 from + step 会让每段少放一个 ID。
        // 不会造成重叠（库里的推进量仍是 step），但会白扔 ID，且 step 越小浪费越大。
        return new long[]{from + 1, from + step + 1};
    }
}
