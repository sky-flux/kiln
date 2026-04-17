# Spring Boot 4 最佳实践

## 核心信息

| 项      | 值                                                                           |
|--------|-----------------------------------------------------------------------------|
| 技术栈定位  | Java 25 LTS + Spring Boot 4.0 + jOOQ + PostgreSQL 18 + Redis 8              |
| 项目架构   | **Modular Monolith + DDD(模块化单体 + 领域驱动)**,每个限界上下文内部采用六边形(Ports & Adapters)分层 |
| 适用场景   | 政府信息化、国企 B 端 SaaS、金融对公系统、招投标项目                                              |
| 数据访问策略 | **Database First**(先设计 SQL,后生成 Java 代码)                                     |
| 并发模型   | **Virtual Threads**(Project Loom)                                           |
| 代码简化   | Java `record` 优先,`Lombok` 按需可选                                              |
| 构建工具   | Gradle 9.4+ Groovy DSL(`build.gradle`)                                      |
| 文档作用域  | 后端全栈,不涉及前端/移动端                                                              |

---

## 目录

1. [Stack 概览](#一stack-概览)
2. [技术栈总表](#二技术栈总表)
3. [项目结构与构建配置](#三项目结构与构建配置)
4. [Web 层与路由](#四web-层与路由)
5. [数据库与 ORM(jOOQ Database First)](#五数据库与-ormjooq-database-first)
6. [缓存层(Redis)](#六缓存层redis)
7. [认证授权(Spring Security + Sa-Token + Argon2id)](#七认证授权spring-security--sa-token--argon2id)
8. [加密与数据保护](#八加密与数据保护)
9. [文件处理与预览](#九文件处理与预览)
10. [异步任务与分布式调度(JobRunr)](#十异步任务与分布式调度jobrunr)
11. [工作流引擎(Flowable)](#十一工作流引擎flowable)
12. [配置管理](#十二配置管理)
13. [可观测性(日志、指标、链路)](#十三可观测性日志指标链路)
14. [错误处理与 API 响应规范](#十四错误处理与-api-响应规范)
15. [OpenAPI 契约](#十五openapi-契约)
16. [测试策略](#十六测试策略)
17. [容器化与部署](#十七容器化与部署)
18. [附录](#十八附录)
19. [领域驱动设计:DDD + 六边形 + Clean Architecture](#十九领域驱动设计ddd--六边形--clean-architecture)

---

## 一、Stack 概览

### 1.1 定位

**Java 25 LTS + Spring Boot 4 + jOOQ + PostgreSQL 18 + Redis 8**,面向政府信息化、国企 B 端 SaaS、金融对公、招投标、B2B SaaS
的后端实现标准。

Modular Monolith 架构:一个 Gradle 工程,多个子模块,单一 `app` 启动入口;业务模块通过 Spring Modulith 事件机制通信,不直接互相
`@Autowired`;模块边界由 `ApplicationModules.of(...).verify()` 单测强制,违规依赖 CI 即失败。

Database First:Flyway SQL 脚本是 schema 真相源,jOOQ 从 SQL 脚本生成类型安全的 Java 访问层。

Virtual Threads 承载 I/O:`spring.threads.virtual.enabled=true` 一键启用,Tomcat handler、`@Async`、`@Scheduled` 全走
VT,业务代码按阻塞式 I/O 书写。

### 1.2 Java 25 基线语法

- `record` 作为 DTO / 值对象一等公民,编译器原生生成 `equals` / `hashCode` / `toString`
- Pattern Matching for switch + Sealed Classes,承载 DDD 代数数据类型
- Primitive Patterns(Java 23+),对基本类型直接模式匹配
- Text Blocks 承载多行 SQL / JSON / HTML
- `java.time` 作为唯一时间类型(`Instant` / `LocalDateTime` / `ZonedDateTime`),禁用 `Date` / `Calendar` / `Joda-Time`
- Virtual Threads(Project Loom)作为 I/O 密集型业务的默认并发模型,synchronized 块已解除 pinning 问题(Java 24+)
- Stable Values(Java 25,JEP 502),替代部分 `static final` + 复杂初始化场景
- Scoped Values(Java 25 正式版),替代部分 `ThreadLocal` 在虚拟线程下的使用场景
- Structured Concurrency(Java 25 正式版),虚拟线程下的并发任务组

### 1.3 Spring Boot 4 关键能力

- `spring.threads.virtual.enabled=true`:一键启用虚拟线程,Tomcat handler / `@Async` / `@Scheduled` 全部切 VT
- `@ConfigurationProperties` + `record`:类型安全配置,构造器绑定
- `@RequestMapping(version = "v1")`:内置 API 版本化(见 4.6 节)
- HTTP Interface Client:声明式 HTTP 调用,自动装配(见 4.9 节)
- `RestTestClient`:REST 集成测试客户端(见 16.6 节)
- Jakarta EE 11 基线:Servlet 6.1、Bean Validation 3.1
- JSpecify null-safety 注解全家桶普及,IDE 静态分析抓 NPE
- `spring-boot` 拆成细粒度模块(`spring-boot-restclient`、`spring-boot-webmvc` 等),GraalVM 原生镜像构建更小

### 1.4 业务覆盖场景

- 政府 / 国企信息化项目(OA、ERP、审批系统、报表平台)
- 金融对公、保险对公系统
- 招投标项目
- B2B SaaS 后端(CRM、工单、合同管理)
- 对接国内多云厂商 SDK 的业务系统
- 需要 LDAP / SAML / OAuth2 集成的企业系统
- 需要审批流 / 工单流转 / 请假流的业务系统

---

## 二、技术栈总表

### 2.1 核心栈

| 类别                   | 选择                                             | 版本                                  |
|----------------------|------------------------------------------------|-------------------------------------|
| **语言**               | Java LTS                                       | 25                                  |
| **JDK 发行版**          | Eclipse Temurin                                | 25                                  |
| **框架**               | Spring Boot                                    | 4.0.5+                              |
| **模块化**              | Spring Modulith                                | 2.0.5+                              |
| **构建**               | Gradle Groovy DSL                              | 9.4.1+                              |
| **Web**              | Spring MVC + Virtual Threads                   | 随 Spring                            |
| **ORM / SQL**        | jOOQ(Database First)                           | 3.20+ / 3.21                        |
| **数据库**              | PostgreSQL                                     | 18.3+                               |
| **应用连接池**            | HikariCP                                       | 随 Spring                            |
| **数据库连接代理**          | PgBouncer                                      | 1.25+                               |
| **数据库迁移**            | Flyway                                         | 11.14+                              |
| **缓存 / 简单 KV**       | Redis + Lettuce(**兼容 Valkey 8.x**)             | 8.2+ / 随 Spring                     |
| **分布式锁 / 限流 / 延迟队列** | Redisson                                       | 3.55+                               |
| **分布式任务调度**          | JobRunr(`jobrunr-spring-boot-4-starter`)       | 8.3.0+                              |
| **工作流引擎**            | Flowable                                       | 8.0.0+                              |
| **微信支付 SDK**         | wechatpay-java(官方 APIv3)                       | 0.2.17+                             |
| **支付宝 SDK**          | alipay-easysdk / alipay-sdk-java(官方)           | 按官方最新                               |
| **Stripe SDK**       | stripe-java(官方)                                | 按官方最新                               |
| **认证(基础)**           | Spring Security                                | 随 Spring                            |
| **认证(会话 + 注解)**      | Sa-Token(`sa-token-spring-boot4-starter`)      | 1.45.0+                             |
| **密码哈希**             | Argon2id(BouncyCastle)                         | OWASP 2024+ 推荐参数                    |
| **加密算法库**            | BouncyCastle(SM4 / SM2 国密)                     | 1.80+                               |
| **JSON 序列化**         | Jackson                                        | 2.21+                               |
| **日期时间**             | `java.time`                                    | JDK 内置                              |
| **工具类库**             | hutool(chinabugotech fork)                     | 5.8.44+                             |
| **OpenAPI**          | springdoc-openapi                              | 2.8+                                |
| **Excel 处理**         | Apache Fesod(原 FastExcel / EasyExcel)          | 2.0.1-incubating+                   |
| **Word 文档**          | Apache POI XWPF + poi-tl(模板填充)                 | 5.3.0+ / 1.12.2+                    |
| **PDF 生成**           | iText Core + openhtmltopdf(HTML → PDF)         | 9.2+ / 1.0.10+                      |
| **电子发票(OFD)**        | ofdrw                                          | 2.2.5+                              |
| **文件预览服务**           | kkFileView(独立 Docker 服务)                       | 4.4.0+                              |
| **模板引擎**             | Freemarker                                     | 2.3.34+                             |
| **WebSocket**        | Spring WebSocket                               | 随 Spring                            |
| **SSE**              | Spring MVC `SseEmitter`                        | 随 Spring                            |
| **数据脱敏**             | Jackson `@JsonSerialize` + 自定义 `@Mask` 注解      | 随 Jackson                           |
| **校验**               | Jakarta Validation + Hibernate Validator       | 3.1 / 8.0                           |
| **日志**               | SLF4J + Logback                                | 随 Spring                            |
| **可观测性**             | Micrometer + Prometheus + OTel                 | 随 Spring                            |
| **测试**               | JUnit 5 + AssertJ + Mockito 5 + Testcontainers | 5.13+ / 3.26+ / 5.14+ / 1.21.3+     |
| **代码质量**             | Spotless + PMD                                 | 8.4.0+ / 7.15.0+                    |
| **容器构建**             | Jib                                            | 3.4.5+                              |
| **Base Image**       | Chainguard JRE                                 | `cgr.dev/chainguard/jre:openjdk-25` |

### 2.2 依赖取舍原则

本栈的**四级依赖优先**:

1. **JDK 标准库** 优先(`java.time`、`java.util.concurrent`、`java.net.http.HttpClient` 等)
2. **Spring Boot 官方 starter** 次之(`spring-boot-starter-*`)
3. **Spring 生态官方整合**(`spring-modulith`、`springdoc-openapi`)
4. **社区共识依赖** 最后(标准:Star > 5000 + 活跃维护 + 领域事实标准)

**原则补充**:

- 同一职责**只选一个库**,不重复引入(见下方拒绝清单)
- 国内特殊场景(如政府招标必填项)可破例,但需在项目 README 注明理由
- 避免链式传递依赖引入不必要的 jar(使用 `gradle dependencies` 定期审查)

### 2.3 版本锁定策略

**不用 dependencyManagement 通配符,不用 `latest.release`**:

- 所有直接依赖写**精确版本号**
- 传递依赖交给 Spring Boot BOM(`spring-boot-dependencies`)统一管理
- Renovate Bot 每月自动 PR 升级,手动合并

示例(`build.gradle`):

```groovy
dependencyManagement {
    imports {
        mavenBom "org.springframework.boot:spring-boot-dependencies:4.0.5"
        mavenBom "org.springframework.modulith:spring-modulith-bom:2.0.5"
    }
}

dependencies {
    // 直接依赖写死版本
    implementation 'org.jooq:jooq:3.20.0'
    implementation 'cn.dev33:sa-token-spring-boot4-starter:1.45.0'
    implementation 'cn.hutool:hutool-core:5.8.44'
    // Spring Boot BOM 管理的传递依赖不写版本
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
}
```

---

## 三、项目结构与构建配置

### 3.1 monorepo 多模块单体

> **⚠️ 架构分级提示**:本节给出的 `api/domain/repo/internal/` 是**简化版**包结构,适用于 Supporting / Generic 子域(
> 见 [第 19 章](#十九领域驱动设计ddd--六边形--clean-architecture) 核心域分级)。
>
> **Core Domain(如 `order`、`payment`)请采用第 19 章的完整六边形结构** `domain/application/adapter/config/`
> ,带聚合根、Port/Adapter 分离、ArchUnit 守护依赖方向。两套结构的迁移映射见 **19.18**。

**一个 Gradle 工程,多个 Gradle 子模块,一个可启动应用**。

**模块分层**:

| 分层   | 模块                                                       | 职责                      |
|------|----------------------------------------------------------|-------------------------|
| 平台层  | `app` / `common` / `infra`                               | 启动装配、通用工具、技术基础设施        |
| 横切能力 | `auth` / `tenant` / `audit` / `dict`                     | 每个项目都要的横向能力             |
| 能力域  | `payment` / `notification` / `file` / `llm` / `location` | 有统一业务语义、多厂商 provider 实现 |
| 功能域  | `workflow` / `report` / `search`                         | 按需启用的功能模块               |
| 业务域  | `user` / `order` / `product` / ...                       | 项目特定业务                  |

**典型工程目录**:

```
myapp/                                    # 工程根目录
├── settings.gradle                       # 模块声明
├── build.gradle                          # 根 build(插件版本、通用配置)
├── gradle.properties
├── gradle/wrapper/
├── gradlew / gradlew.bat
├── compose.yml                           # 本地开发 docker compose
├── README.md
│
├── app/                                  # 启动模块(含 main 方法)
│   └── src/main/java/com/myorg/myapp/
│       ├── Application.java              # @SpringBootApplication 入口
│       └── config/                       # 全局装配(@EnableCaching、@EnableAsync 等)
│
├── common/                               # 通用基础(不依赖 Spring)
│   └── src/main/java/com/myorg/myapp/common/
│       ├── exception/                    # AppException、AppCode
│       ├── result/                       # R<T>、PageQuery、PageResult
│       ├── util/                         # hutool 覆盖不到的工具
│       ├── annotation/                   # @RawResponse 等
│       ├── validation/                   # @Phone 等自定义校验注解
│       ├── mask/                         # @Mask / @MaskPhone / @MaskIdCard 注解 + MaskStrategy 枚举
│       └── constant/                     # 全局常量
│
├── infra/                                # 技术基础设施(无业务语义)
│   └── src/main/java/com/myorg/myapp/infra/
│       ├── jooq/                         # jOOQ 配置 + 生成代码目标包 + VisitListener
│       ├── redis/                        # Redis 客户端 + Redisson 配置
│       ├── cache/                        # Spring Cache Manager + Caffeine L1 + Redis L2
│       ├── security/                     # Spring Security + Sa-Token 过滤链配置
│       ├── openapi/                      # springdoc-openapi 配置
│       ├── jobrunr/                      # JobRunr BackgroundJobServer + Dashboard 鉴权
│       ├── flowable/                     # Flowable 启动配置
│       ├── http/                         # RestClient 全局配置 + HTTP 拦截器
│       ├── mdc/                          # MdcFilter(traceId/userId/tenantId)
│       ├── cors/                         # CorsFilter
│       ├── validation/                   # 全局 Validator + 自定义 ConstraintValidator 实现
│       ├── mask/                         # 数据脱敏 Jackson Serializer(注解定义在 common/mask/)
│       ├── websocket/                    # WebSocket 配置 + Token 握手拦截器 + Redis 广播
│       ├── sse/                          # SSE Broker + 分布式广播
│       ├── encryption/                   # 字段级加解密:FieldCipher + jOOQ Converter
│       ├── crypto/                       # 接口传输加密:动态 AES + RSA 密钥交换
│       ├── health/                       # 自定义 HealthIndicator(PgBouncer / 第三方 API)
│       └── task/                         # 纯基础设施定时任务(DB VACUUM、统计重置)
│
│ ─────────────── 横切能力 ───────────────
│
├── auth/                                 # 登录 / 注册 / Token / 权限数据源
│   └── src/main/java/com/myorg/myapp/auth/
│       ├── api/                          # AuthController(登录/注册/刷新/登出)
│       ├── domain/                       # PasswordService、LoginService、TokenService
│       ├── repo/                         # LoginLogRepository
│       └── task/                         # SessionCleanupJob
│
├── tenant/                               # 多租户上下文
│   └── src/main/java/com/myorg/myapp/tenant/
│       ├── context/                      # TenantContext、TenantHolder
│       ├── web/                          # TenantInterceptor / TenantFilter
│       ├── jooq/                         # TenantAwareVisitListener
│       └── admin/                        # 租户管理 API(限平台管理员)
│
├── audit/                                # 操作审计日志
│   └── src/main/java/com/myorg/myapp/audit/
│       ├── annotation/                   # @Auditable、@AuditIgnore
│       ├── aspect/                       # AuditAspect(AOP 自动记录)
│       ├── domain/                       # AuditService
│       ├── repo/                         # AuditLogRepository
│       ├── api/                          # AuditQueryController(合规人员)
│       └── task/                         # AuditLogArchiveJob
│
├── dict/                                 # 数据字典
│   └── src/main/java/com/myorg/myapp/dict/
│       ├── api/                          # DictController
│       ├── domain/                       # DictService、DictCacheService
│       └── repo/
│
│ ─────────────── 能力域(门面 + provider 模式)───────────────
│
├── payment/                              # 支付:微信支付 / 支付宝 / Stripe / 银联
│   └── src/main/java/com/myorg/myapp/payment/
│       ├── api/                          # PaymentController、PaymentNotifyController
│       ├── domain/                       # PaymentService、PaymentProvider 接口、PaymentChannel enum
│       ├── provider/                     # 各厂商实现(基于官方 SDK)
│       │   ├── wechat/                   # WechatPayProvider - 基于 wechatpay-java 官方 SDK
│       │   ├── alipay/                   # AlipayProvider - 基于 alipay-easysdk / alipay-sdk-java
│       │   ├── stripe/                   # StripeProvider - 基于 stripe-java
│       │   └── unionpay/                 # UnionPayProvider - 基于银联官方 Java SDK
│       ├── repo/                         # PaymentRepository
│       ├── config/                       # PaymentProperties
│       └── task/                         # PaymentReconciliationJob、PaymentTimeoutJob
│
├── notification/                         # 通知:短信 / 邮件 / 企微 / 钉钉 / 站内信 / 推送
│   └── src/main/java/com/myorg/myapp/notification/
│       ├── api/                          # 站内信 Controller
│       ├── domain/                       # NotificationService、TemplateService、NotificationProvider 接口
│       ├── provider/                     # 各通道实现
│       │   ├── sms/                      # AliyunSmsProvider、TencentSmsProvider
│       │   ├── email/                    # SmtpProvider、AliyunDirectMailProvider
│       │   ├── wecom/                    # WeComProvider
│       │   ├── dingtalk/
│       │   └── insite/                   # InSiteProvider(站内信)
│       ├── repo/                         # NotificationRepository、TemplateRepository
│       └── task/                         # SendNotificationJob、RetryFailedNotificationJob
│
├── file/                                 # 文件:阿里 OSS / 腾讯 COS / MinIO / 本地磁盘
│   └── src/main/java/com/myorg/myapp/file/
│       ├── api/                          # FileController(上传/下载/签名链接)
│       ├── domain/                       # FileService、StorageProvider 接口
│       ├── provider/
│       │   ├── aliyun/                   # AliyunOssProvider
│       │   ├── tencent/                  # TencentCosProvider
│       │   ├── minio/                    # MinioProvider
│       │   └── local/                    # LocalStorageProvider
│       ├── repo/                         # FileMetadataRepository
│       └── task/                         # OrphanFileCleanupJob、ThumbnailGenerateJob
│
├── llm/                                  # 大模型:豆包 / Claude / OpenAI / 通义(AI 场景启用)
│   └── src/main/java/com/myorg/myapp/llm/
│       ├── domain/                       # LlmService、LlmProvider 接口、ChatRequest/Response
│       ├── provider/
│       │   ├── doubao/                   # DoubaoProvider(火山引擎)
│       │   ├── claude/                   # ClaudeProvider(Anthropic)
│       │   ├── openai/                   # OpenAiProvider
│       │   └── qwen/                     # QwenProvider(通义)
│       ├── embedding/                    # 文本嵌入(pgvector 协作)
│       └── rag/                          # RAG 检索增强
│
├── location/                             # 地图定位:高德 / 百度 / 腾讯地图(地理场景启用)
│   └── src/main/java/com/myorg/myapp/location/
│       ├── domain/                       # LocationService、LocationProvider 接口
│       └── provider/
│           ├── amap/                     # 高德
│           ├── baidu/
│           └── tencent/
│
│ ─────────────── 功能域(按需启用)───────────────
│
├── workflow/                             # 工作流:Flowable 业务封装(项目含 3+ 流程时启用)
│   └── src/main/java/com/myorg/myapp/workflow/
│       ├── api/                          # TaskController、ProcessDefinitionController
│       ├── domain/                       # WorkflowService、TaskQueryService
│       ├── delegate/                     # 通用 ServiceTask 实现(@Component)
│       └── event/                        # 流程事件监听
│
├── report/                               # 报表与文档生成:Excel / Word / PDF / OFD 发票(按需启用)
│   └── src/main/java/com/myorg/myapp/report/
│       ├── api/                          # ReportController、DocumentController、PreviewController
│       ├── domain/                       # ReportService 门面 + DocumentService 门面 + ExportJobService
│       ├── excel/                        # ExcelExporter / ExcelImporter(Apache Fesod)
│       ├── word/                         # WordGenerator(poi-tl 模板填充)
│       ├── pdf/                          # PdfGenerator(iText Core + openhtmltopdf)
│       ├── invoice/                      # OFD 发票生成(ofdrw),或第三方税务平台对接
│       ├── preview/                      # 文件预览 URL 生成(kkFileView 集成)
│       ├── template/                     # 模板管理(DB + OSS 存储)
│       ├── repo/                         # ExportJobRepository / DocumentTemplateRepository
│       └── task/                         # AsyncExportJob(结果写 file 模块)
│
├── search/                               # 全文搜索:Elasticsearch / Meilisearch / PG FTS
│   └── src/main/java/com/myorg/myapp/search/
│       ├── api/                          # SearchController
│       ├── domain/                       # SearchService、IndexBuilder
│       └── task/                         # IndexRebuildJob
│
│ ─────────────── 业务域 ───────────────
│
├── user/                                 # 业务模块:用户
│   └── src/main/java/com/myorg/myapp/user/
│       ├── api/                          # 对外 Controller / Facade
│       ├── domain/                       # 领域模型、Service、领域事件
│       ├── repo/                         # jOOQ 封装的 Repository
│       ├── config/                       # UserProperties(密码复杂度、注册开关等)
│       ├── task/                         # 本模块定时/异步任务
│       └── internal/                     # 模块内部实现(Modulith 保护,不可被外部访问)
│
├── order/                                # 业务模块:订单
│   └── ...
│       └── task/
│           ├── OrderTimeoutCloseJob.java       # 超时关单
│           ├── OrderExportJob.java             # 异步导出
│           └── DailyOrderStatsJob.java         # 每日统计
│
├── product/                              # 业务模块:商品
│
└── db/                                   # 数据库脚本(独立于 Gradle)
    └── migration/                        # Flyway 迁移脚本
        ├── V1__init_schema.sql
        ├── V2__add_user_index.sql
        └── ...
```

**几个关键约定**:

- **业务模块数量按项目实际**,最简项目可能只有一个 `core` 模块,复杂项目可能有 10+ 个
- **横切能力模块按需选用**,一个小项目可能只用 `auth`,不需要 `tenant` / `audit` / `dict`
- **能力域模块遵循"门面 + provider"模式**(见 3.2)
- **任务类归属"它触发的那个模块"**(见 3.3),不单独拆 `job` 模块

### 3.2 能力域模块的"门面 + provider"模式

所有**背后有多厂商实现**的模块,强制按这个结构组织:

```
<capability>/
├── domain/                               # 门面 Service + Provider 接口 + 领域枚举
│   ├── XxxService.java                   # 唯一对外入口,业务模块只依赖这个
│   ├── XxxProvider.java                  # SPI 接口
│   └── XxxChannel.java                   # enum:可选的后端实现标识
├── provider/                             # 各厂商 / 通道 adapter
│   ├── <vendor-a>/                       # 实现 XxxProvider
│   └── <vendor-b>/
├── repo/                                 # 本能力的持久化(如 payment_order)
├── config/                               # XxxProperties
└── task/                                 # 异步 / 定时任务(如对账、重试)
```

**门面代码模板**(以 payment 为例):

```java
// domain/PaymentProvider.java
public interface PaymentProvider {
    PaymentChannel channel();
    PayResult pay(PayRequest req);
    RefundResult refund(RefundRequest req);
    PayStatus query(String paymentId);
    void handleNotify(HttpServletRequest req);
}
```

```java
// domain/PaymentService.java
@Service
public class PaymentService {

    private final Map<PaymentChannel, PaymentProvider> providers;

    public PaymentService(List<PaymentProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(PaymentProvider::channel, p -> p));
    }

    public PayResult pay(PayRequest req) {
        PaymentProvider provider = providers.get(req.channel());
        if (provider == null) {
            throw new AppException(AppCode.PAYMENT_CHANNEL_NOT_SUPPORTED, req.channel());
        }
        return provider.pay(req);
    }
}
```

**业务模块调用**:

```java
// order 模块内
@Service
public class OrderPaymentService {

    private final PaymentService paymentService;   // 只依赖门面,不 import 厂商 SDK

    public String createPayment(UUID orderId, PaymentChannel channel) {
        var order = orderRepository.findById(orderId).orElseThrow();
        return paymentService.pay(new PayRequest(
                order.id(),
                order.totalAmount(),
                channel,
                order.userId()
        )).redirectUrl();
    }
}
```

**收益**:

- 业务模块不 import 任何厂商 SDK(如 `WechatPayClient` / `AlipayClient`)
- 加新厂商:新增一个 `provider/<vendor>/XxxProvider` 实现并注册为 Spring Bean,业务代码零改动
- 切换厂商:改前端传递的 `channel` 枚举或运维配置,业务代码零改动
- 同一厂商的多种能力(比如微信的支付 + 登录 + 公众号)**按能力拆到不同模块**(`payment/provider/wechat/` +
  `auth/.../wechat/` + ...),不会让"微信相关代码"散落多处

#### 3.2.1 支付 provider 的 SDK 选型

**硬规矩:真金白银交易,走官方 per-channel SDK,不用聚合器**。

聚合器(如某些 "java-pay" / "jeepay-sdk" 类封装)看起来省事,但**一个通道出兼容问题容易连累其他通道**
,且维护方不对接官方,升级滞后。支付场景要求把风险降到最低,每个通道都用**该通道官方发布**的 Java SDK。

| 通道         | 官方 SDK                               | 坐标                                                                    |
|------------|--------------------------------------|-----------------------------------------------------------------------|
| 微信支付 APIv3 | **wechatpay-java**                   | `com.github.wechatpay-apiv3:wechatpay-java:0.2.17+`                   |
| 支付宝        | **alipay-easysdk** / alipay-sdk-java | `com.alipay.sdk:alipay-easysdk`(推荐)/ `com.alipay.sdk:alipay-sdk-java` |
| Stripe     | **stripe-java**                      | `com.stripe:stripe-java`(按 Stripe 官方最新)                               |
| 银联         | 银联官方 Java SDK                        | `com.unionpay.acp:acpsdk`(从银联开放平台下载)                                  |

**wechatpay-java 关键能力**(项目标配):

- 微信支付 APIv3 原生支持(Apache-2.0 许可)
- 自动签名 / 验签,`RSAAutoCertificateConfig` 定时更新平台证书
- 回调通知 `NotificationParser` 验签 + 解密一次完成
- 敏感信息 AES-GCM 加解密
- 双域名容灾(`api.mch.weixin.qq.com` ↔ `api2.wechatpay.cn`)
- 支持微信支付公钥(新)或平台证书(旧)两种签名模式

**依赖声明位置**:各支付 SDK 只声明在 `payment/build.gradle`,业务模块**不直接引入**。

```groovy
// payment/build.gradle
dependencies {
    implementation project(':common')
    implementation project(':infra')
    implementation project(':tenant')
    implementation project(':audit')
    implementation project(':notification')

    // 各通道官方 SDK(互相独立,按需启用)
    implementation 'com.github.wechatpay-apiv3:wechatpay-java:0.2.17'
    implementation 'com.alipay.sdk:alipay-easysdk:2.+'
    implementation 'com.stripe:stripe-java:28.+'

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.modulith:spring-modulith-starter-core'
}
```

**Provider 实现参考模板**(以微信支付为例):

```java
package com.example.myapp.payment.provider.wechat;

import com.example.myapp.payment.domain.*;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.*;
import com.wechat.pay.java.service.payments.model.Transaction;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class WechatPayProvider implements PaymentProvider {

    private final WechatPayProperties props;
    private final NativePayService nativePayService;
    private final NotificationParser notificationParser;

    public WechatPayProvider(WechatPayProperties props) {
        this.props = props;

        Config config = new RSAAutoCertificateConfig.Builder()
                .merchantId(props.mchId())
                .privateKeyFromPath(props.privateKeyPath())
                .merchantSerialNumber(props.merchantSerialNumber())
                .apiV3Key(props.apiV3Key())
                .build();

        this.nativePayService = new NativePayService.Builder().config(config).build();
        this.notificationParser = new NotificationParser(
                (com.wechat.pay.java.core.notification.NotificationConfig) config);
    }

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.WECHAT_PAY;
    }

    @Override
    public PayResult pay(PayRequest req) {
        PrepayRequest prepay = new PrepayRequest();
        Amount amount = new Amount();
        amount.setTotal(req.amount().movePointRight(2).intValue());  // 元 → 分
        prepay.setAmount(amount);
        prepay.setAppid(props.appId());
        prepay.setMchid(props.mchId());
        prepay.setDescription(req.description());
        prepay.setNotifyUrl(props.notifyUrl());
        prepay.setOutTradeNo(req.paymentId());

        PrepayResponse resp = nativePayService.prepay(prepay);
        return new PayResult(req.paymentId(), resp.getCodeUrl(), PayStatus.PENDING);
    }

    @Override
    public void handleNotify(HttpServletRequest req) {
        // 用 NotificationParser 验签 + 解密 + 反序列化
        // Transaction transaction = notificationParser.parse(requestParam, Transaction.class);
        // 更新本地 payment 表状态,发领域事件
    }
}
```

**配置类**:

```java
package com.example.myapp.payment.provider.wechat;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "payment.provider.wechat")
@Validated
public record WechatPayProperties(
        @NotBlank String appId,
        @NotBlank String mchId,
        @NotBlank String merchantSerialNumber,
        @NotBlank String privateKeyPath,
        @NotBlank String apiV3Key,
        @NotBlank String notifyUrl
) {}
```

`application.yml`:

```yaml
payment:
  provider:
    wechat:
      app-id: ${WECHAT_APP_ID}
      mch-id: ${WECHAT_MCH_ID}
      merchant-serial-number: ${WECHAT_MCH_SERIAL}
      private-key-path: ${WECHAT_PRIVATE_KEY_PATH}
      api-v3-key: ${WECHAT_API_V3_KEY}
      notify-url: https://api.example.com/api/v1/payment/notify/wechat
```

**回调接收 Controller**(放在 `payment/api/`):

```java
package com.example.myapp.payment.api;

import com.example.myapp.common.annotation.RawResponse;
import com.example.myapp.payment.domain.PaymentChannel;
import com.example.myapp.payment.domain.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment/notify")
public class PaymentNotifyController {

    private final PaymentService paymentService;

    public PaymentNotifyController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RawResponse   // 跳过 R<T> 包装,走原始响应
    @PostMapping("/wechat")
    public ResponseEntity<String> wechatNotify(HttpServletRequest req) {
        paymentService.handleNotify(PaymentChannel.WECHAT_PAY, req);
        return ResponseEntity.ok("{\"code\":\"SUCCESS\",\"message\":\"成功\"}");
    }

    @RawResponse
    @PostMapping("/alipay")
    public ResponseEntity<String> alipayNotify(HttpServletRequest req) {
        paymentService.handleNotify(PaymentChannel.ALIPAY, req);
        return ResponseEntity.ok("success");
    }

    @RawResponse
    @PostMapping("/stripe")
    public ResponseEntity<String> stripeNotify(HttpServletRequest req) {
        paymentService.handleNotify(PaymentChannel.STRIPE, req);
        return ResponseEntity.ok("");
    }
}
```

**关键约束**:

- 回调 Controller 走 `@RawResponse`,**不包装 R<T>**(微信 / 支付宝 / Stripe 对响应格式各有要求)
- 白名单放过 `/api/v1/payment/notify/**` 的 Sa-Token 拦截(回调不带用户 Token)
- 验签失败直接返回 4xx,**不给重试**(避免篡改报文被接受)
- 业务处理失败返回 5xx,微信 / 支付宝会按各自策略重试

### 3.3 任务类的归属规则

**不单独拆 `job` / `cron` 模块**。JobRunr 启动时自动扫描全工程的 `@Job` / `@Recurring` 类,任务类归属"它触发的那个模块":

```mermaid
graph TD
    Start[新增一个任务] --> Q1{属于某个业务模块吗?}
    Q1 -->|是,如订单超时关单| A1[放 order/task/]
    Q1 -->|是,如发通知| A2[放 notification/task/]
    Q1 -->|否,跨多业务模块| Q2{是运维/平台级任务?}
    Q2 -->|是,如 GDPR 数据擦除| A3[独立 job 模块(可选)]
    Q2 -->|否,纯技术基础设施| A4[放 infra/task/]
```

**常见任务归属对照**:

| 任务            | 归属                   |
|---------------|----------------------|
| 订单超时关单        | `order/task/`        |
| 支付对账          | `payment/task/`      |
| 订阅消息批量发送      | `notification/task/` |
| 图片异步压缩        | `file/task/`         |
| 审计日志归档        | `audit/task/`        |
| 过期 Session 清理 | `auth/task/`         |
| 每日报表生成        | `report/task/`       |
| ES 索引重建       | `search/task/`       |
| 数据库 VACUUM    | `infra/task/`        |
| 跨模块 GDPR 清理   | 独立 `job/` 模块(项目需要时)  |
| 平台级 SLA 巡检    | 独立 `job/` 模块(项目需要时)  |

**任务类就是触发器,业务逻辑必须回调对应 Service**:

```java
// order/task/OrderTimeoutCloseJob.java
@Component
public class OrderTimeoutCloseJob {

    private final OrderService orderService;

    public OrderTimeoutCloseJob(OrderService orderService) {
        this.orderService = orderService;
    }

    @Recurring(id = "order-timeout-close", interval = "PT1M")
    @Job(name = "订单超时关单")
    public void execute() {
        orderService.closeTimeoutOrders();    // 业务逻辑在 Service 里,Job 只做触发
    }
}
```

**独立 `job` 模块的启用条件**(大多数项目不需要):

- 有**跨多个业务模块**的任务(如 GDPR 合规数据擦除涉及 user / order / file / audit)
- 有纯运维级巡检任务(平台健康度、SLA 统计、容量预警)
- 运维任务数量超过 5-6 个且跨模块

启用时,`job` 模块严禁写业务逻辑,只调用业务模块的 Service 门面。

### 3.4 `infra/` 子包职责与归属规则

**`infra/` 的定位**:**技术基础设施,无业务语义**。业务模块依赖 `infra/` 访问通用技术能力,但 `infra/` **不承载任何业务逻辑
**。

**判定一个候选是否属于 `infra`**:

| 候选                      | 有业务语义? | 多厂商实现? | 归属                                      |
|-------------------------|--------|--------|-----------------------------------------|
| 发短信 / 邮件 / 企微           | ✅ 有    | ✅ 有    | `notification/provider/`,**不是 `infra`** |
| 微信支付 / 支付宝 / Stripe     | ✅ 有    | ✅ 有    | `payment/provider/`,**不是 `infra`**      |
| 阿里 OSS / 腾讯 COS / MinIO | ✅ 有    | ✅ 有    | `file/provider/`,**不是 `infra`**         |
| 豆包 / Claude / OpenAI    | ✅ 有    | ✅ 有    | `llm/provider/`,**不是 `infra`**          |
| 高德 / 百度 / 腾讯地图          | ✅ 有    | ✅ 有    | `location/provider/`,**不是 `infra`**     |
| 数据库连接池配置                | ❌ 无    | ❌ 无    | `infra/jooq/`                           |
| Redis 客户端配置             | ❌ 无    | ❌ 无    | `infra/redis/`                          |
| HTTP 客户端基础设施            | ❌ 无    | ❌ 无    | `infra/http/`                           |
| Spring Security 过滤链     | ❌ 无    | ❌ 无    | `infra/security/`                       |

**核心判据**(只要满足任一,就不属于 `infra`):

1. 业务代码**直接调用它完成业务动作**(发通知、发支付、存文件)
2. 有**多个厂商实现需要按通道/渠道切换**
3. 需要**统一的门面 Service 做治理**(频控、降级、审计、模板管理)

满足以下全部,才属于 `infra`:

1. **没有业务语义**,纯技术层抽象
2. **其他模块的基础设施依赖它**,而不是业务直接使用
3. **单一实现**(或者单一实现 + 切换配置参数,而不是独立 provider 类)

#### `infra/` 必加子包(生产项目都要)

| 子包            | 职责                                                                                              |
|---------------|-------------------------------------------------------------------------------------------------|
| `jooq/`       | jOOQ 配置、生成代码目标包、全局 `VisitListener`(自动注入 `tenant_id` / 审计字段)                                     |
| `redis/`      | Redis 连接配置、`RedisTemplate` / `ReactiveRedisTemplate`、`RedissonClient` Bean                      |
| `cache/`      | Spring Cache Manager、Caffeine L1 + Redis L2 两级缓存、`@Cacheable` 序列化配置                             |
| `security/`   | `SecurityFilterChain`、Sa-Token 拦截器配置、`PasswordEncoder`(Argon2id)、`JsonAuthenticationEntryPoint` |
| `openapi/`    | springdoc-openapi 配置、全局 `OpenAPI` Bean、安全方案声明                                                   |
| `jobrunr/`    | JobRunr `BackgroundJobServer` 配置、Virtual Threads 装配、Dashboard 鉴权                                |
| `flowable/`   | Flowable 启动配置、流程引擎 Bean                                                                         |
| `http/`       | `RestClient.Builder` 全局配置(超时、重试、日志)、`HttpServiceProxyFactory` 装配、MDC 透传拦截器                      |
| `mdc/`        | `MdcFilter`(注入 `traceId` / `userId` / `tenantId` / `requestUri`)                                |
| `cors/`       | `CorsFilter` 配置                                                                                 |
| `validation/` | 全局 `Validator` Bean、自定义 `ConstraintValidator` 实现(注解本身定义在 `common/validation/`)                  |
| `health/`     | 自定义 `HealthIndicator`(PgBouncer / 关键第三方 API 的健康探针)                                              |
| `task/`       | 纯基础设施定时任务(DB VACUUM、`pg_stat_statements` 重置、连接池活跃数告警)                                           |

#### `infra/` 条件性加子包(特定场景才加)

| 子包              | 启用条件                                                                                   |
|-----------------|----------------------------------------------------------------------------------------|
| `i18n/`         | 需要多语言(国际化 SaaS / 出海项目):`MessageSource`、`LocaleResolver`、AppCode message 国际化            |
| `ratelimit/`    | 需要**全局 API 限流**(不是业务侧的某个操作限流):基于 Redisson 的 Gateway 级 RateLimiter,按 IP / 用户 / API 路径限流 |
| `idempotent/`   | **多个业务模块**都需要幂等保证:`@Idempotent` 注解 + AOP 实现,Redis Token / request_id 去重                |
| `feature-flag/` | 需要灰度发布 / A/B 测试:基于 Nacos / Apollo / DB 的 flag 读取,`@FeatureFlag` 注解                     |
| `transaction/`  | 需要事务增强:`TransactionSynchronization` 工具、`@TransactionalEventListener` 补充支持              |

**已升级为标配子包的**(不再是条件性,视为必备):

- `mask/` — 数据脱敏 Jackson Serializer,详见 **4.13 节**
- `websocket/` — WebSocket + Token 鉴权 + 分布式会话,详见 **4.14 节**
- `sse/` — SSE 推送 + 分布式会话,详见 **4.15 节**
- `encryption/` — 字段级加解密(AES-GCM / SM4-GCM),详见 **8.2 节**
- `crypto/` — 接口传输加密(动态 AES + RSA),详见 **8.3 节**

**只在业务模块共享该能力时才放 `infra`**。如果**只有一个业务模块用到**(例如只有 `payment` 需要幂等),就直接放 `payment/`
内部,不必提升到 `infra`。

#### `infra/` 不应该出现的内容

- ❌ 任何以 `Controller` 结尾的类(`infra/` 不对外暴露 HTTP API)
- ❌ 任何 `Repository`(数据访问属于业务模块或能力域)
- ❌ 任何包含业务规则的 Service(比如"判断订单是否可取消"这种)
- ❌ 第三方业务 SDK 的封装(微信支付 / 支付宝 / 短信服务 / 地图服务)——这些属于对应能力域 `provider/`
- ❌ 任何跨业务模块的事件监听器(放发布事件的模块,或独立 `job/` 模块)
- ❌ 数据迁移脚本相关 Java 代码(放业务模块或 `job/`)

### 3.5 模块职责与依赖方向

**依赖方向严格遵守**(Spring Modulith 强制,违规 CI 即失败):

```mermaid
graph TD
    app[app 启动]

    user[user / order / product<br/>业务模块]

    workflow[workflow]
    report[report]
    search[search]

    payment[payment]
    notification[notification]
    file[file]
    llm[llm]
    location[location]

    auth[auth]
    tenant[tenant]
    audit[audit]
    dict[dict]

    infra[infra]
    common[common]

    app --> user
    app --> workflow
    app --> report
    app --> search

    user --> payment
    user --> notification
    user --> file
    user --> llm
    user --> location
    user --> workflow
    user --> report
    user --> search
    user --> auth
    user --> tenant
    user --> audit
    user --> dict

    payment --> auth
    payment --> tenant
    payment --> audit
    notification --> tenant
    notification --> audit
    file --> tenant
    file --> audit
    llm --> tenant
    location --> tenant

    auth --> tenant
    auth --> audit
    audit --> tenant

    payment --> infra
    notification --> infra
    file --> infra
    llm --> infra
    location --> infra
    workflow --> infra
    report --> infra
    search --> infra
    auth --> infra
    tenant --> infra
    audit --> infra
    dict --> infra

    infra --> common
```

**规则**:

- `app` **只**依赖业务模块和按需启用的功能域模块(workflow / report / search)
- **业务模块**可以依赖能力域(`payment` / `notification` / `file` / `llm` / `location`)+ 横切能力(`auth` / `tenant` /
  `audit` / `dict`)+ `infra` / `common`
- **业务模块之间不直接依赖**,通过 Spring Modulith 事件通信
- **能力域模块之间不直接依赖**,通过门面 Service 调用其他能力域(例如 `payment` 需要发通知时依赖 `notification` 的
  `NotificationService`)
- **横切能力模块只依赖 `infra` / `common`**,不依赖能力域或业务域
- **能力域模块可以依赖横切能力**(如 `payment` 用 `tenant` 做租户隔离,用 `audit` 记录审计)
- **`infra` 只依赖 `common`**,不依赖任何能力模块
- **`common` 是最底层**,不依赖任何工程内模块,只依赖 JDK + hutool + slf4j + jakarta-validation-api

**`common/` 的硬约束**:

- `common/build.gradle` **不得**引入 `org.springframework.*` 任何依赖
- `common/` 包下**不得**出现 `@Configuration` / `@Bean` / `@Component` / `@Service` / `@Autowired`
- 如果想放的东西需要 Spring,它就不属于 `common`,应归到 `infra` 或对应能力模块

**配置类的归属规则**:

| 配置性质                                          | 放哪                                        |
|-----------------------------------------------|-------------------------------------------|
| 基础设施技术配置(Jackson / Redis / jOOQ / Security)   | `infra/<子领域>/`                            |
| 能力域配置属性(`PaymentProperties`、`FileProperties`) | `<capability>/config/`                    |
| 业务配置属性(`UserProperties` 密码复杂度等)               | `<业务模块>/config/`                          |
| 启动装配组合(`@EnableCaching` / `@EnableAsync`)     | `app/config/`                             |
| 跨能力域配置                                        | `app/config/` 或单独的 `<capability>/config/` |

### 3.6 settings.gradle

```groovy
// settings.gradle
rootProject.name = 'myapp'

include 'app'
include 'common'
include 'infra'

// 横切能力
include 'auth'
include 'tenant'
include 'audit'
include 'dict'

// 能力域(按需选用)
include 'payment'
include 'notification'
include 'file'
// include 'llm'           // AI 场景启用
// include 'location'       // 地理场景启用
// include 'realtime'       // 实时推送(WebSocket + SSE)场景启用

// 功能域(按需启用)
// include 'workflow'       // 工作流场景启用
// include 'report'         // 报表场景启用
// include 'search'         // 全文搜索场景启用

// 业务模块
include 'user'
include 'order'
include 'product'

// 工具模块(独立启动,不随主工程部署)
// include 'codegen'        // 代码生成器,开发期单独执行

pluginManagement {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/spring' }
        mavenCentral()
    }
}
```

### 3.7 根 build.gradle

```groovy
// build.gradle(根)
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.5' apply false
    id 'io.spring.dependency-management' version '1.1.7' apply false
    id 'com.diffplug.spotless' version '8.4.0' apply false
    id 'nu.studer.jooq' version '10.1' apply false
    id 'org.flywaydb.flyway' version '11.14.0' apply false
    id 'com.google.cloud.tools.jib' version '3.4.5' apply false
}

allprojects {
    group = 'com.example'
    version = '1.0.0-SNAPSHOT'

    tasks.withType(JavaCompile).configureEach {
        options.encoding = 'UTF-8'
        options.compilerArgs << '-parameters'   // Spring 必备:保留方法参数名
    }
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'io.spring.dependency-management'
    apply plugin: 'com.diffplug.spotless'

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.ADOPTIUM
        }
    }

    dependencyManagement {
        imports {
            mavenBom "org.springframework.boot:spring-boot-dependencies:4.0.5"
            mavenBom "org.springframework.modulith:spring-modulith-bom:2.0.5"
        }
    }

    dependencies {
        // 所有子模块通用的依赖
        compileOnly 'org.projectlombok:lombok:1.18.40'
        annotationProcessor 'org.projectlombok:lombok:1.18.40'

        testCompileOnly 'org.projectlombok:lombok:1.18.40'
        testAnnotationProcessor 'org.projectlombok:lombok:1.18.40'

        testImplementation 'org.springframework.boot:spring-boot-starter-test'
        testImplementation 'org.assertj:assertj-core'
        testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    }

    test {
        useJUnitPlatform()
        jvmArgs '-XX:+EnableDynamicAgentLoading'   // Mockito 5 + Java 25
    }

    // Spotless:格式化
    spotless {
        java {
            palantirJavaFormat('2.50.0')
            removeUnusedImports()
            importOrder('java|javax', 'jakarta', 'org.springframework', 'com.example', '')
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
```

**说明**:

- `-parameters` 编译参数:Spring 依赖注入、`@RequestParam` 必须保留参数名
- `JvmVendorSpec.ADOPTIUM`:Gradle Toolchain 自动下载 Temurin 25
- `palantirJavaFormat`:业界最广泛使用的 Java 格式化器,比 Google Java Format 更实用
- `-XX:+EnableDynamicAgentLoading`:Java 21+ 默认禁止动态 agent,Mockito 5 需要

### 3.8 app/build.gradle(启动模块)

```groovy
// app/build.gradle
plugins {
    id 'org.springframework.boot'
    id 'com.google.cloud.tools.jib'
}

dependencies {
    // 业务模块
    implementation project(':user')
    implementation project(':order')
    implementation project(':product')

    // 功能域(按需启用,与 settings.gradle 对齐)
    // implementation project(':workflow')
    // implementation project(':report')
    // implementation project(':search')

    // 业务模块会传递引入横切能力 / 能力域 / infra / common,app 无需重复声明
    // 但若 app 自身也需要直接依赖(如启动装配引用),显式加上:
    implementation project(':infra')

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.modulith:spring-modulith-starter-core'
    implementation 'org.springframework.modulith:spring-modulith-starter-jdbc'

    developmentOnly 'org.springframework.boot:spring-boot-devtools'
}

// Spring Boot 打包配置
bootJar {
    archiveFileName = 'app.jar'
}

// Jib 容器构建
jib {
    from {
        image = 'cgr.dev/chainguard/jre:openjdk-25'
        platforms {
            platform {
                architecture = 'amd64'
                os = 'linux'
            }
            platform {
                architecture = 'arm64'
                os = 'linux'
            }
        }
    }
    to {
        image = 'registry.cn-chengdu.aliyuncs.com/myorg/myapp'
        tags = ['latest', project.version]
    }
    container {
        jvmFlags = [
            '-XX:+UseG1GC',
            '-XX:MaxRAMPercentage=75.0',
            '-XX:+ExitOnOutOfMemoryError',
            '-Djava.security.egd=file:/dev/./urandom',
            '-Dfile.encoding=UTF-8',
            '-Duser.timezone=Asia/Shanghai'
        ]
        ports = ['8080']
        user = 'nonroot'
        creationTime = 'USE_CURRENT_TIMESTAMP'
    }
}
```

### 3.9 业务模块的 build.gradle(示例:user)

```groovy
// user/build.gradle
dependencies {
    // 平台层
    implementation project(':common')
    implementation project(':infra')

    // 横切能力
    implementation project(':auth')
    implementation project(':tenant')
    implementation project(':audit')
    implementation project(':dict')

    // 能力域(本模块要用的)
    implementation project(':notification')
    implementation project(':file')

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.modulith:spring-modulith-starter-core'

    implementation 'cn.hutool:hutool-core:5.8.44'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6'
}
```

**注意**:

- 业务模块**不直接依赖** Spring Boot BOM,由根 `subprojects` 继承
- `implementation` 而非 `api`:传递依赖关闭,避免依赖污染
- 每个业务模块独立声明所需依赖,即便重复

### 3.10 infra/build.gradle(基础设施模块)

```groovy
// infra/build.gradle
plugins {
    id 'nu.studer.jooq'
}

dependencies {
    api project(':common')

    // jOOQ
    api 'org.springframework.boot:spring-boot-starter-jooq'
    api 'org.jooq:jooq:3.20.0'
    jooqGenerator 'org.jooq:jooq-meta-extensions:3.20.0'
    jooqGenerator 'org.postgresql:postgresql'

    // Redis
    api 'org.springframework.boot:spring-boot-starter-data-redis'
    api 'org.redisson:redisson-spring-boot-starter:3.55.0'

    // 数据库
    runtimeOnly 'org.postgresql:postgresql'

    // Flyway
    api 'org.flywaydb:flyway-core'
    api 'org.flywaydb:flyway-database-postgresql'

    // 安全
    api 'org.springframework.boot:spring-boot-starter-security'
    api 'cn.dev33:sa-token-spring-boot4-starter:1.45.0'
    api 'org.bouncycastle:bcprov-jdk18on:1.80'

    // 任务调度
    api 'org.jobrunr:jobrunr-spring-boot-4-starter:8.3.0'

    // 工作流
    api 'org.flowable:flowable-spring-boot-starter:8.0.0'
}

// jOOQ 代码生成配置(见第五章详述)
jooq {
    version = '3.20.0'
    configurations {
        main {
            generationTool {
                generator {
                    database {
                        name = 'org.jooq.meta.extensions.ddl.DDLDatabase'
                        properties {
                            property {
                                key = 'scripts'
                                value = "${rootDir}/db/migration/**/*.sql"
                            }
                            property {
                                key = 'sort'
                                value = 'flyway'
                            }
                        }
                    }
                    generate {
                        records = true
                        deprecated = false
                        relations = true
                        fluentSetters = true
                        javaTimeTypes = true
                    }
                    target {
                        packageName = 'com.example.myapp.infra.jooq.generated'
                        directory = 'build/generated-src/jooq/main'
                    }
                }
            }
        }
    }
}
```

**infra 用 `api` 而非 `implementation`**:基础设施模块的依赖需要传递给业务模块使用。

### 3.11 common/build.gradle

```groovy
// common/build.gradle
dependencies {
    // 只依赖 JDK + 最小必要库
    api 'org.slf4j:slf4j-api'
    api 'cn.hutool:hutool-core:5.8.44'
    api 'jakarta.validation:jakarta.validation-api:3.1.0'
}
```

**common 保持极简**:不引入 Spring,不引入数据库,只是纯粹的工具与领域原语。

### 3.12 gradle.properties

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.daemon=true

# 关闭 Spring Boot 开发工具的自动重启(CI 环境)
spring.devtools.restart.enabled=false
```

### 3.13 gradle-wrapper.properties

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

**Gradle 9.4.1**:2026-03-19 发布的最新稳定版。

### 3.14 .gitignore

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# IDE
.idea/
*.iml
*.iws
.vscode/

# jOOQ 生成的代码(不纳入版本控制,每次构建重新生成)
infra/build/generated-src/jooq/

# Spring Boot
*.log
logs/
/data/

# OS
.DS_Store
Thumbs.db

# 本地配置
application-local.yml
application-local.properties
```

**jOOQ 生成代码不入版**:每次 `./gradlew generateJooq` 从 `db/migration/` SQL 重新生成,保证 schema 与代码完全一致。

### 3.15 Spring Modulith 模块声明

**启用模块边界强制**:在 `app/src/test/java/com/myorg/myapp/ModularityTest.java`

```java
package com.example.myapp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(Application.class);

    @Test
    void verifyModularity() {
        modules.verify();  // 违反模块边界时测试失败
    }

    @Test
    void createModuleDocumentation() {
        new Documenter(modules)
            .writeDocumentation()
            .writeIndividualModulesAsPlantUml();
    }
}
```

**每个业务模块的 `package-info.java`**:

```java
// user/src/main/java/com/myorg/myapp/user/package-info.java
@org.springframework.modulith.ApplicationModule(
    displayName = "用户模块",
    allowedDependencies = {"common", "infra"}
)
package com.example.myapp.user;
```

**作用**:

- `ModularityTest.verifyModularity()` 在 CI 中检测模块间违规依赖
- 业务模块之间**必须**通过 Spring 事件通信,不得直接 `@Autowired`
- 生成 PlantUML 模块关系图,随 `./gradlew test` 输出

### 3.16 Spring Boot 主类

```java
// app/src/main/java/com/myorg/myapp/Application.java
package com.example.myapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = "com.example.myapp"
)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

**注意**:

- `scanBasePackages = "com.example.myapp"`:扫描所有子模块
- 不放 `@EnableJpaRepositories` 等注解(本文档用 jOOQ,非 JPA)
- 不放 `@ComponentScan` 重复定义,`scanBasePackages` 已覆盖

### 3.17 application.yml(启动配置)

```yaml
# app/src/main/resources/application.yml
spring:
  application:
    name: myapp

  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

  # Virtual Threads 启用(Java 21+)
  threads:
    virtual:
      enabled: true

  # 数据源
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:6432/myapp}   # 默认走 PgBouncer
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  # jOOQ
  jooq:
    sql-dialect: POSTGRES

  # Flyway
  flyway:
    enabled: true
    locations: classpath:db/migration,filesystem:./db/migration
    baseline-on-migrate: true

  # Redis
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2

  # Jackson
  jackson:
    time-zone: Asia/Shanghai
    date-format: yyyy-MM-dd HH:mm:ss
    serialization:
      write-dates-as-timestamps: false
      fail-on-empty-beans: false
    deserialization:
      fail-on-unknown-properties: false

# 服务器
server:
  port: ${SERVER_PORT:8080}
  compression:
    enabled: true
  tomcat:
    threads:
      max: 200
    accept-count: 100

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: ${spring.application.name}

# 日志
logging:
  level:
    root: INFO
    com.example.myapp: DEBUG
    org.jooq.Constants: WARN

# Sa-Token
sa-token:
  token-name: Authorization
  timeout: 2592000        # 30 天
  active-timeout: -1
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-log: true
  is-print: false

# JobRunr
jobrunr:
  background-job-server:
    enabled: true
    worker-count: 10
  dashboard:
    enabled: true
    port: 8000

# Flowable
flowable:
  async-executor-activate: true
  database-schema-update: false        # 生产默认关闭,dev profile 覆盖为 true
  check-process-definitions: false
```

### 3.18 多环境配置

**文件组织**:

```
application.yml               # 基础配置(所有环境共享)
application-local.yml         # 本地开发(不入版)
application-dev.yml           # 开发环境
application-staging.yml       # 预发环境
application-prod.yml          # 生产环境
```

**原则**:

- 敏感信息(密码、密钥)**不写配置文件**,用环境变量
- 默认值写在 `application.yml`,各环境 override
- `application-local.yml` 加入 `.gitignore`
- 生产环境密码来源:K8s Secret / Vault / 阿里云 KMS / 腾讯云 SSM

---

## 四、Web 层与路由

### 4.1 Controller 原则

- Controller **只做协议转换**:解析 request、校验入参、调用 Service、组装 response
- **零业务逻辑**:任何 `if` 条件涉及业务规则都应下沉到 Service
- **返回 DTO 而非 Domain**:避免序列化时暴露内部字段
- **构造函数注入**:不用 `@Autowired`、不用字段注入
- **Virtual Threads 默认阻塞**:Java 25 下 Tomcat handler 跑在虚拟线程,正常写阻塞代码即可

### 4.2 Controller 示例

```java
package com.example.myapp.user.api;

import com.example.myapp.common.result.PageResult;
import com.example.myapp.user.domain.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        return UserDto.from(userService.create(req));
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable UUID id) {
        return UserDto.from(userService.findById(id));
    }

    @GetMapping
    public PageResult<UserDto> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return userService.list(page, size, keyword).map(UserDto::from);
    }

    @PutMapping("/{id}")
    public UserDto update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest req) {
        return UserDto.from(userService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        userService.delete(id);
    }
}
```

**关键点**:

- 构造函数注入,**单构造函数时 `@Autowired` 可省略**(Spring 4.3+ 默认)
- `@Valid` + Jakarta Validation:自动触发 DTO 校验,失败抛 `MethodArgumentNotValidException`(由第十四章统一异常处理器接管)
- 返回 DTO 而非 Domain,静态工厂方法 `UserDto.from(...)` 转换
- `@ResponseStatus(CREATED/NO_CONTENT)`:显式声明非 200 状态码

### 4.3 DTO 定义(Java record 优先)

```java
// user/api/CreateUserRequest.java
package com.example.myapp.user.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank
        @Email(message = "邮箱格式错误")
        String email,

        @NotBlank
        @Size(min = 2, max = 50, message = "姓名长度 2-50")
        String name,

        @NotBlank
        @Size(min = 8, max = 72, message = "密码长度 8-72")
        String password
) {}
```

```java
// user/api/UpdateUserRequest.java
package com.example.myapp.user.api;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 2, max = 50)
        String name,
        String avatar
) {}
```

```java
package com.example.myapp.user.api;

import com.example.myapp.user.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String name,
        String avatar,
        Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.id(),
                user.email(),
                user.name(),
                user.avatar(),
                user.createdAt()
        );
    }
}
```

**`record` 的约束**:

- JDK 原生,无字节码增强,编译器和 IDE 天然支持
- 自带 `equals` / `hashCode` / `toString` / accessor,不会被误改成 mutable
- DTO 本就 immutable,`record` 强制这个约束
- 与 Jackson、Spring、Validation 原生支持
- 需要 mutable Builder(复杂查询条件组装、旧系统接入)时用 Lombok `@Builder`

**校验注解直接写在 record 组件上**。

### 4.4 Jackson 配置

Spring Boot 默认用 Jackson,通过 `application.yml` 配置:

```yaml
spring:
  jackson:
    time-zone: Asia/Shanghai
    date-format: yyyy-MM-dd HH:mm:ss
    property-naming-strategy: SNAKE_CASE   # 可选:线上 snake_case,Java 端 camelCase
    serialization:
      write-dates-as-timestamps: false
      fail-on-empty-beans: false
      indent-output: false
    deserialization:
      fail-on-unknown-properties: false
      accept-single-value-as-array: true
    default-property-inclusion: non_null    # 不输出 null 字段
```

**需要更细粒度控制时,定义自己的 `ObjectMapper` Bean**:

```java
package com.example.myapp.infra.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .modules(new JavaTimeModule(), new ParameterNamesModule())
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
                );
    }
}
```

**推荐用 `Jackson2ObjectMapperBuilderCustomizer` 而非直接覆盖 `ObjectMapper` Bean**:前者是增量配置,后者会覆盖 Spring
Boot 自动装配的所有行为。

### 4.5 统一 API 响应

**对外所有接口统一用 `R<T>` 包装**,方便前端处理:

```java
package com.example.myapp.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record R<T>(
        int code,
        String message,
        T data,
        String traceId
) {
    public static <T> R<T> ok(T data) {
        return new R<>(0, "success", data, currentTraceId());
    }

    public static <T> R<T> ok() {
        return new R<>(0, "success", null, currentTraceId());
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null, currentTraceId());
    }

    public static <T> R<T> fail(AppCode code) {
        return new R<>(code.getCode(), code.getMessage(), null, currentTraceId());
    }

    public static <T> R<T> fail(AppCode code, Object... args) {
        return new R<>(code.getCode(), code.format(args), null, currentTraceId());
    }

    /** 从 MDC 读当前链路 ID(由 Micrometer Tracing / MdcFilter 注入) */
    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "";
    }
}
```

**字段职责**:

| 字段        | 含义                                   |
|-----------|--------------------------------------|
| `code`    | 业务码,0 表示成功,非 0 为业务失败码(定义见 `AppCode`) |
| `message` | 业务消息,面向用户展示,失败时带原因                   |
| `data`    | 业务数据,成功时返回载荷,失败时为 null               |
| `traceId` | 链路 ID,用户反馈问题时客服 / 运维凭此一键定位完整请求链路     |

**成功响应示例**:

```json
{
    "code": 0,
    "message": "success",
    "data": { "id": "550e8400-...", "email": "a@x.com", "name": "Alice" },
    "traceId": "8f3a2c1b9d4e5f6a"
}
```

**失败响应示例**:

```json
{
    "code": 20001,
    "message": "用户不存在: 550e8400-...",
    "data": null,
    "traceId": "8f3a2c1b9d4e5f6a"
}
```

**自动包装**:用 `ResponseBodyAdvice` 自动把 Controller 返回值套上 `R<T>`,避免每个方法都写 `R.ok(...)`

```java
package com.example.myapp.common.result;

import com.example.myapp.common.annotation.RawResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.http.converter.HttpMessageConverter;

@RestControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // 排除已经是 R<T> 的、已经标注 @RawResponse 的
        return !R.class.isAssignableFrom(returnType.getParameterType())
                && !returnType.hasMethodAnnotation(RawResponse.class);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof String s) {
            // String 返回值特殊处理:Jackson 不会自动序列化 String
            return R.ok(s).toString();
        }
        return R.ok(body);
    }
}
```

```java
package com.example.myapp.common.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RawResponse {
}
```

**`@RawResponse` 的作用**:某些特殊接口(如下载、SSE、WebHook 回调)不需要 `R<T>` 包装,直接返回原始数据。

### 4.6 API Versioning(Spring Boot 4 一等支持)

**Spring Boot 4 新增 API 版本化**,`application.yml` 开启:

```yaml
spring:
  mvc:
    apiversion:
      use:
        path-segment: 0        # 启用路径段版本(/api/v1 vs /api/v2)
      required: true
      default-version: 1
```

```java
// user/api/UserControllerV1.java
@RestController
@RequestMapping(path = "/api/users", version = "1")   // 等价于 /api/v1/users
public class UserControllerV1 {
    // v1 实现
}
```

```java
// user/api/UserControllerV2.java
@RestController
@RequestMapping(path = "/api/users", version = "2")
public class UserControllerV2 {
    // v2 实现(breaking change 场景)
}
```

**版本化的两种主流实现**:

1. **路径版本**:`/api/v1/users`、`/api/v2/users`(推荐,直观、缓存友好)
2. **Header 版本**:`X-API-Version: 1`(在单路径下细粒度控制)

本文档推荐路径版本,简单可控。

### 4.7 参数校验

使用 Jakarta Validation 3.1:

```java
// order/api/CreateOrderRequest.java
public record CreateOrderRequest(
        @NotNull
        UUID userId,

        @NotEmpty(message = "订单项不能为空")
        @Size(max = 100, message = "订单项不超过 100")
        List<@Valid OrderItemRequest> items,

        @NotNull
        @DecimalMin(value = "0.01", message = "金额必须 > 0")
        BigDecimal totalAmount,

        @Size(max = 500)
        String remark
) {}
```

```java
// order/api/OrderItemRequest.java
public record OrderItemRequest(
        @NotNull UUID productId,
        @Min(1) @Max(999) int quantity,
        @NotNull @DecimalMin("0.01") BigDecimal price
) {}
```

**嵌套校验用 `@Valid`**:`List<@Valid OrderItemRequest>` 会对列表每个元素执行校验。

**自定义校验注解**:

```java
package com.example.myapp.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Phone {
    String message() default "手机号格式错误";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

```java
package com.example.myapp.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class PhoneValidator implements ConstraintValidator<Phone, String> {

    private static final Pattern CN_PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;  // null 由 @NotBlank 管
        return CN_PHONE.matcher(value).matches();
    }
}
```

**使用**:

```java
public record RegisterRequest(
        @NotBlank @Phone String phone,
        @NotBlank @Size(min = 6, max = 6) String code
) {}
```

### 4.8 全局异常处理

完整的异常处理见第十四章,Controller 层基本只抛异常:

```java
@GetMapping("/{id}")
public UserDto get(@PathVariable UUID id) {
    User user = userService.findById(id);
    if (user == null) {
        throw new AppException(AppCode.USER_NOT_FOUND, id);
    }
    return UserDto.from(user);
}
```

`AppException` 由 `@RestControllerAdvice` 统一捕获并返回标准 `R<T>` 错误响应。

### 4.9 HTTP Interface Client(Spring Boot 4 增强)

**Spring Boot 4 为 HTTP Interface Client 提供自动配置**,告别手写 `RestTemplate`/`WebClient`:

```java
package com.example.myapp.payment.provider.wechat;

import org.springframework.web.bind.annotation.GetExchange;
import org.springframework.web.bind.annotation.PostExchange;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "https://api.weixin.qq.com", accept = "application/json")
public interface WechatApiClient {

    @GetExchange("/cgi-bin/token")
    AccessTokenResponse getAccessToken(
            @RequestParam("grant_type") String grantType,
            @RequestParam("appid") String appid,
            @RequestParam("secret") String secret
    );

    @PostExchange("/cgi-bin/message/template/send")
    SendResponse sendTemplateMessage(@RequestBody TemplateMessageRequest req);
}
```

**Spring Boot 4 自动装配**(只需声明 Bean 注解):

```java
package com.example.myapp.payment.provider.wechat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfig {

    @Bean
    public WechatApiClient wechatApiClient(org.springframework.web.client.RestClient.Builder builder) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(builder.build()))
                .build()
                .createClient(WechatApiClient.class);
    }
}
```

Spring Boot 4 进一步简化,可以用 `@ImportHttpServices` 批量注册:

```java
@Configuration
@ImportHttpServices({WechatApiClient.class, AlipayApiClient.class})
public class HttpClientConfig {
}
```

**优点**:

- 声明式接口,像 Feign 但无需额外依赖
- 类型安全,编译期检查 URL 和参数
- 与 Spring Boot 自动装配深度集成

### 4.10 跨域 CORS 配置

```java
package com.example.myapp.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(List.of("https://*.example.com", "http://localhost:*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setExposedHeaders(List.of("Authorization", "X-Total-Count"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

**注意**:生产环境不要用 `setAllowedOrigins(List.of("*"))` + `setAllowCredentials(true)` 组合,浏览器会拒绝请求。

### 4.11 分页与排序

**统一分页请求和响应**:

```java
package com.example.myapp.common.result;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageQuery(
        @Min(1) int page,
        @Min(1) @Max(200) int size,
        String sort,       // "createdAt,desc"
        String keyword
) {
    public PageQuery {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
    }

    public int offset() {
        return (page - 1) * size;
    }
}
```

```java
package com.example.myapp.common.result;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(
        List<T> items,
        long total,
        int page,
        int size
) {
    public int totalPages() {
        return (int) Math.ceil((double) total / size);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(
                items.stream().map(mapper).toList(),
                total, page, size
        );
    }
}
```

**Controller 接收**:

```java
@GetMapping
public PageResult<UserDto> list(@Valid PageQuery query) {
    return userService.list(query).map(UserDto::from);
}
```

### 4.12 虚拟线程与阻塞 I/O

**Spring Boot 4 + Java 25 的组合**:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

启用后:

- Tomcat 请求处理线程池自动替换为 Virtual Threads
- `@Async` 任务自动使用 Virtual Threads
- `@Scheduled` 任务自动使用 Virtual Threads
- 业务代码**照常写阻塞 I/O**(JDBC、HTTP、文件),不需要 reactive

**不要做的事**:

- 不要用 `synchronized` 包住 I/O 操作(会 pin 虚拟线程到平台线程)
    - 改用 `java.util.concurrent.locks.ReentrantLock`
- 不要在虚拟线程里跑 CPU 密集型任务(没有好处,有害处)
    - CPU 密集改用 `ForkJoinPool` 或显式 `Executors.newFixedThreadPool`

**Synchronized pinning 问题诊断**(Java 21-23 适用;Java 24+ 已通过 JEP 491 消除 synchronized 块内的 pinning):

```bash
# 启动时加 JVM flag
-Djdk.tracePinnedThreads=short
```

日志会打印哪一行 synchronized 导致了 pin。

### 4.13 数据脱敏

**场景**:身份证号、手机号、银行卡号、姓名等敏感信息,在 API 响应、日志输出时**必须脱敏**。

**分层设计**:

| 层             | 归属             | 职责                                         |
|---------------|----------------|--------------------------------------------|
| 注解定义          | `common/mask/` | `@Mask` / `@MaskPhone` / `@MaskIdCard` 等注解 |
| 脱敏策略          | `common/mask/` | `MaskStrategy` 枚举 + 脱敏函数                   |
| Jackson 序列化集成 | `infra/mask/`  | `MaskSerializer` + Jackson Module 注册       |

#### 注解定义(`common/mask/`)

```java
package com.example.myapp.common.mask;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.example.myapp.infra.mask.MaskSerializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 通用脱敏注解,指定脱敏策略 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@JacksonAnnotationsInside
@JsonSerialize(using = MaskSerializer.class)
public @interface Mask {
    MaskStrategy value();

    /** 自定义保留头部字符数(覆盖策略默认)*/
    int keepHead() default -1;

    /** 自定义保留尾部字符数(覆盖策略默认)*/
    int keepTail() default -1;
}
```

```java
package com.example.myapp.common.mask;

public enum MaskStrategy {

    /** 手机号:138****1234 */
    PHONE(3, 4),

    /** 身份证号:110101********1234 */
    ID_CARD(6, 4),

    /** 中文姓名:张**(2 字)/ 张**三(3+ 字)*/
    CHINESE_NAME(1, 0),

    /** 银行卡号:622888****8888 */
    BANK_CARD(4, 4),

    /** 邮箱:a***@example.com */
    EMAIL(1, 0),

    /** 地址:xx 省 xx 市**** */
    ADDRESS(6, 0),

    /** 密码等完全屏蔽 */
    FULL(0, 0),

    /** 自定义:由 @Mask 的 keepHead/keepTail 决定 */
    CUSTOM(-1, -1);

    public final int keepHead;
    public final int keepTail;

    MaskStrategy(int keepHead, int keepTail) {
        this.keepHead = keepHead;
        this.keepTail = keepTail;
    }

    public String apply(String raw, int keepHeadOverride, int keepTailOverride) {
        if (raw == null || raw.isEmpty()) return raw;

        int head = keepHeadOverride >= 0 ? keepHeadOverride : keepHead;
        int tail = keepTailOverride >= 0 ? keepTailOverride : keepTail;

        // EMAIL 特殊:只脱敏 @ 前部分
        if (this == EMAIL) {
            int at = raw.indexOf('@');
            if (at <= 0) return raw;
            String local = raw.substring(0, at);
            if (local.length() <= head) return raw;
            return local.charAt(0) + "***" + raw.substring(at);
        }

        int len = raw.length();
        if (head + tail >= len) return raw;

        StringBuilder sb = new StringBuilder(len);
        sb.append(raw, 0, head);
        sb.append("*".repeat(len - head - tail));
        sb.append(raw, len - tail, len);
        return sb.toString();
    }
}
```

#### 便捷复合注解(可选)

```java
package com.example.myapp.common.mask;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@JacksonAnnotationsInside
@Mask(MaskStrategy.PHONE)
public @interface MaskPhone {}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@JacksonAnnotationsInside
@Mask(MaskStrategy.ID_CARD)
public @interface MaskIdCard {}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@JacksonAnnotationsInside
@Mask(MaskStrategy.CHINESE_NAME)
public @interface MaskChineseName {}
```

#### Jackson Serializer(`infra/mask/`)

```java
package com.example.myapp.infra.mask;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.example.myapp.common.mask.Mask;
import com.example.myapp.common.mask.MaskStrategy;

import java.io.IOException;

public class MaskSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private final MaskStrategy strategy;
    private final int keepHead;
    private final int keepTail;

    public MaskSerializer() { this(MaskStrategy.FULL, -1, -1); }

    public MaskSerializer(MaskStrategy strategy, int keepHead, int keepTail) {
        this.strategy = strategy;
        this.keepHead = keepHead;
        this.keepTail = keepTail;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider sp) throws IOException {
        gen.writeString(strategy.apply(value, keepHead, keepTail));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider sp, BeanProperty property) {
        if (property == null) return this;
        Mask mask = property.getAnnotation(Mask.class);
        if (mask == null) mask = property.getContextAnnotation(Mask.class);
        if (mask == null) return this;
        return new MaskSerializer(mask.value(), mask.keepHead(), mask.keepTail());
    }
}
```

#### 使用示例

```java
// DTO 定义
public record UserDto(
        UUID id,
        String name,                               // 不脱敏
        @MaskPhone String phone,                   // 138****1234
        @MaskIdCard String idCard,                 // 110101********1234
        @Mask(MaskStrategy.EMAIL) String email,    // a***@example.com
        @Mask(value = MaskStrategy.CUSTOM, keepHead = 2, keepTail = 2)
        String bankCard                            // 62****88
) {}
```

#### 跳过脱敏的场景

**场景 1:内部管理后台查看明文**——通过角色判断,在 Controller 层返回不同的 DTO(`UserDto` vs `UserDetailDto`
,后者无脱敏注解)。

**场景 2:导出 Excel 要明文**——定义导出专用的 DTO,不加脱敏注解。**禁止**在同一个 DTO 上用条件脱敏,会让序列化逻辑极其复杂。

**原则**:**脱敏是 DTO 层的设计决策,不是运行时开关**。不同场景定义不同 DTO。

### 4.14 WebSocket + Token 鉴权 + 分布式会话同步

**场景**:实时推送(消息通知、在线状态、协作文档、IoT 设备数据流)。

#### 依赖

```groovy
// infra/build.gradle
api 'org.springframework.boot:spring-boot-starter-websocket'
```

#### 配置(`infra/websocket/`)

```java
package com.example.myapp.infra.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketAuthHandshakeInterceptor authInterceptor;
    private final BusinessWebSocketHandler businessHandler;

    public WebSocketConfig(WebSocketAuthHandshakeInterceptor authInterceptor,
                           BusinessWebSocketHandler businessHandler) {
        this.authInterceptor = authInterceptor;
        this.businessHandler = businessHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(businessHandler, "/ws")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
```

#### Token 鉴权拦截器(Sa-Token 集成)

```java
package com.example.myapp.infra.websocket;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                    ServerHttpResponse response,
                                    WebSocketHandler wsHandler,
                                    Map<String, Object> attributes) {
        // 从 query 或 header 取 token(WebSocket 握手阶段)
        String token = resolveToken(request);
        if (token == null) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        // Sa-Token 校验
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put("userId", loginId.toString());
            attributes.put("tenantId", resolveTenantId(loginId));
            return true;
        } catch (Exception e) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    private String resolveToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletReq) {
            String token = servletReq.getServletRequest().getParameter("token");
            if (token != null) return token;
            return servletReq.getServletRequest().getHeader("Authorization");
        }
        return null;
    }

    private String resolveTenantId(Object userId) {
        // 从用户信息查租户
        return "default";
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }
}
```

#### 分布式会话同步(Redis Pub/Sub)

**核心问题**:用户通过 Nginx 负载均衡连到 pod A,但消息在 pod B 产生——pod B 没有该用户的 WebSocket session。

**解决**:Redis Pub/Sub 广播到所有 pod。

```java
package com.example.myapp.infra.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BusinessWebSocketHandler extends TextWebSocketHandler {

    /** 本 pod 的 session 池,key = userId */
    private final Map<String, WebSocketSession> localSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        localSessions.put(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        localSessions.remove(userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 处理客户端上行消息(如果需要)
    }

    /** 本 pod 内直接推送 */
    public boolean pushLocal(String userId, String payload) throws Exception {
        WebSocketSession session = localSessions.get(userId);
        if (session == null || !session.isOpen()) return false;
        session.sendMessage(new TextMessage(payload));
        return true;
    }
}
```

```java
package com.example.myapp.infra.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 跨 pod 推送入口:本地推不到 → 走 Redis 广播 */
@Component
public class WebSocketBroker implements MessageListener {

    private static final String CHANNEL = "ws:push";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final BusinessWebSocketHandler handler;

    public WebSocketBroker(StringRedisTemplate redis, ObjectMapper mapper,
                           BusinessWebSocketHandler handler) {
        this.redis = redis;
        this.mapper = mapper;
        this.handler = handler;
    }

    /** 业务调用入口:向指定用户推消息 */
    public void pushToUser(String userId, String payload) throws Exception {
        // 本地直接有该 session → 直接推
        if (handler.pushLocal(userId, payload)) return;
        // 本地没有 → 广播,让其他 pod 处理
        String msg = mapper.writeValueAsString(Map.of(
                "userId", userId, "payload", payload));
        redis.convertAndSend(CHANNEL, msg);
    }

    /** Redis 订阅回调:每个 pod 都会收到广播 */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Map<String, String> envelope = mapper.readValue(message.getBody(), Map.class);
            handler.pushLocal(envelope.get("userId"), envelope.get("payload"));
        } catch (IOException e) {
            // log
        }
    }
}
```

```java
// WebSocketBroker 注册为 Redis 订阅者
@Configuration
public class WebSocketRedisConfig {

    @Bean
    public RedisMessageListenerContainer wsListenerContainer(
            RedisConnectionFactory factory,
            WebSocketBroker broker) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(broker, new ChannelTopic("ws:push"));
        return container;
    }
}
```

#### 业务调用

```java
// 任意业务模块
@Service
public class NotificationService {

    private final WebSocketBroker wsBroker;

    public void notifyUser(String userId, Notification n) throws Exception {
        wsBroker.pushToUser(userId, mapper.writeValueAsString(n));
    }
}
```

### 4.15 SSE(Server-Sent Events)推送

**SSE vs WebSocket 选择**:

| 特性   | SSE                                  | WebSocket      |
|------|--------------------------------------|----------------|
| 协议   | HTTP 长连接                             | 独立协议           |
| 方向   | 单向(服务器 → 客户端)                        | 双向             |
| 断线重连 | 浏览器自动                                | 需自己实现          |
| 代理兼容 | 好(标准 HTTP)                           | 一般(某些代理不支持)    |
| 鉴权   | 同 HTTP,Cookie / Authorization header | 握手阶段鉴权         |
| 场景   | 消息通知、进度推送、AI 流式输出                    | 双向互动(聊天、协作、游戏) |

**不需要双向时优先 SSE**。代理友好、实现简单、自动重连。

#### Controller

```java
package com.example.myapp.notification.api;

import com.example.myapp.common.annotation.RawResponse;
import com.example.myapp.infra.sse.SseBroker;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SseController {

    private final SseBroker sseBroker;

    public SseController(SseBroker sseBroker) {
        this.sseBroker = sseBroker;
    }

    @RawResponse
    @GetMapping(value = "/sse/subscribe/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String userId) {
        // Sa-Token 在 SecurityFilterChain 已校验 token,这里假定已登录
        // 若要求鉴权精细到"只能订阅自己的 userId",这里比较 StpUtil.getLoginId()
        return sseBroker.subscribe(userId);
    }
}
```

#### SseBroker(`infra/sse/`)

```java
package com.example.myapp.infra.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseBroker implements MessageListener {

    private static final String CHANNEL = "sse:push";
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;    // 30 min

    /** 本 pod 的 emitter 池 */
    private final Map<String, SseEmitter> localEmitters = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public SseBroker(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        localEmitters.put(userId, emitter);

        emitter.onCompletion(() -> localEmitters.remove(userId));
        emitter.onTimeout(() -> localEmitters.remove(userId));
        emitter.onError(ex -> localEmitters.remove(userId));

        try {
            // 初始心跳
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            localEmitters.remove(userId);
        }
        return emitter;
    }

    /** 业务调用入口 */
    public void pushToUser(String userId, String eventName, Object payload) throws Exception {
        if (pushLocal(userId, eventName, payload)) return;
        // 本地没有该 emitter → 广播
        String msg = mapper.writeValueAsString(Map.of(
                "userId", userId,
                "event", eventName,
                "payload", mapper.writeValueAsString(payload)));
        redis.convertAndSend(CHANNEL, msg);
    }

    public boolean pushLocal(String userId, String eventName, Object payload) {
        SseEmitter emitter = localEmitters.get(userId);
        if (emitter == null) return false;
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
            return true;
        } catch (Exception e) {
            localEmitters.remove(userId);
            return false;
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Map<String, String> envelope = mapper.readValue(message.getBody(), Map.class);
            Object payload = mapper.readValue(envelope.get("payload"), Object.class);
            pushLocal(envelope.get("userId"), envelope.get("event"), payload);
        } catch (Exception e) {
            // log
        }
    }

    /** 心跳保活:向所有本地 emitter 发 SSE 注释行 `:heartbeat\n\n`,保持连接不被代理断开 */
    public void broadcastHeartbeat() {
        localEmitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                localEmitters.remove(userId);
            }
        });
    }
}
```

#### 业务调用

```java
@Service
public class OrderNotifyService {

    private final SseBroker sseBroker;

    public void notifyOrderPaid(String userId, Order order) throws Exception {
        sseBroker.pushToUser(userId, "order-paid", Map.of(
                "orderId", order.id(),
                "amount", order.totalAmount()
        ));
    }
}
```

#### 心跳保活

SSE 长连接在某些代理下 30 秒无数据会被断开。定时推心跳:

```java
@Component
public class SseHeartbeatJob {

    private final SseBroker broker;

    @Recurring(id = "sse-heartbeat", interval = "PT25S")
    @Job(name = "SSE 心跳保活")
    public void heartbeat() {
        broker.broadcastHeartbeat();    // 给所有 emitter 发 comment(`:heartbeat\n\n`)
    }
}
```

---

## 五、数据库与 ORM(jOOQ Database First)

### 5.1 Database First 工作流

**核心原则**:**数据库 schema 是系统的真实模型**,Java 代码从 schema 生成。

三步节奏:

1. 先写 SQL 迁移脚本(Flyway,见 5.2)
2. 运行代码生成器,从 SQL 脚本生成 jOOQ 类(见 5.3)
3. Java 代码调用生成的类型安全 DSL 写查询(见 5.6)

```java
// 典型查询形态
List<UsersRecord> activeUsers = dsl
    .selectFrom(USERS)
    .where(USERS.STATUS.eq("ACTIVE"))
    .orderBy(USERS.CREATED_AT.desc())
    .limit(20).offset(0)
    .fetch();
```

**落地约束**:

- Java DSL 与 SQL 一对一映射,列名 / 类型在编译期检查
- PostgreSQL 高级特性原生可用:window function、CTE、`RETURNING`、`ON CONFLICT`(UPSERT)、JSONB 操作符
- 生成的 SQL 完全可见可审计,不存在 ORM 的 lazy loading / proxy / session flush 行为

### 5.2 Schema 优先:Flyway 迁移脚本

所有 schema 变更由 Flyway SQL 脚本驱动,放在 `db/migration/`:

```sql
-- db/migration/V1__init_user_schema.sql

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 用户表
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT uuidv7(),
    tenant_id     VARCHAR(32)  NOT NULL DEFAULT 'default',
    email         VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    avatar        VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BANNED'))
);

CREATE INDEX idx_users_tenant ON users (tenant_id);
CREATE INDEX idx_users_created_at ON users (created_at DESC);

COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.tenant_id IS '租户标识,多租户场景';
COMMENT ON COLUMN users.status IS 'ACTIVE/INACTIVE/BANNED';
```

**脚本命名规范**(Flyway 标准):

- `V1__description.sql`:版本化迁移,执行一次
- `V1.1__description.sql`:小版本迁移
- `R__create_view.sql`:可重复迁移(内容变更重新执行,用于视图、函数、存储过程)
- `U1__undo.sql`:回滚脚本(Flyway Teams 版本功能)

**原则**:

- **每次 schema 变更都写一个新文件**,不修改已执行过的脚本
- 使用 `uuidv7()` 作主键(PostgreSQL 18 原生)
- 所有表带 `tenant_id`,默认值 `'default'`,为多租户预留
- 所有表带 `created_at` / `updated_at` 时间戳
- 所有约束命名(`uk_`/`fk_`/`ck_`/`idx_` 前缀),便于后续引用
- 加 `COMMENT ON` 注释,后续 jOOQ 生成代码可保留

### 5.3 jOOQ 代码生成配置

**策略选择**:从 Flyway SQL 脚本直接生成,**无需实际数据库连接**。

在 `infra/build.gradle` 里已经配置(见 3.7),核心是用 `DDLDatabase`:

```groovy
jooq {
    version = '3.20.0'
    configurations {
        main {
            generationTool {
                generator {
                    database {
                        name = 'org.jooq.meta.extensions.ddl.DDLDatabase'
                        properties {
                            property {
                                key = 'scripts'
                                value = "${rootDir}/db/migration/**/*.sql"
                            }
                            property {
                                key = 'sort'
                                value = 'flyway'
                            }
                            property {
                                key = 'defaultNameCase'
                                value = 'lower'
                            }
                        }
                    }
                    generate {
                        records = true           // 生成 Record 类(支持 update/delete 操作)
                        deprecated = false       // 不生成废弃 API
                        relations = true         // 生成外键关系
                        fluentSetters = true     // 链式 setter
                        javaTimeTypes = true     // java.time 而非 java.sql.Date
                        pojos = false            // 不生成 POJO(用 record 替代)
                        daos = false             // 不生成 DAO(业务层自己封装)
                    }
                    target {
                        packageName = 'com.example.myapp.infra.jooq.generated'
                        directory = 'build/generated-src/jooq/main'
                    }
                }
            }
        }
    }
}

tasks.named('compileJava') {
    dependsOn 'generateJooq'
}

// 将生成代码加入 source set
sourceSets.main.java.srcDirs += 'build/generated-src/jooq/main'
```

**几个关键决策**:

- `DDLDatabase`:直接解析 SQL 文件,**不需要跑起数据库**
- `sort = flyway`:按 Flyway 版本号排序执行脚本
- `records = true`:生成可变 Record 类,支持 `store()`/`delete()`
- `pojos = false`:我们用 Java `record` 作为 Domain 对象,不需要 jOOQ 生成 POJO
- `daos = false`:Repository 自己写,不用 jOOQ 生成的 DAO(侵入性强)
- **生成目录不入版控**(见 3.11):每次 `./gradlew build` 都重新生成

**高精度类型方案**:若项目大量用 PostgreSQL 专有类型(JSONB、ARRAY、自定义 enum),改用 Testcontainers 启动真实 PostgreSQL 跑一次
Flyway 后再生成,类型信息更完整。见 16.4 `@JooqTest` 章节。

### 5.4 手动触发代码生成

```bash
./gradlew :infra:generateJooq
```

生成后会在 `infra/build/generated-src/jooq/main` 下出现:

```
com/myorg/myapp/infra/jooq/generated/
├── DefaultCatalog.java
├── Public.java                     # public schema
├── Keys.java                        # 所有约束/索引
├── Tables.java                      # 所有表的静态引用
├── Indexes.java
└── tables/
    ├── Users.java                   # USERS 表
    └── records/
        └── UsersRecord.java         # UsersRecord(可变记录)
```

### 5.5 Domain 模型(与 jOOQ Record 解耦)

**核心原则**:jOOQ 生成的 `UsersRecord` 是**数据传输对象**,不是 Domain。

业务层使用自己定义的 Domain `record`:

```java
// user/domain/User.java
package com.example.myapp.user.domain;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String tenantId,
        String email,
        String name,
        String passwordHash,
        UserStatus status,
        String avatar,
        Instant createdAt,
        Instant updatedAt
) {}
```

```java
// user/domain/UserStatus.java
package com.example.myapp.user.domain;

public enum UserStatus {
    ACTIVE, INACTIVE, BANNED
}
```

**jOOQ Record → Domain 转换**:

```java
package com.example.myapp.user.repo;

import com.example.myapp.infra.jooq.generated.tables.records.UsersRecord;
import com.example.myapp.user.domain.User;
import com.example.myapp.user.domain.UserStatus;

public final class UserMapper {

    private UserMapper() {}

    public static User toDomain(UsersRecord r) {
        return new User(
                r.getId(),
                r.getTenantId(),
                r.getEmail(),
                r.getName(),
                r.getPasswordHash(),
                UserStatus.valueOf(r.getStatus()),
                r.getAvatar(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
```

**关键决策**:

| 层         | 类型                | 作用             |
|-----------|-------------------|----------------|
| 数据库表      | `users`           | SQL schema     |
| jOOQ 生成   | `UsersRecord`     | 从表一对一映射出来的可变记录 |
| 业务 Domain | `User`(record)    | 业务逻辑操作的不可变值对象  |
| 对外 DTO    | `UserDto`(record) | API 响应数据结构     |

**Domain 与 jOOQ Record 的边界**:

- `UsersRecord` 字段与数据库列一对一绑定,只在 Repository 层流转
- `UsersRecord` 可变,通过 `store()` / `update()` 执行写入
- 业务层只见 `User` record,数据库概念不泄漏到上层

### 5.6 Repository 层

```java
package com.example.myapp.user.repo;

import com.example.myapp.common.result.PageQuery;
import com.example.myapp.common.result.PageResult;
import com.example.myapp.user.domain.User;
import com.example.myapp.user.domain.UserStatus;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.myapp.infra.jooq.generated.Tables.USERS;

@Repository
public class UserRepository {

    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<User> findById(UUID id) {
        return dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id))
                .fetchOptional()
                .map(UserMapper::toDomain);
    }

    public Optional<User> findByEmail(String tenantId, String email) {
        return dsl.selectFrom(USERS)
                .where(USERS.TENANT_ID.eq(tenantId))
                .and(USERS.EMAIL.eq(email))
                .fetchOptional()
                .map(UserMapper::toDomain);
    }

    public PageResult<User> list(String tenantId, PageQuery query) {
        var baseCondition = USERS.TENANT_ID.eq(tenantId);

        var condition = (query.keyword() == null || query.keyword().isBlank())
                ? baseCondition
                : baseCondition.and(
                        USERS.NAME.likeIgnoreCase("%" + query.keyword() + "%")
                                .or(USERS.EMAIL.likeIgnoreCase("%" + query.keyword() + "%"))
                );

        long total = dsl.selectCount()
                .from(USERS)
                .where(condition)
                .fetchOne(0, long.class);

        List<User> items = dsl.selectFrom(USERS)
                .where(condition)
                .orderBy(USERS.CREATED_AT.desc())
                .limit(query.size())
                .offset(query.offset())
                .fetch()
                .map(UserMapper::toDomain);

        return new PageResult<>(items, total, query.page(), query.size());
    }

    public User create(String tenantId, String email, String name, String passwordHash) {
        var record = dsl.newRecord(USERS);
        record.setTenantId(tenantId);
        record.setEmail(email);
        record.setName(name);
        record.setPasswordHash(passwordHash);
        record.setStatus(UserStatus.ACTIVE.name());
        record.store();     // INSERT
        return UserMapper.toDomain(record);
    }

    public boolean updateName(UUID id, String name) {
        return dsl.update(USERS)
                .set(USERS.NAME, name)
                .set(USERS.UPDATED_AT, Instant.now())
                .where(USERS.ID.eq(id))
                .execute() > 0;
    }

    public boolean updateStatus(UUID id, UserStatus status) {
        return dsl.update(USERS)
                .set(USERS.STATUS, status.name())
                .set(USERS.UPDATED_AT, Instant.now())
                .where(USERS.ID.eq(id))
                .execute() > 0;
    }

    public boolean delete(UUID id) {
        return dsl.deleteFrom(USERS)
                .where(USERS.ID.eq(id))
                .execute() > 0;
    }
}
```

**说明**:

- `DSLContext` 是 jOOQ 的入口,由 `spring-boot-starter-jooq` 自动装配
- `USERS` 是静态导入的表引用,字段名用 SQL 风格大写(`USERS.EMAIL`)
- `fetchOptional()` 返回 `Optional<R>`,`fetch()` 返回 `Result<R>`
- **Repository 返回 Domain**,不返回 `UsersRecord`

### 5.7 事务:Spring `@Transactional`

**推荐做法**:业务方法上标 `@Transactional`,jOOQ 自动感知 Spring 事务(通过 Spring Boot JDBC 集成)。

```java
package com.example.myapp.user.domain;

import com.example.myapp.common.exception.AppException;
import com.example.myapp.common.exception.AppCode;
import com.example.myapp.user.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)     // 类级别默认只读
public class UserService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;

    public UserService(UserRepository userRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(AppCode.USER_NOT_FOUND, id));
    }

    @Transactional    // 方法级 override,可写事务
    public User create(String tenantId, CreateUserRequest req) {
        if (userRepository.findByEmail(tenantId, req.email()).isPresent()) {
            throw new AppException(AppCode.USER_EMAIL_EXISTS, req.email());
        }
        var hash = passwordService.hash(req.password());
        return userRepository.create(tenantId, req.email(), req.name(), hash);
    }

    @Transactional
    public void delete(UUID id) {
        var user = findById(id);
        userRepository.delete(user.id());
    }
}
```

**原则**:

- **Service 层标 `@Transactional`**,Repository 层不标
- 类级别 `@Transactional(readOnly = true)` 默认只读,Mutation 方法 override 为读写
- **不要跨 Service 互相 `@Transactional` 调用**,除非明确知道传播级别(Spring AOP 默认 `REQUIRED`)

### 5.8 批量操作

```java
public int batchInsert(List<User> users) {
    var queries = users.stream()
            .map(u -> dsl.insertInto(USERS)
                    .set(USERS.ID, u.id())
                    .set(USERS.TENANT_ID, u.tenantId())
                    .set(USERS.EMAIL, u.email())
                    .set(USERS.NAME, u.name())
                    .set(USERS.PASSWORD_HASH, u.passwordHash()))
            .toList();

    int[] results = dsl.batch(queries).execute();
    return results.length;
}
```

### 5.9 UPSERT(PostgreSQL ON CONFLICT)

```java
public User upsertByEmail(String tenantId, String email, String name) {
    return dsl.insertInto(USERS)
            .set(USERS.TENANT_ID, tenantId)
            .set(USERS.EMAIL, email)
            .set(USERS.NAME, name)
            .onConflict(USERS.TENANT_ID, USERS.EMAIL)
            .doUpdate()
            .set(USERS.NAME, name)
            .set(USERS.UPDATED_AT, Instant.now())
            .returning()
            .fetchOne()
            .map(UserMapper::toDomain);
}
```

### 5.10 JSONB 支持

PostgreSQL 的 JSONB 列在 Flyway:

```sql
ALTER TABLE users ADD COLUMN preferences JSONB NOT NULL DEFAULT '{}'::jsonb;
CREATE INDEX idx_users_preferences_theme ON users ((preferences->>'theme'));
```

jOOQ 查询:

```java
import static org.jooq.impl.DSL.jsonbGetAttributeAsText;

List<User> darkThemeUsers = dsl.selectFrom(USERS)
        .where(jsonbGetAttributeAsText(USERS.PREFERENCES, "theme").eq("dark"))
        .fetch()
        .map(UserMapper::toDomain);
```

**推荐**:复杂 JSONB 操作用 `DSL.field("preferences->'notifications'->>'email'", SQLDataType.VARCHAR)` 直接写 SQL 片段。

### 5.11 窗口函数与 CTE

jOOQ 对 PostgreSQL 高级 SQL 特性一等支持:

```java
// 窗口函数:每个租户的用户排名
var rank = DSL.rowNumber()
        .over()
        .partitionBy(USERS.TENANT_ID)
        .orderBy(USERS.CREATED_AT.desc())
        .as("rank");

dsl.select(USERS.ID, USERS.EMAIL, rank)
        .from(USERS)
        .fetch();

// CTE:递归查询部门树
var dept = DSL.table("dept");
var cte = DSL.name("tree").as(
        DSL.select().from(DEPARTMENTS).where(DEPARTMENTS.PARENT_ID.isNull())
                .unionAll(
                        DSL.select().from(DEPARTMENTS)
                                .join(dept).on(DEPARTMENTS.PARENT_ID.eq(dept.field("id", UUID.class)))
                )
);

dsl.withRecursive(cte)
        .select().from(cte)
        .fetch();
```

### 5.12 PostgreSQL 18 新特性:AIO 子系统

**PostgreSQL 18 最重磅的性能改进**:全新的异步 I/O(Asynchronous I/O)子系统,顺序扫描、位图堆扫描、VACUUM 等 I/O 密集型操作*
*最多 3 倍读性能提升**。

**启用方式**(`postgresql.conf`):

```ini
# AIO 后端实现,可选 worker / io_uring / sync
io_method = worker                 # 默认,所有平台可用
# io_method = io_uring             # Linux 5.1+,性能更好,需内核支持

# I/O 工作线程数,根据 CPU 核心数调整
io_workers = 3                     # 默认 3,I/O 密集型负载可调到 4-6

# 单次 I/O 批处理数量
io_combine_limit = 128kB           # 默认值
```

**何时收益最大**:

| 场景                         | 预期提升       |
|----------------------------|------------|
| 大表全表扫描(分析查询、导出)            | **2-3×**   |
| 位图索引扫描(`bitmap heap scan`) | **2×**     |
| VACUUM / 统计收集              | **显著减少窗口** |
| 小查询 OLTP                   | 基本无差异      |

**本栈适用场景**:

- `云汇养老`IoT 时序数据聚合查询(TimescaleDB + 大范围扫描)
- 报表导出(`report/` 模块的长查询)
- 夜间 VACUUM(减少维护窗口)

**运维建议**:

- Linux 5.1+ 建议 `io_method = io_uring`,低 CPU 占用、高吞吐
- 监控指标 `pg_stat_io`(PostgreSQL 18 新增)查看 AIO 命中率和 I/O 分布
- 升级前先在预发环境压测,确认负载实际收益

```sql
-- PostgreSQL 18 新增视图,查看 I/O 统计
SELECT backend_type, object, context,
       reads, read_time, writes, write_time
FROM pg_stat_io
WHERE reads + writes > 0
ORDER BY reads + writes DESC;
```

### 5.13 PostgreSQL 18 新特性:虚拟生成列

**PostgreSQL 12 引入生成列**(`GENERATED ALWAYS AS ... STORED`),但必须占用磁盘空间。**PostgreSQL 18 引入 VIRTUAL 选项并设为默认
**:计算在读取时完成,不占存储。

#### 什么时候用虚拟生成列

| 场景                          | 方案                    |
|-----------------------------|-----------------------|
| 字段值是其他字段的函数,**不需要索引**,不高频查询 | ✅ 虚拟列                 |
| 字段值是其他字段的函数,**需要索引或高频查询**   | STORED 列(写时计算)        |
| 业务计算逻辑复杂,字段跨多表              | jOOQ Converter 或应用层计算 |

#### 常见应用场景

**1. 全名 / 显示名**

```sql
CREATE TABLE users (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    first_name   VARCHAR(50) NOT NULL,
    last_name    VARCHAR(50) NOT NULL,
    -- PostgreSQL 18:VIRTUAL 是默认,显式写出以明确意图
    full_name    VARCHAR(101) GENERATED ALWAYS AS (first_name || ' ' || last_name) VIRTUAL
);
```

**2. 金额小计**

```sql
CREATE TABLE order_items (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    order_id    UUID NOT NULL,
    unit_price  NUMERIC(10,2) NOT NULL,
    quantity    INT NOT NULL,
    -- 小计自动计算,无需应用代码维护
    subtotal    NUMERIC(12,2) GENERATED ALWAYS AS (unit_price * quantity) VIRTUAL
);
```

**3. JSONB 字段展开(替代 jOOQ Converter 部分场景)**

```sql
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    payload     JSONB NOT NULL,
    -- 高频查询字段展开为虚拟列,应用层不用再反复提取
    user_id     TEXT GENERATED ALWAYS AS (payload ->> 'userId') VIRTUAL,
    action      TEXT GENERATED ALWAYS AS (payload ->> 'action') VIRTUAL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 虚拟列可以作查询条件(但 VIRTUAL 列不能直接加索引,大表慢)
SELECT * FROM audit_logs WHERE action = 'LOGIN';
```

#### jOOQ 集成

jOOQ 3.20+ 对 PostgreSQL 18 生成列有良好支持:

- 生成的 Java 代码自动标记为 `readonly = true`
- `INSERT` / `UPDATE` 自动跳过生成列
- 业务代码读取字段值正常

```java
// 自动生成的 POJO(伪代码示意)
public class Users {
    private UUID id;
    private String firstName;
    private String lastName;
    @Readonly        // jOOQ 标记,insert/update 忽略
    private String fullName;
}

// 插入时只传可写字段
db.insertInto(USERS)
  .set(USERS.FIRST_NAME, "张")
  .set(USERS.LAST_NAME, "三")
  .execute();

// 读取时虚拟列自动有值
User user = db.selectFrom(USERS).fetchOne().into(User.class);
user.getFullName();    // "张 三"
```

#### 虚拟列 vs STORED 列

|            | VIRTUAL(PG 18 默认) | STORED(PG 12 起) |
|------------|-------------------|-----------------|
| 存储开销       | 无                 | 占磁盘空间           |
| 计算时机       | 读取时               | 写入时             |
| 可索引        | ❌(PG 18 首版)       | ✅               |
| 查询性能       | 每次读都计算            | 一次计算多次读         |
| ALTER 添加成本 | 轻                 | 重(要回填全表)        |

**选型原则**:

- **低频查询的派生字段** → VIRTUAL
- **需要索引 / 高频查询过滤** → STORED
- **需要唯一约束** → STORED(VIRTUAL 列不能加 UNIQUE)

### 5.14 PostgreSQL 18 新特性:Skip Scan for B-tree

**PostgreSQL 18 之前**:多列 B-tree 索引 `(a, b)`,如果查询 `WHERE b = ?`(没用到前缀列 a),索引**完全用不上**,走全表扫。

**PostgreSQL 18 引入 Skip Scan**:即使没有第一列的等值条件,也能用多列索引。planner 自动跳过不同的 `a` 值并逐段用索引查
`b`。

#### 实战收益

**场景 1:字典表的多维度查询**

```sql
CREATE TABLE dict_items (
    id         UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id  TEXT NOT NULL,
    dict_code  VARCHAR(50) NOT NULL,
    item_code  VARCHAR(50) NOT NULL,
    item_name  VARCHAR(200) NOT NULL,
    INDEX idx_tenant_dict_item (tenant_id, dict_code, item_code)
);

-- PG 17-: 这个查询走全表扫,因为没有 tenant_id 等值
SELECT * FROM dict_items
WHERE dict_code = 'ORDER_STATUS' AND item_code = 'PAID';

-- PG 18+: 启用 Skip Scan,索引 idx_tenant_dict_item 能用上
--         planner 跳过所有 tenant_id 的不同值,逐段用索引查 dict_code + item_code
```

**场景 2:多租户共享索引**

```sql
CREATE INDEX idx_orders_tenant_created ON orders (tenant_id, created_at DESC);

-- PG 18 Skip Scan 让以下查询也能用索引
SELECT * FROM orders WHERE created_at > NOW() - INTERVAL '7 days';
```

#### 何时依然需要独立索引

Skip Scan 不是银弹。当**前缀列的 cardinality 极高**(例如 `user_id`,百万级不同值),跳扫代价仍然很高。此时应另建专用索引。

**决策表**:

| 前缀列 cardinality             | 是否加独立索引                  |
|-----------------------------|--------------------------|
| 低(< 100,如 tenant_id,status) | Skip Scan 高效,**不用**加独立索引 |
| 中(100-10000)                | 视查询频率,**高频则加**           |
| 高(> 10000,如 user_id)        | **必须加**独立索引              |

#### 查看是否用了 Skip Scan

```sql
EXPLAIN ANALYZE
SELECT * FROM dict_items
WHERE dict_code = 'ORDER_STATUS' AND item_code = 'PAID';

-- PG 18 会显示:
--   Index Scan using idx_tenant_dict_item on dict_items
--     Index Cond: ((dict_code = 'ORDER_STATUS') AND (item_code = 'PAID'))
--     Skip Scan: tenant_id
```

### 5.15 PostgreSQL 18 新特性:时间范围约束(Temporal Constraints)

**场景**:审批流"同一员工同一时段只能有一个有效合同"、会议室预订"同一时段不能重叠预订"、库存锁定"同一 SKU
同一时段只能有一个锁定"。

**PostgreSQL 17 之前**:这种"时间段不重叠"的唯一约束**无法用 PRIMARY KEY / UNIQUE 表达**,只能:

- 数据库触发器 + 手写 SQL
- 应用层加分布式锁(不可靠)
- EXCLUDE constraint(范围约束的前身,语法复杂)

**PostgreSQL 18** 让 `PRIMARY KEY` / `UNIQUE` / `FOREIGN KEY` **原生支持时间范围**,使用 `WITHOUT OVERLAPS` 子句。

**前置要求**:所有 temporal 约束依赖 GiST 索引,需先启用扩展:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
```

#### 实战:会议室预订

```sql
CREATE TABLE meeting_room_bookings (
    id             UUID DEFAULT uuidv7(),
    room_id        UUID NOT NULL,
    booking_time   TSTZRANGE NOT NULL,         -- [start, end),范围类型
    booked_by      UUID NOT NULL,
    purpose        TEXT,
    created_at     TIMESTAMPTZ DEFAULT NOW(),

    -- PostgreSQL 18 时间范围约束:同一 room_id 的 booking_time 不允许重叠
    -- 等值列在前、WITHOUT OVERLAPS 列在最后,等价于
    -- EXCLUDE USING GIST (room_id WITH =, booking_time WITH &&)
    PRIMARY KEY (room_id, booking_time WITHOUT OVERLAPS)
);

-- 插入不重叠的预订,成功
INSERT INTO meeting_room_bookings (room_id, booking_time, booked_by)
VALUES
    ('00000000-0000-0000-0000-000000000001', '[2026-04-20 09:00, 2026-04-20 10:00)', '00000000-0000-0000-0000-000000000101'),
    ('00000000-0000-0000-0000-000000000001', '[2026-04-20 10:00, 2026-04-20 11:00)', '00000000-0000-0000-0000-000000000102');

-- 插入重叠的预订,PostgreSQL 自动拒绝
INSERT INTO meeting_room_bookings (room_id, booking_time, booked_by)
VALUES ('00000000-0000-0000-0000-000000000001', '[2026-04-20 09:30, 2026-04-20 10:30)', '00000000-0000-0000-0000-000000000103');
-- ERROR: conflicting key value violates exclusion constraint
```

#### 实战:员工合同有效期

```sql
CREATE TABLE employee_contracts (
    id              UUID DEFAULT uuidv7(),
    employee_id     UUID NOT NULL,
    valid_period    DATERANGE NOT NULL,         -- [start, end),日期范围
    contract_type   VARCHAR(50) NOT NULL,
    salary          NUMERIC(10,2),

    -- 同一员工的合同有效期不能重叠
    UNIQUE (employee_id, valid_period WITHOUT OVERLAPS)
);
```

#### 实战:SKU 库存锁定(电商秒杀)

```sql
CREATE TABLE sku_locks (
    id            UUID DEFAULT uuidv7(),
    sku_id        UUID NOT NULL,
    lock_period   TSTZRANGE NOT NULL,          -- 锁定时段
    locked_by     UUID NOT NULL,               -- 订单 ID
    quantity      INT NOT NULL,

    -- 同一 SKU 同一时段只能有一个锁,天然解决并发问题
    PRIMARY KEY (sku_id, lock_period WITHOUT OVERLAPS)
);
```

#### jOOQ 集成

jOOQ 3.20+ 通过 `jooq-postgres-extensions` 支持 PostgreSQL 范围类型:

```groovy
// infra/build.gradle
api 'org.jooq:jooq-postgres-extensions:3.20.0'
```

```java
// 生成的 POJO 字段类型自动映射为 Range<OffsetDateTime>
public record MeetingRoomBooking(
        UUID id,
        UUID roomId,
        Range<OffsetDateTime> bookingTime,     // 范围类型
        UUID bookedBy
) {}

// 插入
db.insertInto(MEETING_ROOM_BOOKINGS)
  .set(MEETING_ROOM_BOOKINGS.ROOM_ID, roomId)
  .set(MEETING_ROOM_BOOKINGS.BOOKING_TIME,
       Range.closedOpen(start, end))
  .execute();

// 查询某时段可用
var conflicts = db.selectFrom(MEETING_ROOM_BOOKINGS)
    .where(MEETING_ROOM_BOOKINGS.ROOM_ID.eq(roomId))
    .and(rangeOverlaps(MEETING_ROOM_BOOKINGS.BOOKING_TIME,
                       Range.closedOpen(wantStart, wantEnd)))
    .fetch();
```

#### 约束 + 异常转换

数据库层的约束冲突在 Java 端会抛 `DataAccessException` → `SQLIntegrityConstraintViolationException`。建议在
`ExceptionHandler` 层统一转业务错误:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public R<Void> handleConstraint(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();
        if (msg != null && msg.contains("exclusion constraint")) {
            // 时间范围重叠
            return R.fail(AppCode.BOOKING_TIME_CONFLICT, "时段已被占用");
        }
        return R.fail(AppCode.SYSTEM_ERROR, "数据冲突");
    }
}
```

#### 决策:什么时候用时间约束

| 场景                         | 方案                |
|----------------------------|-------------------|
| 互斥时段预订(会议室、车位、设备)          | ✅ 时间范围约束          |
| 有效期不重叠(合同、保险、授权)           | ✅ 时间范围约束          |
| 库存锁定(秒杀、配额)                | ✅ 时间范围约束 + 事务     |
| 历史数据 temporal query(审计、版本) | ❌ 用事件溯源 / SCD 表设计 |

**好处**:把**业务不变量表达在数据库层**,即使应用层有 bug、并发请求绕过应用逻辑,数据库也能兜底保证正确性。*
*对金融、医疗、政府项目是强合规项**。

### 5.16 jOOQ 许可证说明

**开源版 jOOQ 支持**:

- PostgreSQL(本文档用的)
- MySQL、MariaDB
- SQLite
- H2(测试)
- CockroachDB

**商业版才支持**:Oracle、SQL Server、DB2、Sybase、Informix 等。

**本文档默认 PostgreSQL,使用开源版**。如果项目需要 Oracle,需要采购 jOOQ Professional 许可证,或降级使用 MyBatis。

### 5.17 HikariCP 连接池调优

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://pgbouncer:6432/myapp}    # 连 PgBouncer,不直连 PostgreSQL
    hikari:
      maximum-pool-size: 20            # CPU 核心数 × 2 + 磁盘数,常见 10-30
      minimum-idle: 5                  # 保持活跃连接数
      connection-timeout: 30000        # 获取连接超时(ms)
      idle-timeout: 600000             # 空闲超时(ms)
      max-lifetime: 1800000            # 连接最大生命周期(ms)< PgBouncer server_lifetime
      leak-detection-threshold: 60000  # 连接泄漏检测(ms),生产环境建议开启
      pool-name: MyappHikariPool
      validation-timeout: 5000
      initialization-fail-timeout: 1   # 启动时检测连接
      # 配合 PgBouncer transaction pooling 时必须关闭 prepared statement 缓存
      data-source-properties:
        prepareThreshold: 0            # 禁用 JDBC 预处理语句服务端缓存
```

**调优原则**:

- **`maximum-pool-size` 不是越大越好**,PostgreSQL 每个连接占内存,100+ 连接会拖慢数据库
- **所有生产部署通过 PgBouncer 访问数据库**,HikariCP 与 PgBouncer 协作见 5.14
- `leak-detection-threshold`:60 秒未归还的连接会打印告警,用于发现 connection leak
- `max-lifetime` 必须**小于** PgBouncer 的 `server_lifetime`(默认 3600s),避免连接被对端先断开

### 5.18 PgBouncer 连接代理

**生产环境强制要求**:所有 PostgreSQL 访问通过 PgBouncer 代理。PostgreSQL 每个连接独立进程(约 5-10 MB 内存),K8s 多 pod
场景下直连会瞬间打满 `max_connections`。

**架构**:

```
Spring Boot (HikariCP per pod)
        ↓ (pg wire protocol)
      PgBouncer (中心化连接池)
        ↓ (pg wire protocol)
    PostgreSQL (max_connections 受控)
```

假设 10 个 pod × HikariCP 20 连接 = 200 个应用侧连接,经 PgBouncer 汇聚后实际下行到 PostgreSQL 只有 20 个左右连接。

**PgBouncer 部署模式**(三选一):

| 模式              | 返还时机    | 适用场景                                            | 本栈推荐       |
|-----------------|---------|-------------------------------------------------|------------|
| **session**     | 客户端断开连接 | 需要 `LISTEN/NOTIFY`、临时表、prepared statement 跨事务复用 | ❌ 连接复用率低   |
| **transaction** | 事务提交/回滚 | 常规业务 CRUD,连接复用率最高                               | ✅ **默认选用** |
| **statement**   | 每条 SQL  | 纯只读、无事务场景                                       | ❌ 不兼容事务    |

**transaction 模式的应用侧约束**(必须遵守):

- **禁用服务端预处理语句缓存**:PostgreSQL JDBC 驱动的 `prepareThreshold=0`(已在 5.13 配置)
- **禁用 session 级状态**:不用 `SET`、临时表、session 变量、`LISTEN/NOTIFY`
- **客户端 prepared statement 缓存仍可用**:HikariCP/jOOQ 层面的 PreparedStatement 重用是应用线程本地,与 PgBouncer 无关

**PgBouncer 配置示例**(`pgbouncer.ini`):

```ini
[databases]
myapp = host=postgres-primary port=5432 dbname=myapp

[pgbouncer]
listen_addr = 0.0.0.0
listen_port = 6432
auth_type = scram-sha-256
auth_file = /etc/pgbouncer/userlist.txt

# 池化模式
pool_mode = transaction

# 连接限制
max_client_conn = 1000         # 允许应用总连接数上限(PgBouncer 承载)
default_pool_size = 25         # 每个 (db, user) 对后端 PostgreSQL 的连接数
min_pool_size = 5              # 最小保持连接数
reserve_pool_size = 5          # 预留应急连接数
reserve_pool_timeout = 3       # 预留池启用前的等待秒数

# 生命周期
server_lifetime = 3600         # 后端连接最长存活(秒),与 HikariCP max-lifetime 配合
server_idle_timeout = 600      # 后端空闲超时
client_idle_timeout = 0        # 不主动断开空闲客户端(HikariCP 自己管)

# 健康与稳定性
server_reset_query = DISCARD ALL     # 事务归还时重置状态
ignore_startup_parameters = extra_float_digits,search_path

# 日志
log_connections = 1
log_disconnections = 1
log_pooler_errors = 1

# Admin 控制
admin_users = pgbouncer_admin
stats_users = pgbouncer_stats
```

**docker compose 部署**(开发 / 小规模生产):

```yaml
# compose.yml 节选
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: myapp
      POSTGRES_USER: myapp
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    command: >
      postgres
      -c max_connections=100
      -c shared_buffers=256MB

  pgbouncer:
    image: bitnami/pgbouncer:1.25
    environment:
      POSTGRESQL_HOST: postgres
      POSTGRESQL_PORT: 5432
      POSTGRESQL_DATABASE: myapp
      POSTGRESQL_USERNAME: myapp
      POSTGRESQL_PASSWORD: ${DB_PASSWORD}
      PGBOUNCER_PORT: 6432
      PGBOUNCER_POOL_MODE: transaction
      PGBOUNCER_DEFAULT_POOL_SIZE: 25
      PGBOUNCER_MAX_CLIENT_CONN: 1000
      PGBOUNCER_IGNORE_STARTUP_PARAMETERS: extra_float_digits,search_path
    ports:
      - "6432:6432"
    depends_on:
      - postgres

volumes:
  pgdata:
```

**Kubernetes 部署**(大规模生产):

- **方案 A:每个 node 一个 PgBouncer DaemonSet**,pod 通过 `localhost:6432` 访问本节点 PgBouncer
- **方案 B:独立 PgBouncer Deployment + Service**(3-5 副本 + HPA),应用连 PgBouncer Service
- **方案 C:云厂商托管连接代理**(阿里云 RDS Proxy / AWS RDS Proxy / Azure PostgreSQL Connection Pooling),免自运维

本栈常见项目采用**方案 B**,中等规模,运维成本低。

**容量规划公式**:

```
PostgreSQL max_connections = PgBouncer 副本数 × default_pool_size + 运维连接数 + 20% 冗余
```

示例:3 副本 PgBouncer × pool_size 25 = 75,PostgreSQL `max_connections` 设 100 足够。

**监控指标**(Prometheus scrape PgBouncer Admin console):

- `pgbouncer_pools_cl_active`:活跃客户端连接数
- `pgbouncer_pools_cl_waiting`:等待队列长度(> 0 告警)
- `pgbouncer_pools_sv_active`:后端 PostgreSQL 活跃连接数
- `pgbouncer_stats_xact_count`:事务吞吐

**故障排查**:

- 应用侧报 `connection refused` → PgBouncer Pod 未就绪或 Service 端口错
- 应用侧报 `prepared statement "S_1" does not exist` → 忘记设 `prepareThreshold=0`
- PgBouncer 日志 `closing because: server conn crashed` → PostgreSQL 侧问题,查 PostgreSQL 日志
- 应用侧事务报 `FATAL: max_connections exceeded` → `default_pool_size × 副本数` 超出 PostgreSQL `max_connections`
  ,按容量规划公式调整

### 5.19 多数据源(主从分离)

**场景**:读写分离,主库写、从库读。

```java
package com.example.myapp.infra.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.replica")
    public DataSource replicaDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
}
```

**通常建议**:优先使用 **PostgreSQL 逻辑复制 + PgPool/Pgpool-II 或 RDS 读写分离端点** 在数据库层处理,应用层只连一个 JDBC
URL,运维更简单。

### 5.20 开发期数据库运维 compose.yml

```yaml
# compose.yml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: myapp
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      TZ: Asia/Shanghai
    # 不对外暴露 5432,只允许 pgbouncer 通过内部网络连 postgres
    expose:
      - "5432"
    volumes:
      - pg_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      retries: 5

  pgbouncer:
    image: bitnami/pgbouncer:1.25
    environment:
      POSTGRESQL_HOST: postgres
      POSTGRESQL_PORT: 5432
      POSTGRESQL_DATABASE: myapp
      POSTGRESQL_USERNAME: postgres
      POSTGRESQL_PASSWORD: postgres
      PGBOUNCER_PORT: 6432
      PGBOUNCER_POOL_MODE: transaction
      PGBOUNCER_DEFAULT_POOL_SIZE: 25
      PGBOUNCER_MAX_CLIENT_CONN: 1000
      PGBOUNCER_IGNORE_STARTUP_PARAMETERS: extra_float_digits,search_path
      PGBOUNCER_AUTH_TYPE: scram-sha-256
    ports:
      - "6432:6432"
    depends_on:
      postgres:
        condition: service_healthy

  redis:
    image: redis:8.6.2-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      retries: 5

volumes:
  pg_data:
  redis_data:
```

启动:`docker compose up -d`。应用默认通过 `localhost:6432` 连 PgBouncer,与 3.17 `application.yml` 默认配置一致。

**直连 PostgreSQL 查问题**:当需要执行 `psql` 或用 DataGrip / DBeaver 直连 PostgreSQL(用于查询统计、DDL 等 PgBouncer
transaction 模式不支持的操作)时,临时把 postgres 服务的 `expose` 改成 `ports: - "5432:5432"`,用完改回。

---

## 六、缓存层(Redis)

### 6.1 Redis 8 与客户端选择

**Redis 8**:

- Hash field 支持 TTL(`HEXPIRE`)
- 新增 Vector Sets(原生向量搜索)
- Cluster 模式、Sentinel 高可用

**Redis vs Valkey(2026 现状)**:Redis Ltd 2024-03 将 Redis 改为 SSPL/RSAL 双许可(非 OSI 认证的开源),AWS / Google Cloud /
Oracle 等牵头基于 Redis 7.2.4 BSD 末版 fork 出 **Valkey**,由 Linux Foundation 治理。主要云厂商(AWS ElastiCache、Google
MemoryStore、阿里云 Tair)现在默认后端已切到 Valkey 8.x,Redis OSS 和 Valkey 在命令协议上保持 **~90% 兼容**。Lettuce /
Redisson 客户端**透明兼容两者**,应用代码无需变更。本栈自建缓存可直接用 Redis OSS 8.x 或 Valkey 8.x,由运维团队按 license
合规性选择。

**客户端**:

- **Lettuce**:Spring Boot 默认客户端,Netty 异步,线程安全,日常 K/V 操作
- **Redisson 3.55+**:在 Lettuce 之上提供分布式锁、限流、延迟队列等原语

两者协作:日常操作走 `RedisTemplate`(Lettuce),分布式原语走 `RedissonClient`。

### 6.2 Spring Data Redis 基础配置

`infra/build.gradle` 已引入:

```groovy
api 'org.springframework.boot:spring-boot-starter-data-redis'
api 'org.redisson:redisson-spring-boot-starter:3.55.0'
```

`application.yml`:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 3000ms
      connect-timeout: 3000ms
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2
          max-wait: 3000ms
```

### 6.3 RedisTemplate 配置(关键)

**Spring Boot 默认的 `RedisTemplate<Object, Object>` 用 JDK 序列化**,**生产严禁使用**(不跨语言、体积大、不可读)。

强制使用 JSON 序列化:

```java
package com.example.myapp.infra.redis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 开启多态类型信息,反序列化时还原具体类
        var validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.myapp")
                .allowIfSubType("java.util")
                .allowIfSubType("java.time")
                .build();
        mapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL);
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory factory, ObjectMapper redisObjectMapper) {
        var template = new RedisTemplate<String, Object>();
        template.setConnectionFactory(factory);

        var stringSerializer = new StringRedisSerializer();
        var jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
```

### 6.4 使用 RedisTemplate

```java
package com.example.myapp.user.domain;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class UserCacheService {

    private final RedisTemplate<String, Object> redis;

    public UserCacheService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public void cacheUser(User user) {
        String key = "user:" + user.id();
        redis.opsForValue().set(key, user, Duration.ofHours(1));
    }

    public User getCachedUser(UUID id) {
        String key = "user:" + id;
        return (User) redis.opsForValue().get(key);
    }

    public void invalidateUser(UUID id) {
        redis.delete("user:" + id);
    }
}
```

### 6.5 Spring Cache 注解

**推荐用 `@Cacheable` 声明式缓存**,不用手动操作 Redis:

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 1h
      cache-null-values: false
      use-key-prefix: true
      key-prefix: "myapp:cache:"
```

启用:

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory factory, ObjectMapper redisObjectMapper) {
        var jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        var defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("user", defaultConfig.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration("product", defaultConfig.entryTtl(Duration.ofHours(6)))
                .build();
    }
}
```

使用:

```java
@Service
public class UserService {

    @Cacheable(cacheNames = "user", key = "#id", unless = "#result == null")
    public User findById(UUID id) {
        return userRepository.findById(id).orElse(null);
    }

    @CachePut(cacheNames = "user", key = "#result.id")
    public User update(UUID id, UpdateUserRequest req) {
        // 更新数据库,更新缓存
        // ...
    }

    @CacheEvict(cacheNames = "user", key = "#id")
    public void delete(UUID id) {
        userRepository.delete(id);
    }
}
```

**注意**:

- `@Cacheable` 走 AOP 代理,**同类内部方法调用不生效**
- `unless = "#result == null"`:结果为 null 时不缓存,避免缓存穿透
- 复杂 key:`key = "#tenantId + ':' + #id"`

### 6.6 缓存穿透/击穿/雪崩

**三类典型问题**:

| 问题     | 现象                  | 解决                           |
|--------|---------------------|------------------------------|
| **穿透** | 查不存在的数据,每次打 DB      | 布隆过滤器,或缓存 null 值 + 短 TTL     |
| **击穿** | 热 key 过期瞬间,大量请求打 DB | 分布式锁(Redisson `RLock`)+ 互斥重建 |
| **雪崩** | 大量 key 同时过期         | TTL 加随机偏移(±10 分钟)            |

**示例:击穿防护**

```java
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redis;
    private final RedissonClient redisson;

    public User findById(UUID id) {
        String key = "user:" + id;
        User cached = (User) redis.opsForValue().get(key);
        if (cached != null) return cached;

        // 热 key 加锁重建
        RLock lock = redisson.getLock("lock:user:" + id);
        try {
            if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                try {
                    cached = (User) redis.opsForValue().get(key);  // Double check
                    if (cached != null) return cached;

                    User user = userRepository.findById(id).orElse(null);
                    if (user != null) {
                        long ttl = 3600 + ThreadLocalRandom.current().nextInt(600);  // 雪崩防护
                        redis.opsForValue().set(key, user, Duration.ofSeconds(ttl));
                    }
                    return user;
                } finally {
                    lock.unlock();
                }
            } else {
                throw new AppException(AppCode.SYSTEM_BUSY);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(AppCode.SYSTEM_BUSY);
        }
    }
}
```

### 6.7 Redisson 分布式原语

`infra` 自动装配 Redisson:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

# Redisson 走 Spring Data Redis 的连接
```

**分布式锁**:

```java
@Service
public class OrderService {

    private final RedissonClient redisson;

    public void pay(UUID orderId) throws InterruptedException {
        RLock lock = redisson.getLock("lock:order:" + orderId);
        // 最多等待 3 秒获取锁,不传 leaseTime —— 看门狗自动续期(默认 30 秒,每 10 秒续一次)
        // 注意:一旦传了 leaseTime(如 tryLock(3, 30, SECONDS)),看门狗会被禁用,锁到期自动释放
        if (lock.tryLock(3, TimeUnit.SECONDS)) {
            try {
                // 业务逻辑(无论耗时多久,只要进程存活,看门狗都会续期)
            } finally {
                lock.unlock();
            }
        } else {
            throw new AppException(AppCode.ORDER_PROCESSING);
        }
    }
}
```

**限流**:

```java
@Service
public class SmsService {

    private final RedissonClient redisson;

    public void sendCode(String phone) {
        RRateLimiter limiter = redisson.getRateLimiter("rate:sms:" + phone);
        // 每 60 秒最多 1 次
        limiter.trySetRate(RateType.OVERALL, 1, 60, RateIntervalUnit.SECONDS);
        if (!limiter.tryAcquire()) {
            throw new AppException(AppCode.NOTIFY_RATE_LIMITED);
        }
        // 发送短信
    }
}
```

**延迟队列**:

```java
@Component
public class OrderTimeoutQueue {

    private final RedissonClient redisson;

    // 订单超时:30 分钟未支付取消
    public void enqueue(UUID orderId) {
        RBlockingDeque<UUID> deque = redisson.getBlockingDeque("queue:order:timeout");
        RDelayedQueue<UUID> delayed = redisson.getDelayedQueue(deque);
        delayed.offer(orderId, 30, TimeUnit.MINUTES);
    }

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        RBlockingDeque<UUID> deque = redisson.getBlockingDeque("queue:order:timeout");
        UUID orderId = deque.poll();
        if (orderId != null) {
            // 取消超时订单
        }
    }
}
```

**原则**:

- **只有在需要"强一致性互斥"时才用分布式锁**,大多数场景单体内 Java 锁足够
- **不要用 `setnx` 手写分布式锁**,没有锁续期、容易死锁;Redisson 的看门狗机制是业界标杆
- **延迟队列场景若数据量大,应考虑 Kafka 或 RocketMQ**,Redisson 适合轻量场景(百万级以内)

### 6.8 大 key 问题

**定义**:**没有绝对统一标准**,业界常见参考:

| 类型                           | 警告阈值    | 危险阈值    |
|------------------------------|---------|---------|
| String 单 value               | > 10 KB | > 1 MB  |
| Hash / List / Set / ZSet 元素数 | > 5000  | > 10000 |
| Hash / List / Set / ZSet 总大小 | > 1 MB  | > 10 MB |

高并发低延迟场景阈值还要更低(10KB 的 String 在千兆网卡 + 每秒上千 QPS 下就能打满带宽)。

**五大危害**(面试高频,务必记牢):

1. **网络阻塞**:1MB 的 key × 1000 QPS = 1GB/s,千兆网卡(128MB/s)被打满,同节点其他请求跟着慢
2. **主线程阻塞**:Redis 单线程,一次 `HGETALL` / `LRANGE` 大 key 可能耗时 100ms+,期间所有命令排队;`DEL` 大 key
   更是同步释放内存,阻塞时间 = O(元素数)
3. **主从同步延迟**:全量同步时,大 key 序列化 → 网络传输 → 从库加载三个阶段都阻塞;增量同步时大 key 的写命令会把
   replication backlog 冲掉,触发全量同步,恶性循环
4. **Cluster slot 迁移卡顿**:Redis Cluster 一个 key 属于一个 slot,**再大也不会自动拆**,`MIGRATE` 同步操作会长时间卡单节点
5. **持久化放大**:AOF 重写、RDB 生成时,大 key 的 fork 后 COW 开销被放大,容易触发 OOM

**另一个面试高频搭配:大 key + 热 key 双鬼拍门**

单独的大 key 低访问不致命(每秒一两次请求无感知),但**大 key + 热 key 叠加**就是灾难:1MB 大 key + 100 QPS 就能打满千兆网卡。Cluster
中该节点网卡饱和会连累节点下所有其他 key 的请求。

### 6.9 识别大 key

**线上必须用非阻塞方式,`KEYS *` 严禁上生产**。

**方法 1:`redis-cli --bigkeys`**(按元素数采样)

```bash
redis-cli -h redis.prod -a $PASSWORD --bigkeys -i 0.1
# -i 0.1:每 100 次 SCAN 间隔 0.1 秒,降低压力
```

输出每种类型的 TOP 1。缺点:只给最大一个,不给 TOP N;按元素数而非字节。

**方法 2:`redis-cli --memkeys`**(Redis 5.0+,按字节占用采样)

```bash
redis-cli --memkeys -i 0.1
```

比 `--bigkeys` 更准,底层基于 `MEMORY USAGE` 命令。

**方法 3:`redis-cli --keystats`**(Redis 7+ 官方最新推荐)

```bash
redis-cli --keystats -i 0.1 --top 20
# 同时输出 Top 20 按字节排序的 key + 每种类型分布 + 总体统计
```

合并了 `--bigkeys` 和 `--memkeys`,**新项目首选**。

**方法 4:RDB 离线分析**(最准,零生产影响)

从**从库** `bgsave` 生成 rdb,下载后用 rdb-tools 分析:

```bash
# 从库
redis-cli -h redis-slave bgsave

# 下载 dump.rdb 到分析机
pip install rdbtools python-lzf
rdb -c memory dump.rdb --bytes 10240 > big_keys.csv
# 导出所有 > 10KB 的 key,CSV 带 key 名、类型、字节数、元素数
```

推荐**每周跑一次**,产出 TOP 100 大 key 报表。

**方法 5:单 key 查内存**

```bash
MEMORY USAGE user:profile:123    # Redis 4.0+ 命令,返回字节数
OBJECT ENCODING user:profile:123 # 查看底层编码(见 6.13)
```

**方法 6:应用层埋点**

RedisTemplate 写入前预检大小,超阈值告警甚至拒绝:

```java
package com.example.myapp.infra.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SafeRedisWriter {

    private static final Logger log = LoggerFactory.getLogger(SafeRedisWriter.class);
    private static final long WARN_BYTES = 10 * 1024;        // 10 KB
    private static final long DANGER_BYTES = 1024 * 1024;    // 1 MB

    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper mapper;
    private final MeterRegistry registry;

    public SafeRedisWriter(RedisTemplate<String, Object> redis,
                           ObjectMapper mapper,
                           MeterRegistry registry) {
        this.redis = redis;
        this.mapper = mapper;
        this.registry = registry;
    }

    public void set(String key, Object value, Duration ttl) {
        long size = estimateBytes(value);

        if (size > DANGER_BYTES) {
            registry.counter("redis.bigkey.rejected",
                    "prefix", keyPrefix(key)).increment();
            log.error("[ALERT_P1] 拒绝写入大 key: key={}, size={}KB",
                    key, size / 1024);
            throw new IllegalStateException("Value too large: " + size + " bytes");
        }

        if (size > WARN_BYTES) {
            registry.counter("redis.bigkey.warn",
                    "prefix", keyPrefix(key)).increment();
            log.warn("大 key 预警: key={}, size={}KB", key, size / 1024);
        }

        redis.opsForValue().set(key, value, ttl);
    }

    private long estimateBytes(Object value) {
        if (value == null) return 0;
        if (value instanceof String s) return s.getBytes().length;
        if (value instanceof byte[] b) return b.length;
        try {
            return mapper.writeValueAsBytes(value).length;
        } catch (Exception e) {
            return 0;
        }
    }

    private String keyPrefix(String key) {
        int idx = key.indexOf(':');
        return idx > 0 ? key.substring(0, idx) : key;
    }
}
```

### 6.10 大 key 拆分策略(按类型)

#### String 大 value

**场景**:缓存完整商品详情 JSON、用户档案、富文本内容。

**策略 1:只缓存 ID 列表,详情走单 key**

```java
// 反例:一次缓存整个商品列表
redis.opsForValue().set("hot:products", productList, Duration.ofMinutes(10));  // 2 MB

// 正例:缓存 ID 列表 + 单商品独立 key
redis.opsForValue().set("hot:product:ids", productIds, Duration.ofMinutes(10));  // 几 KB
productIds.forEach(id ->
    redis.opsForValue().set("product:" + id, productMap.get(id), Duration.ofHours(1))
);  // 每个 10-50 KB
```

**策略 2:字段拆 Hash**

```java
// 反例:整份档案塞一个 String
redis.opsForValue().set("user:" + id, profileJson);  // 500 KB

// 正例:Hash 按字段拆
var ops = redis.opsForHash();
ops.put("user:" + id, "basic", basicJson);           // 10 KB
ops.put("user:" + id, "preferences", prefJson);      // 20 KB
// history 仍然大?继续拆或压缩
```

**策略 3:压缩**

JSON 文本典型压缩比 20%-30%。**推荐 LZ4**(比 GZIP 快 5-10 倍):

```groovy
// infra/build.gradle
api 'org.lz4:lz4-java:1.8.0'
```

```java
package com.example.myapp.infra.redis;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.springframework.stereotype.Component;

@Component
public class Lz4Codec {

    private static final LZ4Factory FACTORY = LZ4Factory.fastestInstance();
    private final LZ4Compressor compressor = FACTORY.fastCompressor();
    private final LZ4FastDecompressor decompressor = FACTORY.fastDecompressor();

    /** 压缩,头 4 字节存原始长度 */
    public byte[] compress(byte[] raw) {
        byte[] compressed = compressor.compress(raw);
        byte[] result = new byte[compressed.length + 4];
        // 写入原长度
        result[0] = (byte) (raw.length >>> 24);
        result[1] = (byte) (raw.length >>> 16);
        result[2] = (byte) (raw.length >>> 8);
        result[3] = (byte) raw.length;
        System.arraycopy(compressed, 0, result, 4, compressed.length);
        return result;
    }

    public byte[] decompress(byte[] compressed) {
        int originalLength = ((compressed[0] & 0xFF) << 24)
                | ((compressed[1] & 0xFF) << 16)
                | ((compressed[2] & 0xFF) << 8)
                | (compressed[3] & 0xFF);
        return decompressor.decompress(compressed, 4, originalLength);
    }
}
```

#### Hash 大 key

**场景**:一个用户所有订单存一个 Hash,上万个 field。

**策略:按 field 的 hash 分片**

```java
public String orderShardKey(UUID userId, String orderId) {
    int shard = Math.floorMod(orderId.hashCode(), 16);   // 16 片
    return "orders:user:" + userId + ":" + shard;
}

public void saveOrder(UUID userId, String orderId, Order order) {
    String shardKey = orderShardKey(userId, orderId);
    redis.opsForHash().put(shardKey, orderId, order);
}

public Order getOrder(UUID userId, String orderId) {
    String shardKey = orderShardKey(userId, orderId);
    return (Order) redis.opsForHash().get(shardKey, orderId);
}
```

**若需要遍历用户所有订单**:说明数据结构选错了,应该查 DB + 分页。

#### List 大 key

**场景**:消息流、通知列表。

**策略 1:LTRIM 限长**

```java
public void pushNotification(UUID userId, Notification n) {
    String key = "notifs:" + userId;
    redis.opsForList().leftPush(key, n);
    redis.opsForList().trim(key, 0, 499);   // 只保留最新 500 条
}
```

**策略 2:按时间分桶**

```java
String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
String key = "notifs:" + userId + ":" + month;
redis.opsForList().leftPush(key, n);
redis.expire(key, Duration.ofDays(90));     // 3 个月过期
```

**策略 3:改用 Redis Stream**

Stream 原生支持 `MAXLEN` 自动修剪、消费者组、范围查询:

```java
redis.opsForStream().add(
    MapRecord.create("notifs:stream:" + userId,
        Map.of("type", "order", "msg", "订单已发货"))
);
// 配合 XADD MAXLEN 约等于 1000
```

#### ZSet / Set 大 key

**场景**:全站排行榜、明星账号粉丝列表。

**策略 1:分片 ZSet**

```java
public void addScore(UUID userId, double score) {
    int shard = Math.floorMod(userId.hashCode(), 256);
    redis.opsForZSet().add("rank:" + shard, userId.toString(), score);
}

/** TOP N:每片取 N 个,合并再排 */
public List<String> topN(int n) {
    var all = new ArrayList<ZSetOperations.TypedTuple<Object>>();
    for (int i = 0; i < 256; i++) {
        var shard = redis.opsForZSet()
                .reverseRangeWithScores("rank:" + i, 0, n - 1);
        if (shard != null) all.addAll(shard);
    }
    return all.stream()
            .sorted(Comparator.comparingDouble(
                    (ZSetOperations.TypedTuple<Object> t) -> t.getScore()).reversed())
            .limit(n)
            .map(t -> t.getValue().toString())
            .toList();
}
```

**策略 2:榜单分级**

- `rank:weekly`:本周榜(7 天过期,数据量可控)
- `rank:monthly`:本月榜
- **全量榜放 PostgreSQL**,定时任务每 10 分钟刷 Redis TOP 100

**策略 3:关系数据回归数据库**

粉丝列表、关注列表**不是 Redis 的职责**。放 PostgreSQL `follow(follower_id, followee_id)` + 索引,Redis 只缓存**粉丝数**(
单数字)和 **活跃 TOP N 粉丝**(小 Set)。

### 6.11 安全删除大 key

**绝不能直接 `DEL` 大 key**(同步阻塞,2MB ZSet 可能卡 1 秒+)。

**方案 1:`UNLINK`**(Redis 4.0+ 首选)

```java
redis.unlink("big:hash:" + userId);
```

**UNLINK 行为的准确理解**:它是**智能命令**,不是无脑非阻塞。源码阈值 `LAZYFREE_THRESHOLD = 64`:

- 对象元素数 ≤ 64:主线程直接释放(等价 DEL)
- 对象元素数 > 64:丢到后台线程释放,主线程 O(1) 返回

这个设计避免了小对象派发后台线程的开销。面试时这个细节能加分。

**方案 2:开启 lazy-free 配置**(Redis 6+,生产必开)

`redis.conf`:

```
lazyfree-lazy-eviction yes       # maxmemory 淘汰用异步释放
lazyfree-lazy-expire yes         # 过期删除异步
lazyfree-lazy-server-del yes     # 隐式 DEL(如 RENAME 目标 key 已存在时)异步
lazyfree-lazy-user-del yes       # 用户 DEL 自动变 UNLINK(Redis 6.0 新增)
replica-lazy-flush yes           # 从库全量同步前 FLUSHALL 异步
```

**`lazyfree-lazy-expire` 解决"定时炸弹"**:一个 200 万元素的 ZSet 设了 1 小时 TTL,到期会触发同步 DEL 阻塞主线程。开启此配置后
TTL 到期也走异步释放。

**`lazyfree-lazy-user-del yes` 的效果**:业务代码里的 `DEL` 自动按 UNLINK 行为处理,不需要改代码。

**方案 3:Redis 4.0 前手动分批删**

```java
public void safeDeleteHash(String key, int batchSize) {
    ScanOptions options = ScanOptions.scanOptions().count(batchSize).build();
    try (Cursor<Map.Entry<Object, Object>> cursor =
                 redis.opsForHash().scan(key, options)) {
        var batch = new ArrayList<Object>(batchSize);
        while (cursor.hasNext()) {
            batch.add(cursor.next().getKey());
            if (batch.size() >= batchSize) {
                redis.opsForHash().delete(key, batch.toArray());
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            redis.opsForHash().delete(key, batch.toArray());
        }
    }
    redis.delete(key);
}
```

Set/List/ZSet 类似,用 `SSCAN` / `LRANGE` + `LTRIM` / `ZSCAN` 分批。

### 6.12 过期时间打散(防雪崩 & 防大 key 集中删除)

**反例**:固定 TTL 导致集中过期

```java
redis.opsForValue().set(key, value, Duration.ofMinutes(30));  // 所有 key 30 分钟过期
```

**正例**:TTL 加随机偏移

```java
long baseSeconds = 1800;
long jitterSeconds = ThreadLocalRandom.current().nextLong(300);  // 0-5 分钟抖动
redis.opsForValue().set(key, value, Duration.ofSeconds(baseSeconds + jitterSeconds));
```

**双重收益**:

- 缓存雪崩防护(不会瞬间一大批 key 同时过期)
- 即便有大 key,过期删除也被打散,避免瞬时主线程压力

### 6.13 OBJECT ENCODING 与编码拐点

Redis 底层数据结构有**编码切换**机制,元素少时用紧凑编码,超阈值升级为散列/跳表,**内存占用可能翻 10 倍,性能也会突变**。

```bash
OBJECT ENCODING user:profile:123
```

**关键阈值**(默认值,`redis.conf` 可调):

| 类型       | 小编码               | 大编码       | 切换阈值                                                           |
|----------|-------------------|-----------|----------------------------------------------------------------|
| Hash     | listpack          | hashtable | `hash-max-listpack-entries 128` / `hash-max-listpack-value 64` |
| List     | listpack(Redis 7) | quicklist | `list-max-listpack-size -2`(8KB/节点)                            |
| Set(字符串) | listpack          | hashtable | `set-max-listpack-entries 128`                                 |
| Set(纯整数) | intset            | hashtable | `set-max-intset-entries 512`                                   |
| ZSet     | listpack          | skiplist  | `zset-max-listpack-entries 128` / `zset-max-listpack-value 64` |

**实用推论**:

- Hash 超过 128 field **就已经到编码拐点**,远在"大 key"之前。能保持 < 128 field 的 Hash 内存友好度极高
- 整数 Set 用 intset 编码极紧凑,混入字符串元素会强制升级 hashtable
- 设计 key 时尽量让常见访问路径下元素数在编码阈值内

### 6.14 热 key 问题

大 key 常和热 key 配对出现。**识别**:

```bash
# 需要先设 LFU 策略
CONFIG SET maxmemory-policy allkeys-lfu
redis-cli --hotkeys
```

或用 `MONITOR`(**生产慎用,占 CPU**,只在低峰短时间采样)、基于 Prometheus 的 QPS 按 key 前缀分组。

**处理**:

1. **本地缓存(Caffeine L1)**:L1 先挡住 99% 请求,见 6.15
2. **key 副本分摊**:写入时复制到 `key:1` ~ `key:N`,客户端随机读取一个,分摊 QPS
3. **只读副本**:热 key 场景配 Redis 从库,客户端读走从库
4. **不要放 Redis**:超热的小数据(如系统配置),直接进应用内存

### 6.15 本地 + Redis 两级缓存(可选)

追求最低读取延迟(本地纳秒级)时,用 Caffeine 做 L1,Redis 做 L2:

```groovy
// infra/build.gradle
api 'com.github.ben-manes.caffeine:caffeine:3.2.3'
```

```java
@Configuration
public class TwoLevelCacheConfig {

    @Bean
    public CaffeineCache l1Cache() {
        return new CaffeineCache("l1:user",
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .build());
    }

    // L1 miss 时查 L2(Redis),需要自定义 CacheManager 串联
    // ...
}
```

**使用原则**:仅对真正的超高 QPS 热点使用,**90% 场景单级 Redis 缓存已足够**。过早引入 L1 会增加一致性难度(
缓存失效难以广播到所有节点)。

---

## 七、认证授权(Spring Security + Sa-Token + Argon2id)

### 7.1 三件套的分工

| 层次        | 组件                     | 职责                                                                |
|-----------|------------------------|-------------------------------------------------------------------|
| **底层过滤器** | Spring Security        | HTTP 协议层安全:CSRF、CORS、HTTPS、SecurityFilterChain、OAuth2/SSO/LDAP 集成 |
| **业务鉴权**  | Sa-Token               | Session 管理、Token 颁发、注解鉴权、踢人下线、同端互斥登录、账号封禁                         |
| **密码哈希**  | Argon2id(BouncyCastle) | 用户密码存储,RFC 9106 标准,memory-hard                                    |

**分工理由**:

- Spring Security 的过滤器链成熟稳固,应该保留;但它的 `UserDetailsService` + `AuthenticationManager` 对于**已有 Token 体系
  **的业务比较累赘
- Sa-Token 对"会话、踢人、SSO"这类**业务化鉴权需求**提供更直接的 API,省去大量样板代码
- 两者**不冲突**:Spring Security 管过滤器链和非鉴权相关的 HTTP 安全,Sa-Token 管 Token 生命周期与权限注解
- 密码哈希单独由 BouncyCastle 的 Argon2id 实现提供,不依赖任一框架

### 7.2 依赖与版本

`infra` 模块已声明(见 3.7):

```groovy
api 'org.springframework.boot:spring-boot-starter-security'
api 'cn.dev33:sa-token-spring-boot4-starter:1.45.0'
api 'org.bouncycastle:bcprov-jdk18on:1.80'
api 'cn.dev33:sa-token-dao-redis-jackson:1.45.0'     // Sa-Token 用 Redis 存 Session
```

**版本说明**:Sa-Token 1.45.0 是 Maven Central 上的最新稳定版,**官方新增了 `sa-token-spring-boot4-starter` 模块**,专门适配
Spring Boot 4。Spring Boot 4 项目**必须使用 `spring-boot4-starter`**,不要用 `spring-boot-starter`(后者对应 Spring Boot
2/3)。同时官方也新增了 `sa-token-reactor-spring-boot4-starter` 给 WebFlux + Spring Boot 4 场景。

### 7.3 Spring Security 配置

**只开启必要功能**,不做用户名密码认证(那是 Sa-Token 的职责):

```java
package com.example.myapp.infra.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 禁用 CSRF(前后端分离,用 Token 不用 Cookie)
                .csrf(AbstractHttpConfigurer::disable)

                // 禁用 Session(Sa-Token 自己管)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 禁用 formLogin、httpBasic
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                // 允许所有请求通过 Spring Security(鉴权交给 Sa-Token 拦截器)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().permitAll())

                // 安全头
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(opts -> {})
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))

                .build();
    }
}
```

**关键决策**:

- Spring Security `authorizeHttpRequests` 全部 `permitAll()`,**鉴权完全交给 Sa-Token**
- 保留 Spring Security 的**安全头设置**(HSTS、X-Frame-Options、X-Content-Type-Options)
- 保留 Spring Security 的 **CORS 过滤链**(或单独 `CorsFilter`,见 4.10)
- 不启用 Spring Security 的 Session、表单登录、HTTP Basic

### 7.4 Sa-Token 配置

```yaml
# application.yml
sa-token:
  token-name: Authorization          # Header 字段名
  timeout: 2592000                   # Token 有效期 30 天(秒)
  active-timeout: -1                 # 临时活跃有效期,-1 表示不检查
  is-concurrent: true                # 允许同一账号同时登录
  is-share: false                    # 多处登录时,不共享 Token
  token-style: uuid                  # Token 风格:uuid/simple-uuid/random-32/random-64/random-128/tik
  is-log: true                       # 打印操作日志
  is-print: false                    # 启动时不打印 banner
  token-prefix: Bearer               # Header 前缀(兼容 OAuth2)
  # Redis 存储
  alone-redis:
    database: 1                      # 独立 Redis DB,与业务缓存隔离
```

**关键配置说明**:

- `is-concurrent: true + is-share: false`:**同账号允许多端登录**(PC + 手机),每个端独立 Token
- `is-concurrent: false`:**同端互斥登录**,新登录会挤掉旧登录
- `token-style: uuid`:防猜测,避免 simple-uuid 的规律性
- `alone-redis.database: 1`:认证 Session 用独立 Redis DB,与业务缓存隔离,便于清理

### 7.5 Sa-Token 拦截器注册

```java
package com.example.myapp.infra.security;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/v*/auth/login",
                        "/api/v*/auth/register",
                        "/api/v*/auth/refresh",
                        "/api/v*/auth/sms-code",
                        "/api/v*/public/**",
                        "/actuator/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/error"
                );
    }
}
```

**逻辑**:

- 所有 `/**` 路径默认需要登录
- 白名单:登录、注册、刷新 Token、验证码、公开 API、健康检查、OpenAPI 文档

### 7.6 登录流程实现

```java
// user/api/AuthController.java
package com.example.myapp.user.api;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.example.myapp.user.domain.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @SaIgnore   // 登录接口不需要鉴权
    @PostMapping("/login")
    public SaTokenInfo login(@Valid @RequestBody LoginRequest req) {
        var user = userService.authenticate(req.email(), req.password());
        // Sa-Token 登录,生成 Token 并存 Redis
        StpUtil.login(user.id(), "web");     // "web" 是登录设备标识
        // 可选:把额外信息存到 Session
        StpUtil.getSession().set("tenantId", user.tenantId());
        StpUtil.getSession().set("name", user.name());
        return StpUtil.getTokenInfo();       // 返回 Token + 过期时间
    }

    @PostMapping("/logout")
    public void logout() {
        StpUtil.logout();
    }

    @GetMapping("/me")
    public UserDto me() {
        var userId = (java.util.UUID) StpUtil.getLoginId();
        return UserDto.from(userService.findById(userId));
    }

    @SaIgnore
    @PostMapping("/refresh")
    public SaTokenInfo refresh(@RequestBody RefreshRequest req) {
        // Sa-Token 内置续签机制,或手动实现 refresh token
        // ...
        return StpUtil.getTokenInfo();
    }
}
```

```java
// user/api/LoginRequest.java
public record LoginRequest(
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
        @jakarta.validation.constraints.NotBlank String password
) {}
```

```java
// user/api/RefreshRequest.java
public record RefreshRequest(@jakarta.validation.constraints.NotBlank String refreshToken) {}
```

**登录响应示例**:

```json
{
    "code": 0,
    "message": "success",
    "data": {
        "tokenName": "Authorization",
        "tokenValue": "cca27fe6-edf2-4816-a0ea-deb4bc6e4c3d",
        "isLogin": true,
        "loginId": "550e8400-e29b-41d4-a716-446655440000",
        "loginType": "login",
        "tokenTimeout": 2592000,
        "sessionTimeout": 2592000,
        "tokenSessionTimeout": -2,
        "tokenActiveTimeout": -1,
        "loginDevice": "web"
    }
}
```

前端后续请求 Header 带:`Authorization: Bearer cca27fe6-edf2-4816-a0ea-deb4bc6e4c3d`

### 7.7 Service 层实现登录

```java
package com.example.myapp.user.domain;

import com.example.myapp.common.exception.AppException;
import com.example.myapp.common.exception.AppCode;
import com.example.myapp.user.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final String defaultTenantId = "default";

    public UserService(UserRepository userRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    public User authenticate(String email, String password) {
        var user = userRepository.findByEmail(defaultTenantId, email)
                .orElseThrow(() -> new AppException(AppCode.AUTH_BAD_CREDENTIALS));

        if (user.status() != UserStatus.ACTIVE) {
            throw new AppException(AppCode.AUTH_ACCOUNT_DISABLED);
        }

        if (!passwordService.verify(password, user.passwordHash())) {
            throw new AppException(AppCode.AUTH_BAD_CREDENTIALS);
        }

        return user;
    }
}
```

**关键原则**:

- 登录失败时**不透露具体原因**(不告诉前端"密码错误"还是"用户不存在"),统一抛 `AUTH_BAD_CREDENTIALS`,防止枚举爆破
- 对 `INACTIVE` / `BANNED` 账号**优先拒绝**,然后才验密码
- 密码比对用 `passwordService.verify(plain, hash)`,**不用 `equals`**(常数时间比较在 Argon2id 验证内部实现)

### 7.8 Argon2id 密码服务

```java
package com.example.myapp.user.domain;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Argon2id 密码哈希服务(OWASP 2024+ 推荐参数)。
 * 参数:m=19456 KiB (19 MB), t=2, p=1 —— 见 https://cheatsheetseries.owasp.org
 */
@Service
public class PasswordService {

    // OWASP 2024 推荐:memoryKB=19456, iterations=2, parallelism=1
    private static final int MEMORY_KB = 19_456;
    private static final int ITERATIONS = 2;
    private static final int PARALLELISM = 1;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;

    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);

        var params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(MEMORY_KB)
                .withIterations(ITERATIONS)
                .withParallelism(PARALLELISM)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .build();

        var generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] hash = new byte[HASH_LENGTH];
        generator.generateBytes(password.getBytes(java.nio.charset.StandardCharsets.UTF_8), hash);

        // 编码为标准 PHC 格式:$argon2id$v=19$m=19456,t=2,p=1$<salt-b64>$<hash-b64>
        return String.format(
                "$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s",
                MEMORY_KB, ITERATIONS, PARALLELISM,
                Base64.getEncoder().withoutPadding().encodeToString(salt),
                Base64.getEncoder().withoutPadding().encodeToString(hash)
        );
    }

    public boolean verify(String password, String encoded) {
        try {
            // PHC 格式: $argon2id$v=19$m=19456,t=2,p=1$<salt-b64>$<hash-b64>
            // split("\\$") 产生 7 段: ["", "argon2id", "v=19", "m=19456,t=2,p=1", "<salt>", "<hash>"]
            String[] parts = encoded.split("\\$");
            if (parts.length != 6 || !"argon2id".equals(parts[1])) {
                return false;
            }
            // 解析参数段 parts[3] = "m=19456,t=2,p=1"
            String[] paramParts = parts[3].split(",");
            int memoryKB   = Integer.parseInt(paramParts[0].substring(2));    // 去掉 "m="
            int iterations = Integer.parseInt(paramParts[1].substring(2));    // 去掉 "t="
            int parallelism = Integer.parseInt(paramParts[2].substring(2));   // 去掉 "p="

            byte[] salt         = Base64.getDecoder().decode(parts[4]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[5]);

            var params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withSalt(salt)
                    .withMemoryAsKB(memoryKB)
                    .withIterations(iterations)
                    .withParallelism(parallelism)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .build();

            var generator = new Argon2BytesGenerator();
            generator.init(params);

            byte[] actualHash = new byte[expectedHash.length];
            generator.generateBytes(password.getBytes(java.nio.charset.StandardCharsets.UTF_8), actualHash);

            // 常数时间比较,防时序攻击
            return java.security.MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }
}
```

**OWASP 2024 推荐参数**:

| 参数            | 值              | 说明                 |
|---------------|----------------|--------------------|
| `memoryKB`    | 19456 (≈19 MB) | 内存开销,抗 GPU/ASIC 破解 |
| `iterations`  | 2              | 时间开销               |
| `parallelism` | 1              | 并行线程数              |
| `saltLength`  | 16             | 每用户随机 salt         |
| `hashLength`  | 32             | 输出哈希长度             |

**参数调整原则**:

- 服务器 CPU 越好,可以适当**增加 iterations**(3-4)保持安全性
- 内存受限场景(边缘 VPS),可降到 `memoryKB=12288`(12 MB),但不低于此
- **绝不能低于 OWASP 最低推荐**

### 7.9 注解鉴权

Sa-Token 提供细粒度权限注解,Controller 方法上直接标:

```java
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    // 需要 "user:read" 权限
    @SaCheckPermission("user:read")
    @GetMapping("/{id}")
    public UserDto get(@PathVariable UUID id) { ... }

    // 需要 "user:write" 权限
    @SaCheckPermission("user:write")
    @PostMapping
    public UserDto create(@Valid @RequestBody CreateUserRequest req) { ... }

    // 需要 admin 角色
    @SaCheckRole("admin")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) { ... }

    // 组合条件:需要 admin 角色,同时要有 user:delete 权限
    @SaCheckRole("admin")
    @SaCheckPermission("user:delete")
    @DeleteMapping("/batch")
    public void batchDelete(@RequestBody List<UUID> ids) { ... }

    // 满足任一即可:admin 或 manager
    @SaCheckRole(value = {"admin", "manager"}, mode = SaMode.OR)
    @GetMapping("/stats")
    public StatsDto stats() { ... }
}
```

### 7.10 权限与角色数据源

Sa-Token 不管权限数据的存储,提供 `StpInterface` 让业务实现:

```java
package com.example.myapp.infra.security;

import cn.dev33.satoken.stp.StpInterface;
import com.example.myapp.user.repo.UserRoleRepository;
import com.example.myapp.user.repo.UserPermissionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class StpInterfaceImpl implements StpInterface {

    private final UserRoleRepository userRoleRepository;
    private final UserPermissionRepository userPermissionRepository;

    public StpInterfaceImpl(UserRoleRepository userRoleRepository,
                            UserPermissionRepository userPermissionRepository) {
        this.userRoleRepository = userRoleRepository;
        this.userPermissionRepository = userPermissionRepository;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        UUID userId = UUID.fromString(loginId.toString());
        return userPermissionRepository.findPermissionsByUserId(userId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        UUID userId = UUID.fromString(loginId.toString());
        return userRoleRepository.findRolesByUserId(userId);
    }
}
```

**权限表结构建议**(Flyway):

```sql
CREATE TABLE roles (
    id          VARCHAR(32)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE permissions (
    code        VARCHAR(100) PRIMARY KEY,    -- 如 "user:read"
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(500)
);

CREATE TABLE role_permissions (
    role_id         VARCHAR(32)  NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions(code) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_code)
);

CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id VARCHAR(32) NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
```

### 7.11 踢人下线与会话管理

```java
import cn.dev33.satoken.stp.StpUtil;

// 踢某用户下线(所有端)
StpUtil.kickout(userId);

// 踢某用户指定设备(web / app / ios / android)
StpUtil.kickout(userId, "web");

// 封禁账号 7 天
StpUtil.disable(userId, 7 * 24 * 3600);

// 解封
StpUtil.untieDisable(userId);

// 查询账号封禁状态
boolean isDisabled = StpUtil.isDisable(userId);

// 查询指定账号的在线 Token 列表
List<String> tokens = StpUtil.getTokenValueListByLoginId(userId);

// 强制某 Token 下线
StpUtil.logoutByTokenValue(tokenValue);

// 查询当前所有在线用户(分页)
SaSession session = StpUtil.getSession();
```

**典型应用场景**:

- **账号异常修改密码**:修改后踢下线所有会话,强制重新登录
- **管理员踢人**:后台操作员可封禁恶意账号
- **强制多端互斥**:新设备登录踢掉旧设备(修改 `is-concurrent: false`)

### 7.12 接口签名(对外 API)

**政府/金融场景**经常要求对外 API 有签名验证。Sa-Token 提供 `SaSign`:

```yaml
sa-token:
  sign:
    secret-key: ${SIGN_SECRET_KEY:YourLongSecretKeyAtLeast32Chars}
    digest-algo: md5   # 或 sha256
    timestamp-disparity: 900000   # 时间戳窗口 15 分钟
```

```java
import cn.dev33.satoken.sign.SaSignUtil;

// 服务端校验签名
SaSignUtil.checkRequest(request);

// 客户端生成签名参数
Map<String, Object> params = new HashMap<>();
params.put("userId", "10001");
params.put("amount", "100");
String signedParams = SaSignUtil.addSignParamsAndJoin(params);
// 输出:amount=100&userId=10001&timestamp=1734567890&nonce=xxxx&sign=abcd1234
```

### 7.13 JWT 集成(SSO/微服务场景)

单体项目默认 Token 存 Redis 即可,**不需要 JWT**。当需要:

- 多个服务共享 Token
- 或对外输出可验签的 Token(如给合作伙伴)

才用 JWT。Sa-Token 的 JWT 插件:

```groovy
api 'cn.dev33:sa-token-jwt:1.45.0'
```

```yaml
sa-token:
  jwt-secret-key: ${JWT_SECRET:YourJwtSecretKey...}
```

**JWT 的取舍**:

- **优点**:自包含,无状态,服务端不需要存储
- **缺点**:无法主动吊销(只能等过期);Token 体积大;刷新机制复杂
- **结论**:**非必要不用**。单体项目 Sa-Token 默认的 Redis Session 更好用。

### 7.14 集成测试

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserService userService;

    @Test
    void login_success() throws Exception {
        // 预置用户
        userService.create("default", new CreateUserRequest(
                "test@example.com", "Tester", "Password123!"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"Password123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenValue").exists())
                .andExpect(jsonPath("$.data.isLogin").value(true));
    }

    @Test
    void login_bad_credentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"wrong@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }
}
```

---

## 八、加密与数据保护

### 8.1 章节概览

本章覆盖两个维度的加密:

| 层          | 保护对象                                   | 归属                  | 必要性                          |
|------------|----------------------------------------|---------------------|------------------------------|
| **字段级加解密** | 数据库里的敏感字段(手机号、身份证号、银行卡号)               | `infra/encryption/` | 等保二级以上 / GDPR / 金融项目**必备**   |
| **接口传输加密** | HTTP 请求/响应的 body(防止中间人截取,即便 HTTPS 被卸载) | `infra/crypto/`     | 金融、政务、涉密项目**必备**;普通 B 端项目非必须 |

这两者**解决不同问题**,可以独立启用:

- 字段级加密**落地数据库是密文**,即使数据库被拖库也看不到原始值
- 接口加密**网络上传输是密文**,即使 HTTPS 在反向代理处被卸载,body 仍是加密的

**先清楚什么不是本章内容**:

- **密码哈希**(用户登录密码) → `Argon2id`,已在第 7.8 节
- **数据脱敏**(API 响应隐藏部分字符) → `@Mask` 注解,已在第 4.13 节
- **签名 / MAC**(HMAC、RSA 签名) → `common/crypto/` 工具类,按需引入

### 8.2 字段级加解密(`infra/encryption/`)

**目标**:敏感字段**落库即密文**,从数据库任何渠道(SQL 客户端、pg_dump、备份)看到的都是加密后的字符串。业务代码**无感知**,读写
Java 对象像平常一样。

#### 依赖

```groovy
// infra/build.gradle
api 'org.bouncycastle:bcprov-jdk18on:1.80'      // AES-GCM / SM4 实现
```

#### 加密器抽象(`infra/encryption/`)

```java
package com.example.myapp.infra.encryption;

/** 字段加解密器接口 */
public interface FieldCipher {

    /** 加密明文 → Base64 密文 */
    String encrypt(String plaintext);

    /** Base64 密文 → 明文 */
    String decrypt(String ciphertext);

    /** 算法标识,便于密文格式自描述 */
    String algorithm();
}
```

#### AES-GCM 实现(推荐默认)

**选 AES-GCM 的理由**:认证加密(AEAD),同时提供保密性和完整性;篡改密文会在解密时抛异常,避免"解出乱码但不报错"的隐蔽攻击。

```java
package com.example.myapp.infra.encryption;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class AesGcmFieldCipher implements FieldCipher {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;          // GCM 推荐 96 bit IV
    private static final int TAG_LEN = 128;        // GCM Tag 128 bit

    private final byte[] key;                      // 256-bit key
    private final SecureRandom random = new SecureRandom();

    public AesGcmFieldCipher(byte[] key) {
        if (key.length != 32) {
            throw new IllegalArgumentException("AES-256 需要 32 字节密钥");
        }
        this.key = key;
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LEN, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 密文格式: IV(12字节) + Ciphertext+Tag,整体 Base64
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 加密失败", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            byte[] iv = new byte[IV_LEN];
            byte[] encrypted = new byte[combined.length - IV_LEN];
            System.arraycopy(combined, 0, iv, 0, IV_LEN);
            System.arraycopy(combined, IV_LEN, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LEN, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 解密失败(密钥错误或密文被篡改)", e);
        }
    }

    @Override
    public String algorithm() {
        return "AES-GCM";
    }
}
```

#### SM4 实现(等保 / 国密要求)

```java
package com.example.myapp.infra.encryption;

import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class Sm4GcmFieldCipher implements FieldCipher {

    private static final int IV_LEN = 12;
    private static final int TAG_LEN = 128;

    private final byte[] key;                      // 128-bit SM4 key
    private final SecureRandom random = new SecureRandom();

    public Sm4GcmFieldCipher(byte[] key) {
        if (key.length != 16) {
            throw new IllegalArgumentException("SM4 需要 16 字节密钥");
        }
        this.key = key;
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(iv);

        GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
        cipher.init(true, new AEADParameters(new KeyParameter(key), TAG_LEN, iv));

        byte[] input = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] output = new byte[cipher.getOutputSize(input.length)];
        int len = cipher.processBytes(input, 0, input.length, output, 0);
        try {
            cipher.doFinal(output, len);
        } catch (Exception e) {
            throw new IllegalStateException("SM4-GCM 加密失败", e);
        }

        byte[] combined = new byte[iv.length + output.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(output, 0, combined, iv.length, output.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        byte[] combined = Base64.getDecoder().decode(ciphertext);
        byte[] iv = new byte[IV_LEN];
        byte[] encrypted = new byte[combined.length - IV_LEN];
        System.arraycopy(combined, 0, iv, 0, IV_LEN);
        System.arraycopy(combined, IV_LEN, encrypted, 0, encrypted.length);

        GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
        cipher.init(false, new AEADParameters(new KeyParameter(key), TAG_LEN, iv));

        byte[] output = new byte[cipher.getOutputSize(encrypted.length)];
        int len = cipher.processBytes(encrypted, 0, encrypted.length, output, 0);
        try {
            cipher.doFinal(output, len);
        } catch (Exception e) {
            throw new IllegalStateException("SM4-GCM 解密失败(密钥错误或密文被篡改)", e);
        }
        return new String(output, 0, len, StandardCharsets.UTF_8);
    }

    @Override
    public String algorithm() {
        return "SM4-GCM";
    }
}
```

#### 密钥配置(`application.yml`)

```yaml
app:
  encryption:
    algorithm: AES_GCM        # AES_GCM(默认) / SM4_GCM(国密项目)
    key: ${FIELD_CIPHER_KEY}  # Base64 编码,生产通过环境变量或 KMS 注入
```

**密钥产生**:

```bash
# AES-256(32 字节)
openssl rand -base64 32

# SM4(16 字节)
openssl rand -base64 16
```

**密钥管理原则**:

- **绝不硬编码**在代码或配置文件里,只走环境变量或 KMS
- K8s 环境存 Secret,开发期用 `.env` 文件(`.gitignore`)
- 生产环境接入**云 KMS**(阿里云 KMS / AWS KMS / Azure Key Vault),避免明文密钥暴露到 pod
- 备份策略:KMS 主密钥**必须**云厂商托管备份,自建密钥**必须**有纸质 / 离线备份,丢失即全量密文不可恢复

#### 密钥轮换

密钥泄露或合规要求定期轮换(每 90/180 天),设计要点:

- 密文自带算法/密钥版本标识:`{"v":"v1","algo":"AES-GCM","data":"..."}`
- 读取时按版本选密钥解密,写入时用最新版本加密
- 旧数据迁移:后台异步任务,读出→用旧密钥解密→用新密钥加密→写回
- 轮换期间**新旧密钥同时在 KMS 中保留**,迁移完成后才下架旧密钥

本文档不展开轮换的细节实现(业务复杂度大,项目层级设计)。

#### Spring Bean 装配

```java
package com.example.myapp.infra.encryption;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

@Configuration
public class EncryptionConfig {

    @ConfigurationProperties(prefix = "app.encryption")
    public record EncryptionProps(Algorithm algorithm, String key) {
        public enum Algorithm { AES_GCM, SM4_GCM }
    }

    @Bean
    public FieldCipher fieldCipher(EncryptionProps props) {
        byte[] key = Base64.getDecoder().decode(props.key());
        return switch (props.algorithm()) {
            case AES_GCM -> new AesGcmFieldCipher(key);
            case SM4_GCM -> new Sm4GcmFieldCipher(key);
        };
    }
}
```

#### jOOQ Converter:业务层完全无感

**核心机制**:在 jOOQ 生成代码配置里声明 `Converter`,**DB ↔ Java 之间自动加解密**。业务代码读写 Java 字段时看到的是**明文
**,SQL 里写进去的是**密文**。

```java
package com.example.myapp.infra.encryption;

import org.jooq.impl.AbstractConverter;

/**
 * 加密列的 jOOQ Converter:DB 存密文 String,Java 看明文 String。
 * 由 Spring 管理(单例 Bean),通过 EncryptionConverterProvider 注入到 jOOQ Configuration。
 */
public class EncryptedStringConverter extends AbstractConverter<String, String> {

    private final FieldCipher cipher;

    public EncryptedStringConverter(FieldCipher cipher) {
        super(String.class, String.class);
        this.cipher = cipher;
    }

    @Override
    public String from(String databaseObject) {
        return databaseObject == null ? null : cipher.decrypt(databaseObject);
    }

    @Override
    public String to(String userObject) {
        return userObject == null ? null : cipher.encrypt(userObject);
    }
}
```

#### 注册 Converter 到 jOOQ Configuration

jOOQ 代码生成器产生的代码中,会通过 `ConverterProvider` 获取 Converter 实例。在 Spring 启动时注入:

```java
package com.example.myapp.infra.encryption;

import org.jooq.ConverterProvider;
import org.jooq.Converter;
import org.jooq.impl.DefaultConverterProvider;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqEncryptionConfig {

    @Bean
    public DefaultConfigurationCustomizer encryptionConverterCustomizer(
            EncryptedStringConverter encryptedStringConverter) {
        return configuration -> {
            ConverterProvider fallback = configuration.converterProvider();
            configuration.set((ConverterProvider) new ConverterProvider() {
                @Override
                public <T, U> Converter<T, U> provide(Class<T> tType, Class<U> uType) {
                    // 生成代码里加密字段的 Converter 类型是 Converter<String, String>
                    if (tType == String.class && uType == String.class) {
                        @SuppressWarnings("unchecked")
                        Converter<T, U> c = (Converter<T, U>) encryptedStringConverter;
                        return c;
                    }
                    return fallback.provide(tType, uType);
                }
            });
        };
    }

    @Bean
    public EncryptedStringConverter encryptedStringConverter(FieldCipher cipher) {
        return new EncryptedStringConverter(cipher);
    }
}
```

**关键**:`Converter` 是 Spring 管理的 Bean,不是 jOOQ 代码生成器 new 出来的静态实例。避免了"静态字段注入时序"问题,测试时也能替换
`FieldCipher` mock。

**jOOQ 生成配置**(`infra/build.gradle` 的 jOOQ 代码生成配置中):

```groovy
jooq {
    configurations {
        main {
            generationTool {
                database {
                    name = 'org.jooq.meta.postgres.PostgresDatabase'
                    // ...
                    forcedTypes {
                        // 凡是列名匹配 *_enc 或 encrypted_* 的 text 列,
                        // 自动绑定到 EncryptedStringConverter
                        forcedType {
                            userType = 'java.lang.String'
                            converter = 'com.example.myapp.infra.encryption.EncryptedStringConverter'
                            includeExpression = '.*_enc|encrypted_.*'
                            includeTypes = 'text|varchar.*'
                        }
                    }
                }
            }
        }
    }
}
```

**约定**:**列名以 `_enc` 结尾或 `encrypted_` 开头**即自动加密。DDL 示例:

```sql
CREATE TABLE users (
    id         UUID PRIMARY KEY DEFAULT uuidv7(),
    email      VARCHAR(255) NOT NULL,
    phone_enc  TEXT,                    -- 手机号,自动加密
    id_card_enc TEXT,                   -- 身份证号,自动加密
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

#### 业务代码使用(完全无感)

```java
// Repository / Service 里正常读写,明文进明文出
User user = new User(
    UUID.randomUUID(),
    "alice@example.com",
    "13800138000",               // 明文写入
    "110101199001011234"         // 明文写入
);
userRepository.insert(user);

// 数据库表里实际存的是 Base64 密文
// SELECT phone_enc FROM users;
// → "bXlHZW5jcnlwdGVkUGhvbmU...="

User fetched = userRepository.findById(user.id()).orElseThrow();
String phone = fetched.phone();      // 读出来是明文 "13800138000"
```

#### 加密列的限制

加密列**不能当查询条件**:

```java
// ❌ 错的:查询结果永远为空,因为数据库里存的是密文
userRepository.findByPhone("13800138000");

// ✅ 正确做法 1:查询时先加密条件再查
String cipherPhone = fieldCipher.encrypt("13800138000");
// 然后按密文查询(但 AES-GCM 有随机 IV,每次加密结果不同!见下)

// ✅ 正确做法 2:双列策略
//    phone_enc:AES-GCM 加密,存储安全
//    phone_hash:SHA-256(phone + salt),用于查询的确定性哈希
```

**AES-GCM 随机 IV 的问题**:相同明文加密两次得到不同密文,无法用于查询。要查询需要**确定性加密**(AES-SIV)或**独立哈希列**。

**推荐方案**:

- 敏感字段 **只加密,不用于查询**:用户资料里的身份证号、住址、银行卡号,只在详情页展示,不按此查询
- 敏感字段 **要用于查询**:加一个独立 `*_hash` 列,存 `HMAC-SHA256(value)`,查询用 hash 列精确匹配

```sql
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    phone_enc   TEXT,                    -- AES-GCM 密文
    phone_hash  CHAR(64)                 -- HMAC-SHA256 十六进制,用于查询
);

-- PostgreSQL 的索引必须独立 CREATE INDEX,不支持 CREATE TABLE 内行内 INDEX(那是 MySQL 语法)
CREATE INDEX idx_users_phone_hash ON users (phone_hash);
```

写入时:

```java
user.phoneEnc = fieldCipher.encrypt(phone);       // 存密文
user.phoneHash = hmacSha256(phone, hmacKey);      // 查询用
```

查询时:

```java
var rows = db.selectFrom(USERS)
    .where(USERS.PHONE_HASH.eq(hmacSha256(inputPhone, hmacKey)))
    .fetch();
```

### 8.3 接口传输加密(`infra/crypto/`)

**目标**:HTTP 请求 body 和响应 body **在网络上是加密的**,即使 HTTPS 在反向代理被卸载、即使抓包工具接入,也看不到业务数据。

**适用场景**:

- 金融接口(支付、账户、转账)
- 政务涉密接口
- 等保三级以上要求

**不适用场景**:

- 普通 B 端 SaaS 的日常 CRUD(TLS 已经够了,加密传输是过度工程)
- 第三方回调接口(对方 SDK 不支持你的加密协议)
- 文件上传、下载(加密会破坏流式传输)

#### 加密协议设计

采用 **动态 AES + RSA 密钥交换**:

```
1. 前端生成临时 AES-256 密钥(每个请求不同)
2. 用服务端公开的 RSA 公钥加密 AES 密钥 → aesKeyEncrypted
3. 用 AES 加密请求 body → bodyEncrypted
4. 请求体: { "key": aesKeyEncrypted, "body": bodyEncrypted }

服务端处理:
1. 用 RSA 私钥解密 aesKeyEncrypted → AES 密钥
2. 用 AES 解密 bodyEncrypted → 明文 body
3. 交给 Controller 处理,返回响应
4. 用同一个 AES 密钥加密响应 body
5. 返回: { "body": responseBodyEncrypted }
```

**为什么不直接 RSA 加密 body**:RSA 只能加密 ≈ 245 字节数据(2048 位密钥),不能加密完整 body。实际协议都是**RSA
交换对称密钥,对称密钥加密 body**。

**为什么每次请求新 AES 密钥**:前向保密 —— 即使 RSA 私钥未来泄露,攻击者也无法解密过往录制的流量。

#### 依赖

标准 JDK,**无需额外依赖**。AES-GCM 和 RSA 都在 `javax.crypto` 里。

#### RSA 密钥对生成(一次性)

```bash
# 服务端运维一次性执行,生成 2048 位 RSA 密钥对
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out rsa-private.pem
openssl rsa -pubout -in rsa-private.pem -out rsa-public.pem

# 公钥下发给前端(可以直接打到前端代码里,公钥公开无风险)
# 私钥保存到 KMS / Secret,应用启动读取
```

#### 服务端实现(`infra/crypto/`)

```java
package com.example.myapp.infra.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class TransportCrypto {

    private static final String RSA_ALGO = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_LEN = 128;

    private final PrivateKey rsaPrivate;

    public TransportCrypto(byte[] rsaPrivateKeyDer) throws Exception {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(rsaPrivateKeyDer);
        this.rsaPrivate = KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    /** 用 RSA 私钥解出 AES 密钥 */
    public byte[] unwrapAesKey(String encryptedKeyBase64) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, rsaPrivate);
        return cipher.doFinal(Base64.getDecoder().decode(encryptedKeyBase64));
    }

    /** 用 AES 密钥解密请求 body */
    public String decryptBody(String encryptedBodyBase64, byte[] aesKey) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedBodyBase64);
        byte[] iv = new byte[IV_LEN];
        byte[] encrypted = new byte[combined.length - IV_LEN];
        System.arraycopy(combined, 0, iv, 0, IV_LEN);
        System.arraycopy(combined, IV_LEN, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance(AES_ALGO);
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(TAG_LEN, iv));

        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    /** 用 AES 密钥加密响应 body */
    public String encryptBody(String plaintext, byte[] aesKey) throws Exception {
        byte[] iv = new byte[IV_LEN];
        new java.security.SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(TAG_LEN, iv));

        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }
}
```

#### 方法级加密开关

不是所有接口都需要加密(登录、查询健康检查等开放接口不加)。用注解标记需要加密的接口:

```java
package com.example.myapp.common.annotation;

import java.lang.annotation.*;

/** 标记接口需要传输加密 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Encrypted {
    /** 同时加密请求体(默认 true)*/
    boolean request() default true;

    /** 同时加密响应体(默认 true)*/
    boolean response() default true;
}
```

#### 请求解密:Servlet Filter 前置处理

```java
package com.example.myapp.infra.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(50)  // 在 Sa-Token 鉴权 Filter 之前
public class TransportDecryptFilter extends OncePerRequestFilter {

    private static final String AES_KEY_ATTR = "transport.aesKey";

    private final TransportCrypto crypto;
    private final ObjectMapper mapper;

    public TransportDecryptFilter(TransportCrypto crypto, ObjectMapper mapper) {
        this.crypto = crypto;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        // 只处理带 X-Encrypted: 1 header 的请求(或按路径规则)
        if (!"1".equals(request.getHeader("X-Encrypted"))) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // 读原始加密 body
            String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode envelope = mapper.readTree(rawBody);
            String encryptedKey = envelope.get("key").asText();
            String encryptedBody = envelope.get("body").asText();

            // 解密 AES key
            byte[] aesKey = crypto.unwrapAesKey(encryptedKey);
            // 解密 body
            String plaintextBody = crypto.decryptBody(encryptedBody, aesKey);

            // AES key 存 request attribute,供响应加密用
            request.setAttribute(AES_KEY_ATTR, aesKey);

            // 用解密后的 body 替换原请求体,后续 Controller 正常读
            byte[] newBody = plaintextBody.getBytes(StandardCharsets.UTF_8);
            HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
                @Override
                public ServletInputStream getInputStream() {
                    ByteArrayInputStream bais = new ByteArrayInputStream(newBody);
                    return new ServletInputStream() {
                        @Override public boolean isFinished() { return bais.available() == 0; }
                        @Override public boolean isReady() { return true; }
                        @Override public void setReadListener(ReadListener l) {}
                        @Override public int read() { return bais.read(); }
                    };
                }
                @Override
                public int getContentLength() { return newBody.length; }
            };

            chain.doFilter(wrapped, response);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":10002,\"message\":\"传输加密校验失败\"}");
        }
    }
}
```

#### 响应加密:ResponseBodyAdvice

扩展已有的 `GlobalResponseAdvice`(第 4.5 节),增加对 `@Encrypted` 方法的响应加密:

```java
package com.example.myapp.infra.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.myapp.common.annotation.Encrypted;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

@RestControllerAdvice
public class TransportEncryptAdvice implements ResponseBodyAdvice<Object> {

    private static final String AES_KEY_ATTR = "transport.aesKey";

    private final TransportCrypto crypto;
    private final ObjectMapper mapper;

    public TransportEncryptAdvice(TransportCrypto crypto, ObjectMapper mapper) {
        this.crypto = crypto;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(MethodParameter returnType,
                             Class<? extends HttpMessageConverter<?>> converterType) {
        // 只处理标了 @Encrypted 的方法或类
        Encrypted ann = returnType.getMethodAnnotation(Encrypted.class);
        if (ann == null) ann = returnType.getContainingClass().getAnnotation(Encrypted.class);
        return ann != null && ann.response();
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                   MethodParameter returnType,
                                   MediaType mediaType,
                                   Class<? extends HttpMessageConverter<?>> converterType,
                                   ServerHttpRequest request,
                                   ServerHttpResponse response) {
        try {
            byte[] aesKey = null;
            if (request instanceof ServletServerHttpRequest servletReq) {
                aesKey = (byte[]) servletReq.getServletRequest().getAttribute(AES_KEY_ATTR);
            }
            if (aesKey == null) return body;

            String plaintext = mapper.writeValueAsString(body);
            String encrypted = crypto.encryptBody(plaintext, aesKey);
            return Map.of("body", encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("响应加密失败", e);
        }
    }
}
```

#### 使用示例

```java
@RestController
public class PaymentController {

    /** 登录接口不加密(前端还没拿到 session 无法交换 AES key)*/
    @PostMapping("/api/login")
    public R<LoginResponse> login(@RequestBody LoginRequest req) { ... }

    /** 支付接口加密(涉及金额、账号等敏感数据)*/
    @Encrypted
    @PostMapping("/api/pay")
    public R<PayResponse> pay(@RequestBody PayRequest req) { ... }

    /** 也可以只加密响应不加密请求 */
    @Encrypted(request = false, response = true)
    @GetMapping("/api/account")
    public R<AccountDetail> account() { ... }
}
```

前端请求示例(伪代码):

```javascript
// 1. 生成临时 AES 密钥
const aesKey = crypto.getRandomValues(new Uint8Array(32));

// 2. RSA 公钥加密 AES key
const encryptedKey = rsaEncrypt(aesKey, SERVER_PUBLIC_KEY);

// 3. AES 加密 body
const iv = crypto.getRandomValues(new Uint8Array(12));
const encryptedBody = aesGcmEncrypt(JSON.stringify(body), aesKey, iv);

// 4. 发请求
fetch('/api/pay', {
    method: 'POST',
    headers: {
        'X-Encrypted': '1',
        'Content-Type': 'application/json'
    },
    body: JSON.stringify({
        key: base64(encryptedKey),
        body: base64(iv + encryptedBody)    // IV 拼在前面
    })
});

// 5. 解密响应
const resp = await response.json();
const plaintext = aesGcmDecrypt(base64decode(resp.body), aesKey);
const data = JSON.parse(plaintext);
```

#### 配置

```yaml
app:
  crypto:
    transport:
      enabled: ${TRANSPORT_CRYPTO_ENABLED:false}    # 默认关,业务按需开
      rsa-private-key: ${RSA_PRIVATE_KEY}           # PKCS#8 DER Base64,生产通过 Secret/KMS 注入
```

### 8.4 加密策略决策表

面对具体项目,按下表选:

| 项目类型               | 字段级加密            | 传输加密   |
|--------------------|------------------|--------|
| 政府 OA / 审批系统(非涉密)  | 按字段敏感度(身份证、手机号加) | 不需要    |
| 国企 ERP / B2B SaaS  | 按字段敏感度           | 不需要    |
| 电商 SaaS            | 按字段敏感度(实名信息)     | 不需要    |
| 金融对公 / 贷款          | 全部敏感字段           | 推荐开启   |
| 支付 / 账户核心系统        | 全部敏感字段           | **必须** |
| 政务涉密项目(等保三级+)      | **必须**(用 SM4)    | **必须** |
| 医疗系统(HIPAA / 个人信息) | 必须               | 推荐     |
| 教育 / 社交(普通用户数据)    | 按字段敏感度           | 不需要    |

**通用原则**:

- **至少加密**:身份证号、银行卡号、姓名、详细住址、精确位置
- **必须加密**:医疗诊断、金融账户余额、交易明细、政务涉密字段
- **可选加密**:手机号(脱敏已经够,加密增加查询复杂度)
- **不用加密**:用户名(本来就公开展示)、公开地理区域(省市级)

### 8.5 运维与合规

**日志安全**:

- 业务日志**绝不打印**明文敏感字段 —— 虽然 `@Mask` 能保证 API 响应脱敏,但 `log.info(user)` 走 `toString()` 会暴露
- DTO 的 `toString()` 默认也脱敏(record 可重写 `toString`,或用 Lombok `@ToString(exclude = ...)`)

**数据库备份**:

- 加密列的备份同样是密文,泄露风险可控
- **但 KMS 主密钥如果丢失,备份也解不开** —— 主密钥的离线备份**必须有**

**审计要求**:

- 密文字段的 **访问记录必须审计**(谁在什么时候看了哪个用户的身份证号)
- 审计日志存 `audit` 模块,保留 3-5 年

**合规映射**:

| 合规框架       | 字段级加密要求          | 传输加密要求              |
|------------|------------------|---------------------|
| 等保 2.0 二级  | 推荐               | HTTPS 即可            |
| 等保 2.0 三级+ | **必须**           | **必须**(TLS + 应用层加密) |
| GDPR       | **必须** 对个人识别信息加密 | HTTPS 即可            |
| PCI-DSS    | **必须** 银行卡号加密    | **必须**              |
| HIPAA      | **必须**           | **必须**              |

---

## 九、文件处理与预览

### 9.1 章节概览

本章覆盖**所有文件相关的生成、解析、转换、预览场景**,按业务复杂度从低到高。

**选型矩阵**:

| 任务            | 推荐方案                       | 归属                                | 适用场景                           |
|---------------|----------------------------|-----------------------------------|--------------------------------|
| Excel 导入导出    | Apache Fesod               | `common/excel/` 或 `report/excel/` | 通用数据报表、台账导出                    |
| Word 简单生成     | Apache POI XWPF            | `report/word/`                    | 少量文档,代码拼装                      |
| Word 模板填充     | poi-tl                     | `report/word/`                    | 合同、通知、证明等**带固定模板**的文档          |
| PDF 代码生成      | iText Core 9               | `report/pdf/`                     | 发票、报告、证书,逐元素控制                 |
| PDF HTML 转换   | openhtmltopdf              | `report/pdf/`                     | 后端先渲染 Thymeleaf HTML,转 PDF     |
| OFD 电子发票      | ofdrw                      | `report/invoice/`                 | 国家税务总局规范,电子发票合规出具              |
| 在线预览(100+ 格式) | kkFileView 独立服务            | 独立 Docker 服务                      | 政务 / 企业附件预览,Office / CAD / 音视频 |
| 浏览器直接渲染       | PDF.js + docx-preview      | 前端                                | 轻量预览,无独立服务                     |
| 在线编辑          | ONLYOFFICE Document Server | 独立服务                              | 多人协作编辑 Office 文档               |

**模块归属规则**:

- **`common/` 级别**(被业务模块普遍使用):只放轻量工具类,Excel 导入导出工具属于此级别
- **`report/` 功能域**(按业务规模启用):复杂报表、模板填充、PDF/Word 大文档生成、异步导出
- **独立服务**:kkFileView / ONLYOFFICE,通过 REST API 交互,不在主工程内

### 9.2 Excel 处理(Apache Fesod)

**Apache Fesod 是什么**:Apache Fesod(Incubating)是 FastExcel 2025-09 捐献给 Apache 软件基金会后的正式项目。FastExcel
是阿里巴巴 EasyExcel 的社区维护分支。**三个名字指向同一项目**,API 完全兼容。

**为什么用 Fesod 而不继续用 FastExcel**:

- Apache 基金会治理,长期维护保障
- `FastExcel` / `FastExcelFactory` 类在 Fesod 中已标记 `@Deprecated`,未来会移除
- 新项目应使用 `FesodSheet` 类

**依赖**:

```groovy
// common/build.gradle 或 report/build.gradle
api 'org.apache.fesod:fesod-sheet:2.0.1-incubating'
```

从 FastExcel 迁移:替换 groupId/artifactId,import 语句由 `cn.idev.excel.*` 改为 `org.apache.fesod.*`,
`FastExcel.read(...)` 改为 `FesodSheet.read(...)`。

**简单导出**(< 10 万行,同步):

```java
import org.apache.fesod.FesodSheet;
import org.apache.fesod.annotation.ExcelProperty;

public record UserExportDto(
        @ExcelProperty("用户名") String name,
        @ExcelProperty("邮箱") String email,
        @ExcelProperty("注册时间") Instant createdAt
) {}

@RawResponse
@GetMapping("/users/export")
public void export(HttpServletResponse response) throws IOException {
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader("Content-Disposition",
            "attachment; filename=" + URLEncoder.encode("用户.xlsx", StandardCharsets.UTF_8));

    List<UserExportDto> data = userService.listForExport();
    FesodSheet.write(response.getOutputStream(), UserExportDto.class)
            .sheet("用户")
            .doWrite(data);
}
```

**流式导入**(超大文件,避免 OOM):

```java
public class UserImportListener implements ReadListener<UserImportDto> {
    private final List<UserImportDto> batch = new ArrayList<>(1000);
    private final UserService userService;

    public UserImportListener(UserService s) { this.userService = s; }

    @Override
    public void invoke(UserImportDto data, AnalysisContext ctx) {
        batch.add(data);
        if (batch.size() >= 1000) {
            userService.batchImport(batch);
            batch.clear();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext ctx) {
        if (!batch.isEmpty()) userService.batchImport(batch);
    }
}

@PostMapping("/users/import")
public R<Void> importUsers(@RequestParam MultipartFile file) throws IOException {
    FesodSheet.read(file.getInputStream(), UserImportDto.class, new UserImportListener(userService))
            .sheet()
            .doRead();
    return R.ok();
}
```

**复杂模板填充**(固定 Excel 模板 + 动态数据):

```java
// 模板文件 template.xlsx 中用 {变量名} / {.变量名} 占位
Map<String, Object> data = Map.of(
        "title", "2026 年 Q1 销售报表",
        "summary", Map.of("total", 1234567, "count", 890));
List<SaleRow> rows = saleService.list();

FesodSheet.write("output.xlsx")
        .withTemplate("template.xlsx")
        .sheet()
        .doFill(data)
        .doFill(rows);
```

**大数据量异步导出**(> 10 万行,结合 JobRunr + OSS):见 9.7 节。

### 9.3 Word 文档处理

**两种场景两种库**:

| 场景                    | 库               | 依赖                                |
|-----------------------|-----------------|-----------------------------------|
| 代码拼装 Word(少量场景,逐段落控制) | Apache POI XWPF | `org.apache.poi:poi-ooxml:5.3.0+` |
| 模板填充(合同、证明、公函)        | poi-tl          | `com.deepoove:poi-tl:1.12.2+`     |

**绝大多数企业场景用 poi-tl**,因为 Word 文档 90% 是"固定格式 + 动态字段填充",代码拼装 Word 非常繁琐。

**poi-tl 依赖**:

```groovy
// report/build.gradle
api 'com.deepoove:poi-tl:1.12.2'
```

**poi-tl 模板示例**:

Word 模板文件(`contract.docx`)中用 `{{变量名}}` 占位:

```
甲方:{{partyA}}
乙方:{{partyB}}
签订日期:{{signDate}}
合同金额:{{amount}} 元

合同条款:
{{#terms}}
- {{this}}
{{/terms}}
```

生成代码:

```java
@Service
public class ContractGenerator {

    public Path generate(Contract contract, Path outputPath) {
        Map<String, Object> data = new HashMap<>();
        data.put("partyA", contract.partyA());
        data.put("partyB", contract.partyB());
        data.put("signDate", contract.signDate().toString());
        data.put("amount", contract.amount());
        data.put("terms", contract.terms());

        try (InputStream template = getClass().getResourceAsStream("/templates/contract.docx")) {
            XWPFTemplate tpl = XWPFTemplate.compile(template).render(data);
            tpl.writeAndClose(Files.newOutputStream(outputPath));
            return outputPath;
        } catch (IOException e) {
            throw new AppException(AppCode.REPORT_EXPORT_FAILED, e, "合同生成");
        }
    }
}
```

**poi-tl 支持的模板特性**:

- 文本变量 `{{name}}`
- 图片 `{{@picture}}`
- 表格循环 `{{#users}}...{{/users}}`(Markdown 风格循环)
- 条件块 `{{?cond}}...{{/cond}}`
- 多文档合并(拼接多份 Word)
- 自定义插件(高级场景)

**签名 / 盖章**:

电子合同需要盖章,Word 合同生成后用 PDF 库加电子签章。**不推荐在 Word 里处理电子签章**,签名合规用 PDF + iText 的
`Signature` 功能。

### 9.4 PDF 生成

**两种思路**:

| 思路         | 库             | 适用场景                        |
|------------|---------------|-----------------------------|
| 代码逐元素绘制    | iText Core 9  | 发票、证书、报告,需要**精确版式控制**       |
| HTML 转 PDF | openhtmltopdf | 后端用 Thymeleaf 渲染 HTML,转 PDF |

**Flying Saucer 不推荐**:

- Flying Saucer 上游 2023 年后更新缓慢
- **openhtmltopdf 是 Flying Saucer 的活跃 fork**,用 PDFBox 替代 iText,2026 仍在维护
- 社区主流选择 openhtmltopdf

#### iText Core 9 代码生成

**许可证注意**:iText Core 9.x 起使用 **AGPL 3.0**(纯开源)或**商业许可**(闭源商用)。AGPL 要求你的应用也按 AGPL 开源,*
*闭源商用必须买商业授权**。政府和企业项目**几乎必须买商业许可**或改用 openhtmltopdf。

**openhtmltopdf 许可证**:**LGPL 2.1**,闭源商用无需付费,**普通企业项目首选**。

**iText Core 依赖**(只在有商业许可 / 纯开源项目使用):

```groovy
api 'com.itextpdf:itext-core:9.2.0'
// 中文字体支持
api 'com.itextpdf:font-asian:9.2.0'
```

**基础代码**:

```java
public void generatePdf(Path output) throws IOException {
    try (PdfWriter writer = new PdfWriter(output.toString());
         PdfDocument pdf = new PdfDocument(writer);
         Document doc = new Document(pdf)) {

        // 中文字体
        PdfFont font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
        doc.setFont(font);

        doc.add(new Paragraph("发票标题").setFontSize(20).setBold());
        doc.add(new Paragraph("开票日期:2026-04-17"));

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 3, 2}));
        table.addHeaderCell("项目").addHeaderCell("说明").addHeaderCell("金额");
        table.addCell("1").addCell("软件服务费").addCell("¥ 10,000.00");
        doc.add(table);
    }
}
```

#### openhtmltopdf HTML → PDF(推荐用法)

**依赖**:

```groovy
api 'com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10'
api 'com.openhtmltopdf:openhtmltopdf-slf4j:1.0.10'
```

**步骤**:

1. 用 Thymeleaf 在后端渲染 HTML
2. 用 openhtmltopdf 将 HTML 转 PDF

```java
@Service
public class PdfReportService {

    private final TemplateEngine templateEngine;   // Thymeleaf,Spring Boot 自带

    public byte[] renderReport(ReportData data) throws IOException {
        // 1. Thymeleaf 渲染 HTML
        Context ctx = new Context();
        ctx.setVariable("report", data);
        String html = templateEngine.process("report.html", ctx);

        // 2. HTML → PDF
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        // 中文字体(从 classpath 加载)
        builder.useFont(() -> getClass().getResourceAsStream("/fonts/NotoSansSC.ttf"),
                "Noto Sans SC");
        builder.toStream(out);
        builder.run();

        return out.toByteArray();
    }
}
```

**Thymeleaf 模板 `report.html`**(必须是 XHTML 格式):

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <style>
        body { font-family: 'Noto Sans SC', sans-serif; }
        .header { font-size: 20pt; font-weight: bold; }
        table { width: 100%; border-collapse: collapse; }
        th, td { border: 1px solid #333; padding: 6px; }
    </style>
</head>
<body>
    <div class="header" th:text="${report.title}">标题占位</div>
    <p th:text="|生成时间:${report.createdAt}|"></p>
    <table>
        <tr><th>项目</th><th>金额</th></tr>
        <tr th:each="item : ${report.items}">
            <td th:text="${item.name}"></td>
            <td th:text="${item.amount}"></td>
        </tr>
    </table>
</body>
</html>
```

**openhtmltopdf 限制**:

- **只支持 CSS 2.1 子集**,不支持 flex / grid / CSS3 高级特性
- HTML 必须是**严格 XHTML**(所有标签闭合、小写、属性带引号)
- 不执行 JavaScript
- 分页控制用 `page-break-before: always` 等 CSS 属性

#### PDF 水印 / 签章 / 加密

PDF 后处理(加水印、加数字签章、密码保护)用 iText 的 `PdfStamper`:

```java
// 加文本水印
try (PdfDocument doc = new PdfDocument(new PdfReader(input), new PdfWriter(output))) {
    int pages = doc.getNumberOfPages();
    for (int i = 1; i <= pages; i++) {
        PdfCanvas canvas = new PdfCanvas(doc.getPage(i).newContentStreamBefore(),
                doc.getPage(i).getResources(), doc);
        canvas.setFillColorGray(0.75f)
              .setFontAndSize(PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H"), 48)
              .showTextAligned("机密", 300, 400, 1, 45);
    }
}
```

### 9.5 电子发票(OFD + PDF)

**两种电子发票格式**:

| 格式       | 库                     | 适用场景                   |
|----------|-----------------------|------------------------|
| PDF 电子发票 | iText / openhtmltopdf | **一般商业发票**(增值税普通发票电子版) |
| OFD 电子发票 | ofdrw                 | **国家税务总局规范**,全电发票、医疗票据 |

**OFD 是什么**:开放版式文档(Open Fixed-layout Document),国家标准 GB/T 33190,用于电子发票、电子公文。国家税务总局 2021 年起
**全电发票强制使用 OFD**。

**ofdrw 依赖**:

```groovy
api 'org.ofdrw:ofdrw-full:2.2.5'
```

**生成 OFD 发票**:

```java
public void generateOfdInvoice(Invoice invoice, Path output) {
    try (OFDDoc ofd = new OFDDoc(output)) {
        ofd.setDefaultPageLayout(new PageLayout(210d, 140d));  // 210mm × 140mm

        Paragraph title = new Paragraph("电子发票").setFontSize(14d);
        ofd.add(title);

        Paragraph no = new Paragraph("发票号码:" + invoice.number()).setFontSize(10d);
        ofd.add(no);

        // 表格、条目、印章
        // ...
    } catch (IOException e) {
        throw new AppException(AppCode.REPORT_EXPORT_FAILED, e, "OFD 发票生成");
    }
}
```

**签章**:OFD 发票需要数字签章。ofdrw 提供 `ofdrw-sign` 模块,结合 USB Key 或国密 SM2 算法加签。

**实际场景**:项目如果对接税务发票平台,**大部分情况下直接调用税务平台 API**(阿里云 / 腾讯云电子发票平台 / 百望云 /
航天信息),平台返回 OFD/PDF 文件,**不需要自己生成**。只在合规要求自建开票系统时才用 ofdrw。

### 9.6 文件预览(kkFileView)

**场景**:政府附件、企业文档库、工作流附件、项目文档需要**在浏览器预览**,不下载到本地。

**方案对比**:

| 方案                         | 优势                          | 劣势                 | 适用               |
|----------------------------|-----------------------------|--------------------|------------------|
| kkFileView 独立服务            | 支持 130+ 格式,零代码集成,Apache 2.0 | 独立服务,运维成本          | 企业 / 政府项目,预览格式多样 |
| 前端 PDF.js                  | 纯前端,只预览 PDF                 | 仅支持 PDF            | 文档统一先转 PDF 的场景   |
| 前端 docx-preview            | 纯前端,直接渲染 Word               | 格式保真度 80%+,复杂文档有偏差 | 轻量应用,预览简单 Word   |
| ONLYOFFICE Document Server | 支持在线编辑                      | 商业版授权贵,社区版有限制      | 多人协作编辑文档         |

#### kkFileView 部署

**官方 Docker Compose**:

```yaml
# docker-compose.kkfileview.yml
services:
  kkfileview:
    image: keking/kkfileview:4.4.0
    ports:
      - "8012:8012"
    environment:
      # 水印配置(可选)
      KK_WATERMARK_TXT: MyApp
      # 禁用文件上传页面(生产环境必须)
      KK_OFFICE_PREVIEW_SWITCH_DISABLED: "false"
      # 信任域名(只允许特定来源的文件被预览)
      KK_TRUST_HOST: "oss.example.com,*.example.com"
      # 缓存清理时间
      KK_CACHE_CLEAN_CRON: "0 0 3 * * ?"
    volumes:
      - kkfile_data:/opt/kkFileView/file
    restart: unless-stopped

volumes:
  kkfile_data:
```

**K8s 部署**:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kkfileview
  namespace: file-preview
spec:
  replicas: 2                            # 高并发可扩容,LibreOffice 转换是瓶颈
  selector:
    matchLabels:
      app: kkfileview
  template:
    metadata:
      labels:
        app: kkfileview
    spec:
      containers:
        - name: kkfileview
          image: keking/kkfileview:4.4.0
          ports:
            - containerPort: 8012
          env:
            - name: KK_WATERMARK_TXT
              value: "MyApp"
            - name: KK_TRUST_HOST
              value: "oss.example.com,*.example.com"
          resources:
            requests:
              cpu: 500m
              memory: 1Gi
            limits:
              cpu: 2000m
              memory: 4Gi               # LibreOffice 转换时内存需求高
          readinessProbe:
            httpGet:
              path: /
              port: 8012
            initialDelaySeconds: 60     # LibreOffice 启动慢
            periodSeconds: 20
          livenessProbe:
            httpGet:
              path: /
              port: 8012
            initialDelaySeconds: 120
            periodSeconds: 30
---
apiVersion: v1
kind: Service
metadata:
  name: kkfileview
  namespace: file-preview
spec:
  selector:
    app: kkfileview
  ports:
    - port: 8012
      targetPort: 8012
```

#### 后端调用 kkFileView

应用侧**不直接和 kkFileView 交互**——前端直接跳转 kkFileView URL 即可。后端只负责生成**鉴权过的文件 URL**交给前端。

```java
@Service
public class FilePreviewService {

    private static final String KKFILE_BASE = "http://kkfileview.file-preview.svc.cluster.local:8012";

    private final FileService fileService;

    /** 生成预览 URL,供前端 iframe 嵌入 */
    public String buildPreviewUrl(UUID fileId, String userId) {
        // 1. 生成 OSS 预签名 URL(带过期时间)
        String ossUrl = fileService.generatePresignedUrl(fileId, Duration.ofMinutes(30));

        // 2. Base64 编码 + URL 编码(kkFileView 要求)
        String encoded = Base64.getEncoder().encodeToString(ossUrl.getBytes(StandardCharsets.UTF_8));
        String urlEncoded = URLEncoder.encode(encoded, StandardCharsets.UTF_8);

        // 3. 拼 kkFileView URL
        return String.format("%s/onlinePreview?url=%s", KKFILE_BASE, urlEncoded);
    }
}
```

前端嵌入:

```html
<iframe
    :src="previewUrl"
    style="width: 100%; height: 800px; border: none;"
/>
```

#### 安全要点

- ⚠️ **必须配置 `KK_TRUST_HOST`**:只允许可信域名的文件被预览,否则 kkFileView 变成 SSRF 跳板
- ⚠️ **生产环境禁用上传页面**:`KK_OFFICE_PREVIEW_SWITCH_DISABLED` 配置,防止任意用户上传文件到预览服务
- ⚠️ **敏感文档加水印**:`KK_WATERMARK_TXT` 配置动态水印,可以通过 URL 参数传入用户信息水印
- ⚠️ **隔离部署**:kkFileView 部署在独立命名空间 / VPC,不暴露到公网(前端通过应用后端的反向代理访问)
- 参考 kkFileView 官方 [SECURITY_CONFIG.md](https://github.com/kekingcn/kkFileView/blob/master/SECURITY_CONFIG.md)

#### 前端 PDF.js 直接预览(轻量场景)

如果业务只涉及 PDF,**不需要 kkFileView**,前端装 PDF.js 即可:

```html
<script type="module">
  import * as pdfjsLib from 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/4.x/pdf.min.mjs';
  pdfjsLib.GlobalWorkerOptions.workerSrc =
      'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/4.x/pdf.worker.min.mjs';

  const pdf = await pdfjsLib.getDocument(ossUrl).promise;
  // 渲染第一页到 canvas
  const page = await pdf.getPage(1);
  const viewport = page.getViewport({ scale: 1.5 });
  const canvas = document.getElementById('pdf-canvas');
  canvas.width = viewport.width;
  canvas.height = viewport.height;
  await page.render({
      canvasContext: canvas.getContext('2d'),
      viewport
  }).promise;
</script>
```

### 9.7 大文件异步处理

**场景**:超过 10 万行的 Excel 导出、超过 100MB 的 PDF 合并,**同步导出会超时**(Web 请求一般 30 秒超时)。

**异步模式**:

1. 用户点击导出 → 后端返回**任务 ID**
2. JobRunr 调度后台任务执行导出
3. 导出完成后文件存 OSS
4. 通过 SSE / WebSocket 推送完成通知给前端,或前端轮询任务状态
5. 前端拿到完成通知,展示下载链接

**代码骨架**:

```java
@Service
public class AsyncExportService {

    private final JobScheduler jobScheduler;
    private final ExportTaskRepository taskRepo;
    private final FileService fileService;
    private final NotificationService notificationService;

    /** 用户请求导出,立即返回任务 ID */
    public UUID requestExport(UUID userId, ExportCondition cond) {
        UUID taskId = UUID.randomUUID();
        taskRepo.insert(new ExportTask(taskId, userId, "PENDING", Instant.now()));
        jobScheduler.enqueue(() -> exportAsync(taskId, userId, cond));
        return taskId;
    }

    /** 后台异步执行 */
    @Job(name = "导出 Excel")
    public void exportAsync(UUID taskId, UUID userId, ExportCondition cond) {
        taskRepo.updateStatus(taskId, "RUNNING");

        Path tempFile = Files.createTempFile("export-" + taskId, ".xlsx");
        try (var out = Files.newOutputStream(tempFile)) {
            var writer = FesodSheet.write(out, UserExportDto.class)
                    .sheet("数据")
                    .build();
            // 分页查询 + 流式写入
            int page = 0;
            List<UserExportDto> batch;
            while (!(batch = userService.queryPage(cond, page++, 1000)).isEmpty()) {
                writer.write(batch);
            }
            writer.finish();

            // 上传 OSS
            String ossKey = "exports/" + taskId + ".xlsx";
            String url = fileService.upload(tempFile, ossKey);

            taskRepo.complete(taskId, url);

            // 通知用户(通过 notification 模块,自动路由到短信/站内信/邮件)
            notificationService.send(userId, "export-completed", Map.of(
                    "taskId", taskId,
                    "downloadUrl", url
            ));
        } catch (Exception e) {
            taskRepo.fail(taskId, e.getMessage());
            throw new AppException(AppCode.REPORT_EXPORT_FAILED, e, taskId.toString());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
```

### 9.8 模板管理

**小项目**:Word / Excel 模板文件放 `resources/templates/` 下,随应用打包

**企业项目**:模板存数据库 + OSS,后台可视化管理

```sql
CREATE TABLE document_templates (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id    TEXT NOT NULL DEFAULT 'default',
    code         VARCHAR(100) NOT NULL,         -- 业务代码:contract / invoice / report
    name         VARCHAR(200) NOT NULL,
    type         VARCHAR(20) NOT NULL,          -- WORD / EXCEL / PDF / OFD
    storage_key  TEXT NOT NULL,                 -- OSS 路径
    version      INT NOT NULL DEFAULT 1,
    enabled      BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, code, version)
);
```

**使用**:

```java
@Service
public class TemplateManager {
    private final DocumentTemplateRepository repo;
    private final FileService fileService;

    public InputStream load(String code) throws IOException {
        DocumentTemplate tpl = repo.findLatestByCode(code)
                .orElseThrow(() -> new AppException(AppCode.REPORT_TEMPLATE_NOT_FOUND, code));
        return fileService.download(tpl.storageKey());
    }
}
```

### 9.9 依赖与许可证速查

| 库                    | 许可证                  | 商用要求             |
|----------------------|----------------------|------------------|
| Apache Fesod         | Apache 2.0           | 免费商用             |
| Apache POI           | Apache 2.0           | 免费商用             |
| poi-tl               | Apache 2.0           | 免费商用             |
| **iText Core 9.x**   | **AGPL 3.0 / 商业双许可** | **闭源商用必须购买商业授权** |
| openhtmltopdf        | LGPL 2.1             | **免费商用**         |
| Flying Saucer        | LGPL 2.1             | 免费商用(但社区已较不活跃)   |
| ofdrw                | Apache 2.0           | 免费商用             |
| kkFileView           | Apache 2.0           | 免费商用             |
| ONLYOFFICE Community | AGPL 3.0 / 商业双许可     | 闭源商用需购买          |
| PDF.js               | Apache 2.0           | 免费商用             |
| docx-preview         | Apache 2.0           | 免费商用             |

**iText 特别注意**:

- iText 5.x / 7.x / 9.x 都是 AGPL
- 闭源 SaaS / 企业内部项目调用 iText,技术上**需购买商业授权**(约数万元人民币 / 年)
- **绕开方式**:用 openhtmltopdf(LGPL)处理 HTML → PDF 场景,iText 只用来做代码绘制(如果实在需要);或者彻底不用 iText
- **本栈默认建议**:**openhtmltopdf 优先,iText 仅在无法替代的场景(如复杂 PDF 编辑、签章)引入,此时需客户同意承担商业授权费
  **

---

## 十、异步任务与分布式调度(JobRunr)

### 10.1 JobRunr 是什么

**JobRunr** 是 Java 的后台任务调度框架,定位类似 .NET 的 Hangfire:

- **原生 Spring Boot 4 集成**:`jobrunr-spring-boot-4-starter` 自 v8.3.0+ 发布
- **内置 Dashboard Web UI**:任务监控、重试、失败回调
- **持久化基于数据库**:PostgreSQL / MySQL / MongoDB / Redis 任选
- **Virtual Threads 支持**:Java 25 下可全部跑在虚拟线程上
- **Lambda 式任务定义**:`BackgroundJob.enqueue(() -> service.doSomething())`,类型安全

**适用场景**:

- 异步任务(发邮件、推送、生成报表)
- 定时任务(每日清理、每周报告)
- 延迟任务(订单 30 分钟超时)
- 周期性巡检任务

### 10.2 依赖与配置

`infra/build.gradle` 已声明:

```groovy
api 'org.jobrunr:jobrunr-spring-boot-4-starter:8.3.0'
```

`application.yml`:

```yaml
jobrunr:
  database:
    type: sql                        # 用 PostgreSQL 存任务
    skip-create: false               # 首次自动建表
    table-prefix: jobrunr_
  job-scheduler:
    enabled: true
  background-job-server:
    enabled: true
    worker-count: 20                 # 工作线程数
    poll-interval-in-seconds: 5
    delete-succeeded-jobs-after: P7D  # 保留成功任务 7 天
    permanently-delete-deleted-jobs-after: P30D
  dashboard:
    enabled: true
    port: 8000
    username: admin
    password: ${JOBRUNR_DASHBOARD_PASSWORD:change-me}
  jobs:
    metrics:
      enabled: true                  # Micrometer 指标
```

### 10.3 定义任务

**任务类就是普通 Spring Bean**,方法无需任何注解:

```java
package com.example.myapp.notification.task;

import com.example.myapp.notification.domain.NotificationProvider;
import com.example.myapp.notification.domain.NotificationChannel;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationDispatchJob {

    private final Map<NotificationChannel, NotificationProvider> providers;

    public NotificationDispatchJob(java.util.List<NotificationProvider> providers) {
        this.providers = providers.stream()
                .collect(java.util.stream.Collectors.toMap(
                        NotificationProvider::channel, p -> p));
    }

    @Job(name = "发送短信验证码", retries = 3)
    public void sendSmsCode(String phone, String code) {
        providers.get(NotificationChannel.SMS)
                .send(phone, "sms-verify-code", Map.of("code", code));
    }

    @Job(name = "发送欢迎邮件", retries = 2)
    public void sendWelcomeEmail(String email, String name) {
        providers.get(NotificationChannel.EMAIL)
                .send(email, "email-welcome", Map.of("name", name));
    }
}
```

**`@Job` 注解参数**:

- `name`:Dashboard 上显示的任务名
- `retries`:失败重试次数(默认 10)

### 10.4 入队任务

**入队方式(推荐 Lambda 风格)**:

```java
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class UserRegistrationService {

    private final JobScheduler jobScheduler;
    private final NotificationDispatchJob dispatchJob;

    public UserRegistrationService(JobScheduler jobScheduler,
                                   NotificationDispatchJob dispatchJob) {
        this.jobScheduler = jobScheduler;
        this.dispatchJob = dispatchJob;
    }

    public void register(String phone, String email, String name) {
        // 立即入队(异步执行)
        jobScheduler.enqueue(() -> dispatchJob.sendSmsCode(phone, "123456"));

        // 延迟 5 分钟执行
        jobScheduler.schedule(
                Instant.now().plus(5, ChronoUnit.MINUTES),
                () -> dispatchJob.sendWelcomeEmail(email, name));
    }
}
```

**关键约束**:

- Lambda 里的**参数必须是可序列化的**(String、数字、UUID、自定义 POJO 带无参构造器)
- **不要传整个 Entity 对象**,传 ID 再在任务里重新查询
- **不要引用 Bean 以外的字段**,Lambda 会被序列化,外部状态无法恢复

### 10.5 定时任务(Cron)

```java
package com.example.myapp.user.task;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceJob {

    @Recurring(id = "cleanup-expired-tokens", cron = "0 0 2 * * ?")
    @Job(name = "清理过期 Token")
    public void cleanupExpiredTokens() {
        // 每天凌晨 2 点执行
    }

    @Recurring(id = "daily-report", cron = "0 0 9 * * MON")
    @Job(name = "每周一生成报表")
    public void weeklyReport() {
        // 每周一 9 点执行
    }

    @Recurring(id = "heartbeat", interval = "PT5M")
    @Job(name = "心跳检测")
    public void heartbeat() {
        // 每 5 分钟执行一次(ISO 8601 Duration)
    }
}
```

**Cron 表达式**:标准 6 位(秒 分 时 日 月 周)。

**关键特性**:

- **`@Recurring` 是声明式**,应用启动时自动注册到 JobRunr
- `id` 必须**唯一**,改了 id 会创建新任务而不删旧的
- 分布式部署时,JobRunr **自动用数据库锁保证只有一个节点执行**(不需要 ShedLock)

### 10.6 手动注册周期任务

需要动态注册(比如从配置表读取)时:

```java
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;

@Service
public class DynamicScheduler {

    private final JobScheduler jobScheduler;

    public void registerDynamicJob(String jobId, String cron, String handlerMethod) {
        jobScheduler.scheduleRecurrently(
                jobId,
                Cron.using(cron),
                ZoneId.of("Asia/Shanghai"),
                () -> dispatchJob(handlerMethod));
    }

    public void dispatchJob(String method) {
        // 根据 method 名字分发
    }
}
```

### 10.7 失败与重试

**默认行为**:

- 任务失败自动重试 10 次,指数退避(1min、2min、4min、...)
- 超过重试次数进入 **Failed** 状态,Dashboard 可手动重试
- 可通过 `@Job(retries = N)` 覆盖重试次数

**不重试特定异常**:

```java
@Job(name = "特殊任务")
public void specialJob(UUID id) throws NotRetryableException {
    try {
        // 业务
    } catch (IllegalArgumentException e) {
        // 参数错误不重试
        throw new NotRetryableException("参数非法,不重试", e);
    }
}
```

### 10.8 Dashboard 使用

启用后访问 `http://localhost:8000`(生产环境**务必**改端口或加 Nginx 认证):

- **Jobs**:所有任务列表,过滤 Succeeded/Failed/Scheduled/Processing
- **Recurring Jobs**:周期任务概览
- **Servers**:后台服务器节点(分布式部署时多节点)
- **手动操作**:重试失败任务、删除任务

**生产环境保护**:

```yaml
jobrunr:
  dashboard:
    username: ${JOBRUNR_DASH_USER}
    password: ${JOBRUNR_DASH_PASSWORD}
```

或者放到内网 VPN 后面,**不直接暴露公网**。

### 10.9 Virtual Threads 加速

Java 25 + JobRunr 8+ 可让所有 worker 跑在虚拟线程上:

```yaml
jobrunr:
  background-job-server:
    # 使用 Virtual Thread executor
    thread-type: VIRTUAL_THREADS     # 或 PLATFORM_THREADS
    worker-count: 100                # 虚拟线程下可以调得更高
```

**收益**:

- I/O 密集任务(发邮件、调 HTTP)吞吐量显著提升
- 不需要手动优化线程池大小

### 10.10 与 `@Scheduled` 的关系

**不推荐用 Spring 原生 `@Scheduled`**:

- 单节点只执行,不分布式
- 失败无重试
- 无 Dashboard 可视化
- 无法动态暂停/恢复

**JobRunr 完全覆盖 `@Scheduled` 场景**,统一用 JobRunr。唯一保留 `@Scheduled` 的情况:**极简本地工具任务**(
比如刷新本地缓存),无分布式需求。

### 10.11 与消息队列的边界

**各自适用的场景**:

| 场景                              | 选择                             |
|---------------------------------|--------------------------------|
| 本服务内部异步任务(订单超时、发通知)             | JobRunr                        |
| 跨服务事件发布订阅                       | Kafka / RabbitMQ / RocketMQ    |
| 高吞吐实时消息(百万级/秒)                  | Kafka                          |
| 简单工作流(Step A → Step B → Step C) | Flowable(见第十一章)或 JobRunr chain |
| 定时报表、巡检                         | JobRunr `@Recurring`           |

---

## 十一、工作流引擎(Flowable)

### 11.1 什么时候需要工作流引擎

**典型场景**:

- 审批流(请假、报销、采购、合同签署)
- 订单状态流转(预订单 → 待支付 → 已支付 → 发货 → 已完成)
- 政府项目的业务流程(立项、审批、公示、归档)
- 跨角色多人协作(提交 → 组长审 → 部门审 → 财务审 → 总经理审)

**不需要工作流引擎的场景**:

- 简单状态机(2-3 个状态,固定流转):用 `enum` + `switch` 足够
- 非结构化流程:靠 JobRunr chain 串联任务即可
- 高频短流程(毫秒级):工作流引擎有启动开销,不适合

### 11.2 Flowable 8.0 简介

**Flowable 8.0.0**(2026-02 发布)关键特性:

- 原生 Spring Boot 4 / Spring 7 / Jackson 3 支持
- Java 21 基线
- 遵循 BPMN 2.0 / DMN 1.3 / CMMN 1.1 标准
- 嵌入式运行,与业务共用 PostgreSQL,无需独立部署

### 11.3 依赖

`infra/build.gradle`:

```groovy
api 'org.flowable:flowable-spring-boot-starter:8.0.0'
```

**可选 BPMN 模型**:

```groovy
api 'org.flowable:flowable-bpmn-model:8.0.0'
```

`application.yml`:

```yaml
flowable:
  async-executor-activate: true
  database-schema-update: true        # 首次启动自动建表(生产用 manual,预先跑 DDL)
  check-process-definitions: true
  process:
    servlet:
      path: /flowable/process-api      # REST API 路径
  # 历史数据级别
  history-level: full                  # none/activity/audit/full
```

### 11.4 定义流程(BPMN 2.0 XML)

`src/main/resources/processes/leave-approval.bpmn20.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="com.example.myapp">

    <process id="leaveApproval" name="请假审批" isExecutable="true">

        <startEvent id="start" name="提交申请"/>

        <sequenceFlow id="flow1" sourceRef="start" targetRef="managerApproval"/>

        <userTask id="managerApproval" name="直属领导审批"
                  flowable:candidateGroups="${manager}"
                  flowable:formKey="leaveForm">
            <extensionElements>
                <flowable:formProperty id="approved" name="是否批准" type="boolean" required="true"/>
                <flowable:formProperty id="comment" name="审批意见" type="string"/>
            </extensionElements>
        </userTask>

        <sequenceFlow id="flow2" sourceRef="managerApproval" targetRef="approvalGateway"/>

        <exclusiveGateway id="approvalGateway" name="是否批准?"/>

        <sequenceFlow id="approved" sourceRef="approvalGateway" targetRef="hrRegister">
            <conditionExpression xsi:type="tFormalExpression"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                ${approved == true}
            </conditionExpression>
        </sequenceFlow>

        <sequenceFlow id="rejected" sourceRef="approvalGateway" targetRef="notifyReject">
            <conditionExpression xsi:type="tFormalExpression"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                ${approved == false}
            </conditionExpression>
        </sequenceFlow>

        <serviceTask id="hrRegister" name="HR 登记"
                     flowable:delegateExpression="${hrRegisterTask}"/>
        <sequenceFlow id="flow3" sourceRef="hrRegister" targetRef="end"/>

        <serviceTask id="notifyReject" name="通知驳回"
                     flowable:delegateExpression="${notifyRejectTask}"/>
        <sequenceFlow id="flow4" sourceRef="notifyReject" targetRef="end"/>

        <endEvent id="end" name="结束"/>
    </process>
</definitions>
```

**BPMN 核心元素**:

- `startEvent` / `endEvent`:流程起止
- `userTask`:需要人工介入的任务
- `serviceTask`:自动执行的任务(调 Java Bean)
- `exclusiveGateway`:排他网关(if-else)
- `parallelGateway`:并行网关(多分支同时执行)
- `sequenceFlow`:连接线,可带条件

### 11.5 ServiceTask 实现

```java
// hr/workflow/HrRegisterTask.java
package com.example.myapp.hr.workflow;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("hrRegisterTask")
public class HrRegisterTask implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String applicant = (String) execution.getVariable("applicant");
        String days = (String) execution.getVariable("days");

        // 登记请假记录到业务表
        // leaveRecordRepository.save(...);

        execution.setVariable("registeredAt", java.time.Instant.now().toString());
    }
}
```

```java
// hr/workflow/NotifyRejectTask.java
package com.example.myapp.hr.workflow;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("notifyRejectTask")
public class NotifyRejectTask implements JavaDelegate {

    private final NotificationService notificationService;

    public NotifyRejectTask(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String applicant = (String) execution.getVariable("applicant");
        String comment = (String) execution.getVariable("comment");
        notificationService.notify(applicant, "请假申请已驳回: " + comment);
    }
}
```

### 11.6 启动流程 & 操作任务

```java
package com.example.myapp.hr.workflow;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaveApprovalService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public LeaveApprovalService(RuntimeService runtimeService, TaskService taskService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    public String startLeaveApproval(String applicant, String managerId, int days, String reason) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("applicant", applicant);
        variables.put("manager", managerId);
        variables.put("days", days);
        variables.put("reason", reason);

        var instance = runtimeService.startProcessInstanceByKey("leaveApproval", variables);
        return instance.getId();
    }

    public List<Task> listPendingTasks(String userId) {
        return taskService.createTaskQuery()
                .taskCandidateOrAssigned(userId)
                .orderByTaskCreateTime().desc()
                .list();
    }

    public void approve(String taskId, String userId, boolean approved, String comment) {
        // 先认领任务(如果 candidateGroup 式未分配)
        taskService.claim(taskId, userId);

        // 提交审批结果
        Map<String, Object> vars = new HashMap<>();
        vars.put("approved", approved);
        vars.put("comment", comment);
        taskService.complete(taskId, vars);
    }
}
```

### 11.7 多租户支持

Flowable 原生支持多租户,只需在启动流程时带 `tenantId`:

```java
runtimeService.startProcessInstanceByKeyAndTenantId(
        "leaveApproval",
        variables,
        tenantId);
```

查询也要加过滤:

```java
taskService.createTaskQuery()
        .taskTenantId(tenantId)
        .taskCandidateOrAssigned(userId)
        .list();
```

### 11.8 历史查询

```java
import org.flowable.engine.HistoryService;

@Service
public class ProcessHistoryService {

    private final HistoryService historyService;

    public List<HistoricProcessInstance> listCompletedProcesses(String processKey, int limit) {
        return historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(processKey)
                .finished()
                .orderByProcessInstanceEndTime().desc()
                .listPage(0, limit);
    }

    public List<HistoricTaskInstance> listTaskHistory(String processInstanceId) {
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceStartTime().asc()
                .list();
    }
}
```

### 11.9 流程版本管理

**原则**:

- BPMN 文件放 `src/main/resources/processes/`,应用启动时自动部署
- **流程定义可以升级**,旧实例继续跑旧版本,新实例跑新版本
- 修改流程要**新建版本**,不要直接改已发布版本

```java
// 手动部署(热更新)
repositoryService.createDeployment()
        .name("请假审批 v2")
        .addClasspathResource("processes/leave-approval-v2.bpmn20.xml")
        .deploy();
```

### 11.10 BPMN 建模工具

**推荐**:

- **Flowable Modeler**:官方 Web IDE(但不推荐嵌入到业务应用中)
- **bpmn.io**:开源的浏览器内 BPMN 编辑器,可集成到自己的前端
- **IntelliJ IDEA 插件**:Activiti BPMN Visualizer(Flowable 兼容)

**原则**:

- **BPMN 文件手写或建模器生成二选一**,不要混用
- 建议用建模器画,导出 XML 后**人工审查**
- XML 纳入 Git 版本控制

### 11.11 Flowable 数据库表

启动后自动创建约 40 张表,主要类别:

| 前缀         | 用途                        |
|------------|---------------------------|
| `ACT_RE_*` | Repository(流程定义、部署)       |
| `ACT_RU_*` | Runtime(运行时实例、任务、变量)      |
| `ACT_HI_*` | History(历史记录)             |
| `ACT_ID_*` | Identity(用户、组;通常禁用用业务表代替) |
| `ACT_GE_*` | General(通用表)              |

**生产环境建议**:

- **禁用 `ACT_ID_*`**(身份管理),用业务的 `users`/`roles` 表代替
- 用同一个 PostgreSQL 数据库但独立 schema:`flowable` schema 与 `public` schema 分开
- 历史数据定期归档,避免 `ACT_HI_*` 表膨胀

---

## 十二、配置管理

### 12.1 分层配置原则

**四层配置来源,优先级由高到低**:

1. **命令行参数**:`--server.port=9090`
2. **环境变量**:`SERVER_PORT=9090`
3. **外部配置文件**:K8s ConfigMap / 挂载到 `/config` 的 application.yml
4. **内置 application.yml**:打包到 jar 的默认值

**原则**:

- **默认值**写在打包的 `application.yml`
- **环境差异**(URL、密钥)用环境变量覆盖
- **敏感信息**(密码、Token)**只**通过环境变量或 K8s Secret 注入,**不写任何配置文件**

### 12.2 `@ConfigurationProperties` + Record

**定义类型安全的配置类**:

```java
package com.example.myapp.payment.provider.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "payment.provider.wechat")
public record WechatProperties(
        String appId,
        String appSecret,
        String mchId,
        Duration tokenTtl,
        ApiConfig api
) {
    public record ApiConfig(
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout,
            int maxRetries
    ) {}
}
```

**注册到 Spring**:

```java
package com.example.myapp.payment.provider.wechat;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WechatProperties.class)
public class WechatConfig {
}
```

**使用**:

```java
@Service
public class WechatService {

    private final WechatProperties properties;

    public WechatService(WechatProperties properties) {
        this.properties = properties;
    }

    public String getAppId() {
        return properties.appId();
    }
}
```

**配置 YAML**:

```yaml
payment:
  provider:
    wechat:
      app-id: ${WECHAT_APP_ID}
      app-secret: ${WECHAT_APP_SECRET}
      mch-id: ${WECHAT_MCH_ID}
      token-ttl: PT7200S      # ISO 8601 Duration
      api:
        base-url: https://api.weixin.qq.com
        connect-timeout: PT3S
        read-timeout: PT10S
        max-retries: 3
```

**`record` 作为配置类的约束**:

- Record 天然 immutable,配置对象不应被修改
- Spring Boot 原生支持 record 构造器绑定(`@ConstructorBinding` 自动推断)
- 无 getter / setter / equals / hashCode 样板

### 12.3 `@Value` 的取舍

**不推荐 `@Value` 大规模使用**:

- 每个字段要单独注入,冗余
- 类型转换依赖 SpEL,编译期不安全
- Refactor 时 IDE 跟踪不完整

**只在极简场景用**:

```java
@Value("${server.port}")
private int port;
```

**更多字段时,统一用 `@ConfigurationProperties`**。

### 12.4 配置校验

**`@ConfigurationProperties` + Jakarta Validation**:

```java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "payment.provider.wechat")
@Validated
public record WechatProperties(
        @NotBlank String appId,
        @NotBlank String appSecret,
        @NotBlank String mchId,
        @NotNull @Positive Duration tokenTtl,
        @NotNull ApiConfig api
) {
    public record ApiConfig(
            @NotBlank String baseUrl,
            @NotNull @Positive Duration connectTimeout,
            @NotNull @Positive Duration readTimeout,
            @Positive int maxRetries
    ) {}
}
```

**启动时校验失败直接报错**,不会等到运行时 NPE。

### 12.5 Profile 组合

**多 Profile 同时激活**:

```yaml
spring:
  profiles:
    active: prod,asia,oss-cos
```

**各 Profile 单独文件**:

- `application-prod.yml`:生产环境参数
- `application-asia.yml`:亚太区域配置
- `application-oss-cos.yml`:腾讯云 COS 存储(vs `application-oss-oss.yml` 阿里云 OSS)

**条件 Bean**:

```java
// file/provider/cos/TencentCosConfig.java
@Configuration
@Profile("oss-cos")
public class TencentCosConfig { ... }
```

```java
// file/provider/oss/AliyunOssConfig.java
@Configuration
@Profile("oss-oss")
public class AliyunOssConfig { ... }
```

### 12.6 配置热更新(Spring Cloud Config / Nacos)

**本栈典型场景通常不需要热更新**:

- 大多数业务改配置需要重启(修改 schema、改第三方密钥等)
- 热更新复杂度高,引入 Nacos 要额外维护
- **重启服务是常规操作**,不是性能瓶颈

**真正需要热更新的场景**:

- 流量切换(灰度、A/B)
- 动态开关(Feature Flag)

**推荐方案**:

- **简单场景**:用数据库表 + 定时刷新(或 Redis pub/sub 广播)
- **成熟场景**:Nacos / Apollo(需单独部署)
- 不要为了热更新引入 Spring Cloud 全家桶

### 12.7 敏感信息处理

**禁止**:

- ❌ 密码、密钥写进 Git 仓库
- ❌ 用明文存储到配置文件
- ❌ 日志里打印敏感配置

**推荐**:

```yaml
spring:
  datasource:
    password: ${DB_PASSWORD}      # 环境变量
```

**K8s Secret**:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: myapp-secrets
type: Opaque
stringData:
  DB_PASSWORD: ...
  JWT_SECRET: ...
  WECHAT_APP_SECRET: ...
```

**阿里云/腾讯云 KMS**:通过 SDK 启动时拉取解密。

### 12.8 本地开发的 application-local.yml

**原则**:**永远不入版控**,在 `.gitignore` 里排除。

**提供模板** `application-local.yml.template` 入版,让新人复制一份:

```yaml
# application-local.yml.template
# Copy to application-local.yml and fill in your local credentials
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp_dev
    username: postgres
    password: postgres

  data:
    redis:
      host: localhost
      port: 6379

payment:
  provider:
    wechat:
      app-id: YOUR_TEST_APP_ID
      app-secret: YOUR_TEST_APP_SECRET
```

---

## 十三、可观测性(日志、指标、链路)

### 13.1 三件套职责

| 维度     | 实现                           | 用途             |
|--------|------------------------------|----------------|
| **日志** | SLF4J + Logback + MDC        | 问题排查、审计追溯      |
| **指标** | Micrometer + Prometheus      | 性能监控、容量规划、告警   |
| **链路** | OpenTelemetry + Jaeger/Tempo | 分布式请求追踪、性能瓶颈定位 |

**一条原则**:**三者共用同一个 TraceId**,日志/指标/链路可以互相跳转。

### 13.2 Logback 配置

`app/src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <!-- 引入 Spring Boot 的默认配置 -->
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- 日志目录,可通过环境变量覆盖 -->
    <springProperty name="LOG_HOME" source="logging.file.path" defaultValue="./logs"/>
    <springProperty name="APP_NAME" source="spring.application.name" defaultValue="myapp"/>

    <!-- 带 TraceId 的格式 -->
    <property name="CONSOLE_LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} %5level [${APP_NAME},%X{traceId:-},%X{spanId:-}] [%thread] %-40.40logger{39} : %msg%n"/>

    <property name="FILE_LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} %5level [${APP_NAME},%X{traceId:-},%X{spanId:-}] [%thread] %-40.40logger{39} : %msg%n"/>

    <!-- 控制台 Appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <!-- 滚动文件 Appender -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_HOME}/${APP_NAME}.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_HOME}/${APP_NAME}-%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>${FILE_LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <!-- 异步 Appender(高性能场景) -->
    <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="FILE"/>
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <neverBlock>false</neverBlock>
    </appender>

    <!-- JSON 格式(容器/ELK 场景) -->
    <springProfile name="prod">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <customFields>{"app":"${APP_NAME}"}</customFields>
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>spanId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <includeMdcKeyName>tenantId</includeMdcKeyName>
            </encoder>
        </appender>

        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>

    <springProfile name="!prod">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
            <appender-ref ref="ASYNC_FILE"/>
        </root>
    </springProfile>

    <!-- 业务日志级别 -->
    <logger name="com.example.myapp" level="DEBUG"/>
    <logger name="org.jooq.Constants" level="WARN"/>
    <logger name="org.flowable" level="INFO"/>

</configuration>
```

**JSON 日志依赖(生产环境)**:

```groovy
// app/build.gradle
implementation 'net.logstash.logback:logstash-logback-encoder:8.0'
```

### 13.3 日志使用规范

```java
package com.example.myapp.user.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    public User create(String tenantId, CreateUserRequest req) {
        // 正确:占位符 + 参数,不做字符串拼接
        log.info("创建用户开始: tenantId={}, email={}", tenantId, req.email());

        try {
            var user = doCreate(tenantId, req);
            log.info("创建用户成功: id={}", user.id());
            return user;
        } catch (AppException e) {
            // 业务异常只 warn,不 error(不是 bug)
            log.warn("创建用户失败: tenantId={}, email={}, code={}",
                     tenantId, req.email(), e.getCode());
            throw e;
        } catch (Exception e) {
            // 系统异常 error + 完整堆栈
            log.error("创建用户系统异常: tenantId={}, email={}",
                      tenantId, req.email(), e);
            throw e;
        }
    }
}
```

**等级使用准则**:

| 等级      | 使用场景                              |
|---------|-----------------------------------|
| `ERROR` | 系统异常、未预期的 RuntimeException,需要运维介入 |
| `WARN`  | 业务异常、重试中、降级触发,不影响可用性但值得关注         |
| `INFO`  | 关键业务节点、生命周期事件(启动、关闭、重要状态变更)       |
| `DEBUG` | 开发调试信息,生产默认不开                     |
| `TRACE` | 最详细的流程日志,生产绝不开                    |

**禁止**:

- ❌ `log.info("user: " + user)`:字符串拼接,占内存又慢
- ❌ `log.error("错误")`:无上下文,无堆栈
- ❌ 在循环里打 INFO 级日志(量过大)
- ❌ 打印密码、Token、身份证等敏感信息

### 13.4 MDC 上下文

**MDC(Mapped Diagnostic Context)**:把请求级上下文塞进日志。

**自动注入 traceId/spanId**:Spring Boot 4 + Micrometer Tracing 自动处理,不需要手写。

**业务上下文**(userId、tenantId)需要手动注入:

```java
package com.example.myapp.common.web;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class MdcFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (StpUtil.isLogin()) {
                MDC.put("userId", StpUtil.getLoginIdAsString());
                Object tenantId = StpUtil.getSession().get("tenantId");
                if (tenantId != null) {
                    MDC.put("tenantId", tenantId.toString());
                }
            }

            HttpServletRequest req = (HttpServletRequest) request;
            String clientIp = extractClientIp(req);
            MDC.put("clientIp", clientIp);

            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String extractClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
```

**Virtual Thread 注意**:`MDC` 的默认实现基于 `InheritableThreadLocal`,虚拟线程下可能丢失。
解决:Logback 1.5+ 自动感知 virtual thread,无需额外配置;手动传播用 `MDC.getCopyOfContextMap()`。

### 13.5 Actuator 暴露

```yaml
management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: health,info,metrics,prometheus,env,loggers,threaddump,heapdump,mappings

  endpoint:
    health:
      show-details: when-authorized
      show-components: always
      probes:
        enabled: true           # K8s liveness/readiness
    prometheus:
      access: read-only

  info:
    env:
      enabled: true
    git:
      mode: full
    build:
      enabled: true
    java:
      enabled: true
    os:
      enabled: true

  metrics:
    tags:
      application: ${spring.application.name}
      env: ${spring.profiles.active}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.75, 0.95, 0.99
      slo:
        http.server.requests: 100ms,300ms,500ms,1s,3s

  observations:
    http:
      server:
        requests:
          name: http.server.requests
```

**安全**:`/actuator/**` **必须不对公网开放**,要么通过 Sa-Token 拦截器拦,要么单独开管理端口:

```yaml
management:
  server:
    port: 9090   # 管理端口,只在内网开放
    address: 127.0.0.1
```

### 13.6 健康检查

**K8s 探针**:

```yaml
# deployment.yml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 9090
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 9090
  initialDelaySeconds: 10
  periodSeconds: 5
```

**自定义健康检查**:

```java
package com.example.myapp.infra.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("wechatApi")
public class WechatHealthIndicator implements HealthIndicator {

    private final WechatApiClient client;

    public WechatHealthIndicator(WechatApiClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            var resp = client.ping();
            return Health.up()
                    .withDetail("latencyMs", resp.latencyMs())
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
```

### 13.7 Micrometer 指标

**Spring Boot 4 自动暴露关键指标**:

- `http.server.requests`:请求耗时、QPS、状态码分布
- `jvm.memory.used` / `jvm.gc.*`:JVM 内存与 GC
- `hikaricp.connections.*`:连接池状态
- `jdbc.connections.*`:JDBC 连接
- `cache.gets` / `cache.puts`:Spring Cache 命中率
- `tomcat.threads.*`:Tomcat 线程池

**自定义业务指标**:

```java
package com.example.myapp.user.domain;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final Counter registerCounter;
    private final Timer authTimer;

    public UserService(MeterRegistry registry) {
        this.registerCounter = Counter.builder("user.register")
                .description("用户注册数")
                .tag("app", "myapp")
                .register(registry);

        this.authTimer = Timer.builder("user.auth")
                .description("用户登录耗时")
                .publishPercentileHistogram()
                .register(registry);
    }

    public User register(CreateUserRequest req) {
        var user = doRegister(req);
        registerCounter.increment();
        return user;
    }

    public User authenticate(String email, String password) {
        return authTimer.record(() -> doAuthenticate(email, password));
    }
}
```

**通过注解**(更简洁):

```java
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;

@Service
public class UserService {

    @Counted(value = "user.register", description = "用户注册数")
    public User register(CreateUserRequest req) { ... }

    @Timed(value = "user.auth", percentiles = {0.5, 0.95, 0.99})
    public User authenticate(String email, String password) { ... }
}
```

### 13.8 Prometheus 集成

**端点**:`GET /actuator/prometheus`

**Prometheus 配置** `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'myapp'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['myapp:9090']
```

**常用 PromQL**:

```promql
# P99 响应时间
histogram_quantile(0.99,
  sum(rate(http_server_requests_seconds_bucket[5m]))
  by (le, uri, method))

# QPS
sum(rate(http_server_requests_seconds_count[1m])) by (method, uri)

# 5xx 错误率
sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m]))
  / sum(rate(http_server_requests_seconds_count[1m]))

# JVM 堆使用率
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

# HikariCP 活跃连接
hikaricp_connections_active
```

### 13.9 Grafana 看板

**推荐 Dashboard**:

- **JVM(Micrometer)**: ID `4701` - JVM 全指标
- **Spring Boot 2.1 System Monitor**: ID `11378` - 系统级监控
- **HikariCP**: ID `6083` - 连接池监控

本地开发 Grafana + Prometheus compose:

```yaml
# compose.yml(追加)
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9091:9090"
    volumes:
      - ./ops/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - grafana_data:/var/lib/grafana

volumes:
  grafana_data:
```

### 13.10 分布式链路追踪

**Spring Boot 4 默认集成 Micrometer Tracing**。

**依赖**(`app/build.gradle`):

```groovy
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
```

**配置**:

```yaml
management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0     # 开发环境 100% 采样;生产 0.1~0.01
    propagation:
      produce: W3C, B3
      consume: W3C, B3

  otlp:
    tracing:
      endpoint: http://jaeger:4318/v1/traces
      transport: http      # 或 grpc
```

**自动追踪的范围**:

- HTTP 入站请求(Controller)
- HTTP 出站请求(RestClient、HTTP Interface Client)
- 数据库查询(通过 HikariCP + JDBC instrumentation)
- Redis 操作(通过 Lettuce instrumentation)

**业务层手动埋点**:

```java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;

@Service
public class UserService {

    private final ObservationRegistry registry;

    // 注解式(推荐)
    @Observed(name = "user.register", contextualName = "user-registration")
    public User register(CreateUserRequest req) { ... }

    // 手动式(需要自定义 tag)
    public User authenticate(String email, String password) {
        return Observation.createNotStarted("user.authenticate", registry)
                .lowCardinalityKeyValue("auth.method", "password")
                .observe(() -> doAuthenticate(email, password));
    }
}
```

### 13.11 Jaeger 本地部署

```yaml
# compose.yml(追加)
  jaeger:
    image: jaegertracing/all-in-one:latest
    ports:
      - "16686:16686"       # Jaeger UI
      - "4317:4317"         # OTLP gRPC
      - "4318:4318"         # OTLP HTTP
    environment:
      COLLECTOR_OTLP_ENABLED: true
```

访问 `http://localhost:16686`。

### 13.12 SQL 慢查询日志

**jOOQ 执行监听器**:

```java
package com.example.myapp.infra.jooq;

import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class SlowSqlListener implements ExecuteListener {

    private static final Logger log = LoggerFactory.getLogger("SLOW_SQL");
    private static final Duration THRESHOLD = Duration.ofMillis(500);

    private final ThreadLocal<Instant> startHolder = new ThreadLocal<>();

    @Override
    public void executeStart(ExecuteContext ctx) {
        startHolder.set(Instant.now());
    }

    @Override
    public void executeEnd(ExecuteContext ctx) {
        Instant start = startHolder.get();
        if (start == null) return;
        Duration elapsed = Duration.between(start, Instant.now());
        startHolder.remove();

        if (elapsed.compareTo(THRESHOLD) > 0) {
            log.warn("慢 SQL 耗时 {}ms: {}",
                    elapsed.toMillis(), ctx.sql());
        }
    }
}
```

**注册到 jOOQ**:

```java
package com.example.myapp.infra.jooq;

import org.jooq.conf.Settings;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqConfig {

    @Bean
    public DefaultConfigurationCustomizer jooqCustomizer(SlowSqlListener listener) {
        return config -> config.set(listener);
    }
}
```

---

## 十四、错误处理与 API 响应规范

### 14.1 设计原则

1. **错误码与 HTTP 状态码分离**:HTTP 状态反映协议层,业务 code 反映业务层
2. **错误码编码空间按模块划分**:10xxx 系统,20xxx 用户,30xxx 订单...
3. **统一格式**:所有错误响应遵守 `R<T>` 契约,前端统一处理
4. **不泄漏内部细节**:堆栈、SQL、Path 只写日志,不返回前端
5. **国际化友好**:错误消息支持多语言 message key

### 14.2 错误码定义

```java
package com.example.myapp.common.exception;

public enum AppCode {

    // ========== 系统级 1xxxx ==========
    SUCCESS(0, "success"),
    SYSTEM_ERROR(10000, "系统异常"),
    SYSTEM_BUSY(10001, "系统繁忙,请稍后重试"),
    INVALID_PARAMETER(10002, "参数错误"),
    RESOURCE_NOT_FOUND(10003, "资源不存在"),
    RATE_LIMITED(10004, "请求过于频繁"),
    METHOD_NOT_SUPPORTED(10005, "请求方法不支持"),
    MEDIA_TYPE_NOT_SUPPORTED(10006, "请求类型不支持"),
    REQUEST_BODY_MALFORMED(10007, "请求体格式错误"),

    // ========== 认证授权 4xxxx(HTTP 4xx 对应)==========
    AUTH_UNAUTHORIZED(40100, "未登录"),
    AUTH_TOKEN_INVALID(40101, "Token 无效"),
    AUTH_TOKEN_EXPIRED(40102, "Token 已过期"),
    AUTH_BAD_CREDENTIALS(40103, "账号或密码错误"),
    AUTH_ACCOUNT_DISABLED(40104, "账号已禁用"),
    AUTH_ACCOUNT_LOCKED(40105, "账号已锁定"),
    AUTH_KICKED_OUT(40106, "账号已在其他设备登录"),

    AUTH_FORBIDDEN(40300, "权限不足"),
    AUTH_PERMISSION_DENIED(40301, "无权限执行此操作: {0}"),
    AUTH_ROLE_REQUIRED(40302, "需要特定角色: {0}"),
    AUTH_TENANT_MISMATCH(40303, "无权访问该租户数据"),

    // ========== 横切能力 5xxxx ==========
    // audit 50xxx
    AUDIT_LOG_FAILED(50001, "审计日志写入失败"),
    // dict 51xxx
    DICT_NOT_FOUND(51001, "字典不存在: {0}"),
    DICT_ITEM_NOT_FOUND(51002, "字典项不存在: {0}/{1}"),

    // ========== 能力域 6xxxx ==========
    // payment 60xxx
    PAYMENT_CHANNEL_NOT_SUPPORTED(60001, "不支持的支付通道: {0}"),
    PAYMENT_ORDER_NOT_FOUND(60002, "支付单不存在: {0}"),
    PAYMENT_AMOUNT_INVALID(60003, "支付金额非法"),
    PAYMENT_ALREADY_PAID(60004, "订单已支付"),
    PAYMENT_FAILED(60005, "支付失败: {0}"),
    PAYMENT_NOTIFY_VERIFY_FAILED(60006, "支付回调签名验证失败"),
    PAYMENT_REFUND_FAILED(60007, "退款失败: {0}"),
    // notification 61xxx
    NOTIFY_CHANNEL_NOT_SUPPORTED(61001, "不支持的通知通道: {0}"),
    NOTIFY_TEMPLATE_NOT_FOUND(61002, "通知模板不存在: {0}"),
    NOTIFY_SEND_FAILED(61003, "通知发送失败: {0}"),
    NOTIFY_RATE_LIMITED(61004, "通知发送频率限制"),
    // file 62xxx
    FILE_NOT_FOUND(62001, "文件不存在: {0}"),
    FILE_SIZE_EXCEEDED(62002, "文件大小超限: 最大 {0} MB"),
    FILE_TYPE_NOT_ALLOWED(62003, "不支持的文件类型: {0}"),
    FILE_UPLOAD_FAILED(62004, "文件上传失败"),
    // llm 63xxx
    LLM_PROVIDER_ERROR(63001, "LLM 调用失败: {0}"),
    LLM_CONTEXT_TOO_LONG(63002, "上下文超出模型限制"),
    // location 64xxx
    LOCATION_PROVIDER_ERROR(64001, "地图服务调用失败: {0}"),

    // ========== 功能域 7xxxx ==========
    // workflow 70xxx
    WORKFLOW_PROCESS_NOT_FOUND(70001, "流程定义不存在: {0}"),
    WORKFLOW_TASK_NOT_FOUND(70002, "流程任务不存在: {0}"),
    WORKFLOW_NO_PERMISSION(70003, "无权处理该流程任务"),
    // report 71xxx
    REPORT_TEMPLATE_NOT_FOUND(71001, "报表模板不存在: {0}"),
    REPORT_EXPORT_FAILED(71002, "报表导出失败"),
    // search 72xxx
    SEARCH_INDEX_FAILED(72001, "搜索索引构建失败"),

    // ========== 业务域 2xxxx ~ 3xxxx ==========
    // user 20xxx
    USER_NOT_FOUND(20001, "用户不存在: {0}"),
    USER_EMAIL_EXISTS(20002, "邮箱已注册: {0}"),
    USER_PHONE_EXISTS(20003, "手机号已注册: {0}"),
    USER_PASSWORD_INVALID(20004, "密码格式不符合要求"),
    USER_STATUS_INVALID(20005, "用户状态不允许此操作"),

    // order 21xxx
    ORDER_NOT_FOUND(21001, "订单不存在: {0}"),
    ORDER_STATUS_INVALID(21002, "订单状态不允许此操作"),
    ORDER_PROCESSING(21003, "订单处理中,请稍后"),
    ORDER_EXPIRED(21004, "订单已过期"),

    // product 22xxx
    PRODUCT_NOT_FOUND(22001, "商品不存在: {0}"),
    PRODUCT_OUT_OF_STOCK(22002, "商品库存不足: {0}"),
    PRODUCT_OFFLINE(22003, "商品已下架");

    private final int code;
    private final String message;

    AppCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }

    /** 填充占位符:{0} {1} 等 */
    public String format(Object... args) {
        if (args == null || args.length == 0) return message;
        return java.text.MessageFormat.format(message, args);
    }
}
```

**错误码分段规范**:

| 段                 | 用途                                                                                  |
|-------------------|-------------------------------------------------------------------------------------|
| `0`               | 成功                                                                                  |
| `1xxxx`           | 系统级通用错误                                                                             |
| `2xxxx` ~ `3xxxx` | 业务域(user `20xxx`,order `21xxx`,product `22xxx`...)                                  |
| `4xxxx`           | 认证授权(与 HTTP 4xx 对应)                                                                 |
| `5xxxx`           | 横切能力(audit `50xxx`,dict `51xxx`,tenant `52xxx`...)                                  |
| `6xxxx`           | 能力域(payment `60xxx`,notification `61xxx`,file `62xxx`,llm `63xxx`,location `64xxx`) |
| `7xxxx`           | 功能域(workflow `70xxx`,report `71xxx`,search `72xxx`)                                 |

**原则**:

- 新模块占用新段,**一个模块一段**(万位起步,留足扩展空间)
- 错误码 **只增不删**,删除会破坏客户端已有逻辑
- message 支持 `{0}` `{1}` 占位符,通过 `format(args)` 填充

### 14.3 业务异常基类

```java
package com.example.myapp.common.exception;

public class AppException extends RuntimeException {

    private final int code;
    private final String message;

    public AppException(AppCode appCode) {
        super(appCode.getMessage());
        this.code = appCode.getCode();
        this.message = appCode.getMessage();
    }

    public AppException(AppCode appCode, Object... args) {
        super(appCode.format(args));
        this.code = appCode.getCode();
        this.message = appCode.format(args);
    }

    public AppException(AppCode appCode, Throwable cause, Object... args) {
        super(appCode.format(args), cause);
        this.code = appCode.getCode();
        this.message = appCode.format(args);
    }

    public int getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}
```

**使用**:

```java
if (user == null) {
    throw new AppException(AppCode.USER_NOT_FOUND, id);
}

// 异常链:保留底层异常以供排查
try {
    wechatPayClient.prepay(req);
} catch (IOException e) {
    throw new AppException(AppCode.PAYMENT_FAILED, e, "微信支付下单");
}
```

### 14.4 全局异常处理器

```java
package com.example.myapp.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.example.myapp.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- 业务异常(400)----

    @ExceptionHandler(AppException.class)
    public ResponseEntity<R<Void>> handleApp(AppException ex, HttpServletRequest req) {
        log.warn("业务异常: path={}, code={}, message={}",
                req.getRequestURI(), ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ex.getCode(), ex.getMessage()));
    }

    // ---- 参数校验(400)----

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleMethodArgNotValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(AppCode.INVALID_PARAMETER.getCode(), msg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(AppCode.INVALID_PARAMETER.getCode(), msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(AppCode.REQUEST_BODY_MALFORMED));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(AppCode.INVALID_PARAMETER.getCode(),
                        "缺少参数: " + ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(AppCode.INVALID_PARAMETER.getCode(),
                        "参数类型错误: " + ex.getName()));
    }

    // ---- 认证授权(Sa-Token)----

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<R<Void>> handleNotLogin(NotLoginException ex) {
        AppCode code = switch (ex.getType()) {
            case NotLoginException.NOT_TOKEN      -> AppCode.AUTH_UNAUTHORIZED;
            case NotLoginException.INVALID_TOKEN  -> AppCode.AUTH_TOKEN_INVALID;
            case NotLoginException.TOKEN_TIMEOUT  -> AppCode.AUTH_TOKEN_EXPIRED;
            case NotLoginException.BE_REPLACED    -> AppCode.AUTH_KICKED_OUT;
            case NotLoginException.KICK_OUT       -> AppCode.AUTH_KICKED_OUT;
            default                                -> AppCode.AUTH_UNAUTHORIZED;
        };
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(R.fail(code));
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<R<Void>> handleNotPermission(NotPermissionException ex) {
        log.warn("权限不足: permission={}", ex.getPermission());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(AppCode.AUTH_PERMISSION_DENIED, ex.getPermission()));
    }

    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<R<Void>> handleNotRole(NotRoleException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(AppCode.AUTH_ROLE_REQUIRED, ex.getRole()));
    }

    // ---- 路由(404/405)----

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<R<Void>> handleNotFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(AppCode.RESOURCE_NOT_FOUND.getCode(),
                        "路径不存在: " + ex.getRequestURL()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(R.fail(AppCode.METHOD_NOT_SUPPORTED.getCode(),
                        "不支持的方法: " + ex.getMethod()));
    }

    // ---- 数据库异常 ----

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<R<Void>> handleDataAccess(DataAccessException ex, HttpServletRequest req) {
        log.error("数据库异常: path={}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(AppCode.SYSTEM_ERROR));
    }

    // ---- 兜底(500)----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleUnknown(Exception ex, HttpServletRequest req) {
        log.error("[ALERT_P1] 未预期异常: path={}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(AppCode.SYSTEM_ERROR));
    }
}
```

**关键点**:

- `R.fail(AppCode)`:最简写法,直接用枚举定义的 code + message
- `R.fail(AppCode, args)`:填充 `{0}` `{1}` 占位符(如 `R.fail(AppCode.PAYMENT_CHANNEL_NOT_SUPPORTED, "WECHAT_PAY")` →
  message 为 "不支持的支付通道: WECHAT_PAY")
- `R.fail(code, message)`:自定义 message(如参数校验场景,message 是动态拼接的字段错误列表)
- **`@RestControllerAdvice` 只拦截 Controller 层的异常**,过滤器层异常不会被捕获(见 14.5)
- Sa-Token `NotLoginException` 的 `type` 字段区分具体原因
- 兜底 `Exception.class` 必须放**最后**,避免抢先捕获子类
- 所有响应通过 `R.fail(...)` 构造,**自动带上 traceId**(MDC 读取)

### 14.5 过滤器层异常处理

**场景**:Filter 阶段抛异常(如 CORS 失败、流量限制),`@ExceptionHandler` 不生效。

**解决**:自定义 `HandlerExceptionResolver` 或用 Spring Security 的 `AuthenticationEntryPoint`:

```java
package com.example.myapp.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.myapp.common.exception.AppCode;
import com.example.myapp.common.result.R;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper;

    public JsonAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(jakarta.servlet.http.HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                mapper.writeValueAsString(R.fail(AppCode.AUTH_UNAUTHORIZED)));
    }
}
```

### 14.6 错误响应格式

**统一结构**:

```json
{
    "code": 20001,
    "message": "用户不存在: 550e8400-e29b-41d4-a716-446655440000",
    "data": null,
    "traceId": "8f3a2c1b9d4e5f6a7b8c9d0e1f2a3b4c"
}
```

**字段说明**:

| 字段        | 必选 | 说明                                        |
|-----------|----|-------------------------------------------|
| `code`    | ✅  | 业务错误码(0 成功,非 0 失败,定义见 `AppCode`)          |
| `message` | ✅  | 错误信息,面向用户展示                               |
| `data`    | -  | 成功时返回数据;失败时为 `null`                       |
| `traceId` | ✅  | 链路 ID,由 MDC 注入,客户反馈问题时客服 / 运维凭此一键定位完整请求链路 |

**字段级错误处理**(复杂表单场景):

默认做法是**拼到 `message`**(如 `"email: 邮箱格式错误; password: 密码长度 8-72"`),前端 toast 展示即可。

**只在必要时**(如复杂表单 + 字段高亮需求)用扩展结构:

```java
public record RDetail<T>(
        int code,
        String message,
        T data,
        String traceId,
        Map<String, String> errors
) {}

// 使用
Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
                FieldError::getField,
                FieldError::getDefaultMessage));

return new RDetail<>(
        AppCode.INVALID_PARAMETER.getCode(),
        "参数校验失败",
        null,
        MDC.get("traceId"),
        errors
);
```

响应:

```json
{
    "code": 10002,
    "message": "参数校验失败",
    "data": null,
    "traceId": "8f3a2c1b9d4e5f6a",
    "errors": {
        "email": "邮箱格式错误",
        "password": "密码长度 8-72"
    }
}
```

**原则**:大多数接口用标准 `R<T>` 四字段就够,`RDetail` 只在**字段高亮表单**场景才用。

### 14.7 前端处理契约

**推荐的前端拦截器逻辑**:

```typescript
// 伪代码示意
axios.interceptors.response.use(
    (res) => {
        if (res.data.code === 0) return res.data.data;
        // 统一 toast 错误
        toast.error(res.data.message);
        return Promise.reject(res.data);
    },
    (err) => {
        if (err.response?.status === 401) {
            router.push('/login');
        } else if (err.response?.status === 403) {
            toast.error('权限不足');
        } else {
            toast.error('网络异常');
        }
        return Promise.reject(err);
    }
);
```

### 14.8 日志与告警联动

**错误码 10000+ 触发 P1 告警**:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<R<Void>> handleUnknown(Exception ex, HttpServletRequest req) {
    log.error("[ALERT_P1] 未预期异常: path={}", req.getRequestURI(), ex);
    // ELK/Loki 通过 [ALERT_P1] 关键字触发告警
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(R.fail(AppCode.SYSTEM_ERROR.getCode(), "系统异常"));
}
```

**告警渠道**:

- Prometheus AlertManager → 企微/钉钉机器人
- ELK/Loki → Webhook → 企微/钉钉
- Sentry → 直接集成

---

## 十五、OpenAPI 契约

### 15.1 springdoc-openapi 基础

**springdoc-openapi 能力**:

- 运行时扫描 Controller,生成 OpenAPI 3.1 规范
- 内置 Swagger UI 和 ReDoc 界面
- Spring Boot 4 兼容,代码即文档

**依赖**(`user/build.gradle` 或 `infra/build.gradle`):

```groovy
api 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6'
```

### 15.2 全局配置

```java
package com.example.myapp.infra.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MyApp API")
                        .version("v1.0.0")
                        .description("MyApp 后端 API 文档")
                        .contact(new Contact()
                                .name("MyApp Team")
                                .email("admin@example.com")
                                .url("https://example.com"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("本地开发"),
                        new Server().url("https://api-dev.example.com").description("开发环境"),
                        new Server().url("https://api.example.com").description("生产环境")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("Sa-Token")
                                        .in(SecurityScheme.In.HEADER)
                                        .name("Authorization")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
```

`application.yml`:

```yaml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: method
    disable-swagger-default-url: true
    display-request-duration: true
    filter: true
  packages-to-scan: com.example.myapp
  paths-to-match: /api/**
  show-actuator: false
  default-produces-media-type: application/json
  default-consumes-media-type: application/json

  # 生产环境关闭
  # springdoc.api-docs.enabled: false
  # springdoc.swagger-ui.enabled: false
```

### 15.3 Controller 上的注解

```java
package com.example.myapp.user.api;

import com.example.myapp.user.domain.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "用户管理", description = "用户 CRUD 及相关操作")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "创建用户",
            description = "管理员或已授权用户可以创建新用户账号"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "409", description = "邮箱已存在")
    })
    @PostMapping
    public UserDto create(
            @RequestBody(description = "创建用户请求", required = true)
            @Valid @org.springframework.web.bind.annotation.RequestBody
            CreateUserRequest req) {
        return UserDto.from(userService.create("default", req));
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public UserDto get(
            @Parameter(description = "用户 ID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {
        return UserDto.from(userService.findById(id));
    }

    @Operation(summary = "用户列表")
    @GetMapping
    public PageResult<UserDto> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "关键词(姓名/邮箱模糊)") @RequestParam(required = false) String keyword) {
        var query = new PageQuery(page, size, null, keyword);
        return userService.list("default", query).map(UserDto::from);
    }
}
```

### 15.4 DTO 上的注解

```java
package com.example.myapp.user.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创建用户请求")
public record CreateUserRequest(
        @Schema(description = "邮箱", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email
        String email,

        @Schema(description = "姓名", example = "张三", minLength = 2, maxLength = 50)
        @NotBlank @Size(min = 2, max = 50)
        String name,

        @Schema(description = "密码,8-72 位", example = "SecurePass123!")
        @NotBlank @Size(min = 8, max = 72)
        String password
) {}
```

```java
@Schema(description = "用户信息")
public record UserDto(
        @Schema(description = "用户 ID") UUID id,
        @Schema(description = "邮箱") String email,
        @Schema(description = "姓名") String name,
        @Schema(description = "头像 URL") String avatar,
        @Schema(description = "创建时间") Instant createdAt
) {}
```

### 15.5 分组 API

**按业务模块分组**,用于 Swagger UI 的下拉切换:

```java
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;

@Bean
public GroupedOpenApi userApi() {
    return GroupedOpenApi.builder()
            .group("1-user")
            .displayName("用户管理")
            .pathsToMatch("/api/v*/users/**", "/api/v*/auth/**")
            .build();
}

@Bean
public GroupedOpenApi orderApi() {
    return GroupedOpenApi.builder()
            .group("2-order")
            .displayName("订单管理")
            .pathsToMatch("/api/v*/orders/**")
            .build();
}

@Bean
public GroupedOpenApi productApi() {
    return GroupedOpenApi.builder()
            .group("3-product")
            .displayName("商品管理")
            .pathsToMatch("/api/v*/products/**")
            .build();
}
```

### 15.6 生产环境关闭 Swagger UI

**原则**:**生产环境不暴露 API 文档**,减少攻击面。

```yaml
# application-prod.yml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

或通过 Spring Security / Sa-Token 拦截器限制访问:

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new SaInterceptor(handler -> StpUtil.checkRole("admin")))
            .addPathPatterns("/swagger-ui/**", "/v3/api-docs/**");
}
```

### 15.7 导出 OpenAPI JSON

**CI 中固化 API 契约**:

```bash
# 启动应用,然后
curl http://localhost:8080/v3/api-docs > openapi.json
curl http://localhost:8080/v3/api-docs/1-user > openapi-user.json
```

或使用 springdoc 的 Gradle 插件,在构建时生成:

```groovy
plugins {
    id 'org.springdoc.openapi-gradle-plugin' version '1.9.0'
}

openApi {
    apiDocsUrl = 'http://localhost:8080/v3/api-docs'
    outputDir = file("$buildDir/docs")
    outputFileName = 'openapi.json'
}
```

```bash
./gradlew :app:generateOpenApiDocs
```

### 15.8 OpenAPI 驱动前端代码生成

**前端拿到 `openapi.json` 自动生成 TypeScript SDK**:

- **openapi-typescript**:仅生成类型,搭配 `fetch` 自己写
- **hey-api/openapi-ts**:生成完整 SDK
- **orval**:生成 React Query / TanStack Query hooks

后端 CI 每次合并到 main 自动发布 openapi.json 到前端仓库,前端自动生成 SDK,**全链路类型安全**。

### 15.9 API 变更管理

**版本化策略**:

- **不破坏性变更**(新字段、新接口):直接在 v1 上叠加,`@Schema` 补注释
- **破坏性变更**(删字段、改语义):新建 v2,v1 保留过渡 3-6 个月
- **字段弃用**:用 `@Schema(deprecated = true)` 标记

**示例**:

```java
@Schema(deprecated = true, description = "已废弃,请用 fullName")
String name,

@Schema(description = "完整姓名(2025-06 起)")
String fullName
```

### 15.10 ReDoc 替代 Swagger UI

**ReDoc** 是更美观的文档界面,只读展示:

```yaml
# application.yml
springdoc:
  swagger-ui:
    enabled: false
# ReDoc 通过静态 HTML 引用 /v3/api-docs 即可
```

`src/main/resources/static/redoc.html`:

```html
<!DOCTYPE html>
<html>
<head>
    <title>API 文档</title>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://fonts.googleapis.com/css?family=Montserrat:300,400,700|Roboto:300,400,700" rel="stylesheet">
    <style> body { margin: 0; padding: 0; } </style>
</head>
<body>
    <redoc spec-url='/v3/api-docs'></redoc>
    <script src="https://cdn.redoc.ly/redoc/latest/bundles/redoc.standalone.js"></script>
</body>
</html>
```

访问 `/redoc.html` 即可。

---

## 十六、测试策略

### 16.1 测试金字塔

| 层级         | 工具                                             | 比例  | 耗时         |
|------------|------------------------------------------------|-----|------------|
| **单元测试**   | JUnit 5 + AssertJ + Mockito 5                  | 60% | 毫秒级        |
| **切片测试**   | `@WebMvcTest` / `@JooqTest` / `@DataRedisTest` | 25% | 秒级         |
| **集成测试**   | `@SpringBootTest` + Testcontainers             | 10% | 10 秒 ~ 分钟级 |
| **E2E 测试** | RestTestClient + 真实环境                          | 5%  | 分钟级        |

**核心原则**:

- **能写单元测试的绝不写集成测试**,快是第一要义
- **必须依赖 Spring 容器的才用切片测试**,不要无脑 `@SpringBootTest`
- **集成测试只测"跨组件协作"**,如事务回滚、消息发送、外部 API 集成

### 16.2 单元测试基础

`subprojects` 已在 3.4 配好 JUnit 5 + AssertJ + Mockito。

```java
package com.example.myapp.user.domain;

import com.example.myapp.common.exception.AppException;
import com.example.myapp.common.exception.AppCode;
import com.example.myapp.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordService passwordService;

    @InjectMocks UserService userService;

    private final String tenantId = "default";

    @Test
    @DisplayName("登录:邮箱不存在时返回 AUTH_BAD_CREDENTIALS")
    void authenticate_email_not_found() {
        when(userRepository.findByEmail(tenantId, "ghost@x.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.authenticate("ghost@x.com", "any"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getCode())
                .isEqualTo(AppCode.AUTH_BAD_CREDENTIALS.getCode());

        verify(passwordService, never()).verify(any(), any());
    }

    @Test
    @DisplayName("登录:账号被禁用时返回 AUTH_ACCOUNT_DISABLED")
    void authenticate_account_disabled() {
        var user = new User(UUID.randomUUID(), tenantId, "a@x.com", "Alice",
                "hash", UserStatus.BANNED, null,
                java.time.Instant.now(), java.time.Instant.now());
        when(userRepository.findByEmail(tenantId, "a@x.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.authenticate("a@x.com", "any"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getCode())
                .isEqualTo(AppCode.AUTH_ACCOUNT_DISABLED.getCode());
    }

    @Test
    @DisplayName("登录:密码错误时不泄漏具体原因")
    void authenticate_wrong_password() {
        var user = new User(UUID.randomUUID(), tenantId, "a@x.com", "Alice",
                "hash", UserStatus.ACTIVE, null,
                java.time.Instant.now(), java.time.Instant.now());
        when(userRepository.findByEmail(tenantId, "a@x.com"))
                .thenReturn(Optional.of(user));
        when(passwordService.verify(eq("wrong"), eq("hash"))).thenReturn(false);

        assertThatThrownBy(() -> userService.authenticate("a@x.com", "wrong"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getCode())
                .isEqualTo(AppCode.AUTH_BAD_CREDENTIALS.getCode());
    }

    @Test
    @DisplayName("登录:成功返回用户")
    void authenticate_success() {
        var user = new User(UUID.randomUUID(), tenantId, "a@x.com", "Alice",
                "hash", UserStatus.ACTIVE, null,
                java.time.Instant.now(), java.time.Instant.now());
        when(userRepository.findByEmail(tenantId, "a@x.com"))
                .thenReturn(Optional.of(user));
        when(passwordService.verify(eq("correct"), eq("hash"))).thenReturn(true);

        User result = userService.authenticate("a@x.com", "correct");

        assertThat(result).isEqualTo(user);
    }
}
```

**AssertJ 惯用法**:

- `assertThat(x).isEqualTo(y)`:值比较
- `assertThat(list).hasSize(3).containsExactly(a, b, c)`:集合断言
- `assertThatThrownBy(() -> ...).isInstanceOf(X.class)`:异常断言
- `assertThat(user).extracting("email", "name").contains(...)`:属性提取

**Mockito 5 要点**:

- Java 21+ 需要 `-XX:+EnableDynamicAgentLoading`(已在 3.4 配好)
- `@InjectMocks` 按构造函数注入,不需要字段注入
- `verify(x, never())` / `verify(x, times(2))` 检查调用次数
- `ArgumentCaptor<T>` 捕获参数做详细断言

### 16.3 `@WebMvcTest` 切片

**只加载 Controller 层**,Service 必须 Mock:

```java
package com.example.myapp.user.api;

import com.example.myapp.user.domain.User;
import com.example.myapp.user.domain.UserService;
import com.example.myapp.user.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UserService userService;

    @Test
    void create_success() throws Exception {
        var user = new User(UUID.randomUUID(), "default", "a@x.com", "Alice",
                "hash", UserStatus.ACTIVE, null, Instant.now(), Instant.now());
        when(userService.create(any(), any(CreateUserRequest.class))).thenReturn(user);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "a@x.com",
                                    "name": "Alice",
                                    "password": "Password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("a@x.com"))
                .andExpect(jsonPath("$.data.name").value("Alice"));
    }

    @Test
    void create_invalid_email() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-email","name":"A","password":"short"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
```

### 16.4 `@JooqTest` 切片

**只加载 jOOQ + DataSource**,用 Testcontainers 真实 PostgreSQL:

```java
package com.example.myapp.user.repo;

import com.example.myapp.user.domain.UserStatus;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@JooqTest
@Testcontainers
@Import(UserRepository.class)
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired DSLContext dsl;
    @Autowired UserRepository userRepository;

    @Test
    void create_and_find() {
        var user = userRepository.create("default", "a@x.com", "Alice", "hash");

        assertThat(user).isNotNull();
        assertThat(user.email()).isEqualTo("a@x.com");

        var found = userRepository.findByEmail("default", "a@x.com");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(user.id());
    }

    @Test
    void update_status() {
        var user = userRepository.create("default", "b@x.com", "Bob", "hash");

        boolean updated = userRepository.updateStatus(user.id(), UserStatus.BANNED);

        assertThat(updated).isTrue();
        var after = userRepository.findById(user.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(UserStatus.BANNED);
    }
}
```

**关键点**:

- `@JooqTest`:只装配 jOOQ DSLContext + DataSource + Flyway,不装其他 Bean
- `@ServiceConnection`:Spring Boot 3.1+ 特性,自动读取 Testcontainers 的连接信息,不用写 `@DynamicPropertySource`
- `@Import(UserRepository.class)`:把待测 Repository 显式导入
- Flyway 会**自动执行 `db/migration/` 下所有脚本**建表

### 16.5 Testcontainers 依赖

```groovy
// infra/build.gradle
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:junit-jupiter:1.21.3'
testImplementation 'org.testcontainers:postgresql:1.21.3'
```

### 16.6 集成测试(`@SpringBootTest`)

**加载完整 Spring Context**,跑真实 HTTP 请求:

```java
package com.example.myapp.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8.6.2-alpine")
            .withExposedPorts(6379);

    @LocalServerPort int port;

    RestTestClient client;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void full_user_lifecycle() {
        // 1. 注册
        var createResp = client.post()
                .uri("/api/v1/auth/register")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"test@example.com","name":"Tester","password":"Password123!"}
                        """)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody().returnResult();

        // 2. 登录
        var loginResp = client.post()
                .uri("/api/v1/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"test@example.com","password":"Password123!"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.tokenValue").exists()
                .returnResult();
        String token = parseToken(loginResp.getResponseBodyContent());

        // 3. 获取当前用户
        client.get()
                .uri("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.email").isEqualTo("test@example.com");

        // 4. 未授权访问
        client.get()
                .uri("/api/v1/auth/me")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String parseToken(byte[] body) {
        // 实际项目中用 ObjectMapper 解析
        return "xxx";
    }
}
```

**Spring Boot 4 的 `RestTestClient`**:

- 替代旧的 `WebTestClient`(那是给 WebFlux 用的)
- API 风格同 WebTestClient,但跑在 Spring MVC 上
- 支持 `.exchange()` 流式断言

### 16.7 Testcontainers Compose

**当测试需要多个服务**(PostgreSQL + Redis + MinIO):

```java
@Container
static ComposeContainer environment = new ComposeContainer(
        new File("src/test/resources/compose-test.yml"))
        .withExposedService("postgres", 5432)
        .withExposedService("redis", 6379)
        .withExposedService("minio", 9000);
```

**编写 `compose-test.yml`**:

```yaml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: test
      POSTGRES_USER: test
      POSTGRES_PASSWORD: test

  redis:
    image: redis:8.6.2-alpine

  minio:
    image: minio/minio:latest
    command: server /data
    environment:
      MINIO_ROOT_USER: minio
      MINIO_ROOT_PASSWORD: miniotest
```

### 16.8 Flowable 工作流测试

```java
package com.example.myapp.hr.workflow;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.test.Deployment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LeaveApprovalTest {

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;

    @Test
    @Deployment(resources = "processes/leave-approval.bpmn20.xml")
    void approve_flow() {
        var instance = runtimeService.startProcessInstanceByKey("leaveApproval",
                Map.of("applicant", "alice", "manager", "bob", "days", 3));

        var task = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(task.getName()).isEqualTo("直属领导审批");

        taskService.complete(task.getId(),
                Map.of("approved", true, "comment", "同意"));

        var after = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(after).isNull();   // 流程结束
    }
}
```

### 16.9 Mock 外部 HTTP

**WireMock** 用于模拟第三方 API:

```groovy
testImplementation 'org.wiremock:wiremock-standalone:3.9.2'
testImplementation 'org.wiremock.integrations:wiremock-spring-boot:3.0.0'
```

```java
@SpringBootTest
@EnableWireMock
class WechatServiceTest {

    @InjectWireMock
    WireMockServer wireMock;

    @Autowired WechatApiClient wechatClient;

    @Test
    void get_access_token() {
        wireMock.stubFor(get(urlPathEqualTo("/cgi-bin/token"))
                .willReturn(okJson("""
                        {"access_token":"fake-token","expires_in":7200}
                        """)));

        var resp = wechatClient.getAccessToken("client_credential", "appid", "secret");

        assertThat(resp.accessToken()).isEqualTo("fake-token");
    }
}
```

### 16.10 测试数据构造

**Builder 模式构造测试数据**:

```java
package com.example.myapp.user.domain;

import java.time.Instant;
import java.util.UUID;

public final class UserFixtures {

    public static UserBuilder aUser() {
        return new UserBuilder();
    }

    public static class UserBuilder {
        private UUID id = UUID.randomUUID();
        private String tenantId = "default";
        private String email = "test-" + UUID.randomUUID() + "@example.com";
        private String name = "Tester";
        private final String passwordHash = "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA";
        private UserStatus status = UserStatus.ACTIVE;

        public UserBuilder withEmail(String email) { this.email = email; return this; }
        public UserBuilder withStatus(UserStatus status) { this.status = status; return this; }
        public UserBuilder banned() { this.status = UserStatus.BANNED; return this; }

        public User build() {
            return new User(id, tenantId, email, name, passwordHash, status, null,
                    Instant.now(), Instant.now());
        }
    }
}
```

**使用**:

```java
var user = UserFixtures.aUser().withEmail("a@x.com").banned().build();
```

**原则**:

- 每个聚合根配一个 Fixtures
- 默认值合理,只 override 测试关心的字段
- 测试间**不共享 Fixture 实例**,每个测试独立构造

### 16.11 Spring Modulith 测试

在 3.12 已配好。强制在 CI 运行:

```java
@Test
void verify_modularity() {
    ApplicationModules.of(Application.class).verify();
}
```

**作用**:

- 检测业务模块之间违规 `@Autowired`
- 检测未声明的依赖
- 生成 PlantUML 模块关系图

### 16.12 CI 运行测试

```bash
# 全部测试
./gradlew test

# 某模块
./gradlew :user:test

# 只跑单元,跳过集成
./gradlew test -PexcludeIntegration

# 生成 coverage(需加 jacoco 插件)
./gradlew test jacocoTestReport
```

**`build.gradle` 附加 JaCoCo**:

```groovy
// subprojects
apply plugin: 'jacoco'

jacoco {
    toolVersion = '0.8.11'
}

tasks.named('test') {
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
    dependsOn tasks.named('test')
}
```

---

## 十七、容器化与部署

### 17.1 Jib 构建镜像

**Jib 特性**:

- 免 Dockerfile,Java 项目无需手写构建步骤
- 分层优化:依赖、资源、类文件分三层,类改动不触发依赖层重建
- Jib 是 Java 程序,不依赖宿主 Docker daemon,CI 环境友好
- 直推 Registry,无需本机 docker 环境

### 17.2 Jib 配置(已在 3.8 写过)

**Jib 命令**:

```bash
# 构建并推送到远程仓库
./gradlew :app:jib

# 构建到本地 docker daemon(需本地有 docker)
./gradlew :app:jibDockerBuild

# 构建为 tar 文件(可 scp 传输)
./gradlew :app:jibBuildTar
```

### 17.3 Chainguard Base Image

**Chainguard 特性**:

- 零已知 CVE,每日重建,依赖实时安全扫描
- JRE 镜像约 110 MB
- 默认 nonroot 用户
- 每个镜像带 SBOM(软件物料清单),供应链透明

**推荐镜像**:

| 镜像                                  | 用途                         |
|-------------------------------------|----------------------------|
| `cgr.dev/chainguard/jre:openjdk-25` | 生产运行(只有 JRE,已锁定 JDK 25 主线) |
| `cgr.dev/chainguard/jdk:openjdk-25` | 需要 JDK 工具(`jcmd`、`jstack`) |

### 17.4 完整部署流程

**CI/CD 链路**:

```
Git Push → GitHub Actions → Gradle Test + Build
                                ↓
                            Jib 推送镜像到阿里云 ACR
                                ↓
                          触发 K8s Deployment 滚动更新
                                ↓
                              Readiness 检查通过
                                ↓
                                 服务就绪
```

### 17.5 GitHub Actions 配置

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:18
        env:
          POSTGRES_DB: test
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      redis:
        image: redis:8.6.2-alpine
        ports:
          - 6379:6379

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 25
          cache: gradle

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Test
        run: ./gradlew test --no-daemon
        env:
          SPRING_PROFILES_ACTIVE: ci
          DB_URL: jdbc:postgresql://localhost:5432/test
          DB_USERNAME: test
          DB_PASSWORD: test
          REDIS_HOST: localhost

      - name: Upload test report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-report
          path: '**/build/reports/tests/test/'

  build:
    needs: test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 25
          cache: gradle

      - name: Login to Aliyun ACR
        uses: aliyun/acr-login@v1
        with:
          login-server: registry.cn-chengdu.aliyuncs.com
          username: ${{ secrets.ACR_USERNAME }}
          password: ${{ secrets.ACR_PASSWORD }}

      - name: Build and push image
        run: |
          ./gradlew :app:jib \
            -Djib.to.auth.username=${{ secrets.ACR_USERNAME }} \
            -Djib.to.auth.password=${{ secrets.ACR_PASSWORD }} \
            -Djib.to.tags=latest,${{ github.sha }}

      - name: Trigger K8s rollout
        run: |
          curl -X POST "${{ secrets.DEPLOY_WEBHOOK_URL }}" \
               -H "Authorization: Bearer ${{ secrets.DEPLOY_TOKEN }}" \
               -d "{\"image\":\"registry.cn-chengdu.aliyuncs.com/myorg/myapp:${{ github.sha }}\"}"
```

### 17.6 Kubernetes 部署 YAML

`ops/k8s/deployment.yml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
  namespace: myapp
  labels:
    app: myapp
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: myapp
  template:
    metadata:
      labels:
        app: myapp
    spec:
      containers:
        - name: myapp
          # 生产环境必须用不可变 tag(commit SHA 或语义化版本),不要用 :latest
          # 示例:registry.cn-chengdu.aliyuncs.com/myorg/myapp:v1.3.2
          image: registry.cn-chengdu.aliyuncs.com/myorg/myapp:v1.0.0
          imagePullPolicy: IfNotPresent            # 不可变 tag 下用 IfNotPresent 更高效

          ports:
            - name: http
              containerPort: 8080
            - name: mgmt
              containerPort: 9090

          resources:
            requests:
              cpu: 500m
              memory: 512Mi
            limits:
              cpu: 2000m
              memory: 1536Mi

          env:
            - name: SPRING_PROFILES_ACTIVE
              value: prod
            - name: TZ
              value: Asia/Shanghai
            - name: JAVA_TOOL_OPTIONS
              value: "-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

          envFrom:
            - configMapRef:
                name: myapp-config
            - secretRef:
                name: myapp-secret

          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: mgmt
            initialDelaySeconds: 60
            periodSeconds: 20
            timeoutSeconds: 5
            failureThreshold: 3

          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: mgmt
            initialDelaySeconds: 20
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3

          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: mgmt
            initialDelaySeconds: 10
            periodSeconds: 10
            failureThreshold: 30

          volumeMounts:
            - name: logs
              mountPath: /logs

      volumes:
        - name: logs
          emptyDir: {}

---
apiVersion: v1
kind: Service
metadata:
  name: myapp
  namespace: myapp
spec:
  type: ClusterIP
  selector:
    app: myapp
  ports:
    - name: http
      port: 80
      targetPort: 8080

---
apiVersion: v1
kind: ConfigMap
metadata:
  name: myapp-config
  namespace: myapp
data:
  DB_URL: jdbc:postgresql://pgbouncer.db.svc.cluster.local:6432/myapp
  REDIS_HOST: redis.cache.svc.cluster.local
  REDIS_PORT: "6379"

---
apiVersion: v1
kind: Secret
metadata:
  name: myapp-secret
  namespace: myapp
type: Opaque
stringData:
  DB_USERNAME: myapp
  DB_PASSWORD: changeme
  JWT_SECRET: changeme
  WECHAT_APP_SECRET: changeme
```

**PgBouncer 部署 YAML**(部署在 `db` 命名空间,供所有业务命名空间共用):

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: db

---
apiVersion: v1
kind: Secret
metadata:
  name: pgbouncer-secret
  namespace: db
type: Opaque
stringData:
  POSTGRESQL_USERNAME: myapp
  POSTGRESQL_PASSWORD: changeme

---
apiVersion: v1
kind: ConfigMap
metadata:
  name: pgbouncer-config
  namespace: db
data:
  POSTGRESQL_HOST: postgres-primary.db.svc.cluster.local
  POSTGRESQL_PORT: "5432"
  POSTGRESQL_DATABASE: myapp
  PGBOUNCER_PORT: "6432"
  PGBOUNCER_POOL_MODE: transaction
  PGBOUNCER_DEFAULT_POOL_SIZE: "25"
  PGBOUNCER_MAX_CLIENT_CONN: "1000"
  PGBOUNCER_IGNORE_STARTUP_PARAMETERS: extra_float_digits,search_path
  PGBOUNCER_AUTH_TYPE: scram-sha-256
  PGBOUNCER_SERVER_LIFETIME: "3600"
  PGBOUNCER_SERVER_IDLE_TIMEOUT: "600"

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pgbouncer
  namespace: db
  labels:
    app: pgbouncer
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: pgbouncer
  template:
    metadata:
      labels:
        app: pgbouncer
    spec:
      containers:
        - name: pgbouncer
          image: bitnami/pgbouncer:1.25
          ports:
            - name: pgbouncer
              containerPort: 6432
          envFrom:
            - configMapRef:
                name: pgbouncer-config
            - secretRef:
                name: pgbouncer-secret
          resources:
            requests:
              cpu: 100m
              memory: 64Mi
            limits:
              cpu: 500m
              memory: 256Mi
          readinessProbe:
            tcpSocket:
              port: 6432
            initialDelaySeconds: 5
            periodSeconds: 10
          livenessProbe:
            tcpSocket:
              port: 6432
            initialDelaySeconds: 30
            periodSeconds: 20

---
apiVersion: v1
kind: Service
metadata:
  name: pgbouncer
  namespace: db
spec:
  type: ClusterIP
  selector:
    app: pgbouncer
  ports:
    - name: pgbouncer
      port: 6432
      targetPort: 6432

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: pgbouncer-hpa
  namespace: db
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: pgbouncer
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

**应用访问路径**:

```
myapp (myapp namespace)
    ↓ DB_URL: jdbc:postgresql://pgbouncer.db.svc.cluster.local:6432/myapp
pgbouncer (db namespace, ClusterIP Service)
    ↓
postgres-primary (db namespace, StatefulSet 或云托管 PostgreSQL)
```

**容量校验**:

```
PgBouncer 副本数 × default_pool_size = 3 × 25 = 75
PostgreSQL max_connections 需 ≥ 75 + 20(运维连接)+ 5(冗余) = 100
```

上述容量在 PgBouncer HPA 扩到 10 副本时变为:

```
10 × 25 = 250,PostgreSQL max_connections 需 ≥ 275
```

部署前先按**最大扩容副本数**校验 PostgreSQL `max_connections` 是否足够,不够时调整 `PGBOUNCER_DEFAULT_POOL_SIZE` 或 HPA
`maxReplicas`。

### 17.7 HPA 自动扩缩

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: myapp-hpa
  namespace: myapp
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: myapp
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Percent
          value: 100
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 50
          periodSeconds: 60
```

### 17.8 Ingress 配置

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: myapp-ingress
  namespace: myapp
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: 10m
    nginx.ingress.kubernetes.io/proxy-read-timeout: "60"
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - api.example.com
      secretName: api-example-com-tls
  rules:
    - host: api.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: myapp
                port:
                  number: 80
```

### 17.9 JVM 调优

**生产环境 JVM 参数**(Jib 已在 3.5 配好,这里是完整版):

```bash
-XX:+UseG1GC                            # G1 GC,Java 9+ 默认,平衡延迟与吞吐
-XX:MaxRAMPercentage=75.0               # 最大堆占容器内存的 75%
-XX:InitialRAMPercentage=50.0           # 初始堆占容器内存的 50%
-XX:+ExitOnOutOfMemoryError             # OOM 直接退出,K8s 重启 pod
-XX:+HeapDumpOnOutOfMemoryError         # OOM 时生成堆 dump
-XX:HeapDumpPath=/logs/heap.hprof

-Djava.security.egd=file:/dev/./urandom  # 加速启动(Linux 真随机源慢)
-Dfile.encoding=UTF-8
-Duser.timezone=Asia/Shanghai

-XX:+UseStringDeduplication              # G1 字符串去重
-XX:MaxGCPauseMillis=200                 # 目标最大 GC 停顿

-Djdk.tracePinnedThreads=short           # Virtual Thread pin 调试(仅排查时开)
```

**内存规划**(1536 MB 容器为例):

| 区域                    | 占比       | 大小                |
|-----------------------|----------|-------------------|
| Java Heap (75%)       | 1152 MB  | 大部分业务对象           |
| Metaspace (默认)        | 不设上限     | 类元数据,约 150-300 MB |
| Direct Memory (Netty) | 约 100 MB | Lettuce/Netty NIO |
| Thread Stacks         | 约 50 MB  | 每个平台线程 1 MB       |
| JIT Code Cache        | 约 64 MB  | 热代码编译缓存           |
| **剩余**                | ≈ 150 MB | 留给 JVM 其他开销、GC、OS |

**虚拟线程不占 Thread Stack 内存**,这是 Project Loom 虚拟线程的关键特性。

### 17.10 平滑停机

**Spring Boot 4 默认支持 Graceful Shutdown**:

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

**K8s 配合**:

```yaml
spec:
  containers:
    - name: myapp
      lifecycle:
        preStop:
          exec:
            # pod 被 terminate 前休眠 5 秒,等 Service 路由切掉
            command: ["/bin/sh", "-c", "sleep 5"]
  terminationGracePeriodSeconds: 60
```

**流程**:

1. K8s 发 SIGTERM
2. `preStop` 休眠 5 秒(让 Service 把流量切走)
3. Spring Boot 收到 SIGTERM,进入 Graceful 阶段
4. 拒绝新请求,等待进行中的请求完成(最多 30 秒)
5. 关闭 JobRunr、Flowable、数据库连接池
6. 进程退出

### 17.11 镜像仓库选择

| 场景   | 推荐仓库                                   |
|------|----------------------------------------|
| 国内生产 | 阿里云 ACR / 腾讯云 TCR                      |
| 海外生产 | AWS ECR / GCP Artifact Registry        |
| 开发测试 | Harbor 自建 / GitHub Container Registry  |
| 开源发布 | Docker Hub / GitHub Container Registry |

**国内提速**:阿里云 ACR 企业版有专属加速域名,配合 K8s 节点内网拉取,秒级部署。

---

## 十八、附录

### 18.1 Renovate Bot 配置

`renovate.json`:

```json
{
  "$schema": "https://docs.renovatebot.com/renovate-schema.json",
  "extends": [
    "config:recommended",
    ":semanticCommits",
    ":dependencyDashboard"
  ],
  "timezone": "Asia/Shanghai",
  "schedule": ["before 6am on Monday"],
  "labels": ["dependencies"],
  "prConcurrentLimit": 5,
  "rangeStrategy": "pin",

  "packageRules": [
    {
      "matchUpdateTypes": ["patch"],
      "automerge": true,
      "automergeType": "pr"
    },
    {
      "matchPackagePatterns": ["^org.springframework"],
      "groupName": "spring ecosystem",
      "schedule": ["before 6am on the first day of the month"]
    },
    {
      "matchPackagePatterns": ["^org.testcontainers"],
      "groupName": "testcontainers",
      "automerge": true
    },
    {
      "matchDepTypes": ["devDependencies", "testImplementation"],
      "automerge": true
    },
    {
      "matchPackageNames": ["org.projectlombok:lombok"],
      "enabled": false,
      "description": "Lombok 慎升,与 JDK 版本绑定"
    },
    {
      "matchPackageNames": ["org.jooq:jooq"],
      "description": "jOOQ 升级需要同步重新生成代码,手动 review",
      "automerge": false
    }
  ],

  "vulnerabilityAlerts": {
    "enabled": true,
    "labels": ["security"],
    "assignees": ["@liquan"]
  }
}
```

### 18.2 Spring Modulith 进阶

**模块事件发布订阅**:

```java
// user/domain/UserService.java — 发布事件
package com.example.myapp.user.domain;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final ApplicationEventPublisher publisher;

    public void create(...) {
        var user = doCreate(...)
        publisher.publishEvent(new UserCreatedEvent(user.id(), user.email()));
    }
}
```

```java
// user/domain/UserCreatedEvent.java
package com.example.myapp.user.domain;

import java.util.UUID;

public record UserCreatedEvent(UUID userId, String email) {}
```

```java
// notification 模块:订阅事件
package com.example.myapp.notification.provider.email;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WelcomeEmailListener {

    @EventListener
    public void onUserCreated(UserCreatedEvent event) {
        // 发送欢迎邮件
    }
}
```

**`@ApplicationModuleListener`(推荐)**:

- 异步执行
- 事务外处理
- 支持重试

```java
@Component
public class WelcomeEmailListener {

    @ApplicationModuleListener
    public void onUserCreated(UserCreatedEvent event) {
        // 新事务、异步、失败持久化
    }
}
```

需要在 `app/build.gradle` 加:

```groovy
implementation 'org.springframework.modulith:spring-modulith-events-jdbc'
implementation 'org.springframework.modulith:spring-modulith-events-jackson'
```

事件会持久化到 `event_publication` 表,失败自动重试。

### 18.3 性能调优 Cheat Sheet

**应用启动慢**:

- 检查 Flyway 脚本数量(上百个 V1__.sql 可能拖慢启动)→ 考虑 baseline
- 关闭 DevTools(生产)
- 检查 `@PostConstruct` 是否有阻塞 I/O

**CPU 高**:

- 导出线程 dump:`jcmd <pid> Thread.print`
- 找出占 CPU 最多的线程
- 火焰图工具:`async-profiler`、`jfr`

**内存泄漏**:

- 开启 `-XX:+HeapDumpOnOutOfMemoryError`
- MAT / VisualVM 分析 heap dump
- 常见泄漏源:`static` HashMap、线程池未关闭、Redis 连接未释放

**慢 SQL**:

- 开启 13.12 节的 SlowSqlListener
- `pg_stat_statements` 扩展:
  `SELECT query, calls, mean_exec_time FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 20;`
- 用 `EXPLAIN ANALYZE` 看执行计划

**连接池耗尽**:

- 开启 `leak-detection-threshold: 60000`
- Grafana 看 `hikaricp_connections_active` 曲线
- 常见原因:事务方法里调第三方 HTTP、长事务未提交

### 18.4 Java 21+ 语法速查

**Record**:

```java
public record Point(int x, int y) {
    // 紧凑构造器:校验
    public Point {
        if (x < 0 || y < 0) throw new IllegalArgumentException();
    }

    // 静态工厂
    public static Point origin() { return new Point(0, 0); }

    // 实例方法
    public double distance(Point other) {
        return Math.sqrt(Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2));
    }
}
```

**Pattern Matching for switch**:

```java
sealed interface Shape permits Circle, Square, Triangle {}
record Circle(double radius) implements Shape {}
record Square(double side) implements Shape {}
record Triangle(double base, double height) implements Shape {}

double area(Shape shape) {
    return switch (shape) {
        case Circle c -> Math.PI * c.radius() * c.radius();
        case Square s -> s.side() * s.side();
        case Triangle t -> t.base() * t.height() / 2;
    };
}
```

**Text Blocks**:

```java
String json = """
        {
            "name": "Alice",
            "age": 30
        }
        """;

String sql = """
        SELECT u.id, u.email, u.name
        FROM users u
        WHERE u.tenant_id = ?
          AND u.status = 'ACTIVE'
        ORDER BY u.created_at DESC
        LIMIT 20
        """;
```

**Virtual Threads**:

```java
// 显式创建
Thread.startVirtualThread(() -> {
    // task
});

// Executor
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    IntStream.range(0, 10_000).forEach(i ->
        executor.submit(() -> fetchFromApi(i)));
}
```

**Sequenced Collections(Java 21)**:

```java
List<Integer> list = List.of(1, 2, 3);
int first = list.getFirst();    // 1
int last = list.getLast();       // 3
List<Integer> reversed = list.reversed();
```

**Primitive Patterns in switch(Java 23+)**:

```java
Object value = 42;
String result = switch (value) {
    case int i when i > 0 -> "positive int: " + i;
    case int i            -> "non-positive int: " + i;
    case long l           -> "long: " + l;
    case double d         -> "double: " + d;
    case String s         -> "string: " + s;
    default                -> "other";
};
```

**Scoped Values(Java 25 正式版,替代虚拟线程下的 ThreadLocal)**:

```java
public final class TenantContext {
    public static final ScopedValue<String> CURRENT = ScopedValue.newInstance();
}

// 设置作用域内的值
ScopedValue.where(TenantContext.CURRENT, "tenant-abc").run(() -> {
    // 作用域内调用链路,无论跨多少线程都能读到 "tenant-abc"
    userService.findAll();
});

// 读取
String tenantId = TenantContext.CURRENT.get();
```

**Structured Concurrency(Java 25 正式版,虚拟线程下的任务组)**:

```java
try (var scope = StructuredTaskScope.open()) {
    Subtask<User> userTask   = scope.fork(() -> findUser(userId));
    Subtask<List<Order>> ordersTask = scope.fork(() -> findOrders(userId));
    Subtask<Wallet> walletTask = scope.fork(() -> findWallet(userId));

    scope.join();   // 等待所有子任务完成(失败即整体失败)

    return new UserAggregate(userTask.get(), ordersTask.get(), walletTask.get());
}
```

**Stable Values(Java 25,JEP 502)**:

```java
public class AppConfig {
    // 懒加载、线程安全、只初始化一次
    private static final StableValue<DataSource> DS = StableValue.of();

    public static DataSource dataSource() {
        return DS.orElseSet(() -> buildDataSource());
    }
}
```

### 18.5 版本升级路线图

**预期节奏**:

| 版本                        | 场景                           |
|---------------------------|------------------------------|
| Java 25 LTS               | 当前默认(2025-09 GA)             |
| Java 29 LTS               | 2028-09 预期发布,GA 稳定 6 个月后评估升级 |
| Spring Boot 4.0.x         | 当前版本,按月度 patch 跟进            |
| Spring Boot 4.1.x         | 2026 下半年预期发布,等 GA 稳定后评估      |
| Spring Boot 4.2 / 5.0     | 跟随 Spring Framework 重大版本节奏   |
| PostgreSQL 18 → 19        | 等 PG 19 稳定(2026 Q4 以后)后评估    |
| jOOQ 3.21 → 3.22 / 4.x    | 小版本直升;4.x 重大升级需重做代码生成测试      |
| Redis 8 → 9               | 2027 预期发布,评估回归测试后升级          |
| Spring Modulith 2.x → 3.x | 跟随 Spring Boot 主版本           |

**升级原则**:

- Spring Boot 只跟 **GA 稳定版**,不用 M / RC 版本
- JDK 升级以 **LTS 为节点**,不跟非 LTS(Java 26 / 27 / 28 跳过)
- 数据库主版本升级需要**完整回归测试**,不随便升
- 依赖 patch 版本由 Renovate Bot 自动 PR + 单元测试通过自动合并(见 18.1)
- minor 版本升级手动 review,关注 CHANGELOG 与破坏性变更

### 18.6 代码生成器

内部维护独立代码生成工具 `codegen`(不进业务工程,**当前 v1**,后续会持续更新迭代),输入 Flyway 迁移脚本或已有 PostgreSQL
表结构,输出:

- jOOQ 生成配置(自动加入 `infra/build.gradle`)
- DDD 分层代码:`api/<Entity>Controller.java`、`domain/<Entity>.java`、`domain/<Entity>Service.java`、
  `repo/<Entity>Repository.java`
- DTO:`api/<Entity>Dto.java`(含 `@Mask` / `@Valid` 注解推断)
- 测试骨架:`<Entity>ServiceTest.java`、`<Entity>ControllerTest.java`
- OpenAPI 注释占位

**v1 技术栈**:

- Java 25 + Spring Boot CLI
- Freemarker 2.3.34+ 模板引擎
- 输入:Flyway SQL 脚本解析(或连接 PostgreSQL 反查 `information_schema`)

**使用**:

```bash
# 基于 Flyway 脚本生成
codegen from-flyway \
    --source ./db/migration \
    --table users \
    --module user \
    --package com.example.myapp.user \
    --output ./user/src/main/java

# 基于已有数据库反查生成
codegen from-db \
    --url jdbc:postgresql://localhost:6432/myapp \
    --table users \
    --module user \
    --output ./user/src/main/java
```

**生成后开发者的工作**:

- 业务规则填充到 `<Entity>Service.java` 的占位方法
- 按需定制 DTO 字段(添加 `@Mask`、移除内部字段)
- 按需调整 Controller 的路径、权限注解
- 补充测试用例

**不做的事**:

- 不生成 Flyway 迁移脚本(schema 是真相源,应手写)
- 不生成前端代码(前端栈独立演进)
- 不生成复杂业务逻辑(代码生成器做 CRUD 骨架,业务逻辑是人写的)

**定位**:`codegen` 只生成 Java 骨架,前端独立,**一次性生成**,**不作为项目运行时功能**。后续版本迭代方向:
模板可配置化、支持多租户字段自动注入、集成 OpenAPI 契约反向生成等。

### 18.7 References

**官方文档**:

- Spring Boot 4.0: https://docs.spring.io/spring-boot/
- Spring Framework 7: https://docs.spring.io/spring-framework/reference/
- Spring Modulith: https://docs.spring.io/spring-modulith/reference/
- jOOQ: https://www.jooq.org/doc/latest/manual/
- Flowable 8: https://documentation.flowable.com/
- JobRunr: https://www.jobrunr.io/en/documentation/
- Sa-Token: https://sa-token.cc/
- hutool: https://hutool.cn/docs/

---

## 十九、领域驱动设计:DDD + 六边形 + Clean Architecture

> **本章定位**:前 17 章是**工程实施规范**,本章是**方法论**。
>
> 前面章节告诉你"用什么技术、配置怎么写、代码放哪个包",本章告诉你"为什么这样组织、边界画在哪、什么时候该抽象"。
>
> 本章把三种互为补充的架构哲学整合到 Kiln 项目的同一套实现上:
>
> - **DDD(Domain-Driven Design)** — 方法论,回答"设计什么"
> - **六边形架构(Ports & Adapters)** — 架构风格,回答"层次如何组织"
> - **Clean Architecture** — 架构风格,回答"依赖指向哪个方向"
>
> 三者不是竞争关系,而是三个视角看同一件事。

### 19.1 三种范式的关系:一张地形图

| 范式                     | 提出者                     | 核心关注          | Kiln 项目中的落点               |
|------------------------|-------------------------|---------------|---------------------------|
| **DDD**                | Eric Evans(2003)        | 业务语言、限界上下文、聚合 | 战略设计 + 战术设计全覆盖            |
| **Hexagonal**          | Alistair Cockburn(2005) | 领域与 I/O 隔离    | 每个 Bounded Context 内部的包结构 |
| **Clean Architecture** | Robert C. Martin(2012)  | 依赖方向单向内指      | 跨层依赖约束 + ArchUnit 检查      |

三者的关系图:

```mermaid
flowchart LR
    subgraph DDD["DDD(方法论)"]
        BC[Bounded Context<br/>限界上下文]
        AGG[Aggregate<br/>聚合]
        VO[Value Object<br/>值对象]
        DE[Domain Event<br/>领域事件]
    end

    subgraph HEX["Hexagonal(分层)"]
        D[Domain 领域层]
        A[Application 应用层]
        ADA[Adapter 适配器层]
    end

    subgraph CLEAN["Clean(依赖规则)"]
        DR["Dependency Rule<br/>依赖只能内指"]
    end

    BC -.对应.-> D
    AGG -.位于.-> D
    VO -.位于.-> D
    DE -.跨越.-> A
    DR -.约束.-> HEX
```

**一句话总结**:**DDD 决定"长什么样",Hexagonal 决定"怎么摆放",Clean 决定"谁能依赖谁"**。

### 19.2 依赖方向的黄金法则(The Dependency Rule)

整个架构只有一条硬约束:**依赖只能指向内层,永远不能反过来**。

```mermaid
flowchart TB
    subgraph OUT["外层(易变)"]
        FR["Frameworks &amp; Drivers<br/>Spring / jOOQ / Tomcat"]
    end
    subgraph ADA["适配器层"]
        WEB["Web Controller"]
        PER["jOOQ Repository"]
        HTTP["HTTP Client"]
    end
    subgraph APP["应用层"]
        UC["Use Case"]
        PORT["Port(接口)"]
    end
    subgraph DOM["领域层(稳定)"]
        AG["Aggregate"]
        V["Value Object"]
        DE["Domain Event"]
    end

    OUT --> ADA
    ADA --> APP
    APP --> DOM
```

**规则**:

- ✅ `adapter/` 可以 `import` `application/` 和 `domain/`
- ✅ `application/` 可以 `import` `domain/`
- ❌ `domain/` **不允许** `import` `application/`、`adapter/`、Spring、jOOQ、Jackson、Servlet
- ❌ `application/` **不允许** `import` 具体 adapter 实现(只能依赖 `application/port/out/` 接口)

**为什么这样规定**:

- 领域层(核心业务规则)**最稳定** — 几十年不变(记账就是借贷平衡,千年不变)
- 框架层**最易变** — Spring 大版本每 2 年一换,jOOQ / Jackson 的 API 随时演进
- 反过来依赖 = 让最稳定的东西被最易变的东西绑架 = 每次框架升级都要改业务代码

### 19.3 统一语言(Ubiquitous Language)

**原则**:业务专家、产品经理、开发者、DBA **共用同一套词汇**。

这套词汇必须同时出现在:

1. 需求文档
2. API 路径与参数名(`POST /api/v1/orders`,不是 `POST /api/v1/create-new-business-entity`)
3. Java 类 / 方法 / 变量名
4. 数据库表名 / 列名
5. 测试用例名
6. 日志 / 埋点事件名
7. 工单 / bug 描述

**反例**:

| 业务方说 | 需求文档写  | Controller 写         | Service 方法名           | 表名                  | 后果                |
|------|--------|----------------------|-----------------------|---------------------|-------------------|
| "发货" | "物流起运" | `ShipmentController` | `dispatchOrderItem()` | `t_logistics_event` | 4 个同义词,新人永远搞不清谁是谁 |

**正例**:

| 业务方说 | 文档   | Controller                  | Service                      | 表           |
|------|------|-----------------------------|------------------------------|-------------|
| "发货" | "发货" | `ShipmentController.ship()` | `OrderService.ship(OrderId)` | `shipments` |

**落地约束**:

- 每个 Bounded Context 维护一份 `GLOSSARY.md` 或在 `design.md` 里专门开一节
- 禁止使用技术化隐喻代替业务语言(`handler` / `processor` / `manager` / `helper` 是重灾区)
- 跨 BC 沟通时,显式说明对方用的是哪个 BC 的 "订单"(电商订单 vs 工单)

### 19.4 战略设计:限界上下文(Bounded Context)

#### 19.4.1 定义

**限界上下文是 DDD 里最重要的战略概念**:在一个明确的业务边界内,术语、模型、规则是**统一且自洽**的;跨越边界,同一个词可以有不同含义。

**经典例子**:"客户" 在:

- **销售 BC**:有联系人、需求、报价单、意向等级
- **售后 BC**:有工单、SLA、设备序列号
- **财务 BC**:有开票主体、税号、信用额度

强行统一成一个 `Customer` 类就是"上帝对象" — 所有业务改动都要改这个类,最终成为 Big Ball of Mud。**正确做法是让每个 BC
拥有自己的 `Customer` 类**,通过事件或 ACL 进行集成。

#### 19.4.2 在 Kiln 项目上:BC = Spring Modulith 模块

**一个 Gradle 子模块 = 一个 `package-info.java` 声明的 `@ApplicationModule` = 一个 Bounded Context**。

边界通过 `ApplicationModules.verify()` 单测强制,跨 BC 调用必须:

1. 通过 REST API(内部 HTTP,ACL 场景)
2. 通过 Spring `ApplicationEvent`(异步领域事件,推荐)
3. 或依赖对方 BC 的**公开 API 包**(`api/` 子包,Modulith 标注为模块门面)

不允许:

- 直接 `@Autowired` 对方的内部 Service
- `import` 对方 `domain/model/` 的聚合根
- 共享数据库表的直接读写

#### 19.4.3 反模式

```
× UserController, UserService, UserRepository  —— 按技术分层命名,不是 BC
× Module-a, Module-b  —— 无业务语义的命名
× Common(什么都往里塞)  —— 跨 BC 共享内核失控
```

### 19.5 Context Map:9 种集成模式

Context Map 是描述 **BC 之间集成关系**的图。Kiln 项目采用下列 9 种模式中的若干种:

```mermaid
flowchart LR
    subgraph Core["Core Domain"]
        Order["Order BC"]
        Payment["Payment BC"]
    end

    subgraph Support["Supporting"]
        User["User BC"]
        Notif["Notification BC"]
    end

    subgraph Generic["Generic"]
        Auth["Auth BC"]
        Dict["Dict BC"]
        Audit["Audit BC"]
    end

    subgraph Shared["Shared Kernel"]
        Common["common:<br/>Money / PageQuery"]
    end

    subgraph ACL["Anti-Corruption Layer"]
        WechatPay["WechatPayProvider"]
        Alipay["AlipayProvider"]
    end

    Order -- "published event<br/>OrderCreated" --> User
    Order -- "customer/supplier<br/>port" --> Payment
    Payment -- "ACL" --> WechatPay
    Payment -- "ACL" --> Alipay
    Order -.-> Common
    User -.-> Common
    Payment -.-> Common
    User -- "OHS + Published Language<br/>REST API" --> Order
```

**9 种集成模式对照表**:

| 模式                            | 中文     | Kiln 项目上的例子                                                    | 何时使用                            |
|-------------------------------|--------|----------------------------------------------------------------|---------------------------------|
| **Shared Kernel**             | 共享内核   | `common/result/PageQuery`、`common/money/Money`                 | 极少数真正跨 BC 稳定的值对象;代价:任何改动影响所有 BC |
| **Customer/Supplier**         | 客户/供应商 | `order` 消费 `user`;下游可以向上游提需求                                   | 两团队有合作意愿                        |
| **Conformist**                | 跟随者    | `payment` 模块适配微信支付 APIv3 的 JSON 结构                             | 上游无视下游,只能硬吞                     |
| **Anticorruption Layer(ACL)** | 反腐层    | `payment/adapter/out/wechat/WechatPayProvider` 翻译微信脏模型 → 本域纯模型 | 上游模型污染大                         |
| **Open Host Service(OHS)**    | 开放主机服务 | REST API `/api/v1/users` 给多个消费者                                | 一个 BC 被多个其他 BC/外部调用             |
| **Published Language**        | 发布语言   | OpenAPI 契约(springdoc 生成)                                       | 配合 OHS,定义稳定协议                   |
| **Separate Ways**             | 各行其道   | `audit` 与 `dict` 模块无集成                                         | 两个 BC 没有业务关联                    |
| **Partnership**               | 合作     | `order` 与 `payment` 团队共同演进 PaymentPort 契约                      | 双向依赖 + 协同演进                     |
| **Big Ball of Mud**           | 大泥球    | 历史系统里混在一起的遗留代码                                                 | **反模式**,识别后立即用 ACL 隔离           |

### 19.6 核心域 / 支撑子域 / 通用子域

DDD 把所有 BC 按**对业务的战略价值**分三级:

| 子域类型                          | 特征              | 投入程度               | Kiln 项目示例                       | 架构严格度                             |
|-------------------------------|-----------------|--------------------|---------------------------------|-----------------------------------|
| **Core Domain** 核心域           | 公司赚钱的核心能力,外部买不到 | **最高**:最资深工程师、最多投入 | `order`、`payment`(若自研)、业务独有逻辑模块 | **严格 DDD + 完整六边形**                |
| **Supporting Subdomain** 支撑子域 | 支持核心业务,但非核心竞争力  | 中等:能买就买            | `user`、`notification`、`file`    | 简化 DDD(可合并 `service` 和 `usecase`) |
| **Generic Subdomain** 通用子域    | 所有公司都一样,商品化成熟   | **最低**:能用现成方案就用    | `auth`、`dict`、`audit`、`tenant`  | **直接 CRUD**,不用 DDD                |

**规则**:

- **不要在通用子域上过度设计** — `auth/` 用 Sa-Token + 数据库表就够了,别整聚合根
- **不要在核心域上偷懒** — `order/`、`payment/` 必须用聚合根 + 领域事件,不能直接 `OrderRepository.save(orderEntity)`
- **支撑子域灵活处理** — 按团队能力和项目周期平衡

### 19.7 战术设计:聚合(Aggregate)

#### 19.7.1 定义

**聚合是一组一致性边界内的实体和值对象的集合**,有一个唯一的**聚合根(Aggregate Root)** 作为对外入口。

聚合的作用:**保证业务不变式(Invariant)在事务内不被破坏**。

**例**:`Order` 聚合

- 根:`Order`
- 内部实体:`OrderItem[]`
- 值对象:`Money`, `ShippingAddress`, `OrderStatus`
- 不变式:`totalAmount == sum(items.subtotal)` 必须恒成立

如果不用聚合,两个请求可能同时修改 `Order` 的 items 列表,导致 `totalAmount` 和实际数据不一致。

#### 19.7.2 聚合的四条黄金规则

```mermaid
flowchart TD
    A[聚合根 Order] --> B[OrderItem 1]
    A --> C[OrderItem 2]
    A --> D[ShippingAddress]

    E[另一个聚合 User] -.只能持有 ID.-> A

    F[Repository] --> A

    style A fill:#4a90e2
    style E fill:#aaa
```

| 规则                 | 内容                                            | 违反后果                 |
|--------------------|-----------------------------------------------|----------------------|
| **1. 通过根访问**       | 外部只能引用根(`Order`),不能直接引用内部实体(`OrderItem`)      | 不变式被绕过,数据不一致         |
| **2. 一个事务 = 一个聚合** | 一次 `@Transactional` 只能修改一个聚合实例                | 分布式死锁;长事务性能崩溃        |
| **3. 跨聚合用 ID 引用**  | `Order` 持有 `UserId`,不持有 `User` 对象             | 加载一个聚合加载半个数据库;延迟加载陷阱 |
| **4. 跨聚合用事件最终一致**  | `Order` 支付成功 → 发 `OrderPaid` → `User` 模块异步加积分 | 事务边界过大,失败回滚地狱        |

#### 19.7.3 聚合根的 Java 代码模板

```java
// <bc>/domain/model/Order.java
package com.example.myapp.order.domain.model;

import com.example.myapp.common.money.Money;
import com.example.myapp.order.domain.event.DomainEvent;
import com.example.myapp.order.domain.event.OrderCreated;
import com.example.myapp.order.domain.event.OrderPaid;
import com.example.myapp.order.domain.exception.IllegalOrderStateException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Order {

    private final OrderId id;
    private final UserId userId;
    private final List<OrderItem> items;
    private final ShippingAddress shippingAddress;
    private OrderStatus status;
    private final Instant createdAt;
    private Instant paidAt;

    // 暂存的领域事件,应用层拉取后发布
    private final List<DomainEvent> events = new ArrayList<>();

    // 私有构造器,强制走静态工厂
    private Order(OrderId id, UserId userId, List<OrderItem> items,
                  ShippingAddress addr, OrderStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.items = new ArrayList<>(items);
        this.shippingAddress = Objects.requireNonNull(addr);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);

        // 在构造期就验证业务不变式
        if (items.isEmpty()) {
            throw new IllegalOrderStateException("订单必须至少有一个商品项");
        }
    }

    // ─────────── 静态工厂:创建新聚合 ───────────
    public static Order create(UserId userId, List<OrderItem> items,
                               ShippingAddress addr) {
        var order = new Order(OrderId.newId(), userId, items, addr,
                              OrderStatus.PENDING, Instant.now());
        order.events.add(new OrderCreated(order.id, order.userId, order.totalAmount(),
                                          order.createdAt));
        return order;
    }

    // ─────────── 静态工厂:从持久化重建聚合 ───────────
    public static Order reconstitute(OrderId id, UserId userId, List<OrderItem> items,
                                     ShippingAddress addr, OrderStatus status,
                                     Instant createdAt, Instant paidAt) {
        var order = new Order(id, userId, items, addr, status, createdAt);
        order.paidAt = paidAt;
        // 重建路径不发事件 —— 事件已经在第一次创建时发过了
        return order;
    }

    // ─────────── 业务行为(领域方法)───────────
    public void pay() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalOrderStateException(
                    "订单状态 %s,不能支付".formatted(status));
        }
        this.status = OrderStatus.PAID;
        this.paidAt = Instant.now();
        this.events.add(new OrderPaid(id, userId, totalAmount(), paidAt));
    }

    public void cancel(String reason) {
        if (status == OrderStatus.PAID || status == OrderStatus.SHIPPED) {
            throw new IllegalOrderStateException(
                    "已支付或已发货订单不能直接取消,需走退款流程");
        }
        this.status = OrderStatus.CANCELLED;
    }

    // ─────────── 查询(派生状态,不改变聚合)───────────
    public Money totalAmount() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.zero(items.get(0).unitPrice().currency()), Money::add);
    }

    // ─────────── 领域事件接口 ───────────
    public List<DomainEvent> pullEvents() {
        var copy = List.copyOf(events);
        events.clear();
        return copy;
    }

    // ─────────── Getter(不暴露内部集合的可变引用)───────────
    public OrderId id() { return id; }
    public UserId userId() { return userId; }
    public List<OrderItem> items() { return Collections.unmodifiableList(items); }
    public OrderStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant paidAt() { return paidAt; }
}
```

**要点**:

- `final class` + 私有构造器 + 静态工厂 — 保证不变式在构造期就被验证
- 领域方法(`pay()` / `cancel()`)**返回 void 或 this**,不返回数据传输对象
- 事件暂存在聚合内,由应用层在事务提交后拉取并发布
- 不提供 `setXxx()` —— 所有状态变更必须走业务方法
- 不依赖 Spring、不依赖 jOOQ、不依赖 Jackson

#### 19.7.4 聚合大小:小为美

**征兆一个聚合太大**:

- 超过 3 层嵌套(根 → 子实体 → 孙实体)
- 加载一次 > 50 个对象
- 两个团队经常同时改同一个聚合,频繁合并冲突
- 事务锁冲突率 > 5%

**拆分策略**:

- 按业务事务频率拆 — 经常一起变的放一起,不一起变的拆开
- 跨聚合的数据一致性交给领域事件,不追求强一致

### 19.8 战术设计:值对象(Value Object)

#### 19.8.1 6 条判定清单

一个类该是值对象还是实体?过这个清单:

```mermaid
flowchart TD
    Q1{有稳定的<br/>唯一 identity?} --Yes--> E[实体]
    Q1 --No--> Q2{不可变?<br/>构造后永不改}
    Q2 --No--> F[先改成不可变再判]
    Q2 --Yes--> Q3{相等性由<br/>属性值决定?}
    Q3 --No--> E
    Q3 --Yes--> Q4{可以自由替换<br/>同值的另一实例?}
    Q4 --No--> E
    Q4 --Yes--> Q5{无独立生命周期<br/>依附某个实体?}
    Q5 --No--> E
    Q5 --Yes--> V[值对象 ✓]

    style V fill:#4a90e2
    style E fill:#888
```

6 条全部命中才是值对象;任意一条不确定,优先判为实体。

#### 19.8.2 值对象的 Java 代码模板

Java 25 下**默认首选 `record`**:

```java
// common/money/Money.java
package com.example.myapp.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

    // Compact constructor 做 invariant 校验
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.scale() > currency.getDefaultFractionDigits()) {
            throw new IllegalArgumentException(
                    "amount scale %d exceeds %s fraction digits %d"
                            .formatted(amount.scale(), currency,
                                       currency.getDefaultFractionDigits()));
        }
    }

    // 静态工厂
    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(
                BigDecimal.ZERO.setScale(
                        currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY),
                currency);
    }

    // 领域行为:返回新值对象,不修改 self
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(
                amount.multiply(factor).setScale(
                        currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN),
                currency);
    }

    public boolean isPositive() { return amount.signum() > 0; }
    public boolean isZero() { return amount.signum() == 0; }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot operate on %s and %s".formatted(currency, other.currency));
        }
    }
}
```

**要点**:

- `record` 自动生成 `equals()` / `hashCode()` / `toString()`,默认按所有组件值比较
- Compact constructor 做 invariant 校验 — 不合法的值对象**不允许存在**
- 领域行为返回**新实例**,原实例不被修改(不可变性)
- 静态工厂方法(`of()` / `zero()`)优于 `new` — 可读性 + 可替换实现

#### 19.8.3 值对象 vs 实体 对照表

| 维度       | 值对象                                            | 实体                         |
|----------|------------------------------------------------|----------------------------|
| Identity | 无                                              | 有稳定 ID                     |
| 相等性      | 按属性值                                           | 按 ID                       |
| 可变性      | 不可变                                            | 可变                         |
| 生命周期     | 短,依附实体                                         | 独立                         |
| 替换       | 可自由替换                                          | 不可替换                       |
| Java 实现  | `record`                                       | `class` + ID 字段            |
| 典型例子     | `Money`, `Address`, `PhoneNumber`, `DateRange` | `User`, `Order`, `Product` |

#### 19.8.4 典型值对象库(建议放 `common/`)

| 值对象                                | 所属包               | 用途        |
|------------------------------------|-------------------|-----------|
| `Money(amount, currency)`          | `common.money`    | 货币金额      |
| `PhoneNumber(countryCode, number)` | `common.contact`  | 手机号       |
| `EmailAddress(value)`              | `common.contact`  | 邮箱        |
| `IdCardNumber(value)`              | `common.identity` | 身份证号(校验位) |
| `DateRange(start, end)`            | `common.time`     | 日期区间      |
| `GpsLocation(lat, lng)`            | `common.geo`      | GPS 坐标    |

注意:只有**真正跨 BC 稳定**的值对象才放 `common/`。BC 内部的值对象(如 `OrderStatus`)放在 BC 自己的 `domain/model/`。

### 19.9 战术设计:领域事件(Domain Event)

#### 19.9.1 设计规则

1. **命名必须是过去时** — `OrderPaid`,不是 `PayOrder`(那是命令)
2. **载荷最小** — 只放 ID、时间、必要上下文;不放整个聚合
3. **不可变** — Java `record`
4. **可被序列化** — 为了能走消息队列或存事件表
5. **用 `sealed interface` 收拢** — 一个 BC 的所有事件列出来,穷尽匹配

#### 19.9.2 Java 代码模板

```java
// order/domain/event/DomainEvent.java
package com.example.myapp.order.domain.event;

import java.time.Instant;

public sealed interface DomainEvent
        permits OrderCreated, OrderPaid, OrderCancelled, OrderShipped {
    Instant occurredAt();
}

// order/domain/event/OrderPaid.java
import com.example.myapp.common.money.Money;
import com.example.myapp.order.domain.model.OrderId;
import com.example.myapp.order.domain.model.UserId;

public record OrderPaid(
        OrderId orderId,
        UserId userId,
        Money amount,
        Instant occurredAt
) implements DomainEvent {}
```

#### 19.9.3 发布:Spring Events + ApplicationModuleListener

```java
// order/adapter/out/messaging/OrderEventPublisher.java
@Component
class OrderEventPublisher {
    private final ApplicationEventPublisher publisher;

    public OrderEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    void publishAll(List<DomainEvent> events) {
        events.forEach(publisher::publishEvent);
    }
}
```

#### 19.9.4 订阅:跨 BC 消费

```java
// user/adapter/in/event/OrderEventListener.java
@Component
class OrderEventListener {

    private final AddLoyaltyPointsUseCase addPoints;

    public OrderEventListener(AddLoyaltyPointsUseCase addPoints) {
        this.addPoints = addPoints;
    }

    // Spring Modulith 的 @ApplicationModuleListener:
    // - 自动 @Async(虚拟线程执行)
    // - 自动 @Transactional(propagation = REQUIRES_NEW)
    // - 事件发布方事务提交后才执行
    @ApplicationModuleListener
    void on(OrderPaid event) {
        addPoints.execute(event.userId(), event.amount());
    }
}
```

#### 19.9.5 事务边界与事件发布的时序

```mermaid
sequenceDiagram
    participant C as Controller
    participant UC as PayOrderUseCase
    participant O as Order<br/>(聚合根)
    participant R as OrderRepository
    participant EP as EventPublisher
    participant L as OrderEventListener<br/>(User BC)

    C->>UC: pay(orderId)
    activate UC
    Note over UC: @Transactional begin
    UC->>R: load(orderId)
    R-->>UC: Order
    UC->>O: pay()
    O->>O: status = PAID<br/>events.add(OrderPaid)
    UC->>R: save(order)
    UC->>EP: publish(order.pullEvents())
    Note over UC: @Transactional commit
    deactivate UC
    Note over EP,L: 事务提交后 Spring 异步派发
    EP->>L: OrderPaid
    activate L
    L->>L: addLoyaltyPoints()
    deactivate L
```

### 19.10 六边形架构:Ports & Adapters 的完整落地

#### 19.10.1 端口的两个方向

| 方向              | Port(接口)                      | Adapter(实现)                           | 谁调谁                       |
|-----------------|-------------------------------|---------------------------------------|---------------------------|
| **Inbound** 入站  | `CreateOrderUseCase`(应用服务接口)  | `OrderController`(Web adapter)        | Adapter 调 Port            |
| **Outbound** 出站 | `OrderRepository`(领域视角的持久化接口) | `OrderJooqRepositoryAdapter`(jOOQ 实现) | 应用层调 Port,Adapter 实现 Port |

```mermaid
flowchart LR
    subgraph IN["入站适配器"]
        Web["REST Controller"]
        Ev["Event Listener"]
        Sch["Scheduled Job"]
    end

    subgraph CORE["领域 + 应用(核心六边形)"]
        InPort["入站端口<br/>UseCase 接口"]
        UC["UseCase 实现"]
        DOM["Domain Model"]
        OutPort["出站端口<br/>Repository/Gateway 接口"]
    end

    subgraph OUT["出站适配器"]
        JOOQ["jOOQ Repository"]
        HTTP["HTTP Client"]
        Msg["Event Publisher"]
    end

    Web --> InPort
    Ev --> InPort
    Sch --> InPort
    InPort --> UC
    UC --> DOM
    UC --> OutPort
    OutPort --> JOOQ
    OutPort --> HTTP
    OutPort --> Msg
```

#### 19.10.2 推荐包结构(替代旧版 3.1 的 `api/domain/repo/internal/`)

```
<bounded-context>/                     # Gradle 子模块
└── src/main/java/com/example/myapp/<bc>/
    ├── domain/                         # 领域层(零框架依赖)
    │   ├── model/                      # Aggregates, Entities, Value Objects
    │   │   ├── Order.java              # 聚合根
    │   │   ├── OrderItem.java          # 聚合内实体
    │   │   ├── OrderId.java            # ID 类型(record)
    │   │   ├── OrderStatus.java        # enum
    │   │   └── ShippingAddress.java    # 值对象
    │   ├── event/                      # 领域事件
    │   │   ├── DomainEvent.java        # sealed interface
    │   │   ├── OrderCreated.java
    │   │   └── OrderPaid.java
    │   ├── service/                    # 领域服务(跨聚合的纯业务规则)
    │   │   └── OrderPricingService.java
    │   └── exception/                  # 领域异常
    │       └── IllegalOrderStateException.java
    │
    ├── application/                    # 应用层(协调器,定义端口)
    │   ├── port/
    │   │   ├── in/                     # 入站端口(UseCase 接口)
    │   │   │   ├── CreateOrderUseCase.java
    │   │   │   ├── PayOrderUseCase.java
    │   │   │   └── CancelOrderUseCase.java
    │   │   └── out/                    # 出站端口(Gateway/Repository 接口)
    │   │       ├── OrderRepository.java          # 持久化接口
    │   │       ├── PaymentGateway.java           # 外部支付接口
    │   │       └── NotificationSender.java       # 外部通知接口
    │   └── usecase/                    # UseCase 实现(事务边界在这一层)
    │       ├── CreateOrderService.java
    │       ├── PayOrderService.java
    │       └── CancelOrderService.java
    │
    ├── adapter/                        # 适配器层
    │   ├── in/                         # 入站(外部 → 系统)
    │   │   ├── web/                    # REST Controller + Request/Response DTOs
    │   │   │   ├── OrderController.java
    │   │   │   ├── CreateOrderRequest.java
    │   │   │   ├── OrderResponse.java
    │   │   │   └── dto/                 # 仅当 DTO ≥ 5 个时开此子包(否则扁平)
    │   │   │       └── ...
    │   │   ├── event/                  # 消费其他 BC 的事件
    │   │   │   └── PaymentEventListener.java
    │   │   └── job/                    # 定时触发
    │   │       └── OrderTimeoutJob.java
    │   └── out/                        # 出站(系统 → 外部)
    │       ├── persistence/            # jOOQ 实现 OrderRepository
    │       │   ├── OrderJooqRepositoryAdapter.java
    │       │   └── OrderMapper.java    # jOOQ Record ↔ Order 聚合
    │       ├── messaging/              # 发布本 BC 的事件
    │       │   └── OrderEventPublisher.java
    │       └── http/                   # 调外部服务
    │           └── ShippingHttpAdapter.java
    │
    └── config/                         # Spring 装配
        ├── OrderModuleConfig.java
        └── OrderProperties.java
```

#### 19.10.3 UseCase + Port 的 Java 代码模板

**入站端口(应用服务接口)**:

```java
// order/application/port/in/PayOrderUseCase.java
package com.example.myapp.order.application.port.in;

import com.example.myapp.order.domain.model.OrderId;

public interface PayOrderUseCase {
    void execute(Command command);

    record Command(OrderId orderId, String paymentChannel) {}
}
```

**出站端口(领域视角的接口)**:

```java
// order/application/port/out/OrderRepository.java
package com.example.myapp.order.application.port.out;

import com.example.myapp.order.domain.model.Order;
import com.example.myapp.order.domain.model.OrderId;

import java.util.Optional;

public interface OrderRepository {
    Optional<Order> findById(OrderId id);
    void save(Order order);
}
```

**UseCase 实现**:

```java
// order/application/usecase/PayOrderService.java
package com.example.myapp.order.application.usecase;

import com.example.myapp.order.application.port.in.PayOrderUseCase;
import com.example.myapp.order.application.port.out.OrderRepository;
import com.example.myapp.order.application.port.out.PaymentGateway;
import com.example.myapp.order.domain.model.Order;
import com.example.myapp.common.exception.AppException;
import com.example.myapp.common.exception.AppCode;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PayOrderService implements PayOrderUseCase {

    private final OrderRepository orderRepo;
    private final PaymentGateway payment;
    private final ApplicationEventPublisher events;

    PayOrderService(OrderRepository orderRepo, PaymentGateway payment,
                    ApplicationEventPublisher events) {
        this.orderRepo = orderRepo;
        this.payment = payment;
        this.events = events;
    }

    @Override
    @Transactional
    public void execute(Command cmd) {
        Order order = orderRepo.findById(cmd.orderId())
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));

        // 调外部支付(出站适配器)
        payment.charge(cmd.orderId(), order.totalAmount(), cmd.paymentChannel());

        // 调用领域行为,聚合内部改状态 + 产生事件
        order.pay();

        // 持久化
        orderRepo.save(order);

        // 发布领域事件(事务提交后异步派发)
        order.pullEvents().forEach(events::publishEvent);
    }
}
```

**Web 适配器(入站)**:

```java
// order/adapter/in/web/OrderController.java
package com.example.myapp.order.adapter.in.web;

import com.example.myapp.order.application.port.in.PayOrderUseCase;
import com.example.myapp.order.domain.model.OrderId;
import com.example.myapp.common.result.R;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    private final PayOrderUseCase payOrder;

    OrderController(PayOrderUseCase payOrder) {
        this.payOrder = payOrder;
    }

    @PostMapping("/{orderId}/pay")
    public R<Void> pay(@PathVariable String orderId,
                       @RequestBody PayOrderRequest req) {
        payOrder.execute(new PayOrderUseCase.Command(
                OrderId.of(orderId), req.channel()));
        return R.ok();
    }
}
```

**jOOQ 适配器(出站)**:

```java
// order/adapter/out/persistence/OrderJooqRepositoryAdapter.java
package com.example.myapp.order.adapter.out.persistence;

import com.example.myapp.order.application.port.out.OrderRepository;
import com.example.myapp.order.domain.model.Order;
import com.example.myapp.order.domain.model.OrderId;
import com.example.myapp.order.infra.jooq.generated.tables.Orders;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class OrderJooqRepositoryAdapter implements OrderRepository {

    private final DSLContext dsl;
    private final OrderMapper mapper;

    OrderJooqRepositoryAdapter(DSLContext dsl, OrderMapper mapper) {
        this.dsl = dsl;
        this.mapper = mapper;
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        var record = dsl.selectFrom(Orders.ORDERS)
                .where(Orders.ORDERS.ID.eq(id.value()))
                .fetchOptional();
        return record.map(mapper::toAggregate);
    }

    @Override
    public void save(Order order) {
        // UPSERT 实现略
        mapper.toRecord(order).store();
    }
}
```

**关键点**:

- `PayOrderService` 在 `application/usecase/` 下,不在 `domain/`
- `OrderRepository` 接口在 `application/port/out/`,实现在 `adapter/out/persistence/`
- 领域事件由 `Order` 聚合产生,`PayOrderService` 发布,`ApplicationEventPublisher` 派发
- Controller 完全不知道聚合,只知道 UseCase + DTO

### 19.11 Clean Architecture 的四层映射

Robert C. Martin 的 Clean Architecture 四层与 Hexagonal 的对应:

```mermaid
flowchart TB
    subgraph L1["Entities(企业业务规则)"]
        E[domain/model/<br/>domain/service/]
    end
    subgraph L2["Use Cases(应用业务规则)"]
        U[application/usecase/<br/>application/port/]
    end
    subgraph L3["Interface Adapters"]
        A[adapter/in/<br/>adapter/out/]
    end
    subgraph L4["Frameworks &amp; Drivers"]
        F[Spring / jOOQ /<br/>Tomcat / Redis]
    end

    L4 --> L3
    L3 --> L2
    L2 --> L1
```

| Clean 层              | Hexagonal   | Kiln 包                                                |
|----------------------|-------------|-------------------------------------------------------|
| Entities             | Domain      | `<bc>/domain/model/`, `<bc>/domain/service/`          |
| Use Cases            | Application | `<bc>/application/usecase/`, `<bc>/application/port/` |
| Interface Adapters   | Adapters    | `<bc>/adapter/in/`, `<bc>/adapter/out/`               |
| Frameworks & Drivers | —           | `infra/`, 第三方库                                        |

### 19.12 用 ArchUnit 硬约束依赖方向

仅靠人工 review 守不住 Clean Rule,用 **ArchUnit** 写单测自动检查:

```groovy
// <bc>/build.gradle
testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
```

```java
// <bc>/src/test/java/.../ArchitectureTest.java
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final com.tngtech.archunit.core.domain.JavaClasses CLASSES =
            new ClassFileImporter().importPackages("com.example.myapp.order");

    @Test
    void domainHasNoSpringDependencies() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "org.jooq..",
                        "com.fasterxml.jackson..")
                .check(CLASSES);
    }

    @Test
    void domainDoesNotDependOnApplication() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..application..")
                .check(CLASSES);
    }

    @Test
    void applicationDoesNotDependOnAdapter() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .check(CLASSES);
    }
}
```

**CI 失败 = PR 被拦下**,强制所有人遵守依赖规则。

### 19.13 领域异常 vs 应用异常 vs 基础设施异常

| 层                  | 异常类型   | 例子                                | 处理策略                             |
|--------------------|--------|-----------------------------------|----------------------------------|
| **Domain**         | 领域异常   | `IllegalOrderStateException`      | `RuntimeException` 子类,不捕获,让它冒泡   |
| **Application**    | 应用异常   | `AppException(AppCode.NOT_FOUND)` | 在 UseCase 里用 `AppCode` 表达业务错误    |
| **Infrastructure** | 基础设施异常 | `DataAccessException`(jOOQ)       | 在 Adapter 层翻译成领域/应用异常,**不泄漏到上层** |

**反例**:

```java
// ❌ UseCase 捕获 jOOQ 异常 — adapter 的异常泄漏到 application 层
@Override
@Transactional
public void execute(Command cmd) {
    try {
        orderRepo.save(order);
    } catch (DataAccessException e) {   // 不应该出现在这里
        throw new AppException(...)
    }
}
```

**正例**:

```java
// ✓ Adapter 捕获 jOOQ 异常并翻译
@Override
public void save(Order order) {
    try {
        mapper.toRecord(order).store();
    } catch (DataAccessException e) {
        throw new OrderPersistenceException(order.id(), e);
    }
}
```

### 19.14 数据库设计与聚合的对应

**规则**:**一张表不能跨多个聚合**。

| 聚合根       | 主表         | 聚合内子表                        | 跨聚合引用                   |
|-----------|------------|------------------------------|-------------------------|
| `Order`   | `orders`   | `order_items`(`order_id` FK) | `user_id`(仅存 ID,不 JOIN) |
| `User`    | `users`    | `user_addresses`             | —                       |
| `Payment` | `payments` | `payment_notifications`      | `order_id`(仅 ID)        |

**反模式**:

- ❌ 在 `orders` 表上直接 JOIN `users` 展示订单列表(跨聚合访问)
- ✅ 正确做法:`OrderListQuery` 读模型(CQRS 读侧)独立,走只读 SQL 或物化视图

### 19.15 jOOQ Record 不暴露到 domain

jOOQ 生成的 `Record` 是**持久化模型**,不是**领域模型**。必须在 `adapter/out/persistence/` 做双向映射:

```java
// order/adapter/out/persistence/OrderMapper.java
@Component
class OrderMapper {

    Order toAggregate(OrdersRecord r) {
        return Order.reconstitute(
                OrderId.of(r.getId()),
                UserId.of(r.getUserId()),
                loadItems(r.getId()),
                new ShippingAddress(r.getShippingAddress()),
                OrderStatus.valueOf(r.getStatus()),
                r.getCreatedAt(),
                r.getPaidAt()
        );
    }

    OrdersRecord toRecord(Order order) {
        OrdersRecord r = new OrdersRecord();
        r.setId(order.id().value());
        r.setUserId(order.userId().value());
        r.setStatus(order.status().name());
        r.setTotalAmount(order.totalAmount().amount());
        r.setCurrency(order.totalAmount().currency().getCurrencyCode());
        r.setCreatedAt(order.createdAt());
        r.setPaidAt(order.paidAt());
        return r;
    }
}
```

### 19.16 反腐层(ACL):第 3.2 节的 DDD 语义

第 3.2 节的"**门面 + provider**"模式,在 DDD 术语里就是 **Anti-Corruption Layer**:

```mermaid
flowchart LR
    subgraph Order["Order BC(纯净)"]
        OUC[PayOrderService]
        PG[PaymentGateway<br/>出站端口]
    end

    subgraph Payment["Payment BC"]
        PS[PaymentService 门面]
        subgraph ACL["ACL:WechatPayProvider"]
            WPP[翻译脏模型]
            WSDK[wechatpay-java<br/>官方 SDK]
        end
    end

    subgraph WX["微信支付系统(外部)"]
        WAPI[APIv3 JSON 结构]
    end

    OUC --> PG
    PG -.实现.-> PS
    PS --> WPP
    WPP --> WSDK
    WSDK --> WAPI
```

**ACL 的职责**:

1. **协议翻译** — 微信的 `out_trade_no` 字段 → 本域 `PaymentId`
2. **模型翻译** — 微信的 JSON 嵌套结构 → 本域 `PayResult` 值对象
3. **异常翻译** — `WechatApiException` → `PaymentChannelException`
4. **隔离变化** — 微信 API 版本升级只影响 ACL,不影响 Order BC

### 19.17 DDD 的分级采用:Kiln 现有模块分类

基于 19.6 的核心/支撑/通用子域理论,Kiln 项目的现有模块建议分级:

| 模块             | 分类                | 建议架构严格度                                        | 原因                |
|----------------|-------------------|------------------------------------------------|-------------------|
| `order`        | Core Domain       | 严格 Hexagonal + 聚合根 + 领域事件 + ArchUnit           | 订单是电商/SaaS 的核心竞争力 |
| `payment`      | Core(但 ACL 重)     | Hexagonal + 每个支付厂商独立 ACL + 资金一致性校验             | 真金白银,错一分钱都是事故     |
| `product`      | Core              | 严格 Hexagonal(若是电商);Supporting(若是中台)            | 视业务定位             |
| `workflow`     | Core 或 Supporting | 严格 Hexagonal(若流程是产品差异化);Flowable 直接用(若流程是运营工具) | 视业务               |
| `user`         | Supporting        | 简化 Hexagonal(可合并 `service` 和 `usecase`)        | 用户管理通用,可买可用       |
| `notification` | Supporting        | ACL 模式(多通道) + 简化 UseCase                       | 通道厂商多,但业务逻辑浅      |
| `file`         | Supporting        | ACL 模式(多云存储)                                   | 同上                |
| `auth`         | Generic           | **直接 CRUD + Sa-Token**,不需要 DDD                 | 认证授权是标准化能力        |
| `audit`        | Generic           | 直接 CRUD + AOP 拦截                               | 审计逻辑固定            |
| `dict`         | Generic           | 直接 CRUD + Redis 缓存                             | 字典就是键值对           |
| `tenant`       | Generic           | 直接 CRUD + 上下文 Filter                           | 多租户是基础设施          |

**规则**:

- **Core Domain 不允许图省事** — 一定要有聚合根,否则几年后核心业务代码变成泥潭
- **Generic Subdomain 不允许过度设计** — 字典模块写成 4 层 Hexagonal 是自杀
- **Supporting 看团队** — 人手充足可以上 Hexagonal,紧张就合并层次

### 19.18 与前面章节的关系(Diff 式说明)

本章提出的结构与前面章节的对应关系:

| 前面章节                        | 本章的再定位                                                                                             |
|-----------------------------|----------------------------------------------------------------------------------------------------|
| **3.1 模块结构**                | 每个 BC 内部的 `api/domain/repo/internal/` **升级为** `domain/application/adapter/config/`(见 19.10.2 完整目录) |
| **3.2 门面 + provider**       | **命名为** Anti-Corruption Layer 模式                                                                   |
| **3.5 模块依赖方向**              | 由 "横切能力 → 平台层" **细化为** "adapter → application → domain,严格单向,ArchUnit 守护"                           |
| **5.5 Domain 模型**           | 升级为**聚合根** + `static reconstitute()` + 业务方法;jOOQ Record 放在 `adapter/out/persistence/`              |
| **5.6 Repository 层**        | `Repository` 接口放在 `application/port/out/`;jOOQ 实现放在 `adapter/out/persistence/`                     |
| **14.3 业务异常**               | 拆分为 Domain 异常 / Application 异常 / Infrastructure 异常,各司其职                                            |
| **16.11 Modulith 测试**       | 保持,额外增加 ArchUnit 测试(本章 19.12)                                                                      |
| **3.15 @ApplicationModule** | 保持,`allowedDependencies` 的语义就是 Context Map 的形式化                                                    |

### 19.19 迁移路径:从旧结构到新结构

对已按 3.1 旧结构写的代码,分阶段迁移(**不要一次性重构**):

| 阶段                        | 动作                                                                                      | 风险 |
|---------------------------|-----------------------------------------------------------------------------------------|----|
| **1. 新 BC 直接用新结构**        | 未来的业务模块按本章结构创建                                                                          | 低  |
| **2. 添加 ArchUnit 测试(宽松)** | 先跑在新 BC,旧 BC 加 `@Disabled` 或 `// TODO`                                                  | 低  |
| **3. 旧 BC 局部重构**          | 挑一个最活跃的核心域(如 `order`),拆 `api/` → `adapter/in/web/`,`repo/` → `adapter/out/persistence/` | 中  |
| **4. 统一启用 ArchUnit**      | 所有 BC 通过检查,PR 门槛提升                                                                      | 中  |
| **5. 引入聚合根**              | 找到最重要的实体(Order / Payment),改造为聚合根 + 领域事件                                                 | 高  |

**不做**:

- 不一次性重构所有模块 — 大爆炸式重构 90% 会失败
- 不强制 Generic 子域采用 DDD — 浪费人力
- 不重命名不改结构只为"好看" — 没业务价值

### 19.20 本章不覆盖的主题(建议延伸阅读)

以下 DDD 高级主题本章不展开,留给团队按需深入:

- **CQRS(Command Query Responsibility Segregation)** — 读写模型分离,适合读写负载差异巨大的场景
- **Event Sourcing** — 以事件流作为状态真相源,适合审计强需求、金融场景;代价高
- **Saga 模式** — 跨聚合/跨 BC 的长事务编排,与 Flowable 工作流有重叠
- **领域特定语言(DSL)** — 用 Kotlin DSL / Java Fluent API 建模业务规则

**推荐参考资料**:

- Eric Evans《Domain-Driven Design》(2003) — 蓝皮书,战略+战术完整理论
- Vaughn Vernon《Implementing Domain-Driven Design》(2013) — 红皮书,大量代码示例
- Vladimir Khorikov《Domain Modeling Made Functional》 — F# 视角,对 Java 的 `record` + `sealed` 启发巨大
- Tom Hombergs《Get Your Hands Dirty on Clean Architecture》 — 六边形架构 Java 实战
- Robert C. Martin《Clean Architecture》 — Clean 的理论基础

---

