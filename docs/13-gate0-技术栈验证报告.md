# 13 Gate 0 技术栈验证报告

| 文档属性 | 内容 |
|---|---|
| 版本 | V1.1（2026-10-04：补 MySQL 真实库验证，见 §5） |
| 目的 | 回答 [05-系统架构与项目结构方案](05-系统架构与项目结构方案.md) §8-A1 的待办：**Spring Boot 4.1.x 生态是否就绪，要不要回退 Boot 3.5.16** |
| 方法 | 建可运行沙箱工程实际编译 + 启动 + 跑测试（非查版本号推断） |
| 沙箱位置 | [`gate0-sandbox/`](../gate0-sandbox/)（验证用，非交付代码） |
| **结论** | **A1 判定：不回退，按 Boot 4.1.1 开工。** 生态就绪；H2 与真实 MySQL 各跑 11 项测试，均全绿 |
| 未覆盖 | MySQL 版本为 8.0.29（拿不到 8.4，见 §5.1）；**Redis 未验证**（§5.3） |

---

## 1. 环境

| 项 | 实测值 |
|---|---|
| JDK | Eclipse Temurin **21.0.12.1** LTS（`D:\dev\jdk\jdk21`） |
| Maven | Apache Maven **3.9.9** |
| Docker Compose | v5.5.1（**引擎不可用**，见 §5） |
| Spring Boot | **4.1.1**（4.x 线最新 GA；4.2.0-M2 为 milestone，不采用） |
| 本地 Maven 仓库 | 全新（`~/.m2` 原本不存在），依赖全部从 central 直连拉取成功 |

## 2. 版本锁定矩阵

以下版本已由沙箱工程实际解析并跑通，**批 0 脚手架直接采用**：

| 组件 | 坐标 | 版本 | 备注 |
|---|---|---|---|
| 平台 | `org.springframework.boot:spring-boot-starter-parent` | **4.1.1** | JDK 17+ 基线，本项目用 21 |
| Web | `spring-boot-starter-web` | 4.1.1（BOM） | |
| 校验 / 健康 | `spring-boot-starter-validation` / `-actuator` | 4.1.1（BOM） | |
| 持久层 | `com.baomidou:mybatis-plus-spring-boot4-starter` | **3.5.17** | **必须用 boot4 专用 starter**，非 boot3 |
| 分页依赖 | `com.baomidou:mybatis-plus-jsqlparser` | **3.5.17** | 3.5.9+ 起必须显式引入，否则分页静默失效 |
| ↳ 传递依赖 | `org.mybatis:mybatis-spring` | 4.0.0 | Spring 7 适配版 |
| ↳ 传递依赖 | `com.github.jsqlparser:jsqlparser` | 5.2 | |
| 数据库驱动 | `com.mysql:mysql-connector-j` | 9.7.0（BOM） | |
| 接口文档 | `org.springdoc:springdoc-openapi-starter-webmvc-ui` | **3.1.1** | 3.x 线为 Boot 4 适配版 |
| ↳ 传递依赖 | `io.swagger.core.v3:swagger-core-jakarta` | 2.2.55 | |
| 架构测试 | `com.tngtech.archunit:archunit-junit5` | **1.5.1** | |
| 测试基座 | `spring-boot-starter-test` | 4.1.1（BOM） | **不含 MockMvc**，见 §4-① |
| 测试-MVC | `spring-boot-starter-webmvc-test` | 4.1.1（BOM） | Boot 4 新增拆分出的模块 |

> 服务化（阶段 2）的 Nacos / Gateway / OpenFeign / SCA `2025.1.0.0` **本 Gate 不验证**——阶段 2 触发时才引入，届时另起 Gate。

## 3. 验证结果（11 项全绿）

`mvn test` → `Tests run: 11, Failures: 0, Errors: 0`

| # | 验证点 | 对应文档约束 | 结果 |
|---|---|---|---|
| 1 | 上下文装配不冲突（Boot 4 + MP + springdoc 共存） | — | ✅ |
| 2 | `/gate0/ping` 可访问 | — | ✅ |
| 3 | `/actuator/health` = UP | [05] §8-A4 | ✅ |
| 4 | `/v3/api-docs` 生成且含自定义 info 与 paths | [12] §7 DoD-1 | ✅ |
| 5 | 号段模式发号：3000 个 ID 跨 3 个号段，严格递增不重复 | [ADR-016]、[05-data-model] §1 | ✅ |
| 6 | 审计字段自动填充（`MetaObjectHandler` 被自动装配） | [05-data-model] §1 | ✅ |
| 7 | 乐观锁 `@Version` 递增 | [05-data-model] §2 | ✅ |
| 8 | **事务性发件箱**：计划表 + outbox 同事务落库 | [ADR-011]、[05-data-model] §3.1 | ✅ |
| 9 | 事务回滚：抛异常后业务表与 outbox **同时**回滚 | [ADR-011] | ✅ |
| 10 | 幂等消费：同 `event_id` 二次消费返回 DUPLICATE，状态只前进一次 | [06-api-contracts] §6 | ✅ |
| 11 | 分页插件 SQL 改写生效（count + limit 均正确） | — | ✅ |

> 第 5/8/9/10 项是**本项目架构依赖的核心机制**，不是通用冒烟——它们在 Boot 4 上可用，才说明 [ADR-011] 的可靠性三件套不是纸上方案。
>
> 测试数据源为 H2（`MODE=MySQL`），验证的是 **MyBatis-Plus 在 Boot 4 下的行为与 SQL 生成**；MySQL 8.4 的真实驱动/方言验证见 §5。

## 4. 关键发现（Boot 3 → 4 的破坏性差异）

以下是实际踩到并已解决的坑，**批 0 脚手架必须照此写**，否则第一步就报错：

### ① 测试栈被拆分：`@AutoConfigureMockMvc` 换了模块和包名

`spring-boot-starter-test` 在 Boot 4 中**不再包含** MockMvc 测试支持。必须额外引入 `spring-boot-starter-webmvc-test`，且注解包名变了：

```java
// Boot 3（已失效）
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

// Boot 4（正确）
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
```

`MockMvc` 本体、`MockMvcRequestBuilders`、`MockMvcResultMatchers` 仍在 `spring-test` 的 `org.springframework.test.web.servlet.*`，未变。

### ② MyBatis-Plus 必须换用 boot4 专用 starter

```xml
<!-- 错误：boot3 starter 在 Boot 4 下不装配 -->
<artifactId>mybatis-plus-spring-boot3-starter</artifactId>

<!-- 正确 -->
<artifactId>mybatis-plus-spring-boot4-starter</artifactId>
<version>3.5.17</version>
```

### ③ 分页插件需显式引入 jsqlparser

```xml
<artifactId>mybatis-plus-jsqlparser</artifactId>
<version>3.5.17</version>
```

3.5.9 起 `PaginationInnerInterceptor` 的 SQL 解析能力被拆到独立坐标；缺失时**不报错、分页静默失效**（返回全量数据），属高危静默故障，故 §3 专列第 11 项实测。

### ④ springdoc 用 3.x 线

`springdoc-openapi-starter-webmvc-ui:3.1.1` 直接可用，无需规避。其 pom 已依赖 Boot 4 的新模块名（`spring-boot-tomcat`、`spring-boot-health`），确认非旧版兼容层。

### ⑤ Maven 未做任何特殊配置

无需 `-Dspring-boot.repackage` 之类的变通，`spring-boot-maven-plugin` 4.1.1 开箱可用。本地仓库从零拉全量依赖无冲突、无版本仲裁告警。

## 5. 真实数据库运行时验证

**状态：已完成（MySQL 8.0.29，非 8.4）；Redis 未验证。**

### 5.1 为什么不是 MySQL 8.4

目标版本是 8.4，但本机拿不到 8.4 的 Windows 二进制：

| 源 | 结果 |
|---|---|
| `dev.mysql.com`（官方） | **不可达**（HTTP 000，"与服务器的连接意外终止"） |
| 清华 TUNA / 中科大 USTC | 不提供 MySQL 下载镜像 |
| 阿里云 / 腾讯云 / 南大 | 均 404 |
| 华为云 | 有 MySQL，**最高仅 8.0.29**（2022-03） |

Docker 路线同样不通：本机无 WSL2，`wsl --install` 装到一半**下载微软 WSL 3.0.1 包时连接意外终止**（网络层失败，非权限问题），Docker Desktop 的 Linux 引擎起不来。

**故改用华为云的便携版 MySQL 8.0.29**（免安装、免重启、可整目录删除）完成验证。

### 5.2 验证结果：11/11 全绿

`mvn test -Dspring.profiles.active=mysql` → `Tests run: 11, Failures: 0, Errors: 0`

与 H2 完全相同的 11 项测试，改跑真实 MySQL。**已通过直接查库确认数据确实落在 MySQL 而非静默回落 H2**：

| 表 | 行数 | 说明 |
|---|---|---|
| `tms_order_plan` | 7 | 分页测试 5 行 + 事务测试 2 行 |
| `outbox_event` | 7 | 与计划表同事务写入 |
| `inbox_consumed` | 1 | 幂等去重只留一条 |
| `sys_id_segment` | `tms_default`=1000 / `tms_test`=4000 | 号段分配与换段正确（3000 个 ID 跨 4 段） |

具体验证到的：`mysql-connector-j 9.7.0` 连通、`schema-mysql.sql` 的 DDL 在真实 MySQL 合法、`utf8mb4` 字符集、`SELECT ... FOR UPDATE` 持锁分配号段、跨 Mapper 单事务提交与回滚、分页 SQL 改写。

### 5.3 未覆盖的部分

- **MySQL 8.4 相对 8.0 的差异**：认证插件默认值、若干废弃参数、`mysql_native_password` 移除。这些要到正式环境用 8.4 时才暴露，属易定位问题。
- **Redis 7 完全未验证**：本机无 Redis，且 Docker 不可用。批 0 的 Redis 封装组件（缓存/分布式锁）落地时，需另找验证途径，或接受"按官方文档实现 + 集成测试后补"。

### 5.4 复现方式

```bash
# 便携版 MySQL 8.0.29（验证完可整目录删）
cd gate0-sandbox/.mysql/mysql-8.0.29-winx64
./bin/mysqld --initialize-insecure --basedir=. --datadir=./data
./bin/mysqld --basedir=. --datadir=./data --port=3306 --bind-address=127.0.0.1
./bin/mysql -u root --skip-password -h 127.0.0.1 -e "CREATE DATABASE tms_gate0 DEFAULT CHARACTER SET utf8mb4; CREATE USER 'tms'@'%' IDENTIFIED BY 'tms123456'; GRANT ALL ON tms_gate0.* TO 'tms'@'%';"

# 跑验证
cd gate0-sandbox && mvn test -Dspring.profiles.active=mysql
```

## 6. 对批 0 脚手架的落地结论

1. 父 pom 锁 `spring-boot-starter-parent:4.1.1`，`java.version=21`，`maven.compiler.release=21`。
2. 依赖写法照 §2 矩阵，**`mybatis-plus-spring-boot4-starter` + `mybatis-plus-jsqlparser` 成对出现**（建议在父 pom 里用 `<dependencyManagement>` 绑定，避免漏加）。
3. 测试模块从一开始就同时引入 `spring-boot-starter-test` **和** `spring-boot-starter-webmvc-test`。
4. §3 第 5/8/9/10/11 项直接提升为**框架层的常驻测试**（不是一次性验证脚本）——它们是 [ADR-011] / [ADR-016] 的机制保证。
5. [12-execution-plan] §3.3 的"CI 里 docker compose 起本地依赖"**当前不可用**（本机无 WSL2/Docker，§5.1），批 0 的 CI 先跑 H2 + 架构测试；MySQL 侧可用 `-Dspring.profiles.active=mysql` 指向既有 MySQL 实例。
6. ArchUnit 1.5.1 可用，[12] §3.3 的 R-1/R-2 红线规则可落地；[12] §7 "接口文档工具 `[待定]`" **可落定为 springdoc 3.1.1**。
7. **Redis 相关组件（缓存/分布式锁）落地时需先解决验证途径**——本机无 Redis 且 Docker 不可用（§5.3），别把"没有测试"当成"实现正确"。

## 7. 环境能力备忘（后续排障用）

本机网络环境有两处明确的不可达，遇到"装不上"时先查这里，别怀疑配置：

| 目标 | 状态 | 影响 |
|---|---|---|
| `dev.mysql.com` | **不可达**（HTTP 000） | 装不了官方 MySQL；需走镜像（华为云最高 8.0.29） |
| 微软 WSL 组件下载 | **连接意外终止** | WSL2 / Docker Desktop 装不上 |
| `repo.maven.apache.org` | 可达 | Maven 依赖正常拉取 |
| 清华 TUNA / 中科大 / 华为云 | 可达 | 可用作软件镜像源 |

> 建议 **A**（长期收益最大）；若不便重启，选 **B**。

---

## 附：沙箱工程结构

```
gate0-sandbox/
├── pom.xml                              # Boot 4.1.1 父 pom + 锁定矩阵
├── docker-compose.yml                   # MySQL 8.4 + Redis 7（待 §5 解锁）
├── src/main/java/com/example/gate0/
│   ├── Gate0Application.java            # @MapperScan
│   ├── idgen/SegmentAllocator.java      # 号段分配（独立事务持行锁）
│   ├── idgen/SegmentIdGenerator.java    # MP IdentifierGenerator 扩展点
│   ├── config/MybatisPlusConfig.java    # 分页 + 乐观锁 + 全表更新防护
│   ├── config/AuditMetaObjectHandler.java
│   ├── config/OpenApiConfig.java
│   ├── entity/{OrderPlan,OutboxEvent,InboxConsumed}.java
│   ├── mapper/{OrderPlan,OutboxEvent,InboxConsumed}Mapper.java
│   ├── service/PlanCommandService.java  # 事务性发件箱
│   ├── service/EventConsumeService.java # 幂等消费
│   └── controller/Gate0Controller.java
├── src/main/resources/{application.yml,schema-mysql.sql}
└── src/test/java/com/example/gate0/     # 11 项验证
    ├── Gate0ApplicationTests.java
    ├── OpenApiSmokeTest.java
    ├── SegmentIdGeneratorTest.java
    ├── TransactionAndIdempotencyTest.java
    ├── PaginationInterceptorTest.java
    └── architecture/LayeringRulesTest.java
```

复现命令：

```bash
cd gate0-sandbox && mvn test
```
