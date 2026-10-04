package com.tms.support;

import com.tms.admin.TmsAdminApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类：起完整 Spring 上下文，连 H2 内存库。
 *
 * <p>{@code classes} 必须显式给：{@code @SpringBootTest} 默认从测试类所在包向上找
 * {@code @SpringBootConfiguration}，而测试类在 {@code com.tms.arch}/{@code com.tms.event} 下，
 * 启动类在 {@code com.tms.admin}——不在其祖先路径上，自动查找找不到，报上下文加载失败。
 *
 * <p>用 H2 不是因为它等价于 MySQL，而是为了能进 CI。两者差异（分页 SQL 改写、
 * 锁行为、JSON 列）在 MySQL 上的验证见 [13-gate0] §5。
 */
@SpringBootTest(classes = TmsAdminApplication.class)
@ActiveProfiles("h2")
public abstract class IntegrationTestBase {
}
