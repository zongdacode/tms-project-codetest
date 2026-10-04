/**
 * 运输执行域对外契约。
 *
 * <p>本模块按子域组织（[05-系统架构] §2）：
 * {@code shipment}（发运单）/ {@code waybill}（运单）/ {@code dispatch}（调度派车）/
 * {@code track}（在途跟踪）/ {@code sign}（签收登记）/ {@code report}（报表）。
 *
 * <p><b>永不先拆</b>（[ADR-004]）：这些子域之间的耦合是业务本身决定的，
 * 强行拆成独立服务会把进程内调用变成分布式事务，代价远大于收益。
 * 子域之间可以直接调用，但跨模块（到 tms-plan / tms-base / tms-auth）必须走本包。
 *
 * <p><b>红线 [10] R-1</b>：本模块不得 import {@code com.tms.plan.mapper} /
 * {@code com.tms.plan.entity}，也不得在 SQL 里 join {@code tms_order_plan}。
 *
 * <p><b>当前状态</b>：批次 0 只建包与规则；子域包在批次 3 随业务落地。
 */
package com.tms.core.api;
