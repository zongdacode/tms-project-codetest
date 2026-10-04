-- TMS 库表（H2，MODE=MySQL）
--
-- 与 schema-mysql.sql 同结构，去掉了 H2 不接受的 MySQL 专有装饰
-- （ENGINE / CHARSET / COMMENT / TINYINT 上的部分写法）。
--
-- 改动其中一份时**必须同步改另一份**。两份漂移是本方案的已知代价，
-- 也是 application.yml "待办 1"（换 Flyway）要解决的问题。

CREATE TABLE IF NOT EXISTS sys_id_segment
(
    biz_tag VARCHAR(64) NOT NULL,
    max_id  BIGINT      NOT NULL DEFAULT 0,
    step    INT         NOT NULL DEFAULT 1000,
    PRIMARY KEY (biz_tag)
);

CREATE TABLE IF NOT EXISTS outbox_event
(
    id                BIGINT       NOT NULL,
    event_id          VARCHAR(64)  NOT NULL,
    event_type        VARCHAR(64)  NOT NULL,
    source_system     VARCHAR(32)  NOT NULL,
    biz_order_no      VARCHAR(64)  NOT NULL,
    line_no           INT          NOT NULL,
    aggregate_version INT          NOT NULL,
    occurred_at       TIMESTAMP    NOT NULL,
    payload           CLOB         NOT NULL,
    target_system     VARCHAR(32)  NOT NULL,
    trace_id          VARCHAR(64)  NULL,
    status            VARCHAR(16)  NOT NULL,
    retry_count       INT          NOT NULL DEFAULT 0,
    next_retry_at     TIMESTAMP    NULL,
    created_at        TIMESTAMP    NOT NULL,
    sent_at           TIMESTAMP    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_due ON outbox_event (status, next_retry_at);

CREATE TABLE IF NOT EXISTS inbox_consumed
(
    id             BIGINT       NOT NULL,
    event_id       VARCHAR(64)  NOT NULL,
    biz_order_no   VARCHAR(64)  NOT NULL,
    line_no        INT          NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    consumed_at    TIMESTAMP    NOT NULL,
    result_summary VARCHAR(200) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_consume UNIQUE (event_id, biz_order_no, line_no, event_type)
);

CREATE INDEX IF NOT EXISTS idx_inbox_consumed_at ON inbox_consumed (consumed_at);

CREATE TABLE IF NOT EXISTS tms_order_plan
(
    id                    BIGINT         NOT NULL,
    biz_order_no          VARCHAR(64)    NOT NULL,
    line_no               INT            NOT NULL,
    source_system         VARCHAR(32)    NOT NULL,
    source_doc_no         VARCHAR(64)    NULL,
    order_type            VARCHAR(32)    NOT NULL,
    ship_from_code        VARCHAR(32)    NOT NULL,
    ship_to_code          VARCHAR(32)    NOT NULL,
    plan_qty              DECIMAL(18, 4) NOT NULL,
    dispatch_status       TINYINT        NOT NULL,
    dispatched_at         TIMESTAMP      NULL,
    outbound_status       TINYINT        NOT NULL,
    outbound_completed_at TIMESTAMP      NULL,
    inbound_status        TINYINT        NOT NULL,
    inbound_completed_at  TIMESTAMP      NULL,
    signed_status         TINYINT        NOT NULL,
    signed_completed_at   TIMESTAMP      NULL,
    cancel_flag           TINYINT        NOT NULL DEFAULT 0,
    cancelled_at          TIMESTAMP      NULL,
    cancel_reason         VARCHAR(200)   NULL,
    reverse_flag          TINYINT        NOT NULL DEFAULT 0,
    reversed_at           TIMESTAMP      NULL,
    last_event_id         VARCHAR(64)    NULL,
    version               INT            NOT NULL DEFAULT 0,
    created_at            TIMESTAMP      NOT NULL,
    updated_at            TIMESTAMP      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_plan UNIQUE (biz_order_no, line_no)
);

CREATE INDEX IF NOT EXISTS idx_plan_outbound ON tms_order_plan (outbound_status, outbound_completed_at);
CREATE INDEX IF NOT EXISTS idx_plan_signed ON tms_order_plan (signed_status, signed_completed_at);
CREATE INDEX IF NOT EXISTS idx_plan_cancel ON tms_order_plan (cancel_flag);
