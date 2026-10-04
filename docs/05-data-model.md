# 05 关键数据模型（草案）

## 目录

- [1. 建模总则](#1-建模总则)
- [2. 计划表 tms_order_plan](#2-计划表-tms_order_plan)
- [3. 本地事件表（发件箱 / 收件箱）](#3-本地事件表发件箱--收件箱)
- [4. 单号规范](#4-单号规范)
- [5. 唯一约束与索引汇总](#5-唯一约束与索引汇总)
- [6. 待定清单](#6-待定清单)

> 类型列为通用关系型示意，落地时随 `[ADR-016]` 选型调整。TMS 库只建 §2/§3（计划表、事件表）及运单执行域表（执行域表结构随批次 3 方案确认逐步补全，见 [12] §5）。执行域表结构补全前，本文件以计划表与事件表为基线。

---

## 1. 建模总则

1. **关联键强制**：凡参与跨系统流转的表，必须含 `biz_order_no` + `line_no` 两列并建索引（[ADR-012]）。
2. **通用字段**：`id`（主键，生成策略 = **号段模式**，[ADR-016]；禁用业务含义编码）、`created_at`、`updated_at`、`version`（乐观锁）。
3. **状态只前进**：节点状态列不做回退 update；取消/冲销用独立标记列（[ADR-013]）。
4. **记录不可变**：事件记录、节点时间戳只增不改；修正以新增记录 + 引用原记录表达。
5. **软删慎用**：业务事实表不删；配置类表可用 `is_deleted`。
6. 计划表**禁止**出现仓内作业、运输执行细节字段（[ADR-007]，红线 [10] R-2）。

## 2. 计划表 tms_order_plan

归属：TMS（`[未来演进]` 整体迁出为订单中心，[ADR-015]）。粒度：**业务单号 + 行号 = 一行**。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT | ✓ | 主键 |
| biz_order_no | VARCHAR(64) | ✓ | 业务单号（关联键） |
| line_no | INT | ✓ | 行号（关联键） |
| source_system | VARCHAR(32) | ✓ | 需求来源系统 |
| source_doc_no | VARCHAR(64) | — | 源系统单据号（接口幂等去重键 = source_doc_no+行号；独立保存、不覆盖单号） |
| order_type | VARCHAR(32) | ✓ | 业务类型 `[待定]`：调拨/销售/采购退货…枚举随上游确定 |
| ship_from_code | VARCHAR(32) | ✓ | 发货仓编码 |
| ship_to_code | VARCHAR(32) | ✓ | 收货仓/目的地编码 |
| plan_qty | DECIMAL(18,4) | ✓ | 计划数量（+ 单位） |
| dispatch_status | TINYINT | ✓ | 下发：0 未 / 1 已（建档即 1） |
| dispatched_at | DATETIME | ✓ | 下发时间 |
| outbound_status | TINYINT | ✓ | 出库完成：0 / 1 |
| outbound_completed_at | DATETIME | — | 出库完成时间（业务时间） |
| inbound_status | TINYINT | ✓ | 入库完成：0 / 1 |
| inbound_completed_at | DATETIME | — | 入库完成时间 |
| signed_status | TINYINT | ✓ | 签收完成：0 / 1 |
| signed_completed_at | DATETIME | — | 签收完成时间 |
| cancel_flag | TINYINT | ✓ | 取消标记：0 / 1（独立迁移，不改节点状态） |
| cancelled_at / cancel_reason | DATETIME / VARCHAR(200) | — | 取消时间/原因 |
| reverse_flag | TINYINT | ✓ | 冲销标记：0 / 1 |
| reversed_at | DATETIME | — | 冲销时间 |
| last_event_id | VARCHAR(64) | — | 最近消费的事件 ID（排障用） |
| version | INT | ✓ | 乐观锁 |
| created_at / updated_at | DATETIME | ✓ | 通用审计 |

- 唯一键：`uk_plan (biz_order_no, line_no)`。
- 节点更新一律走幂等消费（[06] §6），带 `version` 乐观锁；并发冲突重读重试。
- **不存**：拣货/上架/盘点状态、车辆司机、运费金额、异常明细——分别归 WMS、TMS 执行域（[ADR-006/007]）。

## 3. 本地事件表（发件箱 / 收件箱）

[ADR-011] 要求的基础设施，**每个发送/接收事实的系统各建一套**（WMS、TMS 必备）。

### 3.1 发件箱 outbox_event（发送方）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| event_id | VARCHAR(64) | 事件全局唯一 ID（UUID），投递重试时**不变** |
| event_type | VARCHAR(64) | 见 [06] §3 事件字典 |
| source_system | VARCHAR(32) | 事件来源系统（TMS / WMS / …），[06] §3 信封字段 |
| biz_order_no / line_no | VARCHAR(64) / INT | 关联键 |
| aggregate_version | INT | 聚合版本，[06] §3 信封字段；接收方据此判断事件是否乱序 |
| occurred_at | DATETIME | 业务发生时间 |
| payload | JSON/TEXT | 事件负载**明细**（结构见 [06]）；信封关键字段已各自成列，不在此重复 |
| target_system | VARCHAR(32) | 目标系统（订阅方列表 `[待定]`） |
| trace_id | VARCHAR(64) | 链路追踪 ID，[06] §3 信封字段；可为空 |
| status | VARCHAR(16) | PENDING / SENT / FAILED / DEAD |
| retry_count | INT | 已重试次数 |
| next_retry_at | DATETIME | 下次重试时间（退避） |
| created_at / sent_at | DATETIME | 审计 |

写入规则：业务事务内**同事务落 outbox**，投递器异步扫描发送（事务性发件箱模式），保证"业务成功 ⇔ 事件必达（至少一次）"。

> `[已决策 2026-10-04：批 0 实测修正]` 本表原定义缺 `source_system`、`aggregate_version`、`trace_id` 三列。
> 它们是 [06] §3 事件信封的必填/可选字段，且**从其余列推不出来**——投递时无法还原完整信封。
> 实现阶段发现后补齐，建表脚本与实体同步（`OutboxEvent`、`db/schema-mysql.sql`、`db/schema-h2.sql`）。

### 3.2 收件箱 inbox_consumed（接收方幂等记录）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| event_id | VARCHAR(64) | — |
| biz_order_no / line_no | VARCHAR(64) / INT | — |
| event_type | VARCHAR(64) | 状态类型 |
| consumed_at | DATETIME | 消费时间 |
| result_summary | VARCHAR(200) | 处理结果摘要（重复事件原样返回） |

- 唯一键：`uk_consume (event_id, biz_order_no, line_no, event_type)` —— "事件 ID + 业务单号 + 行号 + 状态类型"去重。
- 消费逻辑与幂等记录**同一本地事务**写入。

## 4. 单号规范

| 项 | 规则 | 状态 |
|---|---|---|
| 业务单号 biz_order_no | 全局唯一，**TMS 统一生成**；结构 `TMS-{类型码}-{YYYYMMDD}-{N位序列}`（前缀+类型+日期+序列）；**不可变**，取消/冲销沿用原单号加标记 | `[已决策 2026-10-04：ADR-012]` |
| 源单号 source_doc_no | 上游系统单据号，独立字段保存、不覆盖单号；接口幂等去重键 = source_doc_no+行号；biz_order_no 经接口响应回传上游与 WMS | `[已决策 2026-10-04]` |
| 行号 line_no | 单内从 1 递增；变更拆行只追加新行号，不重排、不复用 | `[已决策]` |
| 出库单号 / 入库单号 | WMS 自有规则，表内冗余 biz_order_no+line_no | WMS 定 |
| 发运单号 / 运单号 | TMS 自有规则；发运单与业务单号行是多对多时（拼单）必须落关联表 | TMS 定，拼单场景 `[待定]` |
| 事件 ID event_id | UUID，重试不变 | `[已决策]` |

## 5. 唯一约束与索引汇总

| 表 | 唯一约束 | 关键索引 |
|---|---|---|
| tms_order_plan | (biz_order_no, line_no) | (outbound_status, outbound_completed_at)、(signed_status, signed_completed_at)、(cancel_flag) |
| inbox_consumed | (event_id, biz_order_no, line_no, event_type) | (consumed_at) |
| outbox_event | (event_id) | (status, next_retry_at) |

## 6. 待定清单

> 2026-10-04 决策问答后：D-1（单号）、D-2（order_type=调拨/销售/退货）、D-3（主键=号段模式）已决策落定，移入正文。

| # | 事项 | 影响 |
|---|---|---|
| D-5 | 拼单/拆单时发运单与业务单行的多对多关联表 | 运输执行域（批次 3 方案确认） |
| D-7 | GPS 定位平台选型 | 批次 5 轨迹对接 |
| D-8 | 上游系统名称与对接方式（推送/拉取） | 接口契约已定义；阻塞批次 2 联调，不阻塞开发 |
