DROP TABLE IF EXISTS sys_id_segment;
DROP TABLE IF EXISTS tms_order_plan;
DROP TABLE IF EXISTS outbox_event;
DROP TABLE IF EXISTS inbox_consumed;

CREATE TABLE sys_id_segment (
    biz_tag VARCHAR(64) NOT NULL,
    max_id  BIGINT      NOT NULL DEFAULT 0,
    step    INT         NOT NULL DEFAULT 1000,
    PRIMARY KEY (biz_tag)
);

CREATE TABLE tms_order_plan (
    id              BIGINT      NOT NULL,
    biz_order_no    VARCHAR(64) NOT NULL,
    line_no         INT         NOT NULL,
    source_system   VARCHAR(32) NOT NULL,
    source_doc_no   VARCHAR(64),
    outbound_status TINYINT     NOT NULL DEFAULT 0,
    signed_status   TINYINT     NOT NULL DEFAULT 0,
    cancel_flag     TINYINT     NOT NULL DEFAULT 0,
    version         INT         NOT NULL DEFAULT 0,
    created_at      DATETIME    NOT NULL,
    updated_at      DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_plan UNIQUE (biz_order_no, line_no)
);

CREATE TABLE outbox_event (
    id           BIGINT      NOT NULL,
    event_id     VARCHAR(64) NOT NULL,
    event_type   VARCHAR(64) NOT NULL,
    biz_order_no VARCHAR(64) NOT NULL,
    line_no      INT         NOT NULL,
    payload      TEXT,
    status       VARCHAR(16) NOT NULL,
    retry_count  INT         NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_event UNIQUE (event_id)
);

CREATE TABLE inbox_consumed (
    id             BIGINT      NOT NULL,
    event_id       VARCHAR(64) NOT NULL,
    biz_order_no   VARCHAR(64) NOT NULL,
    line_no        INT         NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    result_summary VARCHAR(200),
    created_at     DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_consume UNIQUE (event_id, biz_order_no, line_no, event_type)
);
