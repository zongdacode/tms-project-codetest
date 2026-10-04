package com.tms.idgen;

import com.tms.framework.idgen.SegmentAllocator;
import com.tms.framework.idgen.SegmentIdGenerator;
import com.tms.plan.entity.OrderPlan;
import com.tms.plan.mapper.OrderPlanMapper;
import com.tms.support.IntegrationTestBase;
import com.tms.support.OrderPlanFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 号段发号（常驻测试，按 [12] §3.3 从 [13-gate0] §3 提升）。
 *
 * <p>号段模式的失效方式很安静：ID 重叠不会报错，只会在某天发现两条不同单据共用一个主键。
 * 所以这里必须真的把号段用尽、跨段重取，并逐条验证唯一性与连续性。
 */
class SegmentIdGeneratorIntegrationTest extends IntegrationTestBase {

    /** 每段 3 个，20 次取号必然跨 7 段，无需等待就能覆盖换段逻辑。 */
    private static final int TINY_SEGMENT = 3;

    @Autowired
    private SegmentAllocator allocator;

    @Autowired
    private OrderPlanMapper orderPlanMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("跨号段取号：ID 连续、无重复、库中推进量与实际发放一致")
    void idsStayUniqueAndContiguousAcrossSegmentRollover() {
        String bizTag = "test_rollover_" + UUID.randomUUID().toString().substring(0, 8);
        SegmentIdGenerator generator = new SegmentIdGenerator(allocator, TINY_SEGMENT);

        Set<Long> ids = new LinkedHashSet<>();
        for (int i = 0; i < 20; i++) {
            ids.add(generator.nextId(bizTag));
        }

        assertThat(ids)
                .as("20 次取号出现重复，说明换段时区间算错了——这是号段模式最危险的失效方式")
                .hasSize(20);
        assertThat(ids.iterator().next())
                .as("首个 ID 应从 1 开始")
                .isEqualTo(1L);

        // 期望推进量：ceil(20 / 3) * 3 = 21。若返回区间与库中推进量不同源，这里就对不上。
        Long maxId = jdbcTemplate.queryForObject(
                "SELECT max_id FROM sys_id_segment WHERE biz_tag = ?", Long.class, bizTag);
        assertThat(maxId)
                .as("库中推进量与已发放的 ID 数不匹配，会导致后续发号与本段区间重叠")
                .isEqualTo(21L);

        Integer storedStep = jdbcTemplate.queryForObject(
                "SELECT step FROM sys_id_segment WHERE biz_tag = ?", Integer.class, bizTag);
        assertThat(storedStep)
                .as("实际使用的号段长度应写回库中备查")
                .isEqualTo(TINY_SEGMENT);
    }

    @Test
    @DisplayName("同标签连续取号严格递增，换段处不断档")
    void idsAreStrictlyIncreasing() {
        String bizTag = "test_monotonic_" + UUID.randomUUID().toString().substring(0, 8);
        SegmentIdGenerator generator = new SegmentIdGenerator(allocator, TINY_SEGMENT);

        long previous = 0;
        for (int i = 0; i < 10; i++) {
            long current = generator.nextId(bizTag);
            assertThat(current)
                    .as("第 %d 次取号未递增（上一值 %d）", i, previous)
                    .isGreaterThan(previous);
            previous = current;
        }
    }

    @Test
    @DisplayName("实体落库时由号段生成器发号，而不是 MyBatis-Plus 默认的雪花 ID")
    void mapperInsertUsesSegmentGenerator() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            OrderPlan plan = OrderPlanFixture.newPlan();
            assertThat(orderPlanMapper.insert(plan)).isEqualTo(1);

            assertThat(plan.getId()).as("主键未被填充，说明 IdentifierGenerator 没接上").isNotNull();
            ids.add(plan.getId());
        }
        assertThat(ids).hasSize(5);

        // 这条断言是"用对了生成器"的关键证据：
        // 默认雪花 ID 是 19 位数字（约 1.9e18），号段 ID 从 1 开始缓慢增长。
        // 只断言"非空且唯一"的话，接错了生成器也照样通过。
        assertThat(ids).allSatisfy(id -> assertThat(id)
                .as("ID %d 看起来像雪花 ID：SegmentIdGenerator 可能没被 MyBatis-Plus 采用", id)
                .isLessThan(1_000_000L));
    }
}
