# Kiln

> 基于 **Spring Boot 4** 与 **Spring Modulith** 的模块化单体 (Modular Monolith) 应用脚手架。

[English README](./README.md)

## 项目简介

Kiln 是一个面向生产环境的 Java 后端应用起点,采用模块化单体架构 —— 在保留单体部署与开发简单性的同时,通过 Spring Modulith 的模块边界约束避免代码耦合失控,为未来演进到微服务保留清晰的拆分线。

- **Group**: `com.skyflux`
- **Artifact**: `kiln`
- **版本**: `0.0.1-SNAPSHOT`

## 技术栈

| 分类 | 依赖 |
| --- | --- |
| 运行时 | Java 25 (Gradle Toolchain)、Spring Boot 4.0.5 |
| 架构 | Spring Modulith 2.0.5 (Core / JDBC / Runtime / Actuator / Observability) |
| Web | Spring Web MVC、Spring Security、SpringDoc OpenAPI (Swagger UI) |
| 数据 | Spring Data JDBC、jOOQ、Flyway 迁移、Spring Data Redis |
| 视图 | Apache FreeMarker |
| HTTP 客户端 | Spring `RestClient` |
| 可观测性 | Spring Boot Actuator、OpenTelemetry、Micrometer (OTLP & Prometheus registry) |
| 开发工具 | Spring Boot DevTools (仅开发期) |
| 测试 | JUnit 5、Spring Boot Test、Modulith Test、REST Docs、Security Test、各数据源切片测试 |
| 构建 | Gradle (Groovy DSL)、Asciidoctor (REST Docs 渲染) |

## 环境要求

- **JDK 25** —— 构建脚本通过 Gradle Toolchain 自动下载/匹配,本地无需预装,但推荐安装以获得 IDE 支持。
- **Gradle** —— 使用项目内置 Wrapper (`./gradlew`),无需单独安装。
- **Redis** —— 运行期需要可访问的 Redis 实例。
- **关系型数据库** —— 由 Flyway 驱动迁移;请在 `application.yaml` 中配置 `spring.datasource.*`。

## 快速开始

### 1. 克隆仓库

```bash
git clone <your-repo-url> kiln
cd kiln
```

### 2. 运行应用

```bash
./gradlew bootRun
```

默认端口为 Spring Boot 的 `8080`。可通过 `--args='--server.port=9000'` 覆盖。

### 3. 运行测试

```bash
./gradlew test
```

测试通过后,`build/generated-snippets` 将产出 Spring REST Docs 代码片段。

### 4. 生成 REST Docs 文档

```bash
./gradlew asciidoctor
```

### 5. 构建可执行 Jar

```bash
./gradlew bootJar
java -jar build/libs/kiln-0.0.1-SNAPSHOT.jar
```

### 6. 构建 OCI 镜像 (可选)

Spring Boot Gradle 插件支持直接打包 OCI 镜像,无需编写 Dockerfile:

```bash
./gradlew bootBuildImage
```

## 目录结构

```
kiln/
├── build.gradle                         # 构建脚本与依赖声明
├── settings.gradle
├── gradle/wrapper/                      # Gradle Wrapper
├── src/
│   ├── main/
│   │   ├── java/com/skyflux/kiln/
│   │   │   └── KilnApplication.java     # Spring Boot 启动类
│   │   └── resources/
│   │       └── application.yaml         # 应用配置
│   └── test/
│       └── java/com/skyflux/kiln/
│           └── KilnApplicationTests.java
└── HELP.md                              # Spring Initializr 生成的参考链接
```

> **模块化建议**:在 `com.skyflux.kiln` 之下为每个业务域建立独立子包 (如 `order/`、`catalog/`、`billing/`),Spring Modulith 会自动将其识别为模块边界,跨包调用仅允许通过 `api` 子包暴露的公开接口。测试中通过 `ApplicationModules.verify()` 校验 —— 这正是使用 Modulith 的核心价值。

## 配置参考

当前 `application.yaml` 仅声明了应用名:

```yaml
spring:
  application:
    name: kiln
```

接入数据库、Redis、OpenTelemetry 时,需要补充如下配置(示例):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/kiln
    username: kiln
    password: change-me
  data:
    redis:
      host: localhost
      port: 6379
  flyway:
    enabled: true
    locations: classpath:db/migration

management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  otlp:
    metrics:
      export:
        url: http://localhost:4318/v1/metrics
```

## 观测性端点

启用 Actuator 后,可通过以下路径访问:

- 健康检查: `GET /actuator/health`
- Prometheus 指标: `GET /actuator/prometheus`
- Modulith 模块图: `GET /actuator/modulith`
- OpenAPI 文档 (SpringDoc): `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui.html`

## 参考文档

更详细的外部参考链接见 [`HELP.md`](./HELP.md),包括 Spring Boot 4、Spring Modulith、jOOQ、Flyway、OpenTelemetry、REST Docs 等官方文档。

## 许可证

待定。
