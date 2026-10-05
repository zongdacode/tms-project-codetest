package com.tms.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tms.plan.entity.OrderPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计划表 Mapper。
 *
 * <p><b>红线 [10] R-1</b>：本 Mapper 只能被 {@code com.tms.plan} 包内使用。
 * tms-core（含 auth/base 各域）不得注入它，也不得让执行域实体 join {@code tms_order_plan}——
 * 架构测试 ArchitectureRulesTest 会拦下这类依赖。
 */
@Mapper
public interface OrderPlanMapper extends BaseMapper<OrderPlan> {
}
