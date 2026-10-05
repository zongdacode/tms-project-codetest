/**
 * 计划表模块对外契约——全系统<b>唯一</b>保留的模块边界。
 *
 * <p><b>为什么单独开这个包</b>：计划表将来要整体迁出成订单中心（[ADR-015]）。
 * 到那时调用方只要依赖本包就能不受影响；一旦有人 import 了本模块的 {@code entity/}
 * 或 {@code mapper/}，迁移就会炸在编译期。架构测试 {@code ArchitectureRulesTest} 会拦下这类 import。
 *
 * <p>2026-10-05 收拢后（[ADR-018]），其他业务模块已并入 {@code tms-core}、不再各自开 api/ 包，
 * 这里保留是因为它的拆分理由（整表迁出）是真的，不是"为将来可能的微服务预留"。
 *
 * <p><b>本包内禁止出现</b>：Entity、Mapper、Spring 的 {@code @Service} 实现类。
 * 只放接口与 DTO。
 *
 * <p><b>红线 [10] R-1</b>：本模块与 {@code com.tms.core} 之间只能通过本包交互。
 * 计划表不 join 运单表，执行域也不直接读写 {@code tms_order_plan}。
 *
 * <p><b>当前状态</b>：批次 0 只建包与规则，不定义业务方法——
 * 四节点联动的具体接口签名属于批次 2，且必须先过事件契约评审（[06] §11），
 * 现在定下来只会被推翻。
 */
package com.tms.plan.api;
