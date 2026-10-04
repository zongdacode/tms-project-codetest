-- TMS 库表（MySQL 8.x）
--
-- 幂等：全部使用 CREATE TABLE IF NOT EXISTS，配合 spring.sql.init.mode=always
-- （配置见 application.yml 的"待办"：长期应换成 Flyway 版本化迁移）。
--
-- 与 schema-h2.sql 是同一套结构的两个方言版本。改动其中一份时**必须同步改另一份**——
-- 这是当前方案的固有代价，也是"待办 1"要换 Flyway 的原因。

-- ---------------------------------------------------------------------------
-- 号段发号表（主键 = 号段模式，[ADR-016]）
-- 前缀用 sys_ 而不是 tms_：它服务所有模块，不属于任一业务域。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_id_segment
(
    biz_tag VARCHAR(64) NOT NULL COMMENT '业务标签，一段一锁',
    max_id  BIGINT      NOT NULL DEFAULT 0 COMMENT '已分配出去的最大 ID',
    step    INT         NOT NULL DEFAULT 1000 COMMENT '最近一次分配使用的号段长度',
    PRIMARY KEY (biz_tag)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='号段发号表';

-- ---------------------------------------------------------------------------
-- 发件箱（[05-data-model] §3.1）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS outbox_event
(
    id                BIGINT       NOT NULL COMMENT '主键',
    event_id          VARCHAR(64)  NOT NULL COMMENT '事件全局唯一 ID，重试不变',
    event_type        VARCHAR(64)  NOT NULL COMMENT '事件类型，见 [06] §4',
    source_system     VARCHAR(32)  NOT NULL COMMENT '来源系统',
    biz_order_no      VARCHAR(64)  NOT NULL COMMENT '业务单号',
    line_no           INT          NOT NULL COMMENT '行号',
    aggregate_version INT          NOT NULL COMMENT '事实版本号，接收方据此丢弃旧版本',
    occurred_at       DATETIME     NOT NULL COMMENT '业务发生时间',
    payload           TEXT         NOT NULL COMMENT '事件明细 JSON',
    target_system     VARCHAR(32)  NOT NULL COMMENT '目标系统',
    trace_id          VARCHAR(64)  NULL COMMENT '链路追踪 ID',
    status            VARCHAR(16)  NOT NULL COMMENT 'PENDING / SENT / FAILED / DEAD',
    retry_count       INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_at     DATETIME     NULL COMMENT '下次重试时间（退避）',
    created_at        DATETIME     NOT NULL COMMENT '落库时间',
    sent_at           DATETIME     NULL COMMENT '投递成功时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_due (status, next_retry_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='发件箱';

-- ---------------------------------------------------------------------------
-- 收件箱幂等记录（[05-data-model] §3.2）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inbox_consumed
(
    id             BIGINT       NOT NULL COMMENT '主键',
    event_id       VARCHAR(64)  NOT NULL COMMENT '事件 ID',
    biz_order_no   VARCHAR(64)  NOT NULL COMMENT '业务单号',
    line_no        INT          NOT NULL COMMENT '行号',
    event_type     VARCHAR(64)  NOT NULL COMMENT '事件类型',
    consumed_at    DATETIME     NOT NULL COMMENT '消费时间',
    result_summary VARCHAR(200) NULL COMMENT '处理结果摘要，重复事件原样返回',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consume (event_id, biz_order_no, line_no, event_type),
    KEY idx_inbox_consumed_at (consumed_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='收件箱幂等记录';

-- ---------------------------------------------------------------------------
-- 计划表（[05-data-model] §2）
--
-- 列清单受红线 [10] R-2 约束：只存四节点状态。新增列前必须走 [10] §5 的变更流程
-- （修订/新增 ADR + 文档与架构测试同步改）。
--
-- 无 deleted 列：业务事实表不做删除（[05-data-model] §1-5）。
-- 无 created_by/updated_by：字段清单未包含；确实需要操作人时先改文档再改表。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tms_order_plan
(
    id                   BIGINT         NOT NULL COMMENT '主键',
    biz_order_no         VARCHAR(64)    NOT NULL COMMENT '业务单号（TMS 统一生成，[ADR-012]）',
    line_no              INT            NOT NULL COMMENT '行号',
    source_system        VARCHAR(32)    NOT NULL COMMENT '需求来源系统',
    source_doc_no        VARCHAR(64)    NULL COMMENT '源系统单据号，独立保存不覆盖单号',
    order_type           VARCHAR(32)    NOT NULL COMMENT '业务类型',
    ship_from_code       VARCHAR(32)    NOT NULL COMMENT '发货仓编码',
    ship_to_code         VARCHAR(32)    NOT NULL COMMENT '收货仓/目的地编码',
    plan_qty             DECIMAL(18, 4) NOT NULL COMMENT '计划数量',
    dispatch_status      TINYINT        NOT NULL COMMENT '下发：0 未 / 1 已',
    dispatched_at        DATETIME       NULL COMMENT '下发时间',
    outbound_status      TINYINT        NOT NULL COMMENT '出库完成：0 / 1',
    outbound_completed_at DATETIME      NULL COMMENT '出库完成时间（业务时间）',
    inbound_status       TINYINT        NOT NULL COMMENT '入库完成：0 / 1',
    inbound_completed_at DATETIME       NULL COMMENT '入库完成时间（业务时间）',
    signed_status        TINYINT        NOT NULL COMMENT '签收完成：0 / 1',
    signed_completed_at  DATETIME       NULL COMMENT '签收完成时间（业务时间）',
    cancel_flag          TINYINT        NOT NULL DEFAULT 0 COMMENT '取消标记：0 / 1',
    cancelled_at         DATETIME       NULL COMMENT '取消时间',
    cancel_reason        VARCHAR(200)   NULL COMMENT '取消原因',
    reverse_flag         TINYINT        NOT NULL DEFAULT 0 COMMENT '冲销标记：0 / 1',
    reversed_at          DATETIME       NULL COMMENT '冲销时间',
    last_event_id        VARCHAR(64)    NULL COMMENT '最近消费的事件 ID（排障用）',
    version              INT            NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at           DATETIME       NOT NULL COMMENT '创建时间',
    updated_at           DATETIME       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan (biz_order_no, line_no),
    KEY idx_plan_outbound (outbound_status, outbound_completed_at),
    KEY idx_plan_signed (signed_status, signed_completed_at),
    KEY idx_plan_cancel (cancel_flag)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='计划表（订单中心雏形，[ADR-015]）';
