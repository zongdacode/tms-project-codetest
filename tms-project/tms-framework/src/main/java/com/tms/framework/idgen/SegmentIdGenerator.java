package com.tms.framework.idgen;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 号段模式主键生成器，接入 MyBatis-Plus 的 {@link IdentifierGenerator} 扩展点。
 *
 * <p>按<b>实体类型分段</b>而非全局一段：不同表用独立 biz_tag，避免所有写入抢同一行锁；
 * 同时让"某张表的 ID 增长异常"可直接从 {@code sys_id_segment} 看出来。
 *
 * <p>代价：每个 biz_tag 首次使用会多一次分配；进程重启后已分配但未用完的号段会被丢弃
 * （ID 出现空洞）。这是号段模式的固有特性——ID 无业务含义，空洞无害（[05-data-model] §1）。
 */
@Component
public class SegmentIdGenerator implements IdentifierGenerator {

    /** 未知实体时的兜底标签。 */
    public static final String DEFAULT_BIZ_TAG = "tms_default";

    private static final int DEFAULT_STEP = 1000;

    private final SegmentAllocator allocator;
    private final int step;
    private final Map<String, Segment> segments = new ConcurrentHashMap<>();

    /**
     * 必须标 {@code @Autowired}：本类有两个构造器，Spring 无法自行判断用哪个，
     * 会退回去找无参构造器并抛 "No default constructor found"。
     * 两个构造器都在时不标注解，应用直接起不来。
     */
    @Autowired
    public SegmentIdGenerator(SegmentAllocator allocator) {
        this(allocator, DEFAULT_STEP);
    }

    /** 供测试注入小号段，以便在少量调用内覆盖换段逻辑；不参与 Spring 装配。 */
    public SegmentIdGenerator(SegmentAllocator allocator, int step) {
        this.allocator = allocator;
        this.step = step;
    }

    @Override
    public Long nextId(Object entity) {
        return nextId(bizTagOf(entity));
    }

    public long nextId(String bizTag) {
        Segment segment = segments.computeIfAbsent(bizTag, this::newSegment);
        long candidate = segment.next.getAndIncrement();
        if (candidate < segment.bound) {
            return candidate;
        }
        // 号段用尽：单线程换段，其余线程短暂阻塞等待（分配只走一次 DB，代价可接受）
        synchronized (segment) {
            if (segment.next.get() >= segment.bound) {
                long[] range = allocator.allocate(bizTag, step);
                segment.next.set(range[0]);
                segment.bound = range[1];
            }
            return segment.next.getAndIncrement();
        }
    }

    /**
     * 由实体推导分段标签：优先取 {@code @TableName} 的表名（稳定、可读、与库表对应），
     * 取不到则退回类名。
     */
    public static String bizTagOf(Object entity) {
        if (entity == null) {
            return DEFAULT_BIZ_TAG;
        }
        try {
            TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
            if (tableInfo != null && tableInfo.getTableName() != null) {
                return tableInfo.getTableName();
            }
        } catch (RuntimeException ignored) {
            // 实体未注册到 MP（如单测直接 new）时退回类名，不因此失败
        }
        return entity.getClass().getSimpleName();
    }

    private Segment newSegment(String bizTag) {
        long[] range = allocator.allocate(bizTag, step);
        return new Segment(range[0], range[1]);
    }

    private static final class Segment {
        private final AtomicLong next;
        private volatile long bound;

        private Segment(long start, long bound) {
            this.next = new AtomicLong(start);
            this.bound = bound;
        }
    }
}
