package com.tms.framework.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 缓存读写的最小封装。
 *
 * <p><b>⚠ 本类尚未在真实 Redis 上验证</b>（2026-10-04）：本机无 Redis 服务、Docker 因缺 WSL2
 * 无法启动，故 Gate 0 未覆盖 Redis（[13-gate0] §5.3）。当前只保证编译通过与签名正确，
 * <b>不保证</b>序列化行为、TTL 精度、连接失败时的异常类型符合预期。
 * 批 1 使用前必须先补三项验证：写入-读取往返、TTL 到期自动失效、Redis 不可用时的失败表现。
 *
 * <p>刻意只包字符串：缓存值统一由调用方序列化成 JSON 再放入。
 * 让 RedisTemplate 自己决定序列化方式（JDK 序列化）会把类路径耦合进缓存内容，
 * 之后改一个字段名就读不出旧缓存——而且读失败的表现通常是静默返回 null，被当成缓存未命中。
 */
@Component
public class RedisCacheHelper {

    private final StringRedisTemplate redisTemplate;

    public RedisCacheHelper(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(String key, String jsonValue, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("缓存必须有过期时间：无 TTL 的键会永久堆积，且没人会记得清理");
        }
        redisTemplate.opsForValue().set(key, jsonValue, ttl);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void evict(String key) {
        redisTemplate.delete(key);
    }
}
