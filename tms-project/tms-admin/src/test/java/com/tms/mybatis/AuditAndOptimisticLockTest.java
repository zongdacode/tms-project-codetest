package com.tms.mybatis;

import com.tms.plan.entity.OrderPlan;
import com.tms.plan.mapper.OrderPlanMapper;
import com.tms.support.IntegrationTestBase;
import com.tms.support.OrderPlanFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计字段自动填充 + 乐观锁（常驻测试，[12] §3.3）。
 *
 * <p>这两样都是"接错了也不报错"的组件：忘了注册 {@code MetaObjectHandler}，
 * 时间列就是 null（若表上没加 NOT NULL 就一直没人发现）；忘注册乐观锁拦截器，
 * 并发更新静默丢写，只在数据错乱时才被察觉。
 *
 * <p>所以断言必须落在<b>行为</b>上：时间确实被填了、旧版本号确实更新不动。
 */
class AuditAndOptimisticLockTest extends IntegrationTestBase {

    @Autowired
    private OrderPlanMapper orderPlanMapper;

    @Test
    @DisplayName("插入时自动填充 created_at / updated_at，version 初始为 0")
    void insertFillsAuditFields() {
        OrderPlan plan = OrderPlanFixture.newPlan();
        assertThat(orderPlanMapper.insert(plan)).isEqualTo(1);

        assertThat(plan.getCreatedAt()).as("created_at 未被填充：MetaObjectHandler 没生效").isNotNull();
        assertThat(plan.getUpdatedAt()).as("updated_at 未被填充：MetaObjectHandler 没生效").isNotNull();
        assertThat(plan.getVersion()).as("version 应显式初始化为 0，而不是依赖 DB 默认值").isZero();
    }

    @Test
    @DisplayName("更新时刷新 updated_at 并递增 version")
    void updateRefreshesTimestampAndBumpsVersion() throws InterruptedException {
        OrderPlan plan = OrderPlanFixture.newPlan();
        orderPlanMapper.insert(plan);
        Long id = plan.getId();

        // 基准值必须从库里读，不能拿内存对象上的 createdAt：
        // MetaObjectHandler 填的是 LocalDateTime.now()（纳秒），H2 的列是微秒精度，
        // 落库时被截断。拿纳秒值当基准，它恒比库里的值大最多 999ns——
        // 插入与更新落在同一微秒时，断言就会失败。这跟被测行为无关，纯粹是精度错配。
        // （这条是 clean build 时偶然暴露出来的：此前一直碰巧跨过了微秒边界。）
        LocalDateTime baseline = orderPlanMapper.selectById(id).getUpdatedAt();

        // 等过微秒边界：本测试断言的是"updated_at 被刷新成一个更晚的值"，
        // 要证明"更晚"，时钟得真的走一点。这与"等异步任务完成"是两回事。
        Thread.sleep(5);

        plan.setPlanQty(new BigDecimal("42.5000"));
        assertThat(orderPlanMapper.updateById(plan)).isEqualTo(1);

        OrderPlan reloaded = orderPlanMapper.selectById(id);
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(reloaded.getUpdatedAt())
                .as("updated_at 未随更新变化：MetaObjectHandler 的 updateFill 没生效")
                .isAfter(baseline);
        assertThat(reloaded.getPlanQty()).isEqualByComparingTo("42.5000");
    }

    @Test
    @DisplayName("持旧版本号的更新必须影响 0 行——乐观锁拦截器真的在拦")
    void staleVersionUpdateAffectsNoRow() {
        OrderPlan plan = OrderPlanFixture.newPlan();
        orderPlanMapper.insert(plan);
        Long id = plan.getId();

        // 模拟两个并发请求各自读到同一版本
        OrderPlan firstReader = orderPlanMapper.selectById(id);
        OrderPlan secondReader = orderPlanMapper.selectById(id);

        firstReader.setPlanQty(new BigDecimal("1.0000"));
        assertThat(orderPlanMapper.updateById(firstReader))
                .as("第一个请求应当更新成功")
                .isEqualTo(1);

        secondReader.setPlanQty(new BigDecimal("2.0000"));
        assertThat(orderPlanMapper.updateById(secondReader))
                .as("第二个请求持的是旧 version，必须被乐观锁挡下；"
                        + "返回 1 说明拦截器没生效，并发写会互相覆盖")
                .isZero();

        assertThat(orderPlanMapper.selectById(id).getPlanQty())
                .as("后到的写入不应覆盖先到的")
                .isEqualByComparingTo("1.0000");
    }
}
