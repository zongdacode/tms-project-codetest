/**
 * 计划表模块对外契约（[05-系统架构] §3 补充 A-3：接口与 DTO 集中在模块 {@code api/} 子包）。
 *
 * <p><b>为什么单独开这个包</b>：阶段 2 拆服务时，整个 {@code api/} 会提成独立 jar，
 * 远程实现复用同名接口，调用方代码零改动。前提是调用方<b>只</b>依赖这个包——
 * 一旦有人 import 了本模块的 {@code entity/} 或 {@code mapper/}，拆服务时就炸在编译期。
 * 架构测试 A3CrossModuleApiOnlyTest 会拦下这类 import。
 *
 * <p><b>本包内禁止出现</b>：Entity、Mapper、Spring 的 {@code @Service} 实现类。
 * 只放接口与 DTO。
 *
 * <p><b>红线 [10] R-1</b>：本模块与 {@code com.tms.core}（运单域）之间只能通过本包交互。
 * 计划表不 join 运单表，运单域也不直接读写 {@code tms_order_plan}。
 *
 * <p><b>当前状态</b>：批次 0 只建包与规则，不定义业务方法——
 * 四节点联动的具体接口签名属于批次 2，且必须先过事件契约评审（[06] §11），
 * 现在定下来只会被推翻。
 */
package com.tms.plan.api;
