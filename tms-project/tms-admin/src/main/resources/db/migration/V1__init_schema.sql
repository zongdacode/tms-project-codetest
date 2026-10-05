-- V1: 初始库表结构
--
-- 对应批 0 数据模型：号段发号、发件箱、收件箱、计划表。
-- 从 schema-mysql.sql 迁移而来，后续表结构变更必须通过新增 V2、V3... 迁移脚本，
-- 禁止直接修改本文件（Flyway 会校验 checksum，改了会启动失败）。

-- ---------------------------------------------------------------------------
-- 号段发号表
-- ---------------------------------------------------------------------------
CREATE TABLE sys_id_segment
(
    biz_tag VARCHAR(64) NOT NULL COMMENT '业务标签，一段一锁',
    max_id  BIGINT      NOT NULL DEFAULT 0 COMMENT '已分配出去的最大 ID',
    step    INT         NOT NULL DEFAULT 1000 COMMENT '最近一次分配使用的号段长度',
    PRIMARY KEY (biz_tag)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='号段发号表';

-- ---------------------------------------------------------------------------
-- 发件箱
-- ---------------------------------------------------------------------------
CREATE TABLE outbox_event
(
    id                BIGINT       NOT NULL COMMENT '主键',
    event_id          VARCHAR(64)  NOT NULL COMMENT '事件全局唯一 ID，重试不变',
    event_type        VARCHAR(64)  NOT NULL COMMENT '事件类型',
    source_system     VARCHAR(32)  NOT NULL COMMENT '来源系统',
    biz_order_no      VARCHAR(64)  NOT NULL COMMENT '业务单号',
    line_no           INT          NOT NULL COMMENT '行号',
    aggregate_version INT          NOT NULL COMMENT '事实版本号，接收方据此丢弃旧版本',
    occurred_at       DATETIME(3)     NOT NULL COMMENT '业务发生时间',
    payload           TEXT         NOT NULL COMMENT '事件明细 JSON',
    target_system     VARCHAR(32)  NOT NULL COMMENT '目标系统',
    trace_id          VARCHAR(64)  NULL COMMENT '链路追踪 ID',
    status            VARCHAR(16)  NOT NULL COMMENT 'PENDING / SENT / FAILED / DEAD',
    retry_count       INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_at     DATETIME(3)     NULL COMMENT '下次重试时间（退避）',
    created_at        DATETIME(3)     NOT NULL COMMENT '落库时间',
    sent_at           DATETIME(3)     NULL COMMENT '投递成功时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_due (status, next_retry_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='发件箱';

-- ---------------------------------------------------------------------------
-- 收件箱幂等记录
-- ---------------------------------------------------------------------------
CREATE TABLE inbox_consumed
(
    id             BIGINT       NOT NULL COMMENT '主键',
    event_id       VARCHAR(64)  NOT NULL COMMENT '事件 ID',
    biz_order_no   VARCHAR(64)  NOT NULL COMMENT '业务单号',
    line_no        INT          NOT NULL COMMENT '行号',
    event_type     VARCHAR(64)  NOT NULL COMMENT '事件类型',
    consumed_at    DATETIME(3)     NOT NULL COMMENT '消费时间',
    result_summary VARCHAR(200) NULL COMMENT '处理结果摘要，重复事件原样返回',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consume (event_id, biz_order_no, line_no, event_type),
    KEY idx_inbox_consumed_at (consumed_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='收件箱幂等记录';

-- ---------------------------------------------------------------------------
-- 计划表
-- ---------------------------------------------------------------------------
CREATE TABLE tms_order_plan
(
    id                    BIGINT         NOT NULL COMMENT '主键',
    biz_order_no          VARCHAR(64)    NOT NULL COMMENT '业务单号',
    line_no               INT            NOT NULL COMMENT '行号',
    source_system         VARCHAR(32)    NOT NULL COMMENT '需求来源系统',
    source_doc_no         VARCHAR(64)    NULL COMMENT '源系统单据号',
    order_type            VARCHAR(32)    NOT NULL COMMENT '业务类型',
    ship_from_code        VARCHAR(32)    NOT NULL COMMENT '发货仓编码',
    ship_to_code          VARCHAR(32)    NOT NULL COMMENT '收货仓/目的地编码',
    plan_qty              DECIMAL(18, 4) NOT NULL COMMENT '计划数量',
    dispatch_status       TINYINT        NOT NULL COMMENT '下发：0 未 / 1 已',
    dispatched_at         DATETIME(3)       NULL COMMENT '下发时间',
    outbound_status       TINYINT        NOT NULL COMMENT '出库完成：0 / 1',
    outbound_completed_at DATETIME(3)       NULL COMMENT '出库完成时间',
    inbound_status        TINYINT        NOT NULL COMMENT '入库完成：0 / 1',
    inbound_completed_at  DATETIME(3)       NULL COMMENT '入库完成时间',
    signed_status         TINYINT        NOT NULL COMMENT '签收完成：0 / 1',
    signed_completed_at   DATETIME(3)       NULL COMMENT '签收完成时间',
    cancel_flag           TINYINT        NOT NULL DEFAULT 0 COMMENT '取消标记',
    cancelled_at          DATETIME(3)       NULL COMMENT '取消时间',
    cancel_reason         VARCHAR(200)   NULL COMMENT '取消原因',
    reverse_flag          TINYINT        NOT NULL DEFAULT 0 COMMENT '冲销标记',
    reversed_at           DATETIME(3)       NULL COMMENT '冲销时间',
    last_event_id         VARCHAR(64)    NULL COMMENT '最近消费的事件 ID',
    version               INT            NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at            DATETIME(3)       NOT NULL COMMENT '创建时间',
    updated_at            DATETIME(3)       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan (biz_order_no, line_no),
    KEY idx_plan_outbound (outbound_status, outbound_completed_at),
    KEY idx_plan_signed (signed_status, signed_completed_at),
    KEY idx_plan_cancel (cancel_flag)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='计划表';
