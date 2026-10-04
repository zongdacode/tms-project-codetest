package com.tms.mybatis;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tms.plan.entity.OrderPlan;
import com.tms.plan.mapper.OrderPlanMapper;
import com.tms.support.IntegrationTestBase;
import com.tms.support.OrderPlanFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页 SQL 改写与单页上限（常驻测试，[12] §3.3）。
 *
 * <p>这条测试的存在理由很具体：Boot 4 下漏加 {@code mybatis-plus-jsqlparser} 依赖时，
 * {@code PaginationInnerInterceptor} <b>静默失效</b>——不报错、不分页，
 * 一次查询把整表捞进内存（[13-gate0] §4-③）。没有这条断言，这个依赖哪天被
 * 某次"依赖清理"顺掉，没有任何东西会提示。
 *
 * <p>判别方法：查 3 条、每页 2 条。分页生效则 {@code records} 为 2 且 {@code total} 为 3；
 * 不生效则 {@code records} 为 3 且 {@code total} 为 0。两者不会混淆。
 */
class PaginationRewriteTest extends IntegrationTestBase {

    /** 略多于 maxLimit(200)，用来观察 SQL 上的 LIMIT 到底是多少。 */
    private static final int ROWS_BEYOND_MAX_LIMIT = 205;

    @Autowired
    private OrderPlanMapper orderPlanMapper;

    @Test
    @DisplayName("分页真的生效：每页 2 条、共 3 条")
    void paginationLimitsRowsAndCountsTotal() {
        String marker = OrderPlanFixture.uniqueOrderNo("TMS-PAGE");
        for (int i = 0; i < 3; i++) {
            orderPlanMapper.insert(OrderPlanFixture.newPlan(marker + "-" + i));
        }

        Page<OrderPlan> result = orderPlanMapper.selectPage(new Page<>(1, 2),
                Wrappers.<OrderPlan>lambdaQuery()
                        .likeRight(OrderPlan::getBizOrderNo, marker)
                        .orderByAsc(OrderPlan::getId));

        assertThat(result.getTotal())
                .as("total 为 0 说明 count 查询没被改写——PaginationInnerInterceptor 未生效")
                .isEqualTo(3);
        assertThat(result.getRecords())
                .as("查出 3 条说明 limit 没被追加——所有分页查询都会退化成全表扫描")
                .hasSize(2);
        assertThat(result.getPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("maxLimit 兜底：外部传入超大 pageSize 时，实际取回的行数不超过 200")
    void oversizedPageSizeIsCappedByMaxLimit() {
        String marker = OrderPlanFixture.uniqueOrderNo("TMS-CAP");
        for (int i = 0; i < ROWS_BEYOND_MAX_LIMIT; i++) {
            orderPlanMapper.insert(OrderPlanFixture.newPlan(marker + "-" + i));
        }

        Page<OrderPlan> result = orderPlanMapper.selectPage(new Page<>(1, 100_000),
                Wrappers.<OrderPlan>lambdaQuery().likeRight(OrderPlan::getBizOrderNo, marker));

        // 断言落在"实际取回多少行"上，而不是断言 page.getSize()。
        // 实测（MyBatis-Plus 3.5.17）：SQL 上的 LIMIT 确实被压到了 maxLimit，
        // 但 result.getSize() 仍返回调用方传入的 100000——即分页对象自报的页大小
        // 与实际取回的行数不一致。所以断言 getSize() 会得到一个与真实行为无关的结论；
        // 真正要守住的是"外部传个天文数字的 pageSize 也拉不垮内存"。
        // 使用方若要显示"每页 N 条"，应取 records.size() 而非 getSize()。
        assertThat(result.getRecords())
                .as("一次取回 %d 行，说明 maxLimit 没生效：pageSize 由外部控制，等于没有上限",
                        ROWS_BEYOND_MAX_LIMIT)
                .hasSize(200);
        assertThat(result.getTotal())
                .as("total 应为真实总数，不受 maxLimit 影响")
                .isEqualTo(ROWS_BEYOND_MAX_LIMIT);
    }
}
