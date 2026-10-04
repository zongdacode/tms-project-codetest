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

## 4. 关键发现（Boot 3 → 4 的破坏性差异，及批 0 实装踩到的坑）

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

> **以下 ⑥–⑨ 是批 0 实装阶段补的**（2026-10-04）。Gate 0 当时只建了骨架、没写业务代码，这四条要等到真正写类、写测试时才暴露。
> 它们与 ①–⑤ 同性质：都是 Boot 3 → 4 的破坏性差异，只是触发时机更晚。

### ⑥ Jackson 3：包名变了，异常也不再是受检异常

Boot 4.1.1 的 HTTP 消息转换走的是 **Jackson 3**（`tools.jackson.core:jackson-databind:3.1.5`），不是 Jackson 2。三处影响：

| 项 | Jackson 2（旧写法） | Jackson 3（Boot 4 实际） |
|---|---|---|
| 数据绑定入口 | `com.fasterxml.jackson.databind.JsonNode` / `ObjectMapper` | `tools.jackson.databind.JsonNode` / `ObjectMapper` |
| 异常基类 | `JsonProcessingException`（**受检**，必须 try/catch 或 throws） | `tools.jackson.core.JacksonException`（**非受检**，`RuntimeException` 子类） |
| 注解 | `com.fasterxml.jackson.annotation.*` | **不变**，仍是 `com.fasterxml.jackson.annotation.*`（jackson-annotations 2.21） |

**最容易被误伤的是第 2 行**，而且方向容易想反。旧写法 `catch (JsonProcessingException e)` 在 Boot 4 上写了会直接编译失败（类不存在）。真正危险的是另一种情形：既然异常变成非受检，**"必须处理"的编译期强制就消失了**——原本忘了 catch 会编译不过，现在忘了 catch 照样编译通过，坏数据一路往上冒到兜底处理器才被发现。写反序列化代码时要自己盯住这条。

> 注：`spring-boot-starter-test` 里的 `ObjectMapper` 注入的是 Jackson 3 实例；`application.yml` 里若有 Jackson 2 时代的配置项，需要核对是否改名。

### ⑦ `HttpStatus.UNPROCESSABLE_ENTITY` 已过时 → 改用 `UNPROCESSABLE_CONTENT`

Spring Framework 7 起，`UNPROCESSABLE_ENTITY` 标记 `@Deprecated`，替代品是 `UNPROCESSABLE_CONTENT`（RFC 9110 把 422 的语义名从 Unprocessable Entity 改为 Unprocessable Content）。**状态码数值仍是 422**，只是枚举常量名变了。业务拒绝（4xxxx）映射到 422 的地方要注意：用旧名编译只有告警，但会在后续版本被移除。

### ⑧ ArchUnit 1.x：`failOnEmptyShould` 默认为 `true`，规则匹配不到类会**失败**而不是**通过**

这是好事，但会绊住第一次写架构测试的人：一条"禁止 A 依赖 B"的规则，如果 `importPackages` 里根本没有 A 或 B 的类，规则不会"因为没有违规而通过"，而是直接报 `failed to check any classes`。

批 0 实测到两个具体后果：

1. 反例测试（故意违规的 fixture）必须**把被规则两端的类都 import 进来**，只 import 违规方会让规则匹配不到另一半而失败；
2. 反过来，这个行为正好充当了"规则是否还在生效"的自检——重构把某个包改名后，架构测试会红，而不是静默失效。**不要为了让它变绿去关掉 `failOnEmptyShould`。**

### ⑨ `@TestConfiguration` 的嵌套类只在"声明 `@SpringBootTest` 的那个类"里被发现

若 `@SpringBootTest` 写在测试基类上、嵌套 `@TestConfiguration` 写在子类里，子类那个**不会被注册**（Spring 只扫描声明该注解的类的嵌套配置类）。后果很隐蔽：注入的 `List<Xxx>` 少了一个实现，代码不报错、只是行为不对。

处置：把嵌套配置类改成显式 `@Import(SomeTest.XxxConfig.class)`，或把 `@SpringBootTest` 挪到具体测试类上。批 0 的 `OutboxDispatcherTest` 就是踩到这条之后改成显式 `@Import` 的。

### ⑩ MyBatis-Plus `strictUpdateFill` 会**跳过已有值**的字段——审计时间戳会静默冻结

这一条不是 Boot 4 特有，是 MyBatis-Plus 的语义陷阱，但杀伤力比上面几条都大，所以一并记在这里。

`MetaObjectHandler` 的 `strictInsertFill` / `strictUpdateFill` 语义是"**字段为空才填**"。写审计填充时很容易顺手写成：

```java
public void updateFill(MetaObject metaObject) {
    strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());   // ✗ 错的
}
```

而更新的常规流程是"**从库里读出实体 → 改几个业务字段 → 整对象存回**"。此时实体上的 `updatedAt` 是上次落库的值、并不为空，`strictUpdateFill` 于是直接跳过，UPDATE 语句把**旧的审计值原样写回**。

后果：更新成功、业务字段确实改了，只有 `updated_at` 停在插入时刻不动，**全程不报任何错**。靠它做的增量同步、审计追溯、缓存失效判定会全部失灵。批 0 实测踩中（2026-10-05）。

**正确写法**：`updateFill` 里用 `setFieldValByName` 无条件覆盖（`insertFill` 保持 `strictInsertFill`，以便历史数据迁移能保留原始 `created_at`）。落点：[AuditMetaObjectHandler](../../tms-project/tms-framework/src/main/java/com/tms/framework/mybatis/handler/AuditMetaObjectHandler.java)。

> 这条的教训不止在实现上，也在断言上：最初的测试断言的是 `updatedAt` **≥** 插入时间，
> 而"内存纳秒值 vs 库内微秒值"的精度截断恰好让它恒成立——它一直在**为错误的理由变绿**。
> 改成"严格大于"之后立刻变红，才挖出这个 bug。
> 对比同期的另一条：`OutboxDispatcherTest` 里"配置缺失时事件保持原状"那条一开始也是绿的，
> 但查下来是**因为投递实现压根没注册**（§4-⑨）——同样是"绿得没有道理"。
> **看到绿色要问一句：它是靠什么绿的？**

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
7. 涉及 JSON 读写的代码统一走 `tools.jackson.*`（§4-⑥）；`GlobalExceptionHandler`、`EventEnvelope` 等横切类里的 JSON 异常应按**非受检**处理，不要靠编译器提醒。
8. 状态码映射统一用 `UNPROCESSABLE_CONTENT`（§4-⑦），批 0 的 `GlobalExceptionHandler` 已按此实装。
9. 写架构测试时不要关闭 `failOnEmptyShould`（§4-⑧）；写测试配置时注意嵌套 `@TestConfiguration` 的发现范围（§4-⑨）。
10. `@LocalServerPort` 实测存在于 `org.springframework.boot.test.web.server.LocalServerPort`（自 Boot 3.0 起就在此包，Boot 4 未再挪动），可用。批 0 的启动验收测试改用读属性 `local.server.port`，只是为了少依赖一个类的包路径——两种写法等价。
11. 审计字段填充：`insertFill` 用 `strictInsertFill`、`updateFill` **必须**用 `setFieldValByName`（§4-⑩）——这是批 0 实测发现并修掉的一个真实缺陷，后续所有继承 `BaseEntity` 的模块都受益于此。
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
