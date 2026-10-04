package com.example.gate0.idgen;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 号段模式主键生成器 —— [ADR-016]：应用层发号，分库不冲突，替换雪花。
 *
 * <p>Gate 0 验证点：MyBatis-Plus {@link IdentifierGenerator} 扩展点在 3.5.17 + Boot 4 下是否照常生效。
 */
@Component
public class SegmentIdGenerator implements IdentifierGenerator {

    private static final String DEFAULT_TAG = "tms_default";
    private static final int STEP = 1000;

    private final SegmentAllocator allocator;
    private final Map<String, Segment> segments = new ConcurrentHashMap<>();

    public SegmentIdGenerator(SegmentAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public Long nextId(Object entity) {
        return nextId(DEFAULT_TAG);
    }

    public long nextId(String bizTag) {
        Segment segment = segments.computeIfAbsent(bizTag, Segment::new);
        long value = segment.next.getAndIncrement();
        if (value < segment.bound) {
            return value;
        }
        synchronized (segment) {
            if (segment.next.get() >= segment.bound) {
                long[] range = allocator.allocate(bizTag, STEP);
                segment.next.set(range[0]);
                segment.bound = range[1];
            }
            return segment.next.getAndIncrement();
        }
    }

    private final class Segment {
        private final AtomicLong next = new AtomicLong(Long.MIN_VALUE);
        private volatile long bound = Long.MIN_VALUE;

        private Segment(String bizTag) {
            long[] range = allocator.allocate(bizTag, STEP);
            next.set(range[0]);
            bound = range[1];
        }
    }
}
