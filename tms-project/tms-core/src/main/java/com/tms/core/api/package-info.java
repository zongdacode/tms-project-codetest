/**
 * TMS 业务模块对外契约。
 *
 * <p>本模块是<b>单一业务模块</b>，按域分包（[ADR-018]）：
 * {@code auth}（sys_ 表：用户/角色/菜单/登录/Token/操作日志）、
 * {@code base}（base_ 表：工厂/客户/承运商/地点/车辆/司机/商品/字典）、
 * {@code shipment}（发运单）/ {@code waybill}（运单）/ {@code dispatch}（调度派车）/
 * {@code track}（在途跟踪）/ {@code sign}（签收登记）/ {@code report}（报表）。
 *
 * <p><b>域之间不设接口边界</b>：它们同属一个模块、同一次部署、同一个本地事务，
 * 直接互相调用即可。2026-10-05 取消了"每个业务模块都开 api/ 子包、按未来服务边界隔离"的做法
 * （原 [05-系统架构] §3 补充 A-3）——那只在真的会拆服务时才有回报，而本系统只保留一条拆分预留。
 *
 * <p><b>唯一保留的边界</b>是 {@code tms-plan}：跨到它的调用必须走 {@code com.tms.plan.api}，
 * 见下方 R-1。
 *
 * <p><b>红线 [10] R-1</b>：本模块不得 import {@code com.tms.plan.mapper} /
 * {@code com.tms.plan.entity}，也不得在 SQL 里 join {@code tms_order_plan}。
 * 依据是 [ADR-015]：计划表将来要整体迁出成订单中心，只有契约解耦了，迁移时运单代码才不用动。
 *
 * <p><b>当前状态</b>：批次 0 只建包与规则；各子域包在批次 1 起随业务落地。
 */
package com.tms.core.api;
