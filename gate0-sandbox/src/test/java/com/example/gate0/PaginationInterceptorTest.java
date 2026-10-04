package com.example.gate0;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.gate0.entity.OrderPlan;
import com.example.gate0.mapper.OrderPlanMapper;
import com.example.gate0.service.PlanCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate 0 关键项：分页插件依赖 mybatis-plus-jsqlparser，是 MP 跨 Boot 大版本最容易断的一环，
 * 必须实测 SQL 改写是否生效（而非仅依赖能解析）。
 */
@SpringBootTest
class PaginationInterceptorTest {

    @Autowired
    private PlanCommandService planCommandService;

    @Autowired
    private OrderPlanMapper planMapper;

    @Test
    void paginationRewritesSqlAndCountsTotal() {
        String prefix = "TMS-PAGE-" + UUID.randomUUID();
        for (int i = 1; i <= 5; i++) {
            planCommandService.receiveDemand(prefix, i, "UPSTREAM", "SO-" + i, false);
        }

        IPage<OrderPlan> page = planMapper.selectPage(
                new Page<>(1, 2),
                new QueryWrapper<OrderPlan>().eq("biz_order_no", prefix).orderByAsc("line_no"));

        assertEquals(5, page.getTotal(), "count 语句未生效");
        assertEquals(2, page.getRecords().size(), "limit 语句未生效");
        assertEquals(1, page.getRecords().get(0).getLineNo());
        assertTrue(page.getPages() >= 3);
    }
}
