-- Gate 0 沙箱建表（MySQL 8.4）；正式工程用 Flyway，此处仅为验证
CREATE TABLE IF NOT EXISTS sys_id_segment (
    biz_tag   VARCHAR(64) NOT NULL,
    max_id    BIGINT      NOT NULL DEFAULT 0,
    step      INT         NOT NULL DEFAULT 1000,
    updated_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (biz_tag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS tms_order_plan (
    id             BIGINT       NOT NULL,
    biz_order_no   VARCHAR(64)  NOT NULL,
    line_no        INT          NOT NULL,
    source_system  VARCHAR(32)  NOT NULL,
    source_doc_no  VARCHAR(64)           DEFAULT NULL,
    outbound_status TINYINT     NOT NULL DEFAULT 0,
    signed_status  TINYINT      NOT NULL DEFAULT 0,
    cancel_flag    TINYINT      NOT NULL DEFAULT 0,
    version        INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan (biz_order_no, line_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS outbox_event (
    id          BIGINT       NOT NULL,
    event_id    VARCHAR(64)  NOT NULL,
    event_type  VARCHAR(64)  NOT NULL,
    biz_order_no VARCHAR(64) NOT NULL,
    line_no     INT          NOT NULL,
    payload     TEXT,
    status      VARCHAR(16)  NOT NULL,
    retry_count INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event (event_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS inbox_consumed (
    id             BIGINT      NOT NULL,
    event_id       VARCHAR(64) NOT NULL,
    biz_order_no   VARCHAR(64) NOT NULL,
    line_no        INT         NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    result_summary VARCHAR(200)         DEFAULT NULL,
    created_at     DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_consume (event_id, biz_order_no, line_no, event_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
