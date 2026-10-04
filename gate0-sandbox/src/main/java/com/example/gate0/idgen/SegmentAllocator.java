package com.example.gate0.idgen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 号段分配器：独立事务（REQUIRES_NEW）持行锁取号段，不污染外层业务事务。
 * 单独成 bean 是为了让 Spring 事务代理生效（同类自调用不走代理）。
 */
@Component
public class SegmentAllocator {

    private final JdbcTemplate jdbcTemplate;

    public SegmentAllocator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return {起始值(含), 上界(不含)}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long[] allocate(String bizTag, int step) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_id_segment WHERE biz_tag = ?", Integer.class, bizTag);
        if (exists == null || exists == 0) {
            jdbcTemplate.update("INSERT INTO sys_id_segment(biz_tag, max_id, step) VALUES (?, 0, ?)", bizTag, step);
        }
        Long current = jdbcTemplate.queryForObject(
                "SELECT max_id FROM sys_id_segment WHERE biz_tag = ? FOR UPDATE", Long.class, bizTag);
        long from = current == null ? 0L : current;
        jdbcTemplate.update("UPDATE sys_id_segment SET max_id = max_id + step WHERE biz_tag = ?", bizTag);
        return new long[]{from + 1, from + step};
    }
}
