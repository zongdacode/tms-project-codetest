package com.tms.framework.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis 的分布式锁。
 *
 * <p><b>⚠ 本类尚未在真实 Redis 上验证</b>（2026-10-04，原因见 [13-gate0] §5.3）。
 * 解锁脚本、锁超时行为、并发抢占均未实测。
 *
 * <p><b>本锁不提供续期（watchdog）</b>：TTL 到点即释放，无论持有者是否还在干活。
 * 因此调用方必须给出<b>大于最坏执行时间</b>的 TTL。这个取舍是刻意的——
 * 续期需要额外线程与"持有者已死但仍在续期"的判活逻辑，Batch 0 不引入；
 * 需要长任务保护时，先写 ADR 明确选型，别在这里顺手加。
 *
 * <p><b>释放必须校验持有者</b>：直接 {@code DEL} 会把锁从"超时后拿到锁的新持有者"手里抢走，
 * 造成两把锁并存。所以用 Lua 把"比对 + 删除"做成一个原子操作。
 */
@Component
public class RedisDistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);

    /** 持有者令牌：必须唯一到"每次加锁"级别，用线程名或主机名都会撞。 */
    private static final String TOKEN_PREFIX = "tms:lock:";

    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试加锁，不阻塞。
     *
     * @return 锁句柄；null 表示锁被他人持有。返回非 null 时<b>必须</b>在 finally 里 {@link LockHandle#close()}
     */
    public LockHandle tryLock(String lockKey, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("锁必须有过期时间：否则持锁进程崩溃后锁永不释放");
        }
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(TOKEN_PREFIX + lockKey, token, ttl);
        return Boolean.TRUE.equals(acquired) ? new LockHandle(lockKey, token) : null;
    }

    /**
     * 锁句柄。实现 {@link AutoCloseable} 是为了配合 try-with-resources——
     * 忘记释放的锁要等 TTL 到期，期间所有节点都在等，表现为偶发的整体卡顿。
     */
    public final class LockHandle implements AutoCloseable {

        private final String lockKey;
        private final String token;
        private boolean released;

        private LockHandle(String lockKey, String token) {
            this.lockKey = lockKey;
            this.token = token;
        }

        @Override
        public void close() {
            if (released) {
                return;
            }
            released = true;
            Long deleted = redisTemplate.execute(
                    RELEASE_SCRIPT, List.of(TOKEN_PREFIX + lockKey), token);
            if (deleted == null || deleted == 0L) {
                // 不是错误：锁多半已因 TTL 到期被自动释放，或已被他人持有。
                // 记录是因为它意味着临界区执行时间超过了 TTL——锁在此期间并未真正互斥。
                log.warn("释放锁时未删除任何键（锁已超时或已被他人持有）。lockKey={}", lockKey);
            }
        }
    }
}
