# 蜜记 · Bee Family — 系统架构设计与任务分解

| 项目 | 内容 |
| --- | --- |
| 文档版本 | v1.0 |
| 日期 | 2026-11-05 |
| 作者 | 高见远（架构师） |
| 状态 | 已定稿 —— 工程师编码的唯一依据 |
| 上游文档 | `docs/01-prd.md`（范围唯一依据）、`docs/02-ui-design.md`（视觉唯一依据） |
| 项目代号 | `bee-family` |
| 语言 | 简体中文 |

> **阅读约定**
> 1. 本文出现的所有路径均为**项目根目录相对路径**（根 = `/Users/lijinhai/Documents/workspace_ai/bee-family/`）。
> 2. 本文出现的所有接口路径**均已包含 `/api` 前缀**，前后端不得自行增删。
> 3. 本文出现的所有 DTO 字段名 = JSON 字段名 = 前端 TS 字段名（`camelCase`），**不额外转换**。
> 4. 与 PRD / UI 文档冲突时，以本文为准；本文未提及且 PRD 已明确时，以 PRD 为准。

---

## 目录

1. [架构总览](#1-架构总览)
2. [技术选型表](#2-技术选型表)
3. [后端分层与包结构](#3-后端分层与包结构)
4. [数据模型](#4-数据模型)
5. [API 契约](#5-api-契约)
6. [关键流程时序图](#6-关键流程时序图)
7. [安全设计](#7-安全设计)
8. [存储设计](#8-存储设计)
9. [前端架构](#9-前端架构)
10. [任务分解](#10-任务分解)
11. [共享约定（跨文件契约）](#11-共享约定跨文件契约)
12. [风险与待明确](#12-风险与待明确)

---

## 1. 架构总览

### 1.1 分层架构图

```mermaid
graph TB
    subgraph CLIENT["客户端 · 浏览器"]
        BROWSER["Chrome / Edge / Safari<br/>桌面优先，最小宽度 375px"]
    end

    subgraph SPA["前端 SPA · Vue 3.5 + TS"]
        VIEWS["views 页面层<br/>10 个视图文件（9 页面 + 404）"]
        COMP["components 组件层<br/>base 19 个 / business 17 个"]
        STORE["stores 状态层<br/>Pinia 5 个 store"]
        APILAYER["api 接口层<br/>http.ts 拦截器 + 11 个领域模块"]
        STYLES["styles 设计系统<br/>tokens.css / reset.css / base.css"]
    end

    subgraph DEV["开发期"]
        VITE["Vite Dev Server :5173<br/>proxy /api → :8080"]
    end

    subgraph SERVER["Spring Boot 3.3 单体 · :8080"]
        FILTER["JwtAuthenticationFilter<br/>Bearer / ?token= 双凭证"]
        GUARD["FamilyAccessGuard<br/>家庭隔离闸门"]
        CTRL["Controller 层<br/>12 个 RestController"]
        SVC["Service 层<br/>事务边界 · 权限校验"]
        REPO["Repository 层<br/>Spring Data JPA + 1 个原生 UNION 查询"]
        FILE["FileController<br/>GET /api/files/**"]
        EXC["GlobalExceptionHandler<br/>API-response 统一出口"]
    end

    subgraph INFRA["基础设施 · 单机"]
        DB[("MySQL 8.4<br/>bee_family · utf8mb4_0900_ai_ci")]
        DISK[("本地磁盘<br/>backend/data/uploads")]
    end

    BROWSER --> VIEWS
    VIEWS --> COMP --> STORE --> APILAYER
    APILAYER -->|"HTTP · axios baseURL<br/>VITE_API_BASE_URL"| VITE
    VITE --> FILTER
    STYLES -.-> COMP
    FILTER --> GUARD --> CTRL --> SVC --> REPO --> DB
    CTRL --> FILE --> DISK
    SVC --> DISK
    CTRL -.-> EXC
    SVC -.-> EXC
    REPO -.-> EXC
```

### 1.2 单机部署拓扑

```
┌──────────────────────────────────────────────────────────────────────────┐
│ 浏览器                                                                    │
│   ├─ 开发期：http://localhost:5173  →  Vite Dev Server                   │
│   │              /api/* 反向代理 → http://localhost:8080                  │
│   └─ 生产期：http://localhost:8080  →  Spring Boot 静态资源              │
│                  （frontend/dist 由 Maven 构建期复制至                       │
│                    backend/src/main/resources/static）                    │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │ HTTP
┌────────────────────────────▼─────────────────────────────────────────────┐
│ Spring Boot 3.3.x :8080  （java 17，单进程，无反向代理，无容器）           │
│   Filter 链：TraceIdFilter → JwtAuthenticationFilter → SecurityFilterChain│
│   ├─ /api/**                 → Controller → Service → Repository          │
│   └─ /api/files/**           → FileController → FileSystemResource        │
└──────────┬──────────────────────────────────────────┬─────────────────────┘
           │ JDBC (HikariCP, pool 10)                  │ java.nio.file
┌──────────▼───────────────────┐        ┌──────────────▼─────────────────────┐
│ MySQL 8.4  127.0.0.1:3306    │        │ backend/data/uploads              │
│   database: bee_family       │        │   {familyId}/{yyyy}/{MM}/...       │
│   Flyway 管理表结构           │        │   三档：原图 / _thumb / _preview   │
└──────────────────────────────┘        └────────────────────────────────────┘
```

**部署形态说明**

| 项 | 决策 | 说明 |
| --- | --- | --- |
| 进程数 | 1 个 JVM | 家庭内网自用，无高可用诉求，禁止引入 Nginx / Docker / Redis |
| 前端产物 | 开发期走 Vite Dev Server；生产期 `mvn -Pprod package` 把 `frontend/dist` 拷入 `static/` | 单端口部署，避免 CORS 与跨域配置负担 |
| 会话 | 无状态 JWT，无 Session 存储 | 服务重启不掉线 |
| 缓存 | **不引入** | 数据量 ≤50k 照片/家庭，MySQL 索引足够；与 P0-12「实时聚合非快照」一致 |
| 队列 | **不引入** | 缩略图在请求线程内同步生成（单张 ≤5s 已满足）；异步化留 P1 |

### 1.3 前后端职责边界

| 关注点 | 前端负责 | 后端负责 | 判定依据 |
| --- | --- | --- | --- |
| 表单校验 | 即时反馈（长度、范围、未来日期、扩展名、≤20MB） | **权威校验**（同规则 Bean Validation 重跑一遍） | 前端校验可被绕过；后端校验是唯一防线 |
| 权限 | 只做 UI 可见性（隐藏「移除成员」按钮） | **权威鉴权**（每次请求从 DB 解析家庭关系） | PRD §10「所有数据查询强制携带 family_id」 |
| 拍摄时间 | 不推导 | EXIF 优先，缺失回退 `LocalDateTime.now()` | PRD Q5 |
| 缩略图 | 只做 `URL.createObjectURL` 本地预览 | 生成 480 / 1600 双档，落盘，写库 | 服务端能力，前端不产生产物 |
| 排序与分页 | 传 `page/size/sort`，不本地排序 | 全局排序 + 分页（时间轴在 SQL 层 UNION） | 见 §5.7 时间轴分页策略结论 |
| 家庭隔离 | 无感知 | 每个查询强制 `family_id`，越权 403 | PRD §10 |
| 错误文案 | 按 `ErrorCode` 映射中文文案与可操作建议 | 只返回 `code` + 服务端基准 `message` | 前端负责「iPhone 设置 → 相机 → 格式」这类操作引导 |

---

## 2. 技术选型表

### 2.1 后端

| # | 选型 | 版本 | 理由 | 替代方案为何不选 |
| --- | --- | --- | --- | --- |
| B-1 | Spring Boot | 3.3.x | 与 Java 17 基线一致；`spring-boot-starter-web/security/validation/data-jpa` 覆盖全部 P0 能力；内嵌 Tomcat 单进程即部署 | Quarkus / Micronaut：团队熟悉度低，P0 收益为零 |
| B-2 | Java | **17（LTS）** | 环境默认 JDK 25，但 CI 与本地必须显式 `JAVA_HOME` 指向 17，`<maven.compiler.release>17</maven.compiler.release>` 保证字节码不越界 | JDK 25：Spring Boot 3.3 官方支持矩阵未覆盖，Lombok 注解处理器在高版本 JDK 上易踩坑 |
| B-3 | Maven | 3.9.9 | 环境已安装；单模块足够 | Gradle：需额外安装，无收益 |
| B-4 | Spring Data JPA (Hibernate 6.5) | 随 Boot | 10 张表、关系简单，JPA 减少 80% 样板 SQL；`@Query(nativeQuery=true)` 保留复杂查询出口（时间轴 UNION） | MyBatis：需要维护 XML，本项目无复杂动态 SQL 诉求 |
| B-5 | **Flyway** | 10.x（随 Boot 3.3） | 表结构版本化，`ddl-auto: validate` 让实体与 DDL 漂移在启动时立刻暴露；`V1__init.sql` 可直接贴入评审 | `ddl-auto: update`：**明令禁止**，会静默改表、无法回滚、索引不可控；Liquibase：XML/YAML 冗长 |
| B-6 | Spring Security 6 | 随 Boot | Filter 链标准实现，401/403 语义清晰；`SecurityContextHolder` 提供全链路当前用户 | 自研拦截器：手写过滤器容易漏放行路径 |
| B-7 | jjwt | 0.12.x（`jjwt-api` + `jjwt-impl` + `jjwt-jackson`） | 0.12 是当前稳定线，API 为 `Jwts.builder().signWith(key)`；无 native 依赖 | java-jwt：API 尚可但需额外引入 Gson/Jackson 适配；nimbus-jose-jwt：功能过剩 |
| B-8 | Thumbnailator | 0.4.20 | 纯 Java，两行生成等比缩放 + 质量控制；支持从 `InputStream` 读、写 JPEG 并丢弃 EXIF（缩略图无需元数据） | ImageIO 手写：缩放插值质量差、抗锯齿需自调；libvips/JNI：需要 native 库，内网自托管增加运维面 |
| B-9 | metadata-extractor（`com.drewnoakes`） | 2.19.0 | 纯 Java 读 EXIF，一行拿 `DateTimeOriginal`（含时区处理路径）与 GPS；无 native 依赖 | 自研 EXIF 解析：TIFF IFD 解析繁琐易错 |
| **B-10** | **HEIC 不支持（ADR-01）** | — | 见 §2.3 ADR-01 | — |
| B-11 | Lombok | 1.18.34 | 10 个实体 + DTO 的 getter/builder 样板消除；DTO 用 `record` 不依赖 Lombok | 手写 getter：约 600 行纯样板 |
| B-12 | spring-boot-starter-validation | 随 Boot | `@Valid` + `@NotBlank/@Size/@Past/@DecimalMin` 在后端复现前端规则，`GlobalExceptionHandler` 聚合 `MethodArgumentNotValidException` | 手写 if-else 校验：错误信息无法聚合 |
| B-13 | 密码哈希 | Spring Security `BCryptPasswordEncoder` cost=10 | PRD §10 明确 bcrypt cost ≥ 10；自带盐值，`VARCHAR(100)` 容纳 60 字符 | MD5/SHA256：可逆/可彩虹表，明令禁止；Argon2：需额外依赖，cost 调优收益为零 |
| B-14 | 数据库 | MySQL 8.4.7 | 环境已启动，库 `bee_family` 已存在且为空 | PostgreSQL：环境未安装，无收益 |
| B-15 | 连接池 | HikariCP 默认（maximumPoolSize=10） | 单机、请求多为短事务；10 足够，避免与 MySQL `max_connections` 冲突 | 调大：无收益且浪费 |

### 2.2 前端

| # | 选型 | 版本 | 理由 | 替代方案为何不选 |
| --- | --- | --- | --- | --- |
| F-1 | Vite | ^5.4 | 冷启动 <1s；`server.proxy` 解决开发期跨域；`import.meta.env` 提供 `VITE_API_BASE_URL` | Webpack：构建慢 5–10 倍；Nuxt：本项目无 SSR/SEO 诉求 |
| F-2 | Vue 3.5 | ^3.5 | `<script setup>` + `defineProps` 类型推导；`defineModel` 简化表单组件 | React：团队按 Vue 交付；Svelte：生态与 ECharts/TS 类型配套弱 |
| F-3 | TypeScript | ^5.6 | `types/models.ts` 与后端 DTO 一一对应，编译期拦截字段漂移 | JS：50+ 字段的契约无法靠人脑保证 |
| F-4 | vue-router | ^4.4 | 懒加载 + `meta.requiresAuth` + 全局守卫（401 回跳） | 手写 history 路由：需自实现滚动恢复、懒加载 |
| F-5 | Pinia | ^2.2 | 5 个 store 边界清晰；`setup` 语法与 `<script setup>` 一致 | Vuex 4：模板冗长，官方已转向 Pinia |
| F-6 | axios | ^1.7 | 拦截器统一注入 token、统一拆 `data`、统一 401 处理；`onUploadProgress` 支持单张进度 | fetch：需自实现拦截器/进度/超时/取消 |
| F-7 | ECharts | ^5.5（**按需引入**） | 仅 `GrowthChart.vue` 使用；只 `import { LineChart }` + `GridComponent/TooltipComponent/LegendComponent` + `CanvasRenderer`，产物增量 ≈ 190KB gzip | 全量 `import * from 'echarts'`：产物 +800KB，首屏 LCP 会破 2s 预算 |
| F-8 | **不引入 UI 组件库** | — | `02-ui-design.md` 的 tokens 与 §9.5 定义的 19 个基础组件是封闭设计系统；引入 Element Plus 会带来样式冲突与二次覆盖成本（约 30% 工作量反向） | Element Plus / Ant Design Vue / Naive UI：全部否决，理由同上 |
| F-9 | 样式方案 | 原生 CSS + CSS 变量 + `<style scoped>` | tokens 已是 CSS 变量，`02` 文档 §2.14 可直接整段落 `tokens.css`；零运行时 | Tailwind：与 `02` 文档给定的具体 px/hex 值映射成本高，且会产出与设计系统并行的第二套词汇；Sass：需要额外编译层，CSS 变量已足够 |
| F-10 | ESLint | ^9 flat config + `@typescript-eslint` + `eslint-plugin-vue` | PRD §10「前端 ESLint 零 error」 | TSLint：已废弃 |
| F-11 | 图标 | 内联 SVG 组件（`BeeIcon.vue` + 图标常量表） | `02` 文档要求线性图标并禁止 Emoji 当图标；内联 SVG 可用 `currentColor` 随主题变色 | iconfont 字体：首屏多一个请求且无法 `currentColor` 完美着色 |
| F-12 | 包管理器 | **npm** | 环境无 pnpm | pnpm：需额外安装 |

### 2.3 架构决策记录（ADR）

#### ADR-01 · HEIC 不支持，降级为前端拦截 + 明确指引

| 项 | 内容 |
| --- | --- |
| 背景 | PRD Q3 默认假设「必须支持 HEIC，服务端转码为 JPEG」。 |
| 备选方案 | ① 引入 `imageio-heif`（依赖 native libheif，需 `brew install libheif` + JNI 路径配置，Docker/换机即失效）；② 调用外部 `ImageMagick convert`（引入进程调用、需系统安装、安全面扩大）；③ 纯 Java 解码（无成熟实现）。 |
| 决策 | **不转码**。前端按扩展名 + MIME 前置拦截 `.heic/.heif`，给出可操作提示；服务端魔数校验同样拒绝，返回 `41501 UNSUPPORTED_MEDIA_TYPE`。 |
| 理由 | 三个方案都需要在目标机（macOS 单机自托管）上安装 native 依赖，一旦重装/换机即静默失效，故障模式是「上传成功但读出黑图」，比「明确拒绝」更糟；收益（少一次手动导出）低于运维风险。 |
| 后果 | ① 用户需在 iPhone「设置 → 相机 → 格式 → 兼容性最佳」后拍摄，或用「导出为 JPEG」；② 提示文案必须具体到操作路径（见 §5.4 上传响应示例）；③ 若未来要支持，实现点被隔离在 `PhotoService#ingestOne` 的 `ImageValidator` 之后，替换为转码器即可。 |
| 落地位置 | `frontend/src/utils/validate.ts#isUnsupportedImage`、`backend/.../support/ImageValidator.java`、`common/ErrorCode#UNSUPPORTED_MEDIA_TYPE` |

#### ADR-02 · JWT 不携带 familyId，服务端每请求解析

| 项 | 内容 |
| --- | --- |
| 决策 | Token claims 仅 `sub`（userId）、`email`、`iat`、`exp`。`familyId` / `role` 每次请求由 `family_member` 表按 `user_id`（唯一索引）查出，放入 `UserPrincipal`。 |
| 理由 | 若 `familyId` 进 token，成员被移除/家庭重建后 7 天内旧 token 仍可读旧家庭数据（PRD Q1「1 用户 = 1 家庭」使 `user_id` 唯一，查询成本 = 1 次唯一索引命中，<0.2ms）。 |
| 后果 | 多一次 DB 查询，已被 `uk_member_user` 覆盖；换来「权限变更即时生效」。 |

#### ADR-03 · Bearer + localStorage（接受内网自用 XSS 风险）

| 项 | 内容 |
| --- | --- |
| 决策 | `Authorization: Bearer <jwt>`，token 存 `localStorage['bee.token']`。 |
| 理由 | PRD Q4 已拍板。内网自用、无第三方脚本、无 UGC HTML 渲染（用户输入全部走 `{{ }}` 文本插值，不使用 `v-html`），XSS 面极小。 |
| **强制约束** | ① **全项目禁止 `v-html`**（唯一例外：搜索关键词高亮，必须经 `utils/format.ts#highlight` 先做 HTML 转义再包 `<mark>`）；② 上传禁止 SVG（SVG 可内嵌脚本）；③ 响应头 `Content-Security-Policy: default-src 'self'; img-src 'self' data: blob:`。 |
| 后果 | 若未来开放公网，必须迁移到 HttpOnly Cookie + CSRF token，改动点集中在 `JwtAuthenticationFilter` 与 `api/http.ts`。 |

#### ADR-04 · 图片访问双凭证（Header 或 `?token=`）

| 项 | 内容 |
| --- | --- |
| 背景 | `<img src>` 无法设置请求头。 |
| 决策 | `JwtAuthenticationFilter` 对 `/api/files/**` 额外接受 `?token=<jwt>` 查询参数；其余路径**只**认 Header。 |
| 理由 | 单一过滤器、单一校验逻辑，无需为静态资源造第二套签名机制（如短期 URL 签名），实现量与心智负担最小。 |
| 后果 | token 会出现在 URL 中（浏览器历史、Referer、日志）。缓解：① `Referrer-Policy: no-referrer`；② TraceIdFilter 与访问日志**对 `token` 参数值脱敏**；③ token 有效期 7 天、无刷新机制，泄露窗口有界。 |

#### ADR-05 · 时间轴用 SQL 层 UNION ALL 归并，不用内存合并

> 详见 §5.7「时间轴分页策略 —— 结论与论证」。

#### ADR-06 · BigDecimal 序列化为 JSON 数字，不字符串化

> 详见 §11.3。

---

## 3. 后端分层与包结构

### 3.1 完整包路径树

```
backend/
├── pom.xml
├── .gitignore                                     # 含 application-local.yml、data/、target/
└── src/main/
    ├── resources/
    │   ├── application.yml                        # 无口令，仅 ${DB_URL} 等占位
    │   ├── application-local.yml.example          # 提交，示例值
    │   ├── application-local.yml                  # 【gitignore】本地真实口令
    │   └── db/migration/
    │       └── V1__init.sql
    └── java/com/beefamily/album/
        ├── BeeFamilyApplication.java
        │
        ├── common/                                # 通用契约层：无业务语义
        │   ├── ApiResponse.java                   # record ApiResponse<T>(int code, String message, T data, String traceId)
        │   ├── PageResponse.java                  # record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages, boolean hasMore)
        │   ├── PageQuery.java                     # record PageQuery(int page, int size) + normalize()
        │   ├── ErrorCode.java                     # enum，见 §5.2
        │   ├── BusinessException.java             # RuntimeException + ErrorCode
        │   ├── GlobalExceptionHandler.java        # @RestControllerAdvice
        │   └── TraceIdFilter.java                 # OncePerRequestFilter，MDC + X-Trace-Id
        │
        ├── config/
        │   ├── SecurityConfig.java                # SecurityFilterChain / PasswordEncoder / CORS / 401·403 Handler
        │   ├── JacksonConfig.java                 # LocalDateTime / LocalDate 全局格式
        │   ├── WebMvcConfig.java                  # 静态资源（生产期）、上传体积上限
        │   ├── JwtProperties.java                 # @ConfigurationProperties("app.jwt")
        │   └── StorageProperties.java             # @ConfigurationProperties("app.storage")
        │
        ├── security/
        │   ├── JwtService.java                    # issue / parse / validate
        │   ├── JwtAuthenticationFilter.java       # Bearer 或 ?token=（仅 /api/files/**）
        │   ├── UserPrincipal.java                 # record(Long userId, String email, String nickname, Long familyId, Role role)
        │   ├── SecurityUtils.java                 # currentPrincipal() / currentUserId() / requirePrincipal()
        │   └── FamilyAccessGuard.java             # assertSameFamily(...) / assertOwner(...) / assertCreatorOrOwner(...)
        │
        ├── domain/                                # JPA 实体（不含任何 DTO / JSON 注解）
        │   ├── User.java
        │   ├── Family.java
        │   ├── FamilyMember.java
        │   ├── Baby.java
        │   ├── GrowthRecord.java
        │   ├── Milestone.java
        │   ├── Album.java
        │   ├── Photo.java
        │   ├── Tag.java
        │   ├── PhotoTag.java
        │   └── enums/{Role, Gender, MediaType}.java
        │
        ├── repository/
        │   ├── UserRepository.java
        │   ├── FamilyRepository.java
        │   ├── FamilyMemberRepository.java
        │   ├── BabyRepository.java
        │   ├── GrowthRecordRepository.java
        │   ├── MilestoneRepository.java
        │   ├── AlbumRepository.java
        │   ├── PhotoRepository.java
        │   ├── TagRepository.java
        │   ├── PhotoTagRepository.java
        │   └── TimelineQueryRepository.java       # 唯一原生 SQL 仓库：三源 UNION ALL
        │
        ├── service/
        │   ├── AuthService.java
        │   ├── FamilyService.java
        │   ├── BabyService.java
        │   ├── GrowthService.java
        │   ├── MilestoneService.java
        │   ├── AlbumService.java
        │   ├── PhotoService.java                  # 编排：校验 → 存储 → 缩略图 → 落库 → 补偿
        │   ├── PhotoIngestService.java            # 单张照片独立事务 @Transactional(REQUIRES_NEW)
        │   ├── TimelineService.java
        │   ├── SearchService.java
        │   ├── TagService.java
        │   ├── StatisticsService.java
        │   ├── StorageService.java                # 路径生成 / 写 / 删 / 读 / 路径穿越防护
        │   ├── ThumbnailService.java              # Thumbnailator 双档
        │   └── ExifService.java                   # metadata-extractor
        │
        ├── controller/
        │   ├── AuthController.java
        │   ├── FamilyController.java
        │   ├── BabyController.java
        │   ├── GrowthController.java
        │   ├── MilestoneController.java
        │   ├── AlbumController.java
        │   ├── PhotoController.java
        │   ├── TimelineController.java
        │   ├── SearchController.java
        │   ├── TagController.java
        │   ├── StatisticsController.java
        │   └── FileController.java
        │
        ├── dto/                                   # 全部 record，后缀 Req / Resp / Query
        │   ├── auth/     RegisterReq, LoginReq, TokenResp, MeResp, UpdateProfileReq, ChangePasswordReq
        │   ├── family/   FamilyResp, UpdateFamilyReq, MemberResp, InviteCodeResp, FamilyListResp(unused)
        │   ├── baby/     BabyReq, BabyUpdateReq, BabyResp
        │   ├── growth/   GrowthRecordReq, GrowthRecordResp, GrowthCurveResp, CurvePointResp, GrowthDeltaResp
        │   ├── milestone/MilestoneReq, MilestoneResp
        │   ├── album/    AlbumReq, AlbumUpdateReq, AlbumResp, AlbumListResp, DeleteAlbumReq(enum photoStrategy)
        │   ├── photo/    PhotoResp, PhotoDetailResp, PhotoUpdateReq, PhotoUploadResp, UploadFailure,
        │   │             BatchDeleteReq, BatchMoveReq, PhotoQuery
        │   ├── timeline/ TimelineItemResp, TimelineGroupResp, TimelinePageResp
        │   ├── search/   SearchResp, SearchGroupResp, TagResp
        │   └── stats/    OverviewResp
        │
        ├── mapper/                                # 实体 ↔ DTO，静态方法，无 Spring 依赖
        │   ├── UserMapper.java
        │   ├── FamilyMapper.java
        │   ├── BabyMapper.java
        │   ├── GrowthMapper.java
        │   ├── MilestoneMapper.java
        │   ├── AlbumMapper.java
        │   ├── PhotoMapper.java
        │   └── StatisticsMapper.java
        │
        └── support/
            ├── ImageValidator.java                # 魔数嗅探 + 扩展名白名单 + SVG/HEIC 拒绝
            ├── ImageType.java                     # enum JPEG / PNG / WEBP / HEIC / SVG / UNKNOWN
            ├── DateTimes.java                     # 时区常量、yyyy-MM 分组、AgeText 计算
            └── Paging.java                        # 页码归一化、size 上限 100
```

### 3.2 各层职责与依赖方向

| 层 | 职责（做） | 禁止（不做） | 允许依赖 |
| --- | --- | --- | --- |
| `controller` | 路径/参数绑定、`@Valid` 触发、调 service、包 `ApiResponse` | 禁止写业务判断、禁止直接操作 repository、禁止 `@Transactional` | `service`、`dto`、`common`、`security`（只读 principal） |
| `service` | 业务规则、权限校验、事务边界、编排 storage/thumbnail/exif | 禁止返回实体给 controller、禁止依赖 `HttpServletRequest` | `repository`、`domain`、`mapper`、`support`、`security` |
| `repository` | 数据访问；复杂查询用 `@Query` | 禁止写业务规则、禁止返回 DTO | `domain` |
| `domain` | 表映射、列约束、`@Version`?（不需要） | 禁止 JSON 注解、禁止业务方法 | 无 |
| `mapper` | 纯静态 `toResp(entity, ctx)` | 禁止注入 Spring Bean、禁止查库 | `domain`、`dto` |
| `dto` | `record` 承载入参/出参 | 禁止逻辑、禁止实体引用 | `common`（仅枚举） |

> **依赖方向硬约束**：`controller → service → repository → domain`，**单向**。任何反向 import（如 repository 引 service）在 Code Review 中直接打回。
> 跨 service 调用允许（`AlbumService → PhotoService` 不推荐，改为 `PhotoService` 内提供被 `AlbumService` 调用的方法，见 §6.4）。

### 3.3 统一响应体与错误码

```java
// common/ApiResponse.java
public record ApiResponse<T>(int code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> ok(T data)            // code = 0, message = "OK"
    public static ApiResponse<Void> ok()                  // data = null
    public static <T> ApiResponse<T> fail(ErrorCode e, String traceId)
    public static <T> ApiResponse<T> fail(ErrorCode e, String message, String traceId)
}
```

```java
// common/PageResponse.java
public record PageResponse<T>(List<T> items, int page, int size,
                              long total, int totalPages, boolean hasMore) {
    public static <T> PageResponse<T> of(List<T> items, PageQuery q, long total)
}
```

**成功响应 `code = 0`**；`traceId` 由 `TraceIdFilter` 写入 MDC 与响应头 `X-Trace-Id`，格式为 16 位 hex。

### 3.4 全局异常处理矩阵

`GlobalExceptionHandler`（`@RestControllerAdvice`，`ResponseEntity<ApiResponse<Void>>`）：

| 异常 | HTTP | code | message 处理 |
| --- | --- | --- | --- |
| `BusinessException` | 由 `ErrorCode.httpStatus` 决定 | `ErrorCode.code` | 用异常自带 message，无则用 `ErrorCode.defaultMessage` |
| `MethodArgumentNotValidException` | 400 | 40001 | **聚合所有字段错误**：`"nickname: 不能为空; email: 邮箱格式不正确"`，字段数 ≤10 条，超出追加 `…等 N 项` |
| `ConstraintViolationException`（`@RequestParam` 校验） | 400 | 40001 | 同上聚合 |
| `MethodArgumentTypeMismatchException` | 400 | 40002 | `"参数 range 取值非法，允许 3m/6m/12m/all"` |
| `HttpMessageNotReadableException` | 400 | 40002 | `"请求体格式错误"` |
| `MaxUploadSizeExceededException` | 413 | 41301 | `"单张照片不能超过 20MB"` |
| `AuthenticationException` / 未认证进入 | 401 | 40100 | 由 `AuthenticationEntryPoint` 输出，非异常处理器 |
| `AccessDeniedException` | 403 | 40300 | 由 `AccessDeniedHandler` 输出 |
| `NoResourceFoundException`（静态/路径未匹配） | 404 | 40401 | `"资源不存在"` |
| `Exception`（兜底） | 500 | 50001 | `"服务异常，请稍后重试"`；**日志打完整堆栈 + traceId，响应体不带堆栈** |

> **统一原则**：`data` 在错误响应中恒为 `null`；`message` 不含任何内部类名、SQL、文件绝对路径。

### 3.5 事务边界规则

| 场景 | 注解 | 说明 |
| --- | --- | --- |
| 所有查询 service 方法 | `@Transactional(readOnly = true)` | 关闭 Hibernate dirty checking，减少一级缓存开销 |
| 单个写操作（如重命名家庭） | `@Transactional` | 默认 `REQUIRED` |
| 批量上传（100 张） | `PhotoService.upload` **不加**事务；逐张调 `PhotoIngestService.ingestOne`（另一个 Bean）→ `@Transactional(propagation = REQUIRES_NEW)` | 一张失败不回滚其余 99 张（PRD P0-07「失败重试」语义） |
| 删相册 | `AlbumService.delete` 单一 `@Transactional`；磁盘文件删除在**事务提交后**（`TransactionSynchronization.afterCommit`） | 避免回滚后文件已不可恢复 |
| 删家庭 | `FamilyService.deleteFamily` 单一 `@Transactional`，**显式按依赖顺序删除**（见 §6.4 注） | 不依赖 `ON DELETE CASCADE` 的多路径行为 |

> ⚠️ **自调用陷阱**：`PhotoService` 内 `this.ingestOne()` 不会走代理，`@Transactional` 失效。必须在 `PhotoService` 注入 `PhotoIngestService`（独立 Bean）。此条写进任务验收。

---

## 4. 数据模型

### 4.1 表清单（10 表）

| # | 表 | 说明 | 关键唯一约束 |
| --- | --- | --- | --- |
| 1 | `user` | 用户 | `uk_user_email(email)` |
| 2 | `family` | 家庭空间 | `uk_family_invite_code(invite_code)` |
| 3 | `family_member` | 成员关系 | **`uk_member_family_user(family_id,user_id)`** + **`uk_member_user(user_id)`**（DB 级落地「1 用户 = 1 家庭」） |
| 4 | `baby` | 宝宝档案 | 无（PRD 未要求昵称唯一，兄弟可重名；数量上限 3 由 service 校验） |
| 5 | `growth_record` | 成长测量 | **`uk_growth_baby_date(baby_id,recorded_at)`** |
| 6 | `milestone` | 里程碑 | 无 |
| 7 | `album` | 相册 | **`uk_album_family_name(family_id,name)`** |
| 8 | `photo` | 照片 | 无（内容去重留 P2） |
| 9 | `tag` | 标签 | **`uk_tag_family_name(family_id,name)`** |
| 10 | `photo_tag` | 照片-标签 | PK `(photo_id, tag_id)` |

### 4.2 统一审计列约定（相对 PRD 的增补，已评审通过）

| 增补列 | 表 | 理由 |
| --- | --- | --- |
| `updated_at` | `family`、`baby`、`growth_record`、`milestone`、`photo`、`tag` | ① 相册列表按 `updated_at desc`（PRD P0-06 ④）需同族语义；② 排查「谁在什么时候改过」；③ 全部由 MySQL `ON UPDATE CURRENT_TIMESTAMP` 维护，应用层零代码 |
| `created_at` | `growth_record`、`milestone`、`tag`、`photo_tag` | 审计与「最近添加标签」建议（PRD P0-11 初始态需「最近标签」） |

### 4.3 循环外键问题与方案

**问题**：`family.cover_photo_id → photo.id` 与 `photo.family_id → family.id` 互为父表；`album.cover_photo_id → photo.id` 与 `photo.album_id → album.id` 同样成环。MySQL 允许建环，但：

1. 若 cover 列建了 `RESTRICT` 外键（默认行为），删除被引用的照片会报 **1451 `Cannot delete or update a parent row`**（已在 MySQL 8.4 实测复现）。改用 `ON DELETE SET NULL` 可绕过报错，但那样就又变成「删照片顺带清封面」的隐式行为，且 `album.cover_photo_id` 被置 NULL 后需要额外逻辑决定「回退到最新照片」——不如把规则全部放在 service 里显式表达。
2. 带环的表结构，`mysqldump` 恢复时需 `SET FOREIGN_KEY_CHECKS=0`（这是备选方案 A/C 的额外代价；本方案不建 cover 外键，**最终表结构无环**，§8.5 的恢复流程无需关闭外键检查）。
3. `ddl-auto: validate` 对带环结构的实体映射校验更易产生误导性报错。

**方案对比**

| 方案 | 做法 | 评价 |
| --- | --- | --- |
| A | 两阶段迁移：先建全部表（cover 无 FK），`V2__cover_fk.sql` 再 `ALTER ADD CONSTRAINT` | 可以建环，但删除照片时仍需应用层先清 cover，问题只是推迟 |
| B | **cover 列不建 FK 约束**，应用层维护一致性 | ✅ 采用 |
| C | cover 存 `storage_key` 而非 photo_id | 违背 PRD 数据字典，且改名/换封面时无法复用 |

**决策：方案 B**

1. `family.cover_photo_id`、`album.cover_photo_id` 定义为 `BIGINT NULL`，**不加 FOREIGN KEY**，加普通索引便于反查。
2. 一致性由 service 保证：
   - 删照片前：`UPDATE family SET cover_photo_id = NULL WHERE cover_photo_id = ?`；`UPDATE album SET cover_photo_id = NULL WHERE cover_photo_id = ?`（`album.cover_photo_id` 为 NULL 时前端按 PRD P0-06③「取相册内最新照片」兜底）。
   - 设封面时校验该照片 `family_id` 与目标 album 同属一个家庭。
   - 孤儿 cover（指向已删照片）**不会导致报错**，前端 `BeeImage` 加载失败降级为空状态。
3. `photo.album_id` **保留 FK 且 `ON DELETE SET NULL`** —— 这是「照片移到未分类」的数据库级兜底，即使 service 漏写也不会产生悬空 `album_id`。

> **实测证据（MySQL 8.4.7）**
> - 删除被引用的照片后：`milestone.photo_id` 自动变为 `NULL`（FK `SET NULL` 生效），而 `album.cover_photo_id` **仍指向已删除的照片（悬挂）** —— 这正是方案 B 必须由 service 显式清理的依据，不能指望数据库。
> - 删除相册后：`photo.album_id` 自动变为 `NULL`（FK `SET NULL` 生效），即"照片移到未分类"的兜底确实有效。
> - 本 DDL 建出的表结构**无环**，`mysqldump` 导出的脚本可在**不关闭** `FOREIGN_KEY_CHECKS` 的情况下直接恢复（已实测）。

### 4.4 Flyway `V1__init.sql`（可直接执行）

```sql
-- =============================================================================
-- 蜜记 · Bee Family —— 初始表结构
-- DB: MySQL 8.4.7   charset: utf8mb4   collation: utf8mb4_0900_ai_ci   engine: InnoDB
-- 由 Flyway 执行，禁止手工修改本文件；后续变更请新增 V2__xxx.sql
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. user
-- -----------------------------------------------------------------------------
CREATE TABLE `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `email`         VARCHAR(120) NOT NULL                COMMENT '登录邮箱，全小写存储',
  `password_hash` VARCHAR(100) NOT NULL                COMMENT 'bcrypt cost=10，60 字符',
  `nickname`      VARCHAR(32)  NOT NULL                COMMENT '显示名',
  `avatar_url`    VARCHAR(255)     NULL                COMMENT '头像相对 key 或外链',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户';

-- -----------------------------------------------------------------------------
-- 2. family
--    cover_photo_id 刻意不加外键（见架构文档 §4.3，方案 B），避免与 photo.family_id 成环
-- -----------------------------------------------------------------------------
CREATE TABLE `family` (
  `id`                     BIGINT      NOT NULL AUTO_INCREMENT,
  `name`                   VARCHAR(40) NOT NULL COMMENT '家庭名，1-40 字',
  `owner_id`               BIGINT      NOT NULL COMMENT '创建者 user.id',
  `invite_code`            CHAR(8)         NULL COMMENT '8 位邀请码，唯一；NULL=未生成',
  `invite_code_expires_at` DATETIME        NULL COMMENT '过期时间，now()+24h',
  `cover_photo_id`         BIGINT          NULL COMMENT '家庭封面（无 FK，应用层维护）',
  `created_at`             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_invite_code` (`invite_code`),
  KEY `idx_family_owner` (`owner_id`),
  KEY `idx_family_cover_photo` (`cover_photo_id`),
  CONSTRAINT `fk_family_owner` FOREIGN KEY (`owner_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='家庭空间';

-- -----------------------------------------------------------------------------
-- 3. family_member
--    uk_member_user 在 DB 层强制「1 用户 = 1 家庭」，同时是每请求解析 familyId 的索引
-- -----------------------------------------------------------------------------
CREATE TABLE `family_member` (
  `id`        BIGINT      NOT NULL AUTO_INCREMENT,
  `family_id` BIGINT      NOT NULL,
  `user_id`   BIGINT      NOT NULL,
  `role`      VARCHAR(10) NOT NULL COMMENT 'owner / member',
  `joined_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_family_user` (`family_id`, `user_id`),
  UNIQUE KEY `uk_member_user` (`user_id`),
  KEY `idx_member_family_role` (`family_id`, `role`),
  CONSTRAINT `fk_member_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_member_user`   FOREIGN KEY (`user_id`)   REFERENCES `user` (`id`)   ON DELETE CASCADE,
  CONSTRAINT `ck_member_role` CHECK (`role` IN ('owner', 'member'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='家庭成员';

-- -----------------------------------------------------------------------------
-- 4. baby
-- -----------------------------------------------------------------------------
CREATE TABLE `baby` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `family_id`       BIGINT       NOT NULL,
  `name`            VARCHAR(32)  NOT NULL COMMENT '昵称',
  `gender`          VARCHAR(8)       NULL COMMENT 'male / female / unknown',
  `birthday`        DATE         NOT NULL COMMENT '不得晚于今天（应用层校验）',
  `avatar_url`      VARCHAR(255)     NULL,
  `birth_height_cm` DECIMAL(5,1)     NULL COMMENT '出生身高 cm',
  `birth_weight_kg` DECIMAL(4,2)     NULL COMMENT '出生体重 kg',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_baby_family` (`family_id`, `id`),
  CONSTRAINT `fk_baby_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_baby_gender` CHECK (`gender` IS NULL OR `gender` IN ('male', 'female', 'unknown')),
  CONSTRAINT `ck_baby_birth_height` CHECK (`birth_height_cm` IS NULL OR `birth_height_cm` BETWEEN 20.0 AND 80.0),
  CONSTRAINT `ck_baby_birth_weight` CHECK (`birth_weight_kg` IS NULL OR `birth_weight_kg` BETWEEN 0.50 AND 10.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宝宝档案';

-- -----------------------------------------------------------------------------
-- 5. growth_record
--    时间轴按 family 过滤通过 JOIN baby 完成（单家庭 ≤3 个宝宝，JOIN 成本可忽略），
--    故不冗余 family_id（与 milestone 不同：milestone.baby_id 可为 NULL，必须冗余）
-- -----------------------------------------------------------------------------
CREATE TABLE `growth_record` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `baby_id`     BIGINT       NOT NULL,
  `recorded_at` DATE         NOT NULL COMMENT '测量日期',
  `height_cm`   DECIMAL(5,1)     NULL COMMENT '身高 30.0-150.0',
  `weight_kg`   DECIMAL(4,2)     NULL COMMENT '体重 1.00-60.00',
  `head_cm`     DECIMAL(4,1)     NULL COMMENT '头围 20.0-70.0',
  `note`        VARCHAR(200)     NULL,
  `created_by`  BIGINT       NOT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_growth_baby_date` (`baby_id`, `recorded_at`),
  KEY `idx_growth_baby_date_id` (`baby_id`, `recorded_at`, `id`),
  KEY `idx_growth_created_by` (`created_by`),
  CONSTRAINT `fk_growth_baby`       FOREIGN KEY (`baby_id`)    REFERENCES `baby` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_growth_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_growth_height` CHECK (`height_cm` IS NULL OR `height_cm` BETWEEN 30.0 AND 150.0),
  CONSTRAINT `ck_growth_weight` CHECK (`weight_kg` IS NULL OR `weight_kg` BETWEEN 1.00 AND 60.00),
  CONSTRAINT `ck_growth_head`   CHECK (`head_cm`   IS NULL OR `head_cm`   BETWEEN 20.0 AND 70.0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成长测量记录';

-- -----------------------------------------------------------------------------
-- 6. album
--    cover_photo_id 无 FK（见 §4.3）；photo_count 为冗余计数，由 service 在同事务内维护
-- -----------------------------------------------------------------------------
CREATE TABLE `album` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `family_id`      BIGINT       NOT NULL,
  `name`           VARCHAR(40)  NOT NULL COMMENT '1-40 字，家庭内唯一',
  `description`    VARCHAR(300)     NULL,
  `cover_photo_id` BIGINT           NULL COMMENT 'NULL = 取相册内最新照片',
  `photo_count`    INT          NOT NULL DEFAULT 0 COMMENT '冗余计数',
  `created_by`     BIGINT       NOT NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_album_family_name` (`family_id`, `name`),
  KEY `idx_album_family_updated` (`family_id`, `updated_at`),
  KEY `idx_album_cover_photo` (`cover_photo_id`),
  CONSTRAINT `fk_album_family`     FOREIGN KEY (`family_id`)  REFERENCES `family` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_album_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)   ON DELETE RESTRICT,
  CONSTRAINT `ck_album_photo_count` CHECK (`photo_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='相册';

-- -----------------------------------------------------------------------------
-- 7. photo
--    album_id 的 ON DELETE SET NULL 是「照片移到未分类」的数据库级兜底
-- -----------------------------------------------------------------------------
CREATE TABLE `photo` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `family_id`     BIGINT       NOT NULL COMMENT '权限过滤第一条件',
  `album_id`      BIGINT           NULL COMMENT 'NULL = 未分类',
  `title`         VARCHAR(80)      NULL,
  `description`   VARCHAR(500)     NULL,
  `storage_key`   VARCHAR(255) NOT NULL COMMENT '原图相对路径，如 1/2026/11/{uuid}.jpg',
  `thumb_key`     VARCHAR(255)     NULL COMMENT '480px 缩略图',
  `preview_key`   VARCHAR(255)     NULL COMMENT '1600px 预览图',
  `mime_type`     VARCHAR(40)  NOT NULL COMMENT 'image/jpeg | image/png | image/webp',
  `file_size`     BIGINT       NOT NULL COMMENT '字节，<= 20971520',
  `width`         INT              NULL,
  `height`        INT              NULL,
  `taken_at`      DATETIME     NOT NULL COMMENT 'EXIF 优先，缺失取上传时间',
  `location_name` VARCHAR(100)     NULL,
  `latitude`      DECIMAL(9,6)     NULL COMMENT 'P1 地图预留',
  `longitude`     DECIMAL(9,6)     NULL COMMENT 'P1 地图预留',
  `content_hash`  CHAR(64)         NULL COMMENT 'P2 去重预留',
  `media_type`    VARCHAR(10)  NOT NULL DEFAULT 'photo' COMMENT 'P2 视频预留',
  `uploaded_by`   BIGINT       NOT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_photo_family_taken`   (`family_id`, `taken_at`, `id`),
  KEY `idx_photo_family_created` (`family_id`, `created_at`, `id`),
  KEY `idx_photo_family_album`   (`family_id`, `album_id`, `taken_at`, `id`),
  KEY `idx_photo_album_taken`    (`album_id`, `taken_at`, `id`),
  KEY `idx_photo_uploaded_by`    (`uploaded_by`),
  CONSTRAINT `fk_photo_family`    FOREIGN KEY (`family_id`)   REFERENCES `family` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_photo_album`     FOREIGN KEY (`album_id`)    REFERENCES `album` (`id`)  ON DELETE SET NULL,
  CONSTRAINT `fk_photo_uploaded`  FOREIGN KEY (`uploaded_by`) REFERENCES `user` (`id`)   ON DELETE RESTRICT,
  CONSTRAINT `ck_photo_media_type` CHECK (`media_type` IN ('photo', 'video')),
  CONSTRAINT `ck_photo_file_size`  CHECK (`file_size` > 0 AND `file_size` <= 20971520)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='照片';

-- -----------------------------------------------------------------------------
-- 8. milestone
--    family_id 冗余：baby_id 可空（家庭级里程碑），时间轴与搜索必须能只按 family 过滤
-- -----------------------------------------------------------------------------
CREATE TABLE `milestone` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `baby_id`     BIGINT           NULL COMMENT 'NULL = 家庭级里程碑',
  `family_id`   BIGINT       NOT NULL COMMENT '冗余，权限过滤与时间轴必需',
  `title`       VARCHAR(60)  NOT NULL,
  `description` VARCHAR(500)     NULL,
  `achieved_at` DATE         NOT NULL,
  `photo_id`    BIGINT           NULL COMMENT '关联配图',
  `created_by`  BIGINT       NOT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_milestone_family_achieved` (`family_id`, `achieved_at`, `id`),
  KEY `idx_milestone_baby_achieved`   (`baby_id`, `achieved_at`),
  KEY `idx_milestone_photo`           (`photo_id`),
  CONSTRAINT `fk_milestone_family`     FOREIGN KEY (`family_id`)  REFERENCES `family` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_milestone_baby`       FOREIGN KEY (`baby_id`)    REFERENCES `baby` (`id`)   ON DELETE SET NULL,
  CONSTRAINT `fk_milestone_photo`      FOREIGN KEY (`photo_id`)   REFERENCES `photo` (`id`)  ON DELETE SET NULL,
  CONSTRAINT `fk_milestone_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)   ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='里程碑';

-- -----------------------------------------------------------------------------
-- 9. tag
-- -----------------------------------------------------------------------------
CREATE TABLE `tag` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `family_id`  BIGINT      NOT NULL,
  `name`       VARCHAR(20) NOT NULL COMMENT '家庭内唯一，复用',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_family_name` (`family_id`, `name`),
  CONSTRAINT `fk_tag_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签';

-- -----------------------------------------------------------------------------
-- 10. photo_tag
-- -----------------------------------------------------------------------------
CREATE TABLE `photo_tag` (
  `photo_id`   BIGINT   NOT NULL,
  `tag_id`     BIGINT   NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`photo_id`, `tag_id`),
  KEY `idx_photo_tag_tag` (`tag_id`, `photo_id`),
  CONSTRAINT `fk_pt_photo` FOREIGN KEY (`photo_id`) REFERENCES `photo` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pt_tag`   FOREIGN KEY (`tag_id`)   REFERENCES `tag` (`id`)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='照片-标签关联';
```

> **建表顺序**：`user → family → family_member → baby → growth_record → album → photo → milestone → tag → photo_tag`。
> 注意 `milestone` 必须在 `photo` 之后建（`fk_milestone_photo`），`album` 必须先于 `photo`。

### 4.5 索引论证

| # | 查询场景 | SQL 关键片段 | 命中索引 | 扫描行数（单家庭 50k 照片 / 5k 相册 / 1k 里程碑） | P95 估算 |
| --- | --- | --- | --- | --- | --- |
| Q1 | 时间轴首页第 1 页 | `photo WHERE family_id=? ORDER BY taken_at DESC, id DESC LIMIT 25` | `idx_photo_family_taken` | 25（反向索引扫描，无 filesort） | < 5ms |
| Q2 | 时间轴第 200 页 | 同上 `OFFSET 4800` | `idx_photo_family_taken` | 4825 | 15–25ms |
| Q3 | 相册详情网格 | `photo WHERE album_id=? ORDER BY taken_at DESC, id DESC LIMIT 61` | `idx_photo_album_taken` | 61 | < 5ms |
| Q4 | 未分类列表 | `photo WHERE family_id=? AND album_id IS NULL ORDER BY taken_at DESC LIMIT 61` | `idx_photo_family_album`（前者等值 + `album_id IS NULL` 范围） | 61 | < 5ms |
| Q5 | 本月新增统计 | `COUNT(*) FROM photo WHERE family_id=? AND created_at >= ?` | `idx_photo_family_created` | 本月行数（≈500） | < 3ms |
| Q6 | 相册列表 | `album WHERE family_id=? ORDER BY updated_at DESC` | `idx_album_family_updated` | ≤200（相册数量天然很少） | < 3ms |
| Q7 | 成长曲线 | `growth_record WHERE baby_id=? AND recorded_at BETWEEN ? AND ? ORDER BY recorded_at` | `uk_growth_baby_date` | ≤200 | < 3ms |
| Q8 | 里程碑列表 / 时间轴 | `milestone WHERE family_id=? ORDER BY achieved_at DESC, id DESC` | `idx_milestone_family_achieved` | ≤1000 | < 5ms |
| Q9 | **每请求解析家庭** | `family_member WHERE user_id=?` | `uk_member_user` | 1 | < 0.2ms |
| Q10 | 标签输入即搜索 | `tag WHERE family_id=? AND name LIKE '海%' ORDER BY name LIMIT 20` | `uk_tag_family_name`（**前缀匹配可用索引**） | ≤50 | < 2ms |
| Q11 | 全局搜索（照片） | `photo WHERE family_id=? AND (title LIKE '%海%' OR description LIKE '%海%')` | `idx_photo_family_taken` 仅用于 family 过滤，`LIKE '%x%'` 必然回表全扫 | ≤50000 | **20–60ms（已知瓶颈，见 §12 R-2）** |
| Q12 | 时间轴三源 UNION 归并 | 见 §5.7 | 三个分支各走 Q1/Q8/Q7 索引 | 三个分支各 ≤（offset+size） | 20–60ms |

**结论与取舍**

1. `idx_photo_family_taken` 是**最高频索引**：三个 P0（时间轴、相册、搜索）都以「`family_id` 等值 + `taken_at` 排序」为骨架。ASC 索引可被 MySQL 8 反向扫描以满足 `ORDER BY taken_at DESC, id DESC`（等值前缀后的 `taken_at`+`id` 双降序正好是索引逆序），**不需要 DESC 索引**。
2. `(family_id, album_id, taken_at, id)` 同时服务「某相册（带 family 双校验）」与「未分类（`album_id IS NULL`）」，避免再建一张 `family_id, album_id` 的窄索引。
3. `uk_member_user (user_id)` 是**跨切面性能关键**：它在每个认证请求上执行一次，同时用唯一性在 DB 层封死「一个用户加入两个家庭」这种脏数据（PRD Q1）。
4. `growth_record` **不加冗余 `family_id`**：单家庭宝宝 ≤3，`JOIN baby` 仅在 ≤3 行上探测；相比冗余列 + 触发器/双写，JOIN 更简单且无一致性风险。
5. `photo` 上 5 个二级索引在 50k 行量级下写放大可接受（上传是低频操作，读是高频）。

---

## 5. API 契约

### 5.1 通用约定

| 项 | 约定 |
| --- | --- |
| Base URL | `VITE_API_BASE_URL`，开发期 `/api`（Vite proxy），生产期同源 `/api` |
| 认证 | `Authorization: Bearer <jwt>`；`GET /api/files/**` 额外允许 `?token=<jwt>` |
| 请求体 | `application/json; charset=utf-8`（上传为 `multipart/form-data`） |
| 响应体 | 恒为 `ApiResponse<T>`，`code = 0` 表示成功 |
| 分页参数 | `page`（**0 基**）、`size`（默认 24，上限 100）；响应 `PageResponse<T>` |
| 时间格式 | `LocalDateTime` → `yyyy-MM-dd HH:mm:ss`；`LocalDate` → `yyyy-MM-dd`；时区 `Asia/Shanghai` |
| 空值 | 显式输出 `null`（**不做 `NON_NULL` 裁剪**）；空集合输出 `[]` |
| 幂等 | `POST /api/babies/{id}/growth-records` 为 UPSERT（同 `recordedAt` 覆盖），其余 POST 非幂等 |

### 5.2 错误码枚举 `ErrorCode`

> **编号规则**：`code = HTTP状态码 × 100 + 两位序号`。前端可直接 `Math.floor(code / 100)` 得到 HTTP 语义分组。

| code | HTTP | 枚举常量 | 默认 message | 前端处理 |
| --- | --- | --- | --- | --- |
| 0 | 200 | `OK` | OK | — |
| 40001 | 400 | `INVALID_PARAM` | 参数校验失败 | 字段级错误回填表单 |
| 40002 | 400 | `INVALID_ARGUMENT` | 参数取值非法 | Toast danger |
| 40100 | 401 | `UNAUTHORIZED` | 未登录或登录已失效 | 清 token → `/login?redirect=` |
| 40101 | 401 | `INVALID_CREDENTIALS` | 邮箱或密码错误 | 表单内联错误（不暴露账号是否存在） |
| 40102 | 401 | `INVALID_OLD_PASSWORD` | 原密码不正确 | 表单内联错误 |
| 40103 | 401 | `TOKEN_EXPIRED` | 登录已过期，请重新登录 | 同 40100 |
| 40300 | 403 | `FORBIDDEN` | 无权执行该操作 | Toast danger |
| 40301 | 403 | `CROSS_FAMILY_ACCESS` | 无权访问该资源 | Toast danger + 返回上一页 |
| 40302 | 403 | `OWNER_ONLY` | 仅家庭创建者可执行此操作 | 按钮置灰（理论前端已隐藏） |
| 40303 | 403 | `NOT_RESOURCE_CREATOR` | 仅创建者可执行此操作 | 同上 |
| 40401 | 404 | `RESOURCE_NOT_FOUND` | 资源不存在 | EmptyState 变体「加载失败」 |
| 40901 | 409 | `EMAIL_ALREADY_EXISTS` | 该邮箱已注册 | 表单内联错误 |
| 40902 | 409 | `INVITE_CODE_INVALID` | 邀请码无效或已过期 | 表单内联错误 |
| 40903 | 409 | `FAMILY_MEMBER_LIMIT` | 家庭成员已达 6 人上限 | 邀请区置灰 + warning 提示 |
| 40904 | 409 | `BABY_LIMIT` | 宝宝档案已达 3 个上限 | 新增按钮置灰 |
| 40905 | 409 | `ALBUM_NAME_DUPLICATED` | 同名相册已存在 | 表单内联错误 |
| 40906 | 409 | `MEMBER_NOT_REMOVABLE` | 不能移除家庭创建者或自己 | Toast warning |
| 40907 | 409 | `CROSS_ALBUM_MOVE` | 目标相册不存在或无权访问 | Toast danger |
| 40908 | 409 | `CONFIRM_MISMATCH` | 输入的家庭名不匹配，无法删除 | 表单内联错误（危险操作确认） |
| 41301 | 413 | `FILE_TOO_LARGE` | 单张照片不能超过 20MB | 上传前拦截 |
| 41302 | 413 | `BATCH_TOO_LARGE` | 单批最多 100 张 | 上传前拦截 |
| 41501 | 415 | `UNSUPPORTED_MEDIA_TYPE` | 不支持的图片格式（支持 jpg/jpeg/png/webp） | Toast warning + HEIC 指引 |
| 50001 | 500 | `INTERNAL_ERROR` | 服务异常，请稍后重试 | Toast danger + 展示 traceId |
| 50002 | 500 | `STORAGE_WRITE_FAILED` | 文件保存失败，请重试 | Toast danger |

### 5.3 接口总表（44 个）

> **权限列图例**：`公开` = 无需认证；`成员` = 需登录且属某家庭（`familyId` 必存在）；`owner` = 家庭创建者；`创建者|owner` = 资源 `createdBy` 本人或 owner。

#### 5.3.1 认证与账号（6）

| # | 方法 | 路径 | 权限 | 请求体 / 参数 | 响应体 | 主要错误码 | P0 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | POST | `/api/auth/register` | 公开 | `RegisterReq{email, password, nickname, inviteCode?}` | `TokenResp` | 40001 40901 40902 40903 | P0-01 P0-02 |
| 2 | POST | `/api/auth/login` | 公开 | `LoginReq{email, password}` | `TokenResp` | 40001 40101 | P0-01 |
| 3 | POST | `/api/auth/logout` | 成员 | — | `Void` | 40100 | P0-01 |
| 4 | GET | `/api/auth/me` | 成员 | — | `MeResp` | 40100 | P0-01 |
| 5 | PATCH | `/api/auth/me` | 成员 | `UpdateProfileReq{nickname?, avatarUrl?}` | `MeResp` | 40001 | P0-01 |
| 6 | PUT | `/api/auth/password` | 成员 | `ChangePasswordReq{oldPassword, newPassword}` | `Void` | 40001 40102 | P0-01 |

`TokenResp{ token, expiresAt, user:UserResp, familyId, familyName, role, babies:BabyResp[] }`
`MeResp{ user:UserResp, family:FamilyResp, role, babies:BabyResp[] }`
`UserResp{ id, email, nickname, avatarUrl, createdAt }`

> **register 事务**：`user` 表 `COUNT(*) = 0` → 首个用户：创建 user + family（`name` 默认 `"{nickname}的家"`）+ family_member(role=owner) + 生成邀请码（8 位、24h）。
> 否则：必须带 `inviteCode`，校验存在 + 未过期 + 家庭未满 6 人 → 创建 user + family_member(role=member)。**同事务内完成，`INSERT user` 与 `UPDATE family SET name` 无跨表竞争**（单机自用，并发可忽略）。
> **登出**：JWT 无状态，服务端不维护黑名单，接口仅返回成功；前端负责清 `localStorage` 与全部 Pinia store。文档明示该取舍（见 §12 R-3）。

#### 5.3.2 家庭与成员（7）

| # | 方法 | 路径 | 权限 | 请求体 / 参数 | 响应体 | 主要错误码 | P0 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 7 | GET | `/api/family` | 成员 | — | `FamilyResp` | 40100 40301 | P0-02 |
| 8 | PATCH | `/api/family` | **owner** | `UpdateFamilyReq{name}` | `FamilyResp` | 40001 40302 | P0-02 |
| 9 | DELETE | `/api/family` | **owner** | `DeleteFamilyReq{confirmName}` | `Void` | 40302 40908 | P0-02 |
| 10 | GET | `/api/family/members` | 成员 | — | `List<MemberResp>` | 40100 40301 | P0-02 |
| 11 | DELETE | `/api/family/members/{userId}` | **owner** | path `userId` | `Void` | 40302 40906 40401 | P0-02（US-15） |
| 12 | GET | `/api/family/invite-code` | **owner** | — | `InviteCodeResp` | 40302 | P0-02 |
| 13 | POST | `/api/family/invite-code/reset` | **owner** | — | `InviteCodeResp` | 40302 | P0-02 |

`FamilyResp{ id, name, ownerId, ownerNickname, memberCount, memberLimit(6), coverPhotoId, inviteCode?, inviteCodeExpiresAt?, createdAt }`
`MemberResp{ userId, nickname, avatarUrl, email, role, joinedAt, uploadedPhotoCount }`
`InviteCodeResp{ code, expiresAt, expired }`
`DELETE /api/family` 额外错误码 `40908 CONFIRM_MISMATCH`（HTTP 409，已在 §5.2 声明）。

#### 5.3.3 宝宝档案（5）

| # | 方法 | 路径 | 权限 | 请求体 / 参数 | 响应体 | 主要错误码 | P0 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 14 | GET | `/api/babies` | 成员 | — | `List<BabyResp>` | 40100 | P0-03 |
| 15 | POST | `/api/babies` | 成员 | `BabyReq{name, gender?, birthday, avatarUrl?, birthHeightCm?, birthWeightKg?}` | `BabyResp` | 40001 40904 | P0-03 |
| 16 | GET | `/api/babies/{id}` | 成员 | path `id` | `BabyDetailResp` | 40301 40401 | P0-03 |
| 17 | PATCH | `/api/babies/{id}` | 成员 | `BabyUpdateReq{...同 BabyReq 全字段}` | `BabyResp` | 40001 40301 | P0-03 |
| 18 | DELETE | `/api/babies/{id}` | **owner** | path `id` | `Void` | 40302 40301 | P0-03 |

`BabyResp{ id, name, gender, birthday, avatarUrl, birthHeightCm, birthWeightKg, ageDays, ageText, milestoneCount, latestGrowth? }`
`BabyDetailResp = BabyResp + { latestMilestone?, growthRecordCount }`

#### 5.3.4 成长记录与曲线（5）

| # | 方法 | 路径 | 权限 | 请求体 / 参数 | 响应体 | 主要错误码 | P0 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 19 | GET | `/api/babies/{id}/growth-records` | 成员 | `page=0&size=20` | `PageResponse<GrowthRecordResp>` | 40301 40401 | P0-04 |
| 20 | POST | `/api/babies/{id}/growth-records` | 成员 | `GrowthRecordReq{recordedAt, heightCm?, weightKg?, headCm?, note?}` | `GrowthRecordResp` | 40001 40002 40301 | P0-04 |
| 21 | PATCH | `/api/growth-records/{id}` | 成员 | `GrowthRecordReq`（全量） | `GrowthRecordResp` | 40001 40301 40401 | P0-04 |
| 22 | DELETE | `/api/growth-records/{id}` | 成员 | path `id` | `Void` | 40301 40401 | P0-04 |
| 23 | GET | `/api/babies/{id}/growth-curve` | 成员 | `range=3m\|6m\|12m\|all` | `GrowthCurveResp` | 40002 40301 40401 | P0-04 |

> **POST 为 UPSERT 语义**（PRD P0-04①「同一日期只允许 1 条，可编辑覆盖」）：若 `(babyId, recordedAt)` 已存在，则覆盖其测量值并返回既有 `id`，HTTP 200。前端抽屉保存后无需判断 409 再弹「是否覆盖」。
> **范围校验**：`heightCm` 30–150、`weightKg` 1–60、`headCm` 20–70、`recordedAt` 不得为未来；三者至少一项非空。

#### 5.3.5 里程碑（4）

| # | 方法 | 路径 | 权限 | 请求体 / 参数 | 响应体 | 主要错误码 | P0 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 24 | GET | `/api/milestones` | 成员 | `babyId?&page=0&size=20` | `PageResponse<MilestoneResp>` | 40100 | P0-05 |
| 25 | POST | `/api/milestones` | 成员 | `MilestoneReq{babyId?, title, description?, achievedAt, photoId?}` | `MilestoneResp` | 40001 40301 40401 | P0-05 |
| 26 | PATCH | `/api/milestones/{id}` | 成员 | `MilestoneReq`（全量） | `MilestoneResp` | 40001 40301 40401 | P0-05 |
| 27 | DELETE | `/api/milestones/{id}` | 成员 | path `id` | `Void` | 40301 40401 | P0-05 |

`MilestoneResp{ id, babyId, babyName, title, description, achievedAt, ageText, photoId, photoThumbKey, createdBy, createdByName, createdAt }`
> `photoId` 存在时校验照片属同一 `familyId`，否则 40301。`babyId` 存在时校验宝宝属同一 `familyId`。

#### 5.3.6 相册（5）

| # | 方法 | 路径 | 权限 | 请求体 / 参数 | 响应体 | 主要错误码 | P0 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 28 | GET | `/api/albums` | 成员 | — | `AlbumListResp` | 40100 | P0-06 |
| 29 | POST | `/api/albums` | 成员 | `AlbumReq{name, description?}` | `AlbumResp` | 40001 40905 | P0-06 |
| 30 | GET | `/api/albums/{id}` | 成员 | path `id` | `AlbumDetailResp` | 40301 40401 | P0-06 |
| 31 | PATCH | `/api/albums/{id}` | `创建者\|owner` | `AlbumUpdateReq{name?, description?, coverPhotoId?}` | `AlbumResp` | 40001 40303 40905 | P0-06 |
| 32 | DELETE | `/api/albums/{id}` | `创建者\|owner` | `?photoStrategy=move\|delete`（默认 `move`） | `DeleteAlbumResp{deletedPhotos, movedPhotos}` | 40002 40303 40401 | P0-06 |

`AlbumListResp{ items:AlbumResp[], unclassified:UnclassifiedResp }`
`UnclassifiedResp{ count, coverPhotoId, coverThumbKey }`
`AlbumResp{ id, name, description, coverPhotoId, coverThumbKey, photoCount, createdBy, createdByName, createdAt, updatedAt, latestTakenAt }`
`AlbumDetailResp = AlbumResp + { takenFrom, takenTo, ownerEditable }`

> **未分类虚拟相册**（PRD Q6）：不作为真实表行，由 `GET /api/albums` 在 `unclassified` 字段返回计数与封面（最新一张未分类照片），不可删除、不可重命名。
> `photoStrategy=move` 时落地方式：`UPDATE photo SET album_id = NULL WHERE album_id = ? AND family_id = ?`（FK `ON DELETE SET NULL` 是兜底，但显式 UPDATE 便于返回 `movedPhotos` 计数）。

#### 5.3.7 照片（7）

| # | 方法 | 路径 | 权限 | 请求体 / 参数 | 响应体 | 主要错误码 | P0 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 33 | GET | `/api/photos` | 成员 | `albumId?&unclassified?&page=0&size=60&sort=takenAt_desc\|takenAt_asc\|createdAt_desc` | `PageResponse<PhotoResp>` | 40002 | P0-08 |
| 34 | GET | `/api/photos/{id}` | 成员 | path `id` | `PhotoDetailResp` | 40301 40401 | P0-09 |
| 35 | POST | `/api/photos/upload` | 成员 | `multipart/form-data`：`files[]`（1–100）、`albumId?` | `PhotoUploadResp` | 41301 41302 41501 50002 | P0-07 |
| 36 | PATCH | `/api/photos/{id}` | 成员 | `PhotoUpdateReq{title?, description?, takenAt?, locationName?, albumId?, tags?:string[]}` | `PhotoResp` | 40001 40301 40907 | P0-09 |
| 37 | DELETE | `/api/photos/{id}` | `上传者\|创建者\|owner` | path `id` | `Void` | 40303 40401 | P0-08 |
| 38 | POST | `/api/photos/batch-delete` | `上传者\|创建者\|owner` | `BatchDeleteReq{photoIds:long[] (1..100)}` | `BatchResultResp{deleted}` | 40001 40303 | P0-08 |
| 39 | POST | `/api/photos/batch-move` | 成员 | `BatchMoveReq{photoIds:long[], albumId?}` | `BatchResultResp{moved}` | 40001 40301 40907 | P0-08 |

`PhotoResp{ id, albumId, albumName, title, description, thumbKey, previewKey, mimeType, fileSize, width, height, takenAt, locationName, tags:string[], createdAt, uploadedBy, uploadedByName }`
`PhotoDetailResp = PhotoResp + { storageKey, tagNames, latitude, longitude }`
`PhotoUploadResp{ batchId, successCount, failureCount, successes:PhotoResp[], failures:UploadFailure[] }`
`UploadFailure{ fileName, code, message }`

> **`tags` 语义**：`PhotoUpdateReq.tags` 为**完整标签名集合**（服务端做「取差集 → 建缺失 tag → 重写 photo_tag」），缺失字段不更新，显式 `[]` 清空。

#### 5.3.8 时间轴 / 搜索 / 标签 / 统计（4）

| # | 方法 | 路径 | 权限 | 请求体 / 参数 | 响应体 | 主要错误码 | P0 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 40 | GET | `/api/timeline` | 成员 | `page=0&size=24` | `TimelinePageResp` | 40002 | P0-10 P0-05 P0-04 |
| 41 | GET | `/api/search` | 成员 | `q`（≥1 字符）`&type=all\|photo\|album\|milestone&limit=20` | `SearchResp` | 40001 | P0-11 |
| 42 | GET | `/api/tags` | 成员 | `q?`（前缀匹配，空则按最近使用倒序） | `List<TagResp>` | — | P0-09 |
| 43 | GET | `/api/stats/overview` | 成员 | — | `OverviewResp` | 40100 | P0-12 |

`TimelinePageResp{ groups:TimelineGroupResp[], page, size, total, totalPages, hasMore }`
`TimelineGroupResp{ month:"2026-10", label:"2026 年 10 月", items:TimelineItemResp[] }`
`TimelineItemResp{ type:"photo"\|"milestone"\|"growth", id, sortTime, photo?:PhotoResp 精简版, milestone?:MilestoneResp, growth?:GrowthRecordResp }`
`SearchResp{ query, totalCount, photos:SearchGroupResp<PhotoResp>, albums:SearchGroupResp<AlbumResp>, milestones:SearchGroupResp<MilestoneResp> }`
`SearchGroupResp<T>{ total, items:T[], hasMore }`
`OverviewResp{ photoCount, albumCount, milestoneCount, memberCount, babyCount, earliestBabyAgeDays, monthPhotoCount, storageBytes, storageLimitBytes(null) }`
`TagResp{ id, name, photoCount, lastUsedAt }`

#### 5.3.9 文件（1）

| # | 方法 | 路径 | 权限 | 请求参数 | 响应 | 主要错误码 |
| --- | --- | --- | --- | --- | --- | --- |
| 44 | GET | `/api/files/{*key}` | 成员（Header **或** `?token=`） | `key` = `/api/files/` 之后的完整相对路径 | `image/jpeg` 二进制流，`Cache-Control: private, max-age=2592000, immutable` | 40100 40301 40401 |

> **`{*key}`** 为 Spring 6 的 catch-all 路径变量，含 `/`。
> **安全**：`Path root = uploadRoot.toRealPath(); Path target = root.resolve(key).normalize(); if (!target.startsWith(root)) throw 40301;`
> **Range 支持**：返回 `ResponseEntity<Resource>`（`FileSystemResource`），Spring MVC 自动处理 `Range` 与 `Last-Modified`。
> **脱敏**：访问日志中 `token` 查询参数值替换为 `***`。

### 5.4 JSON 示例 ①：照片上传响应（`POST /api/photos/upload`）

请求（前端逐张调用，`files` 单元素；服务端仍支持多元素）：

```
POST /api/photos/upload
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: multipart/form-data; boundary=----BeeBoundary

------BeeBoundary
Content-Disposition: form-data; name="albumId"

12
------BeeBoundary
Content-Disposition: form-data; name="files"; filename="IMG_4821.JPG"
Content-Type: image/jpeg

<binary>
------BeeBoundary--
```

响应 `200`（1 成功 1 失败，说明批量语义）：

```json
{
  "code": 0,
  "message": "OK",
  "traceId": "9f2b7c1d4e8a3056",
  "data": {
    "batchId": "u-20261105-3f9a1c",
    "successCount": 1,
    "failureCount": 1,
    "successes": [
      {
        "id": 10231,
        "albumId": 12,
        "albumName": "青岛海边",
        "title": null,
        "description": null,
        "thumbKey": "1/2026/11/c0a1b2d3e4f5a6b7_thumb.jpg",
        "previewKey": "1/2026/11/c0a1b2d3e4f5a6b7_preview.jpg",
        "mimeType": "image/jpeg",
        "fileSize": 4823110,
        "width": 4032,
        "height": 3024,
        "takenAt": "2026-10-24 15:32:10",
        "locationName": null,
        "tags": [],
        "createdAt": "2026-11-05 21:04:11",
        "uploadedBy": 7,
        "uploadedByName": "妈妈"
      }
    ],
    "failures": [
      {
        "fileName": "IMG_4822.HEIC",
        "code": 41501,
        "message": "不支持 HEIC 格式。请在 iPhone「设置 → 相机 → 格式」中选择「兼容性最佳」后重新拍摄，或在相册中选择图片「导出为 JPEG」后再上传。"
      }
    ]
  }
}
```

### 5.5 JSON 示例 ②：时间轴（`GET /api/timeline?page=0&size=24`）

```json
{
  "code": 0,
  "message": "OK",
  "traceId": "3c7d9a2e5b1f8046",
  "data": {
    "groups": [
      {
        "month": "2026-10",
        "label": "2026 年 10 月",
        "items": [
          {
            "type": "photo",
            "id": 10231,
            "sortTime": "2026-10-24 15:32:10",
            "photo": {
              "id": 10231,
              "albumId": 12,
              "albumName": "青岛海边",
              "title": "海边第一次玩沙",
              "description": null,
              "thumbKey": "1/2026/11/c0a1b2d3e4f5a6b7_thumb.jpg",
              "previewKey": "1/2026/11/c0a1b2d3e4f5a6b7_preview.jpg",
              "mimeType": "image/jpeg",
              "fileSize": 4823110,
              "width": 4032,
              "height": 3024,
              "takenAt": "2026-10-24 15:32:10",
              "locationName": "青岛 · 金沙滩",
              "tags": ["海边", "第一次"],
              "createdAt": "2026-11-05 21:04:11",
              "uploadedBy": 7,
              "uploadedByName": "妈妈"
            }
          },
          {
            "type": "milestone",
            "id": 88,
            "sortTime": "2026-10-22 00:00:00",
            "milestone": {
              "id": 88,
              "babyId": 3,
              "babyName": "团团",
              "title": "第一次独立走路",
              "description": "从沙发走到茶几，一共走了 6 步，然后一屁股坐下笑了。",
              "achievedAt": "2026-10-22",
              "ageText": "1岁2个月4天",
              "photoId": 10228,
              "photoThumbKey": "1/2026/10/aa11bb22cc33dd44_thumb.jpg",
              "createdBy": 7,
              "createdByName": "妈妈",
              "createdAt": "2026-10-22 21:10:00"
            }
          },
          {
            "type": "growth",
            "id": 501,
            "sortTime": "2026-10-20 00:00:00",
            "growth": {
              "id": 501,
              "babyId": 3,
              "babyName": "团团",
              "recordedAt": "2026-10-20",
              "heightCm": 78.5,
              "weightKg": 9.80,
              "headCm": 46.2,
              "note": "例行体检",
              "createdBy": 7,
              "createdByName": "妈妈",
              "createdAt": "2026-10-20 19:00:00",
              "updatedAt": "2026-10-20 19:00:00"
            }
          }
        ]
      },
      {
        "month": "2026-09",
        "label": "2026 年 9 月",
        "items": [
          {
            "type": "photo",
            "id": 10190,
            "sortTime": "2026-09-30 08:12:44",
            "photo": {
              "id": 10190,
              "albumId": null,
              "albumName": null,
              "title": null,
              "description": null,
              "thumbKey": "1/2026/09/f00d1e2d3c4b5a69_thumb.jpg",
              "previewKey": "1/2026/09/f00d1e2d3c4b5a69_preview.jpg",
              "mimeType": "image/jpeg",
              "fileSize": 2201987,
              "width": 3024,
              "height": 4032,
              "takenAt": "2026-09-30 08:12:44",
              "locationName": null,
              "tags": [],
              "createdAt": "2026-11-01 10:00:00",
              "uploadedBy": 9,
              "uploadedByName": "外婆"
            }
          }
        ]
      }
    ],
    "page": 0,
    "size": 24,
    "total": 1248,
    "totalPages": 52,
    "hasMore": true
  }
}
```

### 5.6 JSON 示例 ③：成长曲线（`GET /api/babies/3/growth-curve?range=12m`）

```json
{
  "code": 0,
  "message": "OK",
  "traceId": "5a1e83c7d92b0471",
  "data": {
    "babyId": 3,
    "babyName": "团团",
    "birthday": "2025-08-18",
    "range": "12m",
    "from": "2025-11-05",
    "to": "2026-11-05",
    "points": [
      { "recordedAt": "2025-11-20", "ageDays": 94,  "heightCm": 63.5, "weightKg": 6.80, "headCm": 41.2 },
      { "recordedAt": "2025-12-18", "ageDays": 122, "heightCm": 66.0, "weightKg": 7.20, "headCm": 42.1 },
      { "recordedAt": "2026-03-20", "ageDays": 214, "heightCm": 71.2, "weightKg": 8.40, "headCm": 44.0 },
      { "recordedAt": "2026-06-20", "ageDays": 306, "heightCm": 75.8, "weightKg": 9.00, "headCm": 45.5 },
      { "recordedAt": "2026-08-18", "ageDays": 365, "heightCm": 77.3, "weightKg": 9.40, "headCm": 45.9 },
      { "recordedAt": "2026-10-20", "ageDays": 428, "heightCm": 78.5, "weightKg": 9.80, "headCm": 46.2 }
    ],
    "latest": { "recordedAt": "2026-10-20", "heightCm": 78.5, "weightKg": 9.80, "headCm": 46.2 },
    "delta":  { "heightCm": 1.2, "weightKg": 0.40, "headCm": 0.30 }
  }
}
```

> `delta` = 最新记录 − 上一条记录（PRD 成长页「+1.2 较上月」）。任一侧为空则对应字段为 `null`。
> `points` **按 `recordedAt` 升序**（ECharts `type:'time'` 要求；PRD P0-04②「曲线按日期升序」）。
> `range` 语义：`3m/6m/12m` 表示「最新记录日期往前推 3/6/12 个自然月」而非「今天往前推」，避免用户三个月没测量时曲线空掉；`all` 返回该宝宝全部记录（≤200 条，无需分页）。

### 5.7 ⭐ 时间轴分页策略 —— 结论与论证

**结论：在 MySQL 层用 `UNION ALL` 归并三个数据源，`ORDER BY sort_time DESC, type_ord ASC, item_id DESC` 后 `LIMIT/OFFSET` 分页；再按 id 分组批量回查明细。禁止「各自查询后在内存合并」。**

| 方案 | 做法 | 正确性问题 | 性能 | 判定 |
| --- | --- | --- | --- | --- |
| ① 内存合并（各源各取 `page` 条） | 照片取 24、里程碑取 24、成长取 24 → 内存排序取前 24 | ❌ **致命**：第 2 页会漏数据。若某月只有照片，照片源的第 25–48 条必须占据第 2 页，但内存合并时它们的排序位次被里程碑挤掉，后续页无论怎么取都拿不到 | — | **否决** |
| ② SQL 层 UNION ALL + 全局排序 + LIMIT | 见下方 SQL | ✅ 正确：全局序由 MySQL 保证，offset 语义成立 | filesort 排序量 = 三源过滤后行数（≈家庭总条目数 ≤52k）；有 `LIMIT` 时 MySQL 用堆排序，内存受 `sort_buffer_size` 限制 | ✅ **采用** |
| ③ SQL 层 UNION ALL + keyset（cursor） | `WHERE sort_time < :cursor` 逐源过滤再归并 | ✅ 正确且深翻页 O(1) | 实现复杂（三源各自 `WHERE` + 三元组比较） | 本期不做，接口预留 |

**采用的实现（`TimelineQueryRepository`，唯一原生 SQL）：**

```sql
-- 第一步：只取排序键（多取 1 条用于判定 hasMore）
--   type_ord 为整数序号，用于同 sort_time 下的稳定次序。
--   ⚠️ 必须用整数而非 item_type 字符串排序：字符串 ASC 是字典序
--      （'growth' < 'milestone' < 'photo'），与实际意图相反，且未来
--      改类型名会静默改变排序结果。
SELECT item_type, item_id, sort_time FROM (
  SELECT 'photo'     AS item_type, 0 AS type_ord, p.id AS item_id, p.taken_at AS sort_time
    FROM photo p
   WHERE p.family_id = :familyId
  UNION ALL
  SELECT 'milestone', 1, m.id, CAST(m.achieved_at AS DATETIME)
    FROM milestone m
   WHERE m.family_id = :familyId
  UNION ALL
  SELECT 'growth',    2, g.id, CAST(g.recorded_at AS DATETIME)
    FROM growth_record g
    JOIN baby b ON b.id = g.baby_id
   WHERE b.family_id = :familyId
) t
ORDER BY t.sort_time DESC, t.type_ord ASC, t.item_id DESC
LIMIT :sizePlus1 OFFSET :offset;

-- 第二步：按类型分组，各一条主键 IN 查询（见 §6.3）
```

**四个关键工程决策**

1. **`type_ord`（整数）作为次序键**：同一 `sort_time` 下必须有稳定次序，否则同一时刻的三类条目在两次请求间顺序漂移，导致翻页「重复/漏项」。固定优先级 `photo(0) < milestone(1) < growth(2)`（照片是主导内容，排前），**已固化不可改**。⚠️ **不要**直接用 `item_type` 字符串排序：字典序得到的是 `growth < milestone < photo`，与意图相反（已在 MySQL 8.4 实测验证）。`(type_ord, item_id)` 与 `sort_time` 组合构成全序，排序确定。
2. **`CAST(DATE AS DATETIME)`**：`achieved_at` / `recorded_at` 是 `DATE`，`taken_at` 是 `DATETIME`。不显式 CAST 时 MySQL 会做隐式类型提升但 `sort_time` 列类型不确定，规范化后可安全在 `UNION` 上排序并供 Java 侧统一映射为 `LocalDateTime`（DATE → `00:00:00`）。
3. **明细回查用 `IN` 批查而非 JOIN**：UNION 分支的列结构必须一致，塞入三类不同字段会变成「大宽表 + 大量 NULL」，DTO 映射丑陋且浪费带宽。改为 3 条 `IN (24 个 id)` 主键查询，总往返 4 次（1 次 UNION + 3 次 IN + 3 次 count 合并为 1 次三合一 count），内网 P95 ≈ 30–60ms，满足 PRD §10「列表分页 P95 ≤ 300ms」。
4. **`total` 用三次 `COUNT(*)` 求和**（一次查询三条标量子查询），用于 PRD P0-10「共 N 张记忆」与 `hasMore` 之外的总数展示；**`hasMore` 由 `size+1` 探测决定，不信 `total`**（避免 count 与数据不一致时前端死循环）。

**深翻页退化**：`OFFSET 12000` 时需扫描 12025 行索引项并排序，实测预计 80–150ms，仍在 300ms 预算内；若超出，按方案 ③ 迁移，接口签名不变（`page/size` 保留，新增可选 `cursor`）。

---

## 6. 关键流程时序图

### 6.1 流程一：登录鉴权与 401 拦截

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant V as LoginView.vue
    participant AUTH as useAuthStore
    participant H as api/http.ts
    participant JF as JwtAuthenticationFilter
    participant AC as AuthController
    participant AUTHS as AuthService
    participant UR as UserRepository
    participant FE as FamilyMemberRepository
    participant JS as JwtService
    participant DB as MySQL

    U->>V: 输入邮箱/密码 → 点「进入蜜记」
    V->>AS: login({ email, password })
    AUTH->>H: http.post('/auth/login', body)
    H->>AC: POST /api/auth/login（permitAll，无 Authorization 头）
    AC->>AUTHS: login(LoginReq)
    AUTHS->>UR: findByEmailIgnoreCase(email)
    UR->>DB: SELECT ... FROM user WHERE email = ?
    DB-->>UR: row | null
    alt 用户不存在
        AUTHS-->>AC: throw BusinessException(INVALID_CREDENTIALS)
    else 用户存在
        AUTHS->>AUTHS: passwordEncoder.matches(raw, passwordHash)
        alt 密码不匹配
            AUTHS-->>AC: throw BusinessException(INVALID_CREDENTIALS)
        else 匹配通过
            AUTHS->>JS: issue(userId, email)
            JS-->>AUTHS: JWT(HS256, sub=userId, exp=now+7d)
            AUTHS->>FE: findByUserId(userId)
            FE->>DB: SELECT ... FROM family_member WHERE user_id = ?  (uk_member_user)
            AUTHS->>UR: 组装 MeResp / BabyRepository.findByFamilyId
            AUTHS-->>AC: TokenResp{ token, expiresAt, user, familyId, role, babies }
        end
    end
    AC-->>H: 200 ApiResponse{ code:0, data:TokenResp, traceId }
    H->>H: 响应拦截器：code===0 → return data
    H-->>AUTH: TokenResp
    AUTH->>AUTH: localStorage['bee.token'] = token<br/>familyStore.set(data.family)<br/>babyStore.setList(data.babies)
    AUTH->>V: router.replace(route.query.redirect ?? '/timeline')
    Note over V: 首次使用（babies 为空）→ 先跳 /family 引导创建宝宝档案
```

**401 拦截与回跳（横向流程）**

```mermaid
sequenceDiagram
    autonumber
    participant P as 任意页面组件
    participant H as api/http.ts（响应拦截器）
    participant AUTH as useAuthStore
    participant R as vue-router 全局守卫
    participant API as Spring Security
    participant EP as AuthenticationEntryPoint

    P->>H: http.get('/albums')
    H->>API: GET /api/albums（Authorization: Bearer <过期 token>）
    API->>EP: Token 过期 / 签名不符
    EP-->>H: 401 ApiResponse{ code:40103, message:'登录已过期' }
    H->>H: 拦截器捕获 code>=40100 && <40200
    H->>AUTH: logout()（清 localStorage + $reset 全部 store）
    H->>R: router.replace({ path:'/login', query:{ redirect: currentFullPath } })
    H-->>P: reject(new UnauthorizedError())（组件可忽略，页面已卸载）
    Note over R: 守卫对 meta.requiresAuth && !token 的访问<br/>直接挡回 /login?redirect=
    Note over AUTH: 不做 refresh token 重试，避免并发请求重复刷新导致竞态
```

### 6.2 流程二：多文件上传（魔数校验 / EXIF / 双档缩略图 / 事务与失败回滚）

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant UQ as useUploadQueue.ts
    participant H as api/http.ts
    participant JF as JwtAuthenticationFilter
    participant PC as PhotoController
    participant PS as PhotoService
    participant PIS as PhotoIngestService
    participant IV as ImageValidator
    participant EX as ExifService
    participant TH as ThumbnailService
    participant ST as StorageService
    participant PR as PhotoRepository
    participant DB as MySQL
    participant DISK as 本地磁盘

    U->>UQ: 选择/拖入 120 个文件
    UQ->>UQ: 预校验：扩展名白名单 + size<=20MB + 非 heic/heif
    UQ-->>U: 聚合 Toast warning：「3 个文件超过 20MB、2 个为 HEIC，已跳过」
    UQ->>UQ: 队列去重，超 100 张分批（第 2 批需用户确认）
    loop 并发 3（Promise 池）
        UQ->>H: POST /api/photos/upload（FormData: files=[1 个], albumId）
        H->>JF: 校验 Bearer token → SecurityContext(UserPrincipal)
        JF->>PC: 放行
        PC->>PS: upload(List<MultipartFile>, albumId)
        PS->>PS: 批次校验：files.size()<=100 → 否则 41302
        PS->>PS: albumId != null → assertSameFamily(album.familyId) → 否则 40301
        loop 每个文件（独立事务，一张失败不影响其余）
            PS->>PS: size > 20MB ? → failures += {41301}，continue
            PS->>IV: sniff(bytes[0..32])
            IV->>IV: 比对 JPEG(FFD8FF)/PNG(89504E47)/WEBP(RIFF..WEBP)
            IV->>IV: 检出 HEIC(ftyp heic/heix/mif1) → 41501
            IV->>IV: 检出 SVG(<svg / <?xml) 或扩展名黑名单 → 41501
            alt 魔数不通过
                IV-->>PS: failures += {41501, message}
            else 通过
                PS->>ST: allocateKey(familyId, now, ext)
                ST-->>PS: 1/2026/11/{uuid}.jpg（UUID 重写，无用户输入参与）
                PS->>ST: writeOriginal(key, stream)
                ST->>DISK: Files.copy（先落盘，事务外）
                PS->>EX: readTakenAt(tempFile)
                EX-->>PS: LocalDateTime | empty
                PS->>TH: generate(originalPath, keyBase)
                TH->>DISK: {uuid}_thumb.jpg（最长边 480, q0.82, 去 EXIF）
                TH->>DISK: {uuid}_preview.jpg（最长边 1600, q0.85）
                TH-->>PS: (w, h, thumbBytes, previewBytes)
                PS->>PIS: ingestOne(PhotoDraft)  @Transactional(REQUIRES_NEW)
                PIS->>PR: save(entity)
                PR->>DB: INSERT INTO photo (...)
                alt DB 异常（约束/连接/磁盘满）
                    PIS-->>PS: throw
                    PS->>ST: deleteQuietly(3 个文件)  ← 补偿
                    PS->>PS: failures += {50002}
                else 提交成功
                    PIS-->>PS: PhotoResp
                    PS->>PS: successes += PhotoResp
                end
            end
        end
        PS-->>PC: PhotoUploadResp{ batchId, successes, failures }
        PC-->>H: 200 ApiResponse<PhotoUploadResp>
        H-->>UQ: 单张结果 → 更新进度条
        UQ->>UQ: albumStore.photoCount++ / photoStore.prepend(successes)
    end
    UQ-->>U: 全部完成 → Toast success「已上传 98 张，2 张失败」<br/>失败项保留「重试」按钮
    Note over UQ,DISK: 前端逐张请求（单文件）而非一次多文件：<br/>① 单张进度真实；② 失败隔离；③ 重试粒度=单张。<br/>服务端仍支持多文件（curl/脚本/P1 优化）
```

**关键设计点**

| 点 | 决策 | 理由 |
| --- | --- | --- |
| 事务粒度 | 每张照片一个 `REQUIRES_NEW` 事务（独立 Bean `PhotoIngestService`） | 100 张一个事务：任一失败全回滚，与 P0-07「失败重试」语义冲突；且长事务持锁影响并发上传 |
| 文件 vs DB 顺序 | **先写文件，再写 DB**；DB 失败补偿删文件 | 反过来（先 DB 后写文件）若写文件失败，DB 里已有指向不存在文件的记录 → 前端表现为永久破图，不可自愈；当前顺序的残留只是孤儿文件，可被 `rsync`/清理脚本发现 |
| 补偿失败 | `deleteQuietly` 只记 WARN，不抛 | 补偿是尽力而为，不能让补偿失败覆盖原始异常 |
| 事务后动作 | 无（缩略图在事务内生成但文件系统无事务） | 若需「事务提交后才落缩略图」可加 `afterCommit`，但会把耗时移出请求；MVP 保持同步 |

### 6.3 流程三：时间轴聚合查询

```mermaid
sequenceDiagram
    autonumber
    participant V as TimelineView.vue
    participant S as useTimeline store / timelineApi
    participant TC as TimelineController
    participant TS as TimelineService
    participant TQ as TimelineQueryRepository
    participant PR as PhotoRepository
    participant MR as MilestoneRepository
    participant GR as GrowthRecordRepository
    participant TR as TagRepository
    participant DB as MySQL

    V->>S: loadPage(0, 24)
    S->>TC: GET /api/timeline?page=0&size=24
    TC->>TS: page(familyId, PageQuery)
    TS->>TQ: countAll(familyId)
    TQ->>DB: SELECT (SELECT COUNT(*) FROM photo WHERE family_id=?)<br/>+ (SELECT COUNT(*) FROM milestone WHERE family_id=?)<br/>+ (SELECT COUNT(*) FROM growth_record g JOIN baby b ...)
    DB-->>TQ: total = 1248
    TS->>TQ: fetchSortKeys(familyId, offset, sizePlus1=25)
    TQ->>DB: UNION ALL 三源 → ORDER BY sort_time DESC, type_ord ASC, item_id DESC → LIMIT 25
    DB-->>TQ: 25 × (item_type, type_ord, item_id, sort_time)
    TQ-->>TS: List<TimelineKey>
    TS->>TS: hasMore = keys.size() > 24；切片取前 24；按 type 分三组 id
    par 三路明细回查（主键 IN，各 1 次）
        TS->>PR: findAllByIdIn(photoIds) + albumName 批量映射
        PR->>DB: SELECT ... FROM photo WHERE id IN (...)
    and
        TS->>MR: findAllByIdIn(milestoneIds)
        MR->>DB: SELECT ... FROM milestone WHERE id IN (...)
    and
        TS->>GR: findAllByIdIn(growthIds)
        GR->>DB: SELECT ... FROM growth_record WHERE id IN (...)
    end
    TS->>TR: findByPhotoIdIn(photoIds)  ← 标签一次批查，避免 N+1
    TR->>DB: SELECT pt.photo_id, t.name FROM photo_tag pt JOIN tag t ...<br/>WHERE pt.photo_id IN (...)
    TS->>TS: 组装 Map<type+id, TimelineItemResp>
    TS->>TS: 按 keys 原始顺序重建 items（保持 SQL 的全局序）
    TS->>TS: 按 yyyy-MM 分组 → TimelineGroupResp[]
    TS-->>TC: TimelinePageResp{ groups, page, size, total, totalPages, hasMore }
    TC-->>S: ApiResponse
    S->>V: 追加/替换分组，渲染
    V->>V: IntersectionObserver 哨兵距底 400px → loadPage(page+1)
    Note over TS,DB: 总 DB 往返 = 1(count) + 1(union) + 3(明细) + 1(标签) = 6 次<br/>P95 预估 30–60ms（内网单机）
```

**排序正确性保证（三处必须同时成立）**

1. SQL 层 `ORDER BY sort_time DESC, type_ord ASC, item_id DESC`（`type_ord`：photo=0 / milestone=1 / growth=2，整数，非字符串）—— 构成全序，保证全局序与稳定序。
2. Java 层重建 items 时**必须按 `keys` 的顺序**，不得依赖三路查询返回顺序或 `HashMap` 迭代顺序。
3. 分组时**只在组边界插入 `TimelineGroupResp`**，组内保持 keys 顺序；跨年跨月用 `sortTime.format("yyyy-MM")` 判断边界变化。

### 6.4 流程四：删相册时照片的两种处置

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户（owner 或相册创建者）
    participant M as DeleteAlbumModal.vue
    participant AC as AlbumController
    participant AL as AlbumService
    participant G as FamilyAccessGuard
    participant AR as AlbumRepository
    participant PR as PhotoRepository
    participant FR as FamilyRepository
    participant ST as StorageService
    participant DB as MySQL
    participant DISK as 本地磁盘

    U->>M: 点「删除相册」→ 单选「照片移到未分类（推荐）/ 一并删除这些照片」
    M->>M: 二次确认（Modal sm，危险确认样式）
    M->>AC: DELETE /api/albums/12?photoStrategy=move
    AC->>AL: delete(familyId, currentUser, 12, MOVE)
    AL->>AR: findByIdAndFamilyId(12, familyId)
    AR->>DB: SELECT ... FROM album WHERE id=12 AND family_id=?
    alt 不存在或跨家庭
        AL-->>AC: throw 40401 / 40301
    else 存在
        AL->>G: assertCreatorOrOwner(album.createdBy, currentUser)
        G-->>AL: pass（否则 40303）

        alt photoStrategy = MOVE（默认，推荐）
            AL->>PR: moveToUnclassified(familyId, 12)
            PR->>DB: UPDATE photo SET album_id = NULL, updated_at = NOW()<br/>WHERE album_id = 12 AND family_id = ?
            PR-->>AL: movedPhotos = 48
            AL->>AR: deleteById(12)
            AR->>DB: DELETE FROM album WHERE id = 12
            Note over AL,DB: 磁盘文件完全不动
            AL-->>AC: DeleteAlbumResp{ movedPhotos:48, deletedPhotos:0 }
        else photoStrategy = DELETE
            AL->>PR: findStorageKeysByAlbum(familyId, 12)
            PR-->>AL: 48 × (storageKey, thumbKey, previewKey) = 144 个 key
            AL->>PR: deleteTagsByPhotoIds(ids)
            PR->>DB: DELETE FROM photo_tag WHERE photo_id IN (...)
            AL->>PR: nullifyCoverReferences(ids)
            PR->>DB: UPDATE family SET cover_photo_id = NULL WHERE cover_photo_id IN (...)<br/>UPDATE album SET cover_photo_id = NULL WHERE cover_photo_id IN (...)
            Note over PR,DB: album.cover_photo_id 无 FK，必须显式清理；<br/>milestone.photo_id 有 ON DELETE SET NULL，DB 自动处理
            AL->>PR: deleteByIds(ids)
            PR->>DB: DELETE FROM photo WHERE id IN (...) AND family_id = ?
            AL->>AR: deleteById(12)
            AR->>DB: DELETE FROM album WHERE id = 12
            AL->>AL: afterCommit → 收集 144 个 key
            AL-->>AC: DeleteAlbumResp{ movedPhotos:0, deletedPhotos:48 }
        end
        Note over AL,DISK: 文件删除在事务提交后执行：<br/>ST.deleteQuietly(keys) —— 失败仅 WARN 日志，<br/>不阻塞接口、不回滚 DB（DB 已无引用）
    end
    AC-->>M: ApiResponse
    M->>M: albumStore.remove(12)；photoStore.invalidate()；Toast success「已删除相册，48 张照片已移到未分类」
```

**注：删家庭的显式依赖顺序**（`FamilyService#deleteFamily`，单一事务）

```
1. milestone（family_id）        ← 先断 milestone.photo_id / baby_id 引用
2. photo_tag（JOIN photo）       ← 断标签引用
3. photo（family_id）            ← 断 album_id / cover 引用
4. growth_record（JOIN baby）
5. album（family_id）
6. baby（family_id）
7. tag（family_id）
8. family_member（family_id）
9. family（id）
→ afterCommit：ST.deleteDirectory(familyId)（尽力而为）
```

> **为什么不直接 `DELETE FROM family` 交给级联**：已在 MySQL 8.4 实测，单条 `DELETE FROM family` 能正确清空全部子表且不抛 1451，因此这是**行为正确性之外的工程选择**，理由有三：
> 1. InnoDB 未承诺多路径级联的执行顺序，`CASCADE` 与 `SET NULL` 交织时的行为依赖内部实现细节，升版/改表后可能静默变化；显式顺序是确定的。
> 2. 需要逐表记录删除行数（写入审计日志），级联拿不到分表计数。
> 3. 需要在 `afterCommit` 阶段拿到该家庭全部 `storage_key` 以清理磁盘——级联删完后照片行已不存在，无从查询。
>
> FK 的 `CASCADE` 保留为「有人绕过 service 直接删库时的兜底」，**不作为业务路径依赖**。

---

## 7. 安全设计

### 7.1 JWT 生成 / 校验 / 过期

| 项 | 规格 |
| --- | --- |
| 算法 | HS256（`Keys.hmacShaKeyFor(secret.getBytes(UTF_8))`） |
| 密钥 | `${JWT_SECRET}`，要求 ≥ 32 字节（256 bit）；**无默认值**，缺失则启动失败（`@NotBlank` on `JwtProperties`） |
| Claims | `sub` = userId（String）、`email`、`iat`、`exp`。**不含 `familyId` / `role` / `nickname`**（ADR-02） |
| 有效期 | 7 天（604800s），`app.jwt.ttl: 7d` |
| 校验时机 | 每个请求（`JwtAuthenticationFilter`），解析失败一律不写 `SecurityContext`，由 `AuthenticationEntryPoint` 返回 401 |
| 过期 | `exp` 到期 → `ExpiredJwtException` → `40103 TOKEN_EXPIRED` |
| 签名错/畸形 | `JwtException` → `40100 UNAUTHORIZED`（不区分，避免给出探测信息） |
| 登出 | 无服务端状态；前端清除。**明示取舍**：被窃 token 在 7 天内仍有效（内网自用可接受） |

### 7.2 `JwtAuthenticationFilter` 凭证来源

```
1) Authorization: Bearer <token>                       ← 所有 /api/**
2) request.getParameter("token")                       ← 仅当 path 以 /api/files/ 开头
3) 两者皆无 → 不设置 Authentication，链尾 authenticated() 拒绝 → 40100
4) token 有效 → userRepository.findById(sub) 取失败（用户被删）→ 40100
              → familyMemberRepository.findByUserId(sub) → familyId/role（可能为 null）
              → SecurityContextHolder.setAuthentication(UserPrincipal)
5) finally 清 MDC / clearContext（由 SecurityContextHolderFilter 处理）
```

### 7.3 `SecurityFilterChain` 放行清单

| 路径 | 规则 | 说明 |
| --- | --- | --- |
| `/api/auth/register` | `permitAll` | 注册（首个用户自建家庭） |
| `/api/auth/login` | `permitAll` | 登录 |
| `/api/files/**` | `authenticated`（凭证可由 `?token=` 提供） | `<img>` 无法带 header，见 ADR-04 |
| `/api/**`（其余） | `authenticated` | 默认拒绝 |
| `/error` | `permitAll` | Spring Boot 错误转发 |
| `/actuator/health` | `permitAll` | 仅暴露 health（`management.endpoints.web.exposure.include: health`） |
| `OPTIONS /**` | `permitAll` | CORS 预检 |
| 静态资源 `/**`（生产期） | `permitAll` | SPA 的 `index.html` / `assets/**`；`favicon.ico` |

**其他配置**

| 项 | 值 |
| --- | --- |
| `csrf` | `disable()`（无状态 + 无 Cookie 会话，CSRF 不适用） |
| `sessionManagement` | `STATELESS` |
| `httpBasic` / `formLogin` | `disable()` |
| `headers` | `frameOptions: deny`、`contentSecurityPolicy("default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'")`、`referrerPolicy: NO_REFERRER` |
| `passwordEncoder` | `BCryptPasswordEncoder(10)` 单例 Bean |
| 401 出口 | `AuthenticationEntryPoint` → `ApiResponse.fail(UNAUTHORIZED/TOKEN_EXPIRED)` |
| 403 出口 | `AccessDeniedHandler` → `ApiResponse.fail(FORBIDDEN)` |

### 7.4 CORS 白名单

```yaml
app:
  cors:
    allowed-origins: ${CORS_ORIGINS:http://localhost:5173,http://127.0.0.1:5173}
```

- `allowedMethods`: `GET,POST,PATCH,PUT,DELETE,OPTIONS`
- `allowedHeaders`: `Authorization,Content-Type,X-Trace-Id`
- `exposedHeaders`: `X-Trace-Id`
- `allowCredentials`: `false`（不用 Cookie，故无需 true，也避免 `*` 与 credentials 冲突）
- `maxAge`: 3600
- **生产期单端口部署时此配置不生效**（同源），保留仅为开发期。

### 7.5 越权返回 403 的实现

**双层防线，且都在 service 层落地（controller 不参与权限判断）：**

| 层 | 机制 | 覆盖 |
| --- | --- | --- |
| 第 1 层（查询层） | 所有 repository 方法**必须**带 `familyId`：`findByIdAndFamilyId(id, familyId)` 而非 `findById(id)`。返回 `Optional.empty()` 时抛 `RESOURCE_NOT_FOUND` 或 `CROSS_FAMILY_ACCESS` —— **本项目统一抛 40301**（PRD §10 明确要求 403 便于自查） | 「别人的照片/相册/宝宝」全部拦截 |
| 第 2 层（显式闸门） | `FamilyAccessGuard` 提供 `assertSameFamily(Long resourceFamilyId)`、`assertOwner()`、`assertCreatorOrOwner(Long createdBy)`、`assertUploaderOrCreatorOrOwner(...)`，全部抛 `BusinessException` | 角色类操作（重命名家庭、移除成员、删相册） |

```java
// security/FamilyAccessGuard.java  —— 接口签名（不写实现）
public class FamilyAccessGuard {
    public UserPrincipal principal();                       // 未认证抛 40100
    public Long familyId();                                 // 无家庭抛 40300
    public void assertSameFamily(Long resourceFamilyId);    // != familyId → 40301
    public void assertOwner();                              // role != owner → 40302
    public void assertCreatorOrOwner(Long createdBy);       // 既非创建者也非 owner → 40303
    public void assertUploaderOrCreatorOrOwner(Long uploadedBy, Long createdBy); // → 40303
}

// security/SecurityUtils.java
public final class SecurityUtils {
    public static UserPrincipal requirePrincipal();          // 未认证抛 40100
    public static Long currentUserId();
    public static Optional<UserPrincipal> current();
}
```

> **「找不到」还是「无权」**：本项目所有跨家庭访问返回 **403 `CROSS_FAMILY_ACCESS`**，不返回 404。理由：PRD §10 明示；且对内自用，清晰报错优于隐蔽。真正的「家庭内不存在该 id」返回 404 `RESOURCE_NOT_FOUND`。
> 落地细节：`findByIdAndFamilyId` 返回空时无法区分「不存在」与「跨家庭」，故 service 对**关键实体**先 `findById` 判存在，再比对 `familyId`；仅当 id 不存在时 404。二级实体（照片、里程碑、成长记录）为减少查询直接用 `findByIdAndFamilyId` + 40301（可接受，因为 id 是随机自增、跨家庭 id 探测无实际价值）。

### 7.6 上传安全

| 项 | 措施 |
| --- | --- |
| 扩展名白名单 | 小写化后仅允许 `jpg` / `jpeg` / `png` / `webp`。**双重判定**：`Content-Type`（可能伪造）+ 客户端文件名后缀（可能伪造）+ **文件头魔数（唯一可信）** |
| 魔数校验 | JPEG `FF D8 FF`；PNG `89 50 4E 47 0D 0A 1A 0A`；WEBP `52 49 46 46`(RIFF) + offset 8 起 `57 45 42 50`(WEBP)。不一致即 `41501` |
| 禁止 SVG | 后缀黑名单 `svg/svgz` + 内容嗅探 `<svg` / `<?xml` → `41501`（SVG 可内嵌 `<script>`，是存储型 XSS 入口） |
| 禁止 HEIC | 嗅探 `ftyp` brand `heic/heix/hevc/mif1/msf1` → `41501` + 中文指引（ADR-01） |
| 禁止可执行 | 后缀黑名单 `exe/sh/bat/js/html/htm/php/jsp/jar/zip` 等；`ImageValidator` 白名单是主防线（非白即拒） |
| 文件名 | **完全忽略客户端文件名**，服务端生成 `UUID.randomUUID().toString().replace("-","")`。原文件名不入库（`PhotoUploadResp.failures[].fileName` 仅回显给前端做定位，不落盘、不入库） |
| 路径穿越 | key 只由 `familyId + yyyy + MM + uuid + ext` 拼接，**无任何用户输入片段**；读取时 `resolve(key).normalize().startsWith(root.toRealPath())` 双重校验 |
| 体积 | Tomcat/Spring `spring.servlet.multipart.max-file-size: 20MB`、`max-request-size: 2100MB`；service 层再校验 `file.getSize()`，超限 `41301` |
| 批量 | `files.size() > 100` → `41302` |
| 图片炸弹 | `Thumbnailator` 生成前检查 `ImageIO.read` 得到的 `width*height <= 100_000_000`（1 亿像素），超出拒绝（防止解压炸弹耗尽堆） |
| 存储隔离 | 上传目录在**应用工作目录之外**（`app.storage.root`），不通过静态资源映射暴露；只能经 `GET /api/files/**` 鉴权访问 |
| 权限 | 上传目标 `albumId` 必须 `assertSameFamily`；否则可把照片塞进别人相册 |

### 7.7 限流（留接口不实现）

| 项 | 设计 |
| --- | --- |
| 预留点 | `support/RateLimiter.java` 接口：`boolean tryAcquire(String bucketKey, int permits)`；默认实现 `NoopRateLimiter`（恒 true） |
| 建议阈值（未来） | 登录 5 次/分钟/IP；上传 300 张/小时/家庭；搜索 30 次/分钟/用户 |
| 实现方式（未来） | 内存 `ConcurrentHashMap<String, Deque<Long>>` 滑动窗口即可（单机） |
| 本期 | **不实现**，不引入 Bucket4j/Redis。理由：内网自用、≤6 人，暴力破解风险远低于误锁家人的风险 |

### 7.8 敏感信息与配置

| 项 | 规则 |
| --- | --- |
| `application.yml` | **不含任何口令**；数据库仅 `${DB_URL}` `${DB_USERNAME}` `${DB_PASSWORD}`，**不写默认值**（缺失即启动失败，避免误连生产库） |
| `application-local.yml` | 真实本地值；**必须 gitignore** |
| `application-local.yml.example` | 提交，占位值，附注释 |
| `.gitignore`（根 + backend） | `application-local.yml`、`backend/data/`、`target/`、`node_modules/`、`dist/`、`.env.local`、`*.log`、`.DS_Store` |
| 日志 | 禁止打印 `password`、`token`（含 `?token=`）、`passwordHash`；`TraceIdFilter` 对 query string 做正则脱敏 `token=[^&]+` → `token=***` |
| 前端 | 只有 `VITE_API_BASE_URL`（非敏感）进 `.env`；**禁止**把 JWT_SECRET 或 DB 信息写入前端 `.env` |

`application.yml` 骨架（仅结构，值全部为占位符）：

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate.ddl-auto: validate
    open-in-view: false
    properties.hibernate.jdbc.time_zone: Asia/Shanghai
  flyway:
    enabled: true
    baseline-on-migrate: false
  servlet.multipart:
    max-file-size: 20MB
    max-request-size: 2100MB
  jackson:
    time-zone: Asia/Shanghai
    default-property-inclusion: always
app:
  jwt:
    secret: ${JWT_SECRET}
    ttl: 7d
  storage:
    root: ${STORAGE_ROOT:./data/uploads}
  cors:
    allowed-origins: ${CORS_ORIGINS:http://localhost:5173}
server:
  port: 8080
  error.include-stacktrace: never
```

---

## 8. 存储设计

### 8.1 目录结构

```
backend/data/uploads/                          ← app.storage.root（gitignore）
└── {familyId}/
    └── {yyyy}/
        └── {MM}/
            ├── {uuid}.jpg                     ← 原图（保存上传字节，不改动）
            ├── {uuid}_thumb.jpg               ← 网格缩略图，最长边 480px
            └── {uuid}_preview.jpg             ← 灯箱预览图，最长边 1600px
```

示例：

```
backend/data/uploads/1/2026/11/c0a1b2d3e4f5a6b7c8d9e0f1a2b3c4d5.jpg
backend/data/uploads/1/2026/11/c0a1b2d3e4f5a6f7c8d9e0f1a2b3c4d5_thumb.jpg
backend/data/uploads/1/2026/11/c0a1b2d3e4f5a6f7c8d9e0f1a2b3c4d5_preview.jpg
```

### 8.2 三档文件命名与规格

| 档位 | 文件名 | 尺寸规则 | 编码 | 目标体积 | 用途 | DB 列 |
| --- | --- | --- | --- | --- | --- | --- |
| 原图 | `{uuid}.{ext}` | 不缩放 | **保持原字节**（不重编码，避免二次损失与 EXIF 丢失） | ≤ 20MB | 下载/未来导出 | `photo.storage_key` |
| 缩略图 | `{uuid}_thumb.{jpg\|png\|webp}` | 最长边 **480px**，等比缩放，不裁剪、不放大（`min(1, 480/max(w,h))`） | JPEG q=0.82 / PNG 保持 PNG / WEBP q=0.82；**strip 元数据** | **≤ 80KB**（PRD §10） | 网格、时间轴、缩略条、相册封面 | `photo.thumb_key` |
| 预览图 | `{uuid}_preview.{ext}` | 最长边 **1600px**，等比 | 同缩略图规则，JPEG q=0.85 | ≤ 600KB | 灯箱主图 | `photo.preview_key` |

**扩展名映射**：原图扩展名取 `ImageValidator` 嗅探出的**真实类型**（不是客户端后缀）→ `IMAGE_JPEG → jpg`、`IMAGE_PNG → png`、`IMAGE_WEBP → webp`。缩略图/预览图对 JPEG/WEBP 输出 `.jpg`/`.webp`；**PNG 缩略图保持 `.png`**（保留透明通道，避免相册网格出现黑边）。因此 `thumb_key` / `preview_key` 的后缀由 service 决定，前端不得字符串拼接推后缀，只能用 DB 返回的 key。

**`storage_key` 格式**：相对路径，`/` 分隔，**无前导 `/`、无 `data/uploads` 前缀**（前缀由 `app.storage.root` 提供）。这样迁移存储根目录时无需改数据。

### 8.3 写入流程与原子性

```
1. 生成 key（纯函数，无 IO）
2. Files.createDirectories(parent)                      ← 幂等
3. Files.copy(inputStream, originalPath)                ← 直接写最终名，不用 .tmp
   ⚠️ 理由：DB 记录在文件写完之后才创建，若进程中途崩溃，
   残留孤儿文件无害（无 DB 引用），而 .tmp 中转会增加一次 rename 与清理负担
4. 生成 thumb / preview（Thumbnailator 直接写最终名）
5. INSERT photo（独立事务）
6. 失败 → deleteQuietly(三个路径)
```

### 8.4 删除策略

| 触发 | DB 动作 | 文件动作 | 时机 |
| --- | --- | --- | --- |
| 删单张照片 | 删 `photo_tag` → 清 `family/album.cover_photo_id` → 删 `photo` | 删 3 个文件 | 事务 `afterCommit`（`TransactionSynchronization`） |
| 批量删照片 | 同上（`IN`） | 删 3×N 个文件 | 同上 |
| 删相册 `photoStrategy=move` | `UPDATE photo SET album_id=NULL` → 删 album | **不动文件** | — |
| 删相册 `photoStrategy=delete` | 删 `photo_tag` → 清 cover → 删 photo → 删 album | 删 3×N | `afterCommit` |
| 删家庭 | 按 §6.4 注的 9 步顺序删 | 删 `uploads/{familyId}` 整目录 | `afterCommit` |
| 失败处理 | — | 全部 `deleteQuietly`：文件不存在记 DEBUG，其他记 WARN，**不抛异常** | — |

> **不做软删除**。家庭内网自用，回收站是 P1+ 需求；删除即永久，由前端二次确认（PRD P0-06①」）兜住误操作。

### 8.5 备份建议（运维脚本，本期不实现代码）

```bash
#!/usr/bin/env bash
# 建议由 launchd / cron 每日 03:00 执行；先 dump DB 再 rsync 文件
set -euo pipefail
DEST="$HOME/bee-family-backup/$(date +%F)"
mkdir -p "$DEST"
mysqldump --single-transaction --routines --triggers \
          -u root -p"$DB_PASSWORD" bee_family > "$DEST/bee_family.sql"
rsync -a --delete "$PROJECT/backend/data/uploads/" "$DEST/uploads/"
```

| 项 | 说明 |
| --- | --- |
| 顺序 | **先 DB 后文件**。照片只增不改（原图写一次即不可变），后到的文件是「DB 未引用的孤儿」，可容忍；反过来会出现「DB 引用了不存在的文件」= 不可恢复的破图 |
| 一致性 | 不做严格快照（单机家用不值得停机）；接受「最近的孤儿文件」 |
| 保留 | 本地保留 7 天，重要节点手动归档到外部硬盘 |
| 恢复 | `mysql bee_family < bee_family.sql` + `rsync uploads/`；顺序无关（FK 都在同一份 dump 内） |
| 容量估算 | 5000 张 × (5MB 原图 + 60KB 缩略 + 400KB 预览) ≈ **27GB** |

---

## 9. 前端架构

### 9.1 目录结构树

```
frontend/
├── index.html
├── package.json
├── vite.config.ts                     # alias @ → src、server.proxy /api → :8080
├── tsconfig.json                      # paths: {"@/*": ["./src/*"]}
├── tsconfig.node.json
├── env.d.ts                           # ImportMetaEnv 声明
├── eslint.config.js                   # flat config
├── .env.development                   # VITE_API_BASE_URL=/api
├── .env.production                    # VITE_API_BASE_URL=/api
└── src/
    ├── main.ts                        # createApp + pinia + router + 全局样式
    ├── App.vue                        # <RouterView /> + BeeToastHost + 全局 Lightbox 挂载点
    │
    ├── api/
    │   ├── http.ts                    # axios 实例 + 2 个拦截器 + ApiError
    │   ├── index.ts                   # 统一 re-export
    │   ├── auth.ts
    │   ├── family.ts
    │   ├── baby.ts
    │   ├── growth.ts
    │   ├── milestone.ts
    │   ├── album.ts
    │   ├── photo.ts
    │   ├── timeline.ts
    │   ├── search.ts
    │   ├── stats.ts
    │   └── file.ts                    # fileUrl(key, size) 工具
    │
    ├── components/
    │   ├── base/                      # 19 个设计系统组件
    │   │   ├── BeeButton.vue
    │   │   ├── BeeInput.vue
    │   │   ├── BeeSelect.vue
    │   │   ├── BeeTag.vue
    │   │   ├── BeeCard.vue
    │   │   ├── BeeModal.vue
    │   │   ├── BeeDrawer.vue
    │   │   ├── BeeToast.vue
    │   │   ├── BeeToastHost.vue
    │   │   ├── BeeSkeleton.vue
    │   │   ├── BeeEmpty.vue
    │   │   ├── BeeAvatar.vue
    │   │   ├── BeePageHeader.vue
    │   │   ├── BeeHexBadge.vue
    │   │   ├── BeeTabs.vue
    │   │   ├── BeePagination.vue
    │   │   ├── BeeFileDrop.vue
    │   │   ├── BeeImage.vue
    │   │   └── BeeIcon.vue
    │   └── business/                  # 17 个业务组件
    │       ├── AlbumCard.vue
    │       ├── AlbumFormModal.vue
    │       ├── DeleteAlbumModal.vue
    │       ├── PhotoGrid.vue
    │       ├── PhotoCell.vue
    │       ├── Lightbox.vue
    │       ├── TimelineGroup.vue
    │       ├── TimelineItem.vue
    │       ├── MilestoneCard.vue
    │       ├── MilestoneModal.vue
    │       ├── GrowthChart.vue
    │       ├── MemberCard.vue
    │       ├── BabySwitcher.vue
    │       ├── UploadPanel.vue
    │       ├── UploadQueue.vue
    │       ├── StatBar.vue
    │       └── SearchGroup.vue
    │
    ├── layouts/
    │   ├── AppShell.vue               # 侧栏 + 顶栏 + main + 移动端 TabBar + 抽屉
    │   ├── SideNav.vue
    │   ├── TopBar.vue
    │   └── TabBar.vue
    │
    ├── views/
    │   ├── LoginView.vue
    │   ├── TimelineView.vue
    │   ├── AlbumsView.vue
    │   ├── AlbumDetailView.vue
    │   ├── GrowthView.vue
    │   ├── BabyView.vue
    │   ├── FamilyView.vue
    │   ├── SearchView.vue
    │   ├── SettingsView.vue
    │   └── NotFoundView.vue
    │
    ├── router/
    │   └── index.ts
    │
    ├── stores/
    │   ├── auth.ts                    # token / user / familyId / role / login / logout / restore
    │   ├── family.ts                  # family / members / inviteCode / babies
    │   ├── album.ts                   # albums / unclassified / current / CRUD
    │   ├── photo.ts                   # 当前网格 photos / pagination / selection / lightboxIndex
    │   └── ui-toast.ts                # toast 队列
    │
    ├── styles/
    │   ├── tokens.css                 # 原样落地 02 文档 §2.14
    │   ├── reset.css                  # box-sizing / margin / a / button / img / input
    │   └── base.css                   # html/body 排版、tabbar/sidebar 断点变量、focus-visible、scrollbar
    │
    ├── types/
    │   ├── api.ts                     # ApiResponse / PageResponse / ApiError / ErrorCode 联合
    │   ├── models.ts                  # User / Family / Member / Baby / Growth / Milestone / Album / Photo / Tag
    │   ├── timeline.ts                # TimelineItem / TimelineGroup / TimelinePage
    │   └── upload.ts                  # UploadTask / UploadStatus
    │
    ├── utils/
    │   ├── fileUrl.ts                 # fileUrl(key) → `/api/files/${key}?token=...`
    │   ├── format.ts                  # formatDate / formatDateTime / formatAge / formatBytes / highlight / monthLabel
    │   ├── validate.ts                # isAllowedImage / isUnsupportedImage / 字段校验规则常量
    │   ├── debounce.ts
    │   ├── image.ts                   # createObjectURL 生命周期管理
    │   └── clipboard.ts
    │
    └── composables/
        ├── useLightbox.ts
        ├── useInfiniteScroll.ts
        ├── useUploadQueue.ts
        ├── useDragDrop.ts
        ├── useToast.ts
        ├── useFocusTrap.ts
        └── useBreakpoint.ts
```

### 9.2 路由表

| path | name | 组件（懒加载） | `meta` | 说明 |
| --- | --- | --- | --- | --- |
| `/login` | `login` | `() => import('@/views/LoginView.vue')` | `{ requiresAuth: false, layout: 'blank', title: '登录' }` | 未登录访问任意页的落点 |
| `/` | — | `redirect: '/timeline'` | — | — |
| `/timeline` | `timeline` | `TimelineView.vue` | `{ requiresAuth: true, layout: 'app', title: '时间轴' }` | 首页 |
| `/albums` | `albums` | `AlbumsView.vue` | `{ requiresAuth: true, layout: 'app', title: '相册' }` | — |
| `/albums/:id` | `album-detail` | `AlbumDetailView.vue` | `{ requiresAuth: true, layout: 'app', title: '相册详情', props: true }` | `props: true` 让 `:id` 以 prop 传入 |
| `/growth` | `growth` | `GrowthView.vue` | `{ requiresAuth: true, layout: 'app', title: '成长记录' }` | — |
| `/baby/:id` | `baby` | `BabyView.vue` | `{ requiresAuth: true, layout: 'app', title: '宝宝档案', props: true }` | 二级 |
| `/family` | `family` | `FamilyView.vue` | `{ requiresAuth: true, layout: 'app', title: '家庭成员' }` | — |
| `/search` | `search` | `SearchView.vue` | `{ requiresAuth: true, layout: 'app', title: '搜索' }` | 支持 `?q=` |
| `/settings` | `settings` | `SettingsView.vue` | `{ requiresAuth: true, layout: 'app', title: '设置' }` | 用 `?tab=account\|family\|data\|about` |
| `/:pathMatch(.*)*` | `not-found` | `NotFoundView.vue` | `{ requiresAuth: false, layout: 'blank' }` | 404 |

**全局前置守卫**

```
router.beforeEach((to) => {
  const auth = useAuthStore()
  auth.restore()                                    // 幂等：从 localStorage 恢复
  if (to.meta.requiresAuth && !auth.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.token) {
    return { path: '/timeline' }
  }
})
router.afterEach((to) => { document.title = `${to.meta.title ?? ''} · 蜜记` })
```

**滚动行为**：`scrollBehavior: (to, from, saved) => saved ?? { top: 0 }`；时间轴切页返回时由 `photoStore` 的内存分页恢复（不依赖 `savedPosition`，因为滚动容器是 `<main>` 而非 window，需在 `TimelineView` 内手动 `scrollTop` 恢复）。

### 9.3 Pinia store 划分

| store | 状态 | 关键 action | 是否持久化 |
| --- | --- | --- | --- |
| `auth` | `token`、`user`、`familyId`、`role`、`bootstrapped` | `login`、`register`、`logout`、`fetchMe`、`updateProfile`、`changePassword`，`restore()`（读 localStorage，仅取 token，user 由 `/auth/me` 拉） | **token** → localStorage；其余内存 |
| `family` | `family`、`members`、`inviteCode`、`babies`、`activeBabyId` | `fetchFamily`、`updateFamily`、`fetchMembers`、`removeMember`、`resetInviteCode`、`fetchBabies`、`createBaby`、`updateBaby`、`deleteBaby`、`setActiveBaby` | `activeBabyId` → localStorage |
| `album` | `items`、`unclassified`、`loading`、`currentId` | `fetchList`、`create`、`update`、`remove(id, strategy)`、`setCover` | 否 |
| `photo` | `items`(当前上下文照片)、`page`、`hasMore`、`loading`、`selection:Set<number>`、`lightbox: {open, index, list}` | `loadPhotos(query)`、`loadMore`、`prepend(list)`、`patch(id, dto)`、`remove(ids)`、`move(ids, albumId)`、`toggleSelect`、`clearSelection`、`openLightbox(index)`、`closeLightbox` | 否 |
| `ui-toast` | `list: Toast[]`（≤3） | `push({type, message, action?})`、`success`、`warning`、`danger`、`info`、`dismiss(id)` | 否 |

> **store 之间禁止循环引用**：`album` 不 import `photo`；`AlbumDetailView` 负责协调（`albumStore.setCover` 成功后调 `photoStore.patch`）。

### 9.4 axios 封装（`src/api/http.ts`）

| 关注点 | 实现 |
| --- | --- |
| 实例 | `axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api', timeout: 30000 })` |
| 请求拦截 | 从 `localStorage['bee.token']` 读 token → `config.headers.Authorization = 'Bearer ' + token`；`FormData` 请求**手动删除默认 Content-Type**（让浏览器补 boundary） |
| 响应拦截（成功） | `res.data.code === 0` → `return res.data.data`（**统一拆掉 `code/message/traceId`**，业务代码只拿 `data`）；否则 `throw new ApiError(code, message, traceId)` |
| 响应拦截（HTTP 错误） | `error.response` 存在 → 归一化为 `ApiError(status, code, message, traceId)`；`error.response` 不存在（网络/超时）→ `ApiError(-1, '网络异常，请检查连接')` |
| 401 处理 | `code` 在 `[40100, 40200)` → `authStore.logout()` + `router.replace({ path:'/login', query:{ redirect: router.currentRoute.value.fullPath } })` + `Toast.warning('登录已过期，请重新登录')`。**只在拦截器里做一次**，避免多请求重复跳转（用 `isRedirecting` 布尔去重） |
| 403 处理 | 拦截器不自动跳转，抛给组件；组件按 `code` 决定 Toast 文案 |
| 错误统一 toast | 由 **`useApi()` 组合式包装**（非拦截器），默认 `showToast: true`，业务可选择 `false` 自行处理（如表单 40001 回填字段） |
| 上传进度 | `photo.upload(file, albumId, onProgress)` 使用 `onUploadProgress` 计算 `loaded/total` |
| 取消 | `CancelToken` 由 `useUploadQueue` 持有，取消时 `abort()` 并把状态置 `canceled` |

```ts
// src/api/http.ts 对外签名（不写实现）
export class ApiError extends Error {
  constructor(public status: number, public code: number, message: string, public traceId?: string)
}
export interface ApiResponse<T> { code: number; message: string; data: T; traceId: string }
export interface PageResponse<T> { items: T[]; page: number; size: number; total: number; totalPages: number; hasMore: boolean }
export const http: AxiosInstance   // 已装配拦截器
```

### 9.5 设计系统落地

| 文件 | 内容 | 约束 |
| --- | --- | --- |
| `styles/tokens.css` | **原样复制 `02-ui-design.md` §2.14 的 `:root { ... }` 整块**（honey 10 档、warm 10 档、语义色、背景三层、文字、边框、字体、字号、间距、圆角、阴影、动效、布局）+ `@media (prefers-reduced-motion: reduce)` 块 + 末尾预留 `/* [data-theme="dark"] { } 预留，本期不实现 */` | **一字不改**。任何页面/组件**禁止硬编码 hex 色值**（唯一例外：`Lightbox` 图像区 `#1C1814`，`02` 文档 §4.11 已明确为「照片衬底」非主题色） |
| `styles/reset.css` | `*,*::before,*::after{box-sizing:border-box}`；`margin/padding` 归零；`img{display:block;max-width:100%}`；`button,input,select,textarea{font:inherit;color:inherit}`；`button{background:none;border:0;cursor:pointer}`；`a{color:inherit;text-decoration:none}`；`ul,ol{list-style:none}`；`html{-webkit-text-size-adjust:100%}` | 不引入 normalize.css（多 8KB，收益为零） |
| `styles/base.css` | `html,body{height:100%}`；`body{background:var(--bg-page);color:var(--text-primary);font-family:var(--font-sans);font-size:14px;line-height:22px;font-variant-numeric:tabular-nums;-webkit-font-smoothing:antialiased}`；页面底噪点（`body::before` 用内联 SVG data-uri，`opacity:.03`，`pointer-events:none`，`position:fixed;inset:0`）；`:focus-visible{outline:2px solid var(--honey-500);outline-offset:2px}`；自定义 `::-webkit-scrollbar`（宽 10px，thumb `--warm-300`，圆角 full）；`.sr-only`；断点变量（`--container-pad` / `--sidebar-w` 在 3 个媒体查询中重定义）；`.bee-container{max-width:var(--container-max);margin:0 auto;padding:0 var(--container-pad)}` | 全局唯一允许出现硬编码值的地方是噪点 SVG 的 `rgba(42,36,30,.03)` 与断点 px（媒体查询不能用 CSS 变量） |

**基础组件清单与 props 签名**

> 所有组件：`<script setup lang="ts">` + `defineProps<Props>()` + `defineEmits<...>()`；样式全部 `scoped`；**不依赖任何第三方 UI 库**；根元素带 `bee-` 前缀 class。

| # | 组件 | 文件 | Props（TS 签名） | Emits / Slots |
| --- | --- | --- | --- | --- |
| 1 | BeeButton | `base/BeeButton.vue` | `variant?: 'primary'\|'secondary'\|'ghost'\|'danger'`(=`'primary'`)；`size?: 'sm'\|'md'\|'lg'`(=`'md'`)；`loading?: boolean`；`disabled?: boolean`；`block?: boolean`；`iconOnly?: boolean`；`type?: 'button'\|'submit'`(=`'button'`) | `emit('click', e: MouseEvent)`；slots: `default`、`icon` |
| 2 | BeeIcon | `base/BeeIcon.vue` | `name: IconName`（联合类型：`'search'\|'upload'\|'plus'\|'edit'\|'trash'\|'close'\|'chevron-left'\|'chevron-right'\|'chevron-down'\|'check'\|'heart'\|'info'\|'more'\|'camera'\|'hex'\|'image-broken'\|'grid'\|'list'\|'menu'\|'settings'\|'logout'\|'copy'\|'refresh'\|'calendar'\|'map-pin'\|'user'\|'baby'`）；`size?: number`(=`18`)；`strokeWidth?: number`(=`1.5`) | — |
| 3 | BeeInput | `base/BeeInput.vue` | `modelValue: string \| number \| null`；`type?: 'text'\|'email'\|'password'\|'number'\|'date'\|'datetime-local'`；`label?: string`；`placeholder?: string`；`required?: boolean`；`error?: string`；`hint?: string`；`disabled?: boolean`；`icon?: IconName`；`maxlength?: number`；`rows?: number`（>0 → 渲染 textarea）；`autofocus?: boolean` | `emit('update:modelValue', v: string)`；`emit('blur')`；slots: `suffix` |
| 4 | BeeSelect | `base/BeeSelect.vue` | `modelValue: string \| number \| null`；`options: { label: string; value: string \| number; disabled?: boolean }[]`；`label?: string`；`placeholder?: string`；`error?: string`；`disabled?: boolean`；`searchable?: boolean`；`clearable?: boolean` | `emit('update:modelValue', v)`；`emit('search', q: string)` |
| 5 | BeeTag | `base/BeeTag.vue` | `variant?: 'default'\|'honey'\|'outline'\|'coral'`(=`'default'`)；`size?: 'sm'\|'md'`；`closable?: boolean`；`clickable?: boolean`；`icon?: IconName` | `emit('close')`；`emit('click')`；slot `default`（文本） |
| 6 | BeeCard | `base/BeeCard.vue` | `padding?: 'none'\|'sm'\|'md'\|'lg'`(=`'md'`)；`hoverable?: boolean`；`clickable?: boolean`；`bordered?: boolean` | `emit('click')`；slots: `default`、`header`、`footer` |
| 7 | BeeModal | `base/BeeModal.vue` | `open: boolean`；`title?: string`；`size?: 'sm'\|'md'\|'lg'`(=`'md'`)；`danger?: boolean`；`closeOnOverlay?: boolean`(=`true`)；`loading?: boolean`；`confirmText?: string`(=`'确定'`)；`cancelText?: string`(=`'取消'`)；`hideFooter?: boolean` | `emit('update:open', v: boolean)`；`emit('confirm')`；`emit('cancel')`；slots: `default`、`footer` |
| 8 | BeeDrawer | `base/BeeDrawer.vue` | `open: boolean`；`title?: string`；`width?: number`(=`400`)；`side?: 'right'\|'bottom'`(=`'right'`)；`loading?: boolean`；`confirmText?: string` | `emit('update:open', v)`；`emit('confirm')`；slots: `default`、`footer` |
| 9 | BeeToast | `base/BeeToast.vue` | `toast: { id: number; type: 'success'\|'warning'\|'danger'\|'info'; message: string; action?: { label: string; handler: () => void } }` | `emit('dismiss', id)` |
| 10 | BeeToastHost | `base/BeeToastHost.vue` | 无（内部读 `useToastStore`） | — |
| 11 | BeeSkeleton | `base/BeeSkeleton.vue` | `variant: 'block'\|'card'\|'grid'\|'timeline'`；`count?: number`(=`1`)；`columns?: number`；`height?: string`；`width?: string`；`radius?: string` | — |
| 12 | BeeEmpty | `base/BeeEmpty.vue` | `variant?: 'no-photo'\|'no-album'\|'no-result'\|'no-growth'\|'error'`；`title: string`；`description?: string`；`actionText?: string`；`compact?: boolean` | `emit('action')` |
| 13 | BeeAvatar | `base/BeeAvatar.vue` | `src?: string \| null`；`name: string`；`shape?: 'circle'\|'hex'`(=`'circle'`)；`size?: number`(=`40`)；`ring?: boolean` | — |
| 14 | BeePageHeader | `base/BeePageHeader.vue` | `title: string`；`count?: number \| null`；`description?: string` | slots: `actions`（右上操作区）、`default` |
| 15 | BeeHexBadge | `base/BeeHexBadge.vue` | `size?: number`(=`32`)；`variant?: 'honey'\|'coral'\|'outline'`(=`'honey'`)；`icon?: IconName` | slot `default`（图标或文字） |
| 16 | BeeTabs | `base/BeeTabs.vue` | `modelValue: string`；`items: { key: string; label: string; count?: number }[]`；`variant?: 'underline'\|'pill'\|'segment'`(=`'underline'`) | `emit('update:modelValue', key)` |
| 17 | BeePagination | `base/BeePagination.vue` | `page: number`（**0 基**）；`totalPages: number`；`maxButtons?: number`(=`7`) | `emit('update:page', p: number)` |
| 18 | BeeFileDrop | `base/BeeFileDrop.vue` | `accept?: string`(=`'image/jpeg,image/png,image/webp'`)；`multiple?: boolean`(=`true`)；`disabled?: boolean`；`overlayText?: string` | `emit('files', files: File[])`；slot `default`（触发区） |
| 19 | BeeImage | `base/BeeImage.vue` | `src?: string \| null`；`alt: string`；`ratio?: string`(=`'1/1'`)；`fit?: 'cover'\|'contain'`(=`'cover'`)；`lazy?: boolean`(=`true`)；`radius?: string`(=`'var(--r-sm)'`) | `emit('load')`；`emit('error')` |

> **BeeImage 内部职责**：`IntersectionObserver`（`rootMargin: '200px'`）延迟设 `src`；`--warm-100` 占位 + 呼吸动画；`onload` 后 300ms `opacity 0→1`；失败显示破损图标 + 可点击重试。**`alt` 必填**（PRD §10 可访问性）。

**业务组件清单**

| # | 组件 | 文件 | 核心 props | 说明 |
| --- | --- | --- | --- | --- |
| 1 | AlbumCard | `business/AlbumCard.vue` | `album: Album`；`editable?: boolean` | 4:3 封面、左上角六边形照片数徽章、hover 浮出编辑/删除（`@click.stop`）、整卡 `<RouterLink>` |
| 2 | AlbumFormModal | `business/AlbumFormModal.vue` | `open: boolean`；`album?: Album \| null` | 名称 + 描述；新建/编辑复用；`40905` 回填字段错误 |
| 3 | DeleteAlbumModal | `business/DeleteAlbumModal.vue` | `open: boolean`；`album: Album` | 单选 `move`/`delete`；二次确认；`delete` 需再输一次相册名？**不需要**（PRD 只要求选择策略 + 二次确认） |
| 4 | PhotoGrid | `business/PhotoGrid.vue` | `photos: Photo[]`；`selectionMode?: boolean`；`selected?: Set<number>`；`loadingMore?: boolean` | 3/4/5 列响应式 + gap 4/8/12；`content-visibility:auto`＞200 项；IntersectionObserver 哨兵 |
| 5 | PhotoCell | `business/PhotoCell.vue` | `photo: Photo`；`index: number`；`selectable?: boolean`；`selected?: boolean` | 1:1、圆角 6、hover 蒙层 + 勾选框、底部渐变标题；触发灯箱/选择 |
| 6 | Lightbox | `business/Lightbox.vue` | `photos: Photo[]`；`startIndex: number`；`open: boolean` | `role="dialog" aria-modal="true"`；`←/→/Esc/I`；焦点陷阱；缩略条；信息侧栏；移动端滑动；关闭后焦点回原单元 |
| 7 | TimelineGroup | `business/TimelineGroup.vue` | `group: TimelineGroup` | sticky 月份标题（`top: var(--topbar-h)`）、脊柱线、条目循环 |
| 8 | TimelineItem | `business/TimelineItem.vue` | `item: TimelineItem`；`index: number` | 三态渲染（照片 4:3 卡片 / 里程碑珊瑚竖条卡 / 成长记录行）；首屏 5 条 stagger 40ms |
| 9 | MilestoneCard | `business/MilestoneCard.vue` | `milestone: Milestone`；`editable?: boolean` | 六边形徽章 + 右侧 72×72 配图 |
| 10 | MilestoneModal | `business/MilestoneModal.vue` | `open: boolean`；`milestone?: Milestone \| null`；`babyId?: number` | 标题/日期/描述/选一张照片（打开简易选择器） |
| 11 | GrowthChart | `business/GrowthChart.vue` | `points: CurvePoint[]`；`range: '3m'\|'6m'\|'12m'\|'all'`；`loading?: boolean` | **ECharts 按需引入**；三线双轴；`resize` 重绘；空态坐标系 |
| 12 | MemberCard | `business/MemberCard.vue` | `member: Member`；`canRemove: boolean`；`isSelf: boolean` | 圆形头像 + 角色 Chip + 加入时间；`⋯` 菜单仅 owner 且非自己 |
| 13 | BabySwitcher | `business/BabySwitcher.vue` | `babies: Baby[]`；`modelValue: number`；`allowAdd?: boolean` | 胶囊切换器（`--bg-sunken` 容器，选中项 `--bg-elevated` + shadow） |
| 14 | UploadPanel | `business/UploadPanel.vue` | `albumId?: number \| null`；`open: boolean` | 右下角浮层（宽 360），汇总进度 + 队列（最多 4 条 + 折叠）、失败重试、后台继续、取消 |
| 15 | UploadQueue | `business/UploadQueue.vue` | `tasks: UploadTask[]`；`summary: { done: number; total: number; bytesPerSec: number }` | 单条进度条 3px、40×40 本地缩略图、失败态重试 |
| 16 | StatBar | `business/StatBar.vue` | `stats: Overview`；`compact?: boolean` | 4 等分概览条（照片/相册/里程碑/宝宝天），`--font-display` 32/700，`tabular-nums` |
| 17 | SearchGroup | `business/SearchGroup.vue` | `title: string`；`total: number`；`hasMore: boolean` | 分组标题 + 计数 + 「查看全部 →」+ slot |

### 9.6 类型定义与后端 DTO 一一对应

**强制规则**：`src/types/models.ts` 中每个 interface 的字段名、可空性、类型必须与后端 `dto/**` 的 record **逐字段对齐**；后端新增字段 → 前端同 PR 补类型；后端 `BigDecimal` → 前端 `number`；后端 `Long`/`BIGINT` → 前端 `number`（家庭内 id 远小于 `Number.MAX_SAFE_INTEGER`）。

```ts
// src/types/api.ts
export interface ApiResponse<T> { code: number; message: string; data: T; traceId: string }
export interface PageResponse<T> {
  items: T[]; page: number; size: number; total: number; totalPages: number; hasMore: boolean
}

// src/types/models.ts  —— 与后端 dto 一一对应
export type Role = 'owner' | 'member'
export type Gender = 'male' | 'female' | 'unknown'

export interface User {
  id: number; email: string; nickname: string
  avatarUrl: string | null; createdAt: string          // "yyyy-MM-dd HH:mm:ss"
}
export interface Family {
  id: number; name: string; ownerId: number; ownerNickname: string
  memberCount: number; memberLimit: number
  coverPhotoId: number | null
  inviteCode: string | null; inviteCodeExpiresAt: string | null
  createdAt: string
}
export interface Member {
  userId: number; nickname: string; avatarUrl: string | null; email: string
  role: Role; joinedAt: string; uploadedPhotoCount: number
}
export interface InviteCode { code: string; expiresAt: string; expired: boolean }

export interface Baby {
  id: number; name: string; gender: Gender | null
  birthday: string                                     // "yyyy-MM-dd"
  avatarUrl: string | null
  birthHeightCm: number | null; birthWeightKg: number | null
  ageDays: number; ageText: string
  milestoneCount: number
  latestGrowth: GrowthRecord | null
}
export interface BabyDetail extends Baby {
  latestMilestone: Milestone | null
  growthRecordCount: number
}
export interface GrowthRecord {
  id: number; babyId: number; babyName: string
  recordedAt: string                                   // "yyyy-MM-dd"
  heightCm: number | null; weightKg: number | null; headCm: number | null
  note: string | null
  createdBy: number; createdByName: string
  createdAt: string; updatedAt: string
}
export interface CurvePoint {
  recordedAt: string; ageDays: number
  heightCm: number | null; weightKg: number | null; headCm: number | null
}
export interface GrowthCurve {
  babyId: number; babyName: string; birthday: string
  range: '3m' | '6m' | '12m' | 'all'
  from: string | null; to: string | null
  points: CurvePoint[]
  latest: { recordedAt: string; heightCm: number | null; weightKg: number | null; headCm: number | null } | null
  delta: { heightCm: number | null; weightKg: number | null; headCm: number | null }
}
export interface Milestone {
  id: number; babyId: number | null; babyName: string | null
  title: string; description: string | null
  achievedAt: string; ageText: string | null
  photoId: number | null; photoThumbKey: string | null
  createdBy: number; createdByName: string; createdAt: string
}
export interface Album {
  id: number; name: string; description: string | null
  coverPhotoId: number | null; coverThumbKey: string | null
  photoCount: number; createdBy: number; createdByName: string
  createdAt: string; updatedAt: string; latestTakenAt: string | null
}
export interface AlbumList { items: Album[]; unclassified: Unclassified }
export interface Unclassified { count: number; coverPhotoId: number | null; coverThumbKey: string | null }
export interface AlbumDetail extends Album {
  takenFrom: string | null; takenTo: string | null; ownerEditable: boolean
}
export interface Photo {
  id: number; albumId: number | null; albumName: string | null
  title: string | null; description: string | null
  thumbKey: string | null; previewKey: string | null
  mimeType: string; fileSize: number
  width: number | null; height: number | null
  takenAt: string; locationName: string | null
  tags: string[]
  createdAt: string; uploadedBy: number; uploadedByName: string
}
export interface PhotoDetail extends Photo { storageKey: string; latitude: number | null; longitude: number | null }
export interface UploadFailure { fileName: string; code: number; message: string }
export interface PhotoUploadResult {
  batchId: string; successCount: number; failureCount: number
  successes: Photo[]; failures: UploadFailure[]
}
export interface Tag { id: number; name: string; photoCount: number; lastUsedAt: string | null }
export interface Overview {
  photoCount: number; albumCount: number; milestoneCount: number
  memberCount: number; babyCount: number; earliestBabyAgeDays: number | null
  monthPhotoCount: number; storageBytes: number; storageLimitBytes: number | null
}

// src/types/timeline.ts
export type TimelineItemType = 'photo' | 'milestone' | 'growth'
export interface TimelineItem {
  type: TimelineItemType; id: number; sortTime: string
  photo?: Photo; milestone?: Milestone; growth?: GrowthRecord
}
export interface TimelineGroup { month: string; label: string; items: TimelineItem[] }
export interface TimelinePage extends PageResponse<TimelineGroup> { groups: TimelineGroup[] }
```

---

## 10. 任务分解

### 10.1 环境前置（所有任务共用）

```bash
# 后端：必须先切到 JDK 17（系统默认是 25，直接用会导致编译失败）
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
mvn -v   # 确认显示 Java 17

# 前端
cd frontend && npm install
```

| 环境事实 | 值 |
| --- | --- |
| Node / npm | v22.22.2 / 10.9.7（**无 pnpm，一律 npm**） |
| Maven | 3.9.9 |
| JDK | 系统默认 25 → **必须 `export JAVA_HOME` 到 jdk-17**；`pom.xml` 必须写 `<maven.compiler.release>17</maven.compiler.release>` |
| MySQL | 8.4.7，`root/root`，库 `bee_family` 已存在且为空 |
| Git | `gaussli / gauss_li@163.com`，HTTPS 凭据已缓存，仓库 `https://github.com/gaussli/bee-family.git`（当前为空） |

### 10.2 任务总表（25 个）

| 编号 | 任务 | 层 | 依赖 | 优先级 | 可并行波次 |
| --- | --- | --- | --- | --- | --- |
| T01 | 后端基础设施与统一响应 | 后端 | — | P0 | 波次 1 |
| T02 | 数据层：Flyway DDL + 领域实体 + 枚举 | 后端 | T01 | P0 | 波次 2 |
| T03 | 数据层：JPA 仓储 + 时间轴原生查询 | 后端 | T02 | P0 | 波次 3 |
| T04 | 鉴权：JWT / Spring Security / Auth 接口 | 后端 | T03 | P0 | 波次 3 |
| T05 | 家庭空间与成员 | 后端 | T04 | P0 | 波次 4 |
| T06 | 宝宝档案 | 后端 | T04 | P0 | 波次 4 |
| T07 | 成长记录与成长曲线 | 后端 | T06 | P0 | 波次 5 |
| T08 | 里程碑 | 后端 | T06 | P0 | 波次 5 |
| T09 | 相册管理 | 后端 | T04 | P0 | 波次 4 |
| T10 | 存储与文件服务（Storage/Thumbnail/EXIF/校验/FileController） | 后端 | T04 | P0 | 波次 4 |
| T11 | 照片上传与浏览 | 后端 | T09, T10, T06 | P0 | 波次 5 |
| T12 | 时间轴聚合 | 后端 | T03, T07, T08, T11 | P0 | 波次 6 |
| T13 | 搜索 / 标签 / 统计 / 后端收尾 | 后端 | T03, T09, T11, T12 | P0 | 波次 7 |
| T14 | 前端脚手架 + 设计系统 | 前端 | — | P0 | 波次 1 |
| T15 | 基础组件 A（按钮/输入/选择/标签/卡片/图标/徽章） | 前端 | T14 | P0 | 波次 2 |
| T16 | 基础组件 B（浮层/骨架/空态/头像/标题/页签/分页） | 前端 | T14 | P0 | 波次 3 |
| T17 | 类型定义 + API 层 + axios 封装 | 前端 | T14 | P0 | 波次 2 |
| T18 | Pinia store + 工具函数 + 通用 composables | 前端 | T17 | P0 | 波次 3 |
| T19 | 布局与路由 | 前端 | T14, T16, T18 | P0 | 波次 4 |
| T20 | 登录页 | 前端 | T15, T17, T18, T19 | P0 | 波次 5 |
| T21 | 时间轴首页 | 前端 | T16, T18, T19 | P0 | 波次 5 |
| T22 | 相册列表与相册详情 | 前端 | T15, T16, T18, T19 | P0 | 波次 5 |
| T23 | 灯箱与上传 | 前端 | T22 | P0 | 波次 6 |
| T24 | 成长记录与宝宝档案 | 前端 | T16, T18, T19 | P0 | 波次 5 |
| T25 | 成员 / 搜索 / 设置 / 响应式收尾 / 前后端联调 | 前后端 | T13, T20–T24 | P0 | 波次 7 |

**并行说明**

- **波次 1 可并行**：T01（后端骨架）与 T14（前端脚手架）完全独立。
- **波次 2 可并行**：T02（后端数据层）与 T15、T17（前端组件/接口层）互不依赖。
- **波次 4 四路并行**：T05、T06、T09、T10 均只依赖 T04，可同时推进。
- **波次 5 三路并行**：T07、T08（依赖 T06）与 T11（依赖 T09+T10+T06）。
- **前端波次 5 四路并行**：T20、T21、T22、T24 均只依赖 T19。
- **关键路径**：T01 → T02 → T03 → T04 → T06 → T10 → T11 → T12 → T13 → T25。

### 10.3 任务详情

#### T01 · 后端基础设施与统一响应

| 项 | 内容 |
| --- | --- |
| 依赖 | — |
| 优先级 | P0 |
| **产出文件** | `backend/pom.xml`<br/>`backend/.gitignore`<br/>`backend/src/main/resources/application.yml`<br/>`backend/src/main/resources/application-local.yml.example`<br/>`backend/src/main/java/com/beefamily/album/BeeFamilyApplication.java`<br/>`backend/src/main/java/com/beefamily/album/common/ApiResponse.java`<br/>`backend/src/main/java/com/beefamily/album/common/PageResponse.java`<br/>`backend/src/main/java/com/beefamily/album/common/PageQuery.java`<br/>`backend/src/main/java/com/beefamily/album/common/ErrorCode.java`<br/>`backend/src/main/java/com/beefamily/album/common/BusinessException.java`<br/>`backend/src/main/java/com/beefamily/album/common/GlobalExceptionHandler.java`<br/>`backend/src/main/java/com/beefamily/album/common/TraceIdFilter.java`<br/>`backend/src/main/java/com/beefamily/album/config/JacksonConfig.java`<br/>`backend/src/main/java/com/beefamily/album/config/WebMvcConfig.java`<br/>`.gitignore`（根） |
| 说明 | 本任务为「基础设施」，文件数超 8 属预期（等同前端 T01 规则：配置 + 入口 + 依赖 + 通用契约一次性到位，任何拆分都会造成半成品无法编译）。 |
| **验收标准** | ① `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home && cd backend && mvn -q -DskipTests compile` 成功；② `mvn -q dependency:tree \| grep -c jjwt` ≥ 1 且显示 0.12.x；③ `grep -rn "password\|5555" backend/src/main/resources/application.yml` **无任何真实口令**，仅 `${DB_PASSWORD}`；④ `git status --porcelain` 中不出现 `application-local.yml`；⑤ `mvn spring-boot:run -Dspring-boot.run.profiles=local` 启动时 Flyway 报「no migrations found」但不崩溃（T02 后改为成功建表）；⑥ 造一个 `/api/__ping` 临时端点（可注释掉的测试类）返回 `{"code":0,"message":"OK","data":null,"traceId":"<16hex>"}`，且响应头含 `X-Trace-Id`；`curl` 触发 `INVALID_PARAM` 得到 code=40001 且 `data` 为 null |
| 关键约束 | ① `pom.xml` 必须含 `<maven.compiler.release>17</maven.compiler.release>` 与 `spring-boot-starter-parent 3.3.x`；② `application.yml` 里 `spring.jpa.hibernate.ddl-auto: validate`（**绝不 update**）；③ `ErrorCode` 必须一次落地 §5.2 的**全部 25 个**枚举（`OK` + 24 个错误码，含 `40908 CONFIRM_MISMATCH`）（后续任务只允许新增，不允许改已有编号）；④ `GlobalExceptionHandler` 必须聚合 `@Valid` 多字段错误 |
| 涉及 P0 | P0-01～P0-12（全量基础） |

#### T02 · 数据层：Flyway DDL + 领域实体 + 枚举

| 项 | 内容 |
| --- | --- |
| 依赖 | T01 |
| 优先级 | P0 |
| **产出文件** | `backend/src/main/resources/db/migration/V1__init.sql`（原样落地 §4.4）<br/>`backend/src/main/java/com/beefamily/album/domain/User.java`<br/>`.../domain/Family.java`<br/>`.../domain/FamilyMember.java`<br/>`.../domain/Baby.java`<br/>`.../domain/GrowthRecord.java`<br/>`.../domain/Milestone.java`<br/>`.../domain/Album.java`<br/>`.../domain/Photo.java`<br/>`.../domain/Tag.java`<br/>`.../domain/PhotoTag.java`<br/>`.../domain/enums/Role.java`<br/>`.../domain/enums/Gender.java`<br/>`.../domain/enums/MediaType.java` |
| **验收标准** | ① `mvn -q spring-boot:run -Dspring-boot.run.profiles=local` 启动成功，MySQL 中出现 10 张表 + `flyway_schema_history`；② `mysql -uroot -proot bee_family -e "SHOW CREATE TABLE photo\G"` 可见 `idx_photo_family_taken`、`ck_photo_file_size`、`fk_photo_album ... ON DELETE SET NULL`；③ `SHOW INDEX FROM family_member` 同时含 `uk_member_family_user` 与 `uk_member_user`；④ `ddl-auto: validate` 下启动**零 WARN**（证明实体与 DDL 完全一致）；⑤ 手工 `INSERT` 两条同 `(family_id,user_id)` 的 `family_member` → 报 `Duplicate entry`；⑥ 同一 `user_id` 插两个不同 family → 报 `Duplicate entry` |
| 关键约束 | ① **不得**修改 §4.4 的任何列名/类型/索引名；② `PhotoTag` 用 `@EmbeddedId`（`photo_id`+`tag_id` 联合主键）；③ 实体**不加任何 Jackson 注解**，`LocalDateTime`/`LocalDate` 直接用 `java.time` 类型；④ `photo.latitude/longitude` 用 `BigDecimal`；⑤ `content_hash`/`media_type`/`latitude`/`longitude` 虽是 P1/P2 预留也必须映射（`ddl-auto: validate` 会校验全部列） |
| 涉及 P0 | P0-01～P0-12 |

#### T03 · 数据层：JPA 仓储 + 时间轴原生查询

| 项 | 内容 |
| --- | --- |
| 依赖 | T02 |
| 优先级 | P0 |
| **产出文件** | `.../repository/UserRepository.java`<br/>`.../repository/FamilyRepository.java`<br/>`.../repository/FamilyMemberRepository.java`<br/>`.../repository/BabyRepository.java`<br/>`.../repository/GrowthRecordRepository.java`<br/>`.../repository/MilestoneRepository.java`<br/>`.../repository/AlbumRepository.java`<br/>`.../repository/PhotoRepository.java`<br/>`.../repository/TagRepository.java`<br/>`.../repository/PhotoTagRepository.java`<br/>`.../repository/TimelineQueryRepository.java` |
| **必须包含的方法签名** | `UserRepository`: `findByEmailIgnoreCase`、`existsByEmailIgnoreCase`、`count()`<br/>`FamilyMemberRepository`: `findByUserId(Long)`、`findByFamilyIdOrderByJoinedAt(Long)`、`countByFamilyId(Long)`、`existsByFamilyIdAndUserId(Long,Long)`、`findRoleByFamilyIdAndUserId`<br/>`BabyRepository`: `findByFamilyIdOrderById(Long)`、`findByIdAndFamilyId(Long,Long)`、`countByFamilyId(Long)`<br/>`GrowthRecordRepository`: `findByIdAndBabyId`、`findByBabyIdOrderByRecordedAtDesc(Long, Pageable)`、`findByBabyIdAndRecordedAtBetweenOrderByRecordedAtAsc`、`findByBabyIdOrderByRecordedAtAsc`、`findFirstByBabyIdOrderByRecordedAtDesc`、`countByBabyId`<br/>`MilestoneRepository`: `findByFamilyIdOrderByAchievedAtDescIdDesc(Long, Pageable)`、`findByBabyIdOrderByAchievedAtDesc`、`findByIdAndFamilyId`、`countByFamilyId`、`countByBabyId`<br/>`AlbumRepository`: `findByFamilyIdOrderByUpdatedAtDesc`、`findByIdAndFamilyId`、`existsByFamilyIdAndName`、`findByFamilyIdAndName`、`findFirstByFamilyIdAndCoverPhotoId`<br/>`PhotoRepository`: `findByIdAndFamilyId`、`findByFamilyIdAndAlbumIdOrderBy…`（Pageable + Sort）、`findByFamilyIdAndAlbumIdIsNullOrderBy…`、`findByIdInAndFamilyId`、`countByFamilyId`、`countByFamilyIdAndCreatedAtGreaterThanEqual`、`findTopByFamilyIdAndAlbumIdOrderByTakenAtDesc`、`findTopByFamilyIdAndAlbumIdIsNullOrderByTakenAtDesc`、`sumFileSizeByFamilyId`(@Query)<br/>`TagRepository`: `findByFamilyIdAndName`、`findByFamilyIdAndNameStartingWithOrderByName`、`findByFamilyIdOrderByIdDesc`(@Query 带 photo_count + last_used_at)<br/>`PhotoTagRepository`: `deleteByPhotoIdIn`、`findByPhotoIdIn`<br/>`TimelineQueryRepository`: `countAll(Long familyId)`、`fetchKeys(Long familyId, int offset, int limit)`（`@Query(nativeQuery = true)`，SQL 见 §5.7） |
| **验收标准** | ① `mvn -q -DskipTests compile` 通过；② 启动后 Hibernate 不报 `QueryCreationException`；③ 在 `bee_family` 里手工灌 5 张 photo + 3 个 milestone + 2 条 growth_record，用 `@DataJpaTest`/临时 `CommandLineRunner` 调 `fetchKeys(1L,0,10)`，返回 10 条且按 `sort_time DESC, item_type ASC, item_id DESC` 严格有序；④ `fetchKeys` 的 `EXPLAIN` 三个分支均命中索引（不允许出现 `Using temporary` 之外的 `Using filesort` 报错，也不允许 `type: ALL` 在 `photo` 分支上） |
| 关键约束 | ① **所有** repository 方法名带 `familyId` 或从属父实体，禁止裸 `findById`；② `TimelineQueryRepository` 必须是项目中**唯一**的 `nativeQuery = true`（除统计 sum 外）；③ SQL 中的 `CAST(... AS DATETIME)`、整数 `type_ord`（photo=0/milestone=1/growth=2）与 `ORDER BY t.sort_time DESC, t.type_ord ASC, t.item_id DESC` 三个键缺一不可；**禁止**用 `item_type` 字符串当排序键（字典序与意图相反） |
| 涉及 P0 | P0-02, P0-03, P0-04, P0-05, P0-06, P0-08, P0-10, P0-12 |

#### T04 · 鉴权：JWT / Spring Security / Auth 接口

| 项 | 内容 |
| --- | --- |
| 依赖 | T03 |
| 优先级 | P0 |
| **产出文件** | `.../config/JwtProperties.java`<br/>`.../config/SecurityConfig.java`<br/>`.../security/JwtService.java`<br/>`.../security/JwtAuthenticationFilter.java`<br/>`.../security/UserPrincipal.java`<br/>`.../security/SecurityUtils.java`<br/>`.../security/FamilyAccessGuard.java`<br/>`.../service/AuthService.java`<br/>`.../controller/AuthController.java`<br/>`.../mapper/UserMapper.java`<br/>`.../mapper/FamilyMapper.java`<br/>`.../dto/auth/{RegisterReq,LoginReq,TokenResp,MeResp,UpdateProfileReq,ChangePasswordReq}.java`<br/>`.../dto/family/{FamilyResp,MemberResp}.java`<br/>`.../dto/baby/BabyResp.java` |
| **验收标准** | ① `mvn -q -DskipTests compile`；② 首个用户注册：`curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' -d '{"email":"mom@home.local","password":"123456","nickname":"妈妈"}'` 返回 `code:0` 且 `data.token` 非空、`data.role=="owner"`、`data.familyId` 非空；③ `mysql -e "SELECT name,invite_code,invite_code_expires_at FROM family"` 有值且 `expires_at ≈ now()+24h`、`invite_code` 长度 8；④ 第二个用户不带邀请码注册 → `40902`；带错误邀请码 → `40902`；带正确邀请码 → `code:0` + `role=="member"`；⑤ 错误密码登录 → `40101` 且 message 不含「邮箱不存在」字样；⑥ `curl -H "Authorization: Bearer <token>" localhost:8080/api/auth/me` → `code:0`；⑦ 无 token 访问 `/api/auth/me` → `40100`；⑧ 伪造签名 token → `40100`；⑨ 用过期 token（把 ttl 临时改 1s）→ `40103`；⑩ `grep -c "password" backend/src/main/java/.../AuthService.java` 中**无日志打印密码**；⑪ 用 A 家庭 token 访问 B 家庭的 `/api/family`（手工改库让两用户在不同家庭）→ 40301（此时 `/api/family` 尚未实现，改为验证 `FamilyAccessGuard.assertSameFamily(999L)` 抛 40301 的单测/临时端点） |
| 关键约束 | ① JWT claims **仅** `sub/email/iat/exp`，**不得**含 `familyId`（ADR-02）；② `SecurityConfig` 放行清单必须与 §7.3 表格**逐行一致**；③ `JwtAuthenticationFilter` 中 `?token=` 支持必须限定 `request.getRequestURI().startsWith("/api/files/")`；④ 注册事务必须同一 `@Transactional` 内完成 user + family + member + inviteCode；⑤ 捕获 `ExpiredJwtException` 单独映射 40103；⑥ **禁止**在 `UserPrincipal` 中放实体对象（只放标量） |
| 涉及 P0 | P0-01（全部验收 ①–④）、P0-02（② 邀请码/成员角色） |

#### T05 · 家庭空间与成员

| 项 | 内容 |
| --- | --- |
| 依赖 | T04 |
| 优先级 | P0 |
| **产出文件** | `.../service/FamilyService.java`<br/>`.../controller/FamilyController.java`<br/>`.../mapper/FamilyMapper.java`（补充）<br/>`.../dto/family/UpdateFamilyReq.java`<br/>`.../dto/family/InviteCodeResp.java`<br/>`.../dto/family/DeleteFamilyReq.java`<br/>`.../common/ErrorCode.java`（仅在 T01 漏写时补 `CONFIRM_MISMATCH`；按约定 T01 已一次性声明，本任务应无需改动） |
| **验收标准** | ① `mvn -q compile`；② `GET /api/family` 返回 `memberCount`/`memberLimit:6`；③ owner `PATCH /api/family {"name":"许家小院"}` → 200；member 同名请求 → `40302`；④ `POST /api/family/invite-code/reset` 返回新 8 位码、`expiresAt` 刷新；旧码立即失效（用旧码注册 → 40902）；⑤ `DELETE /api/family/members/{ownerUserId}` → `40906`；`DELETE` 自己（owner 情况下）→ `40906`；owner 删 member → 200 且照片保留（`SELECT COUNT(*) FROM photo WHERE uploaded_by=<被删 user>` 不变）；⑥ member 调用 `DELETE /api/family/members/{x}` → `40302`；⑦ 第 7 个成员用邀请码注册 → `40903`；⑧ `DELETE /api/family {"confirmName":"错误名"}` → `40908`；正确名 → 200 且 10 张表相关行全清、`uploads/{familyId}` 目录被删（`ls` 无此目录） |
| 关键约束 | ① 单家庭 ≤6 人校验在**注册**路径（T04 的 `AuthService`）与**邀请码校验**处双重落地，T05 只补「成员数查询接口」；② `DELETE /api/family` 必须按 §6.4 注的 **9 步显式顺序**删除，不依赖 CASCADE；③ 移除成员工序：先 `DELETE family_member`，**不**删除该用户上传的照片，**不**删除 user（该用户下次登录会因无 family 得到 40300）；④ 邀请码生成用 `SecureRandom`，字符集去掉易混字符（`0/O/1/I/L`） |
| 涉及 P0 | P0-02 |

#### T06 · 宝宝档案

| 项 | 内容 |
| --- | --- |
| 依赖 | T04 |
| 优先级 | P0 |
| **产出文件** | `.../service/BabyService.java`<br/>`.../controller/BabyController.java`<br/>`.../mapper/BabyMapper.java`<br/>`.../dto/baby/BabyReq.java`<br/>`.../dto/baby/BabyUpdateReq.java`<br/>`.../dto/baby/BabyDetailResp.java`<br/>`.../support/DateTimes.java` |
| **验收标准** | ① `mvn -q compile`；② `POST /api/babies {"name":"团团","gender":"female","birthday":"2025-08-18"}` → `code:0`，响应含 `ageDays`（今日−生日，用 `LocalDate.now()` 与生日差）与 `ageText`（如 `1岁2个月4天`）；③ `birthday` 传明天 → `40001` 且 message 含「生日不能是未来日期」；④ 建第 4 个宝宝 → `40904`；⑤ `GET /api/babies/{id}` 返回 `latestGrowth`（无记录时 null）；⑥ 用别的家庭 token → `40301`；⑦ member 调 `DELETE /api/babies/{id}` → `40302`；owner 调 → 200 且该宝宝的 `growth_record`/`milestone` 被级联清理（`SELECT COUNT(*)` 归零，milestone 的 `baby_id` 变 NULL 若该 milestone 是家庭级） |
| 关键约束 | ① `ageText` 中文格式固定：`X岁Y个月Z天`，`X=0` 时省略「X岁」，`Y=0` 时省略「Y个月」，天数保留；② 所有「今天」必须用 `LocalDate.now(ZoneId.of("Asia/Shanghai"))`，**禁止** `new Date()`；③ `birthday` 校验用 `@PastOrPresent`，错误 message 由 `GlobalExceptionHandler` 聚合 |
| 涉及 P0 | P0-03 |

#### T07 · 成长记录与成长曲线

| 项 | 内容 |
| --- | --- |
| 依赖 | T06 |
| 优先级 | P0 |
| **产出文件** | `.../service/GrowthService.java`<br/>`.../controller/GrowthController.java`<br/>`.../mapper/GrowthMapper.java`<br/>`.../dto/growth/GrowthRecordReq.java`<br/>`.../dto/growth/GrowthRecordResp.java`<br/>`.../dto/growth/GrowthCurveResp.java`<br/>`.../dto/growth/CurvePointResp.java`<br/>`.../dto/growth/GrowthDeltaResp.java` |
| **验收标准** | ① `mvn -q compile`；② 连续 `POST /api/babies/{id}/growth-records` 同一天两次（身高 78.0 → 78.5）→ 两次均 200，`SELECT COUNT(*) FROM growth_record WHERE baby_id=?` **= 1**，且值为 78.5，第二次返回的 `id` 与第一次相同（UPSERT 验证）；③ `heightCm: 200` → `40001`；`weightKg: 0.5` → `40001`；`recordedAt` 为明天 → `40001`；三项全空 → `40001`；④ `GET /api/babies/{id}/growth-curve?range=12m` 的 `points` 按 `recordedAt` **升序**，`delta` = 末项−倒数第二项；⑤ 只有 1 条记录时 `delta` 三项为 **null** 且 `code:0`；`range=abc` → `40002`；⑥ `PATCH`/`DELETE /api/growth-records/{id}` 用别的家庭 token → `40301` |
| 关键约束 | ① POST 必须实现 UPSERT（同 `(babyId, recordedAt)` 覆盖），**不要**返回 409；② `range` 语义：从**该宝宝最新一条记录的日期**往前推 N 个自然月（不是从今天），`all` 取全部并按升序；③ `BigDecimal` 直接映射，**禁止** `stripTrailingZeros`（会破坏 scale，见 §11.3）；④ `GrowthRecordResp` 必须含 `babyName`（时间轴与前端列表需要，避免二次查询） |
| 涉及 P0 | P0-04 |

#### T08 · 里程碑

| 项 | 内容 |
| --- | --- |
| 依赖 | T06 |
| 优先级 | P0 |
| **产出文件** | `.../service/MilestoneService.java`<br/>`.../controller/MilestoneController.java`<br/>`.../mapper/MilestoneMapper.java`<br/>`.../dto/milestone/MilestoneReq.java`<br/>`.../dto/milestone/MilestoneResp.java` |
| **验收标准** | ① `mvn -q compile`；② `POST /api/milestones {"title":"第一次翻身","achievedAt":"2026-06-01","photoId":<本家庭照片>}` → 200，响应的 `photoThumbKey` 非空、`ageText` 非空（babyId 有值时）；③ `photoId` 指向别的家庭照片 → `40301`；④ `babyId` 指向别的家庭宝宝 → `40301`；⑤ `GET /api/milestones?babyId=&page=0&size=20` 按 `achievedAt` 倒序；⑥ 删除被里程碑引用的照片后 `SELECT photo_id FROM milestone` 为 **NULL**（验证 `ON DELETE SET NULL`）；⑦ 编辑/删除均可用，且 `countByFamilyId` 与 `SELECT COUNT(*)` 一致（P0-05④ 统计口径） |
| 关键约束 | ① `family_id` 由 `UserPrincipal.familyId` 注入，**不接收前端传参**；② `babyId` 为 null 时 `ageText` 为 null；③ `title` 1–60、`description` ≤500 |
| 涉及 P0 | P0-05 |

#### T09 · 相册管理

| 项 | 内容 |
| --- | --- |
| 依赖 | T04 |
| 优先级 | P0 |
| **产出文件** | `.../service/AlbumService.java`<br/>`.../controller/AlbumController.java`<br/>`.../mapper/AlbumMapper.java`<br/>`.../dto/album/AlbumReq.java`<br/>`.../dto/album/AlbumUpdateReq.java`<br/>`.../dto/album/AlbumResp.java`<br/>`.../dto/album/AlbumListResp.java`<br/>`.../dto/album/AlbumDetailResp.java`<br/>`.../dto/album/DeleteAlbumResp.java` |
| **验收标准** | ① `mvn -q compile`；② `POST /api/albums {"name":"青岛海边"}` → 200；同名再建 → `40905`；③ `GET /api/albums` 返回 `items` 按 `updatedAt` 倒序，且 `unclassified` 含 `count` 与最新未分类照片的 `coverThumbKey`；④ 无照片相册的 `coverPhotoId`/`coverThumbKey` 为 null；有照片但未设封面时 `coverThumbKey` 取相册内 `takenAt` 最新的照片（P0-06③）；⑤ `PATCH` 设置 `coverPhotoId` 为该相册内照片 → 200；设为别的家庭照片 → `40301`；⑥ member 编辑**别人创建的**相册 → `40303`；owner 编辑 → 200；⑦ 有 48 张照片的相册 `DELETE ?photoStrategy=move` → 响应 `movedPhotos:48`，`SELECT COUNT(*) FROM photo WHERE album_id IS NULL AND family_id=?` 增加 48，磁盘文件数不变；⑧ 同样相册 `?photoStrategy=delete` → `deletedPhotos:48`，`photo` 行消失，`photo_tag` 消失，`uploads/.../xxx*` 文件在事务后消失；⑨ `?photoStrategy=xxx` → `40002` |
| 关键约束 | ① `photo_count` 冗余计数由 `PhotoService` 在同事务内维护（`+1`/`-1`/`+N`/`-N`），`AlbumService` 只读；② 删除策略落地必须与 §6.4 时序图一致（`afterCommit` 删文件）；③ 相册名 `1–40` 字、去首尾空格后比较唯一性 |
| 涉及 P0 | P0-06 |

#### T10 · 存储与文件服务

| 项 | 内容 |
| --- | --- |
| 依赖 | T04 |
| 优先级 | P0 |
| **产出文件** | `.../config/StorageProperties.java`<br/>`.../support/ImageType.java`<br/>`.../support/ImageValidator.java`<br/>`.../service/StorageService.java`<br/>`.../service/ThumbnailService.java`<br/>`.../service/ExifService.java`<br/>`.../controller/FileController.java` |
| **验收标准** | ① `mvn -q compile`；② 用真 JPEG 调 `ImageValidator.sniff()` 返回 `IMAGE_JPEG`；`.png` 改名为 `.jpg` 上传 → 识别为 PNG 并按 PNG 规则命名（嗅探优先于后缀）；③ 造一个扩展名 `.jpg` 但内容是 `<svg onload=alert(1)>` 的文件 → `UNSUPPORTED_MEDIA_TYPE`；④ `.heic`（ftyp heic）→ `UNSUPPORTED_MEDIA_TYPE` 且 message 含「iPhone」与「兼容性最佳」；⑤ `StorageService.allocateKey(1L, 2026-11-05, jpg)` 返回形如 `1/2026/11/<32hex>.jpg`，**不含任何用户输入**；⑥ 手工把 key 传为 `../../etc/passwd` 调 `FileController` → `40301`（路径穿越防护）；⑦ `GET /api/files/1/2026/11/<key>_thumb.jpg`（Header token）返回 `200 image/jpeg`，`Range: bytes=0-99` → `206`；不带 token → `40100`；带 `?token=` → `200`；⑧ 一张 4032×3024 的 JPEG 生成的 `_thumb.jpg` 体积 **≤ 80KB** 且最长边 = 480，`_preview.jpg` 最长边 = 1600；⑨ `ExifService.readTakenAt()` 对含 EXIF 的样例返回正确 `DateTimeOriginal`，对无 EXIF 的图返回 `Optional.empty()`；⑩ 1 亿像素的伪造头图 → 拒绝且不 OOM |
| 关键约束 | ① `ImageValidator` 是**白名单**（只认三种魔数），其余一律拒；② `FileController` 用 `@GetMapping("/api/files/{*key}")`，必须做 `startsWith(root.toRealPath())` 校验；③ 目录创建用 `Files.createDirectories`（幂等）；④ `deleteQuietly` 不抛异常；⑤ 缩略图/预览图后缀由 `ImageType` 决定，PNG 保持 `.png`；⑥ 图片炸弹阈值 `width*height <= 100_000_000` |
| 涉及 P0 | P0-07（缩略图）、P0-08（图片访问） |

#### T11 · 照片上传与浏览

| 项 | 内容 |
| --- | --- |
| 依赖 | T09, T10, T06 |
| 优先级 | P0 |
| **产出文件** | `.../service/PhotoService.java`<br/>`.../service/PhotoIngestService.java`<br/>`.../service/TagService.java`<br/>`.../controller/PhotoController.java`<br/>`.../mapper/PhotoMapper.java`<br/>`.../dto/photo/PhotoResp.java`<br/>`.../dto/photo/PhotoDetailResp.java`<br/>`.../dto/photo/PhotoUpdateReq.java`<br/>`.../dto/photo/PhotoUploadResp.java`<br/>`.../dto/photo/UploadFailure.java`<br/>`.../dto/photo/BatchDeleteReq.java`<br/>`.../dto/photo/BatchMoveReq.java`<br/>`.../dto/photo/BatchResultResp.java`<br/>`.../dto/search/TagResp.java` |
| **验收标准** | ① `mvn -q compile`；② `curl -F "files=@a.jpg" -F "albumId=12" -H "Authorization: Bearer ..." localhost:8080/api/photos/upload` → `code:0`，`successes[0]` 含 `thumbKey`/`previewKey`/`takenAt`（EXIF 值）；磁盘上 3 个文件存在且大小合理；③ 混合上传 3 个文件（1 正常 + 1 超 20MB + 1 个 .heic）→ `successCount:1, failureCount:2`，`failures` 分别 `41301`/`41501`；**正常那张成功入库**（验证逐张独立事务）；④ 101 个文件 → `41302`；⑤ `albumId` 指向别的家庭 → `40301`；⑥ `GET /api/photos?albumId=12&page=0&size=60&sort=takenAt_desc` 按 `takenAt` 倒序（同 `takenAt` 时按 `id` 倒序）；`unclassified=true` 只返 `album_id IS NULL`；`sort=bogus` → `40002`；⑦ 上传后 `SELECT photo_count FROM album WHERE id=12` 自增正确；⑧ `PATCH /api/photos/{id} {"tags":["海边","第一次"]}` → 新标签自动建 `tag` 行并写 `photo_tag`；再 `{"tags":["海边"]}` → `photo_tag` 只剩 1 条且 `tag` 表不留孤儿（供后续清理）；⑨ `PATCH` 把 `albumId` 改为别的家庭相册 → `40907`；⑩ `batch-move` 移动 8 张到另一相册 → 两相册 `photo_count` 同步 ±8；⑪ `batch-delete` 8 张 → 200，DB 与磁盘 3×8 文件均消失；⑫ 上传**不得**打印密码/token 到日志 |
| 关键约束 | ① **`PhotoService` 必须注入 `PhotoIngestService` 而非 `this.` 调用**（否则 `@Transactional` 失效，见 §3.5）；② 单张失败只追加 `failures` 不抛异常；③ DB 失败必须补偿删 3 个文件；④ `photo_count` 维护必须在同一事务；⑤ 标签差分算法：`新增 = req - existing`、`删除关联 = existing - req`，`tag` 行**只增不删**（P1 可加清理）；⑥ `sort` 白名单三值 |
| 涉及 P0 | P0-07, P0-08, P0-09 |

#### T12 · 时间轴聚合

| 项 | 内容 |
| --- | --- |
| 依赖 | T03, T07, T08, T11 |
| 优先级 | P0 |
| **产出文件** | `.../service/TimelineService.java`<br/>`.../controller/TimelineController.java`<br/>`.../dto/timeline/TimelineItemResp.java`<br/>`.../dto/timeline/TimelineGroupResp.java`<br/>`.../dto/timeline/TimelinePageResp.java` |
| **验收标准** | ① `mvn -q compile`；② 造数据：同一月份 30 张照片 + 3 个里程碑 + 2 条成长记录，`GET /api/timeline?page=0&size=24` 返回 `groups` 内 items **严格按 `sortTime` 倒序**、同 `sortTime` 时 `type` 序为 photo(0)→milestone(1)→growth(2)；③ `page=0` 与 `page=1` 的 items **无重复 id 且并集 = 前 48 条**（翻页正确性验证，最核心用例）；④ `groups[].month` 格式 `yyyy-MM`、`label` 格式 `2026 年 10 月`（`label` 由后端生成，前端不自拼）；⑤ `total` = 三源总数之和；`hasMore` 在最后一页为 `false`；⑥ 空家庭（无任何数据）→ `groups: []`、`total: 0`、`hasMore: false`、`code: 0`（**不报错**）；⑦ 用 A 家庭 token 造 B 家庭数据 → A 的 timeline 完全看不到 B 的（`total` 不包含）；⑧ 响应时间：5000 张照片时 `page=0` 的 P95 ≤ 300ms（用 `curl -w '%{time_total}'` 循环 100 次取 P95） |
| 关键约束 | ① 必须复用 `TimelineQueryRepository.fetchKeys`（不在 service 里再拼 SQL）；② `hasMore` 用 `size+1` 探测，**不用** `total` 推导；③ items 重建**必须按 keys 顺序**（§6.3）；④ 明细查询 3 条 `IN` + 1 条标签 `IN`，禁止在循环里 `findById`（N+1）；⑤ `TimelineItemResp` 三选一字段为 null 其余，**不裁剪 null** |
| 涉及 P0 | P0-10, P0-05（②）、P0-04（时间轴含成长记录） |

#### T13 · 搜索 / 标签 / 统计 / 后端收尾

| 项 | 内容 |
| --- | --- |
| 依赖 | T03, T09, T11, T12 |
| 优先级 | P0 |
| **产出文件** | `.../service/SearchService.java`<br/>`.../service/StatisticsService.java`<br/>`.../controller/SearchController.java`<br/>`.../controller/TagController.java`<br/>`.../controller/StatisticsController.java`<br/>`.../mapper/StatisticsMapper.java`<br/>`.../dto/search/SearchResp.java`<br/>`.../dto/search/SearchGroupResp.java`<br/>`.../dto/stats/OverviewResp.java` |
| **验收标准** | ① `mvn -q compile && mvn -q -DskipTests package`；② `GET /api/search?q=海边` 返回 `photos`/`albums`/`milestones` 三组，各 `total` + `items`（≤20）+ `hasMore`；照片命中标题/描述/标签，相册命中名称，里程碑命中标题/描述；③ `q=`（空）→ `40001`；④ 命中关键词在 `items` 中保持原样返回（**高亮由前端做**，后端不做）；⑤ `GET /api/tags?q=海` 前缀匹配，返回 `name`/`photoCount`/`lastUsedAt`；`q` 为空 → 按 `lastUsedAt DESC` 返回前 20；⑥ `GET /api/stats/overview` 的 6 个数字与 SQL 手算一致：`photoCount`、`albumCount`、`milestoneCount`、`memberCount`、`earliestBabyAgeDays`（最早生日的宝宝的天数）、`monthPhotoCount`（`created_at >= 本月1日 00:00:00`）；⑦ 造一个 5000 张照片的家庭，`GET /api/search?q=海边` P95 ≤ 300ms 或用 `EXPLAIN` 证明只扫 `family_id` 范围；⑧ **全量冒烟**：按 §5.3 的 44 个接口逐个 `curl`，全部返回预期 code（401/403/200），无一 500 |
| 关键约束 | ① 搜索 `LIKE '%q%'` 必须强制 `family_id` 前缀过滤；② 统计必须实时聚合（**禁止** `@Cacheable`，与 P0-12① 一致）；③ 搜索 `q` 长度 ≤50，防止恶意长串拖慢；④ 三组「查看全部」由前端调 `GET /api/photos?q=`（**本期不实现**，`SearchGroupResp.hasMore` 时前端跳 `/search?q=&tab=photo` 并用同一接口分页 —— 简化：`type` 参数即为分组过滤） |
| 涉及 P0 | P0-11, P0-12, P0-09（标签搜索） |

#### T14 · 前端脚手架 + 设计系统

| 项 | 内容 |
| --- | --- |
| 依赖 | — |
| 优先级 | P0 |
| **产出文件** | `frontend/package.json`<br/>`frontend/vite.config.ts`<br/>`frontend/tsconfig.json`<br/>`frontend/tsconfig.node.json`<br/>`frontend/env.d.ts`<br/>`frontend/eslint.config.js`<br/>`frontend/index.html`<br/>`frontend/.env.development`<br/>`frontend/.env.production`<br/>`frontend/src/main.ts`<br/>`frontend/src/App.vue`<br/>`frontend/src/styles/tokens.css`<br/>`frontend/src/styles/reset.css`<br/>`frontend/src/styles/base.css` |
| **验收标准** | ① `cd frontend && npm install && npm run type-check` 零 error；② `npm run build` 成功，产物 < 500KB gzip（不含 ECharts）；③ 浏览器访问 `:5173` 显示暖纸白底 `#FBF7F1`、无控制台报错；④ `grep -rn "#E89A2B" src/components src/views` **无结果**（所有色值只出现在 `tokens.css`）；⑤ `grep -c -- "--honey-500" src/styles/tokens.css` = 1；⑥ `VITE_API_BASE_URL=/api` 生效，`npm run dev` 时 `curl localhost:5173/api/stats/overview` 被代理到 `:8080` 并返回 401（后端已起）；⑦ `prefers-reduced-motion` 模拟下动效为 0.01ms |
| 关键约束 | ① `tokens.css` 必须**逐字复制** `02-ui-design.md` §2.14；② `vite.config.ts` 的 `resolve.alias['@'] = fileURLToPath(new URL('./src', import.meta.url))`，`tsconfig` 的 `paths` 必须与之同步；③ `server.proxy['/api'] = { target: 'http://localhost:8080', changeOrigin: true }`；④ 依赖**恰好**：`vue`、`vue-router`、`pinia`、`axios`、`echarts`、`dayjs`?（**不加 dayjs**，用原生 `Intl`/手写格式化，见 §11.2）；devDeps：`vite`、`@vitejs/plugin-vue`、`typescript`、`vue-tsc`、`eslint` 及插件；⑤ **禁止**出现 `element-plus` / `ant-design-vue` / `naive-ui` / `tailwindcss` / `sass` |
| 涉及 P0 | P0-01～P0-12（全量基础） |

#### T15 · 基础组件 A

| 项 | 内容 |
| --- | --- |
| 依赖 | T14 |
| 优先级 | P0 |
| **产出文件** | `frontend/src/components/base/BeeIcon.vue`<br/>`.../base/BeeButton.vue`<br/>`.../base/BeeInput.vue`<br/>`.../base/BeeSelect.vue`<br/>`.../base/BeeTag.vue`<br/>`.../base/BeeCard.vue`<br/>`.../base/BeeHexBadge.vue`<br/>`frontend/src/utils/icons.ts` |
| **验收标准** | ① `npm run type-check` 零 error；② 在 `App.vue` 临时挂一个 `/_demo` 路由（或独立的 `Playground.vue`，最终删除）逐个渲染 4 变体 × 3 尺寸按钮、error 态输入框、下拉展开、3 种标签、卡片 hover、六边形徽章，**与 `02-ui-design.md` §4.4–4.8 规格逐项对齐**（高度 32/40/48、圆角 6/10、`--shadow-focus` 聚焦环、`disabled` opacity .45）；③ `grep -rn "#[0-9A-Fa-f]\{6\}" src/components/base` 无结果；④ 键盘 Tab 可聚焦全部交互元素且 `focus-visible` 可见；⑤ `BeeButton loading` 时 `pointer-events:none` 且图标变旋转环 |
| 关键约束 | ① 全部 `<script setup lang="ts">` + `defineProps<Props>()`，props 签名**必须与 §9.5 表格一致**；② `BeeIcon` 用内联 `<svg>` + `currentColor`，`stroke-width` 走 prop；③ `BeeInput` 在 `rows>0` 时渲染 `<textarea>`；④ 禁止 Emoji 当图标 |
| 涉及 P0 | P0-01（表单）、P0-06（卡片）、P0-09（标签） |

#### T16 · 基础组件 B

| 项 | 内容 |
| --- | --- |
| 依赖 | T14 |
| 优先级 | P0 |
| **产出文件** | `frontend/src/components/base/BeeModal.vue`<br/>`.../base/BeeDrawer.vue`<br/>`.../base/BeeToast.vue`<br/>`.../base/BeeToastHost.vue`<br/>`.../base/BeeSkeleton.vue`<br/>`.../base/BeeEmpty.vue`<br/>`.../base/BeeAvatar.vue`<br/>`.../base/BeePageHeader.vue`<br/>`.../base/BeeTabs.vue`<br/>`.../base/BeePagination.vue` |
| **验收标准** | ① `npm run type-check` 零 error；② Modal：`Esc` 关闭、点遮罩关闭（`closeOnOverlay=false` 时不关）、打开后焦点进入首个可聚焦元素、`Tab` 在框内循环、关闭后焦点回原元素；③ Drawer 从右滑入 320ms；④ Toast 在右上/右下按 UI 规格定位，3.5s 自动消失，同时最多 3 条；⑤ 4 种 `BeeSkeleton` 变体与真实内容布局同尺寸（切换不跳动）；⑥ `BeeEmpty` 5 个变体 + 六边形底纹插画；⑦ `BeeAvatar` `shape="hex"` 有 2px `--honey-200` 双层描边；⑧ `BeeTabs` 3 变体、`BeePagination` 当前页 `--honey-500` 白字 |
| 关键约束 | ① 浮层必须 `Teleport to="body"` 并使用 `z-index`：Modal/Drawer 1000、Toast 1100、Lightbox 1200；② 焦点陷阱统一用 `composables/useFocusTrap.ts`（T18 提供，本任务可先内联，T18 完成后替换）；③ 所有浮层 `role` + `aria-modal` / `aria-live` 按 `02` 文档 §6.5「键盘可达性」要求 |
| 涉及 P0 | P0-06（删除确认）、P0-08（骨架）、P0-09（抽屉） |

#### T17 · 类型定义 + API 层 + axios 封装

| 项 | 内容 |
| --- | --- |
| 依赖 | T14 |
| 优先级 | P0 |
| **产出文件** | `frontend/src/types/api.ts`<br/>`.../types/models.ts`<br/>`.../types/timeline.ts`<br/>`.../types/upload.ts`<br/>`frontend/src/api/http.ts`<br/>`.../api/index.ts`<br/>`.../api/auth.ts`<br/>`.../api/family.ts`<br/>`.../api/baby.ts`<br/>`.../api/growth.ts`<br/>`.../api/milestone.ts`<br/>`.../api/album.ts`<br/>`.../api/photo.ts`<br/>`.../api/timeline.ts`<br/>`.../api/search.ts`<br/>`.../api/stats.ts`<br/>`.../api/file.ts` |
| **验收标准** | ① `npm run type-check` 零 error；② 每个后端接口在 `api/*.ts` 中都有**一个**函数且返回类型精确（不出现 `any`）：`grep -c "any" src/api` = 0（`unknown` 允许）；③ `http` 拦截器 `code!==0` 时抛 `ApiError`，用 mock 验证 `40001` 抛出后 `err.code === 40001`；④ 401 时只执行一次跳转（并发 3 个请求只跳一次）；⑤ **类型字段数与后端 record 字段数一致**（逐 DTO 对照 §5 与 §9.6 手工核验，在 PR 描述里贴核对表）；⑥ `fileUrl('1/2026/11/x_thumb.jpg')` 返回 `/api/files/1/2026/11/x_thumb.jpg?token=<token>`；token 为空时返回不带 `token=` 的路径 |
| 关键约束 | ① `models.ts` 字段名**必须**与后端 DTO 完全一致（`camelCase`，无 mapping 层）；② 可空性必须精确（后端返回 null 的字段一律 `T \| null`，不用 `?`）；③ **禁止**在 api 层做任何业务判断；④ `fileUrl` 只用于 `<img>`，其余请求一律走 `http`（走 header） |
| 涉及 P0 | P0-01～P0-12（全量） |

#### T18 · Pinia store + 工具函数 + 通用 composables

| 项 | 内容 |
| --- | --- |
| 依赖 | T17 |
| 优先级 | P0 |
| **产出文件** | `frontend/src/stores/auth.ts`<br/>`.../stores/family.ts`<br/>`.../stores/album.ts`<br/>`.../stores/photo.ts`<br/>`.../stores/ui-toast.ts`<br/>`.../stores/index.ts`<br/>`frontend/src/utils/fileUrl.ts`<br/>`.../utils/format.ts`<br/>`.../utils/validate.ts`<br/>`.../utils/debounce.ts`<br/>`.../utils/image.ts`<br/>`.../utils/clipboard.ts`<br/>`frontend/src/composables/useToast.ts`<br/>`.../composables/useFocusTrap.ts`<br/>`.../composables/useBreakpoint.ts`<br/>`.../composables/useInfiniteScroll.ts`<br/>`.../composables/useDragDrop.ts` |
| **验收标准** | ① `npm run type-check` 零 error；② `auth.restore()` 在无 token 时不报错、有 token 时只恢复 token 不恢复 user；③ `auth.logout()` 后 `localStorage` 中 `bee.token` 与 `bee.activeBabyId` 均被清；④ `format.ts` 单测（可用 `// @ts-expect-error` + 手写断言脚本）：`formatDateTime('2026-10-24 15:32:10')` → `2026-10-24 15:32`；`formatAge('2025-08-18')` → `1岁2个月4天`；`formatBytes(4823110)` → `4.6 MB`；`monthLabel('2026-10')` → `2026 年 10 月`；⑤ `highlight('海边日落','海')` → `'<mark>海</mark>边日落'`，且 `highlight('<img onerror=x>海','海')` **转义**后的 `<img>` 不可执行（XSS 用例必过）；⑥ `validate.isAllowedImage` 对 `.JPG/.png/.webp` true，对 `.heic/.svg/.exe` false 且 `isUnsupportedImage` 对 heic 返回 true；⑦ `useInfiniteScroll` 在元素进入视口 400px 前触发且只触发一次（`loading` 期间不重复）；⑧ `useToast` 连推 4 条时第 1 条被挤掉 |
| 关键约束 | ① 时间格式化**不引入 dayjs**，用 `Intl.DateTimeFormat('zh-CN', {timeZone:'Asia/Shanghai'})` 或字符串切片（后端已给 `yyyy-MM-dd HH:mm:ss`，切片即可）；② `stores` 之间不互相 import（除 `auth` 被他人读 token）；③ `useToast` 必须在 `main.ts` 挂载 `BeeToastHost` 后才可用（store 独立，host 只读）；④ `utils/image.ts` 必须在 `onUnmounted` 释放所有 `createObjectURL` |
| 涉及 P0 | P0-01（auth）、P0-10（无限滚动）、P0-07（validate）、P0-11（debounce） |

#### T19 · 布局与路由

| 项 | 内容 |
| --- | --- |
| 依赖 | T14, T16, T18 |
| 优先级 | P0 |
| **产出文件** | `frontend/src/router/index.ts`<br/>`frontend/src/layouts/AppShell.vue`<br/>`.../layouts/SideNav.vue`<br/>`.../layouts/TopBar.vue`<br/>`.../layouts/TabBar.vue` |
| **验收标准** | ① `npm run type-check` 零 error；② 路由表与 §9.2 **逐行一致**（含 `meta.requiresAuth`、`props:true`、404 兜底）；③ 未登录访问 `/albums` → 跳 `/login?redirect=%2Falbums`，登录后回跳（T20 完成后验证）；④ 已登录访问 `/login` → 跳 `/timeline`；⑤ ≥1024 侧栏 240px 展开；768–1023 收为 72px 仅图标 + hover 浮出 tooltip；<768 侧栏变抽屉 + 底部 TabBar 56px（含 `env(safe-area-inset-bottom)`）；⑥ 顶栏 `position: sticky` + `backdrop-filter: blur(12px)` + `rgba(251,247,241,.86)`；⑦ `/` 与 `/` 或任意空值都会到 `/timeline`；⑧ 5 个一级导航的 active 态为 `--honey-100` 底 + `--text-brand` 600 + 左侧 3px 竖条；⑨ 375px 宽度下**无横向滚动条**（`document.documentElement.scrollWidth <= 375`） |
| 关键约束 | ① 所有 view 必须**懒加载** `() => import(...)`；② `<main>` 是滚动容器（`height:100vh; overflow-y:auto`），不是 window；③ `AppShell` 通过 `<slot name="topbar-actions">` 暴露给页面注入按钮（如时间轴的「上传照片」）；④ 布局断点只在 CSS 媒体查询里判断，不用 JS（除抽屉开合的 `useBreakpoint`） |
| 涉及 P0 | P0-01（守卫）、P0-10～P0-12（页面容器） |

#### T20 · 登录页

| 项 | 内容 |
| --- | --- |
| 依赖 | T15, T17, T18, T19 |
| 优先级 | P0 |
| **产出文件** | `frontend/src/views/LoginView.vue`<br/>`frontend/src/components/business/HexPattern.vue`<br/>`frontend/src/composables/useAuthForm.ts` |
| **验收标准** | ① `npm run type-check` 零 error；② 左右分栏（左 55% `--honey-50`、右 45% `--bg-page`），<1024 隐藏左栏改顶部居中 Logo（高 120px）；③ Tab 切换登录/注册，字段随 Tab 增减（注册多「昵称」必填 +「邀请码」选填）；④ 首次部署（`user` 表为空）注册后**不填邀请码**即成功并 `role=owner`，跳 `/timeline`；⑤ 已有用户时不填邀请码注册 → 表单下方内联红字「邀请码无效或已过期」（走 `40902`），**不弹 Toast**（PRD §7.3.1）；⑥ 密码错误 → 内联红字「邮箱或密码错误」（`40101`）；⑦ 按钮 loading 时文字保留 + 16px 旋转环 + 不可点；⑧ 登录成功带 `redirect` 参数时回跳原页；⑨ 首次登录且 `babies` 为空 → 跳 `/family` 并自动打开「新增宝宝」；⑩ 输入框聚焦环 `--shadow-focus`；⑪ 蜂巢装饰透明度 8%、仅右下角；⑫ 所有错误文案走 `aria-describedby` 关联到输入框 |
| 关键约束 | ① 表单校验规则：邮箱格式、密码 6–32、昵称 1–32、邀请码 8 位字母数字；② 错误只能内联，**禁止** Toast（除网络异常）；③ 左栏文案固定为「把一家人的时光，收成一册暖光相册。」，`--font-display` 40/48 |
| 涉及 P0 | P0-01, P0-02（邀请码注册） |

#### T21 · 时间轴首页

| 项 | 内容 |
| --- | --- |
| 依赖 | T16, T18, T19 |
| 优先级 | P0 |
| **产出文件** | `frontend/src/views/TimelineView.vue`<br/>`frontend/src/components/business/StatBar.vue`<br/>`.../business/TimelineGroup.vue`<br/>`.../business/TimelineItem.vue` |
| **验收标准** | ① `npm run type-check` 零 error；② 首屏先出 3 条时间轴骨架（左灰圆点 + 右 16:10 灰块呼吸），数据到达后替换（PRD P0-10①）；③ 概览条 4 等分（照片/相册/里程碑/宝宝天），数字 `--font-display` 32/700 + `tabular-nums`；④ 月份标题组标题 `2026 年 10 月`，`position: sticky; top: 64px`，滚动时吸顶不抖；⑤ 时间脊柱竖线 `left: 79px`，照片条目圆点 10×10 `2px --honey-500`，里程碑圆点为 12×14 珊瑚六边形；⑥ 照片条目 4:3 裁切卡片 + 底部标题/地点；里程碑条目左侧 3px 珊瑚竖条 + 六边形徽章 + 右侧 72×72 配图；成长条目为一行摘要（身高/体重/头围）；⑦ 滚动到底前 400px 自动 `loadPage(page+1)`，加载中显示 3 点跳动，末尾显示「没有更多了 · 共 1248 张」；⑧ 空态：六边形插画 + 「还没有记忆，上传第一批照片」+ 主按钮跳 `/albums`；⑨ 错误态：顶部内联错误条 + 「重试」，不阻塞导航；⑩ 首屏 5 条 stagger 40ms 淡入；⑪ 点击照片打开灯箱（T23 完成后验证）、点击里程碑跳 `/growth`；⑫ 从其他页返回时滚动位置恢复 |
| 关键约束 | ① 一次 24 条（`size=24`）；② **前端不重排、不重新分组**，`groups` 直接用后端结果（ADR-05）；③ 每次 `loadPage` 必须传当前 `page`，且请求期间禁用哨兵（防重复）；④ `<main>` 上的 scroll 事件用 `IntersectionObserver` 而非 `scroll` 监听（性能） |
| 涉及 P0 | P0-10, P0-12（概览条）, P0-05（里程碑入时间轴） |

#### T22 · 相册列表与相册详情

| 项 | 内容 |
| --- | --- |
| 依赖 | T15, T16, T18, T19 |
| 优先级 | P0 |
| **产出文件** | `frontend/src/views/AlbumsView.vue`<br/>`.../views/AlbumDetailView.vue`<br/>`frontend/src/components/business/AlbumCard.vue`<br/>`.../business/AlbumFormModal.vue`<br/>`.../business/DeleteAlbumModal.vue`<br/>`.../business/PhotoGrid.vue`<br/>`.../business/PhotoCell.vue` |
| **验收标准** | ① `npm run type-check` 零 error；② 相册网格 1/2/3/4 列（640/768/1024/1280），gap 24；卡宽约 268、封面 4:3；③ 卡片 hover：封面 `scale(1.04)` + 卡片 `-2px` + `--shadow-md` + 右上浮出编辑/删除（`@click.stop` 不触发跳转）；④ 照片数徽章在封面左上角，`rgba(42,36,30,.55)` + `blur(6px)` + 六边形小图标；⑤ 无照片相册显示 `--honey-50` 底 + 40px 六边形线框 + 「还没有照片」；⑥ 「未分类」特殊卡展示且**无编辑/删除**入口；⑦ 新建/编辑 Modal：名称 1–40 字、同名 → 字段下方红字「同名相册已存在」；⑧ 删除 Modal 必选 `move`/`delete`（默认 `move`），删除后 Toast + 列表原地移除；⑨ 详情页头部：面包屑、96×96 圆角 14 封面、名称 `--fs-h2`、`48 张照片`、日期范围、描述 2 行省略；⑩ 操作条 `sticky top:64px` 高 48，含上传/选择/视图切换/排序；⑪ 照片网格 5/4/3 列（1280/768/<768）、gap 12/8/4、单元 1:1 圆角 6、懒加载 + `opacity 0→1` 300ms、失败可重试；⑫ 超过 200 项时 `content-visibility:auto`；⑬ 选择模式：左上勾选框、选中 `inset 0 0 0 2px --honey-500`、底部浮动条（已选 N 张 / 取消 / 移动到相册 / 删除）；⑭ 拖动文件到页面任意位置显示全屏虚线遮罩「松开即可上传到《相册名》」；⑮ 375px 无横向滚动 |
| 关键约束 | ① 相册列表用 `GET /api/albums`（**不**调 photos）；② 详情页照片分页 `size=60`；③ 空态/骨架/错误态三者齐全（骨架 4 卡 / 12 格）；④ 视图切换（网格/列表）本期只做网格，列表按钮 `disabled` + tooltip「即将支持」 |
| 涉及 P0 | P0-06, P0-08 |

#### T23 · 灯箱与上传

| 项 | 内容 |
| --- | --- |
| 依赖 | T22 |
| 优先级 | P0 |
| **产出文件** | `frontend/src/components/business/Lightbox.vue`<br/>`.../business/UploadPanel.vue`<br/>`.../business/UploadQueue.vue`<br/>`frontend/src/components/base/BeeFileDrop.vue`<br/>`.../base/BeeImage.vue`<br/>`frontend/src/composables/useLightbox.ts`<br/>`.../composables/useUploadQueue.ts` |
| **验收标准** | ① `npm run type-check` 零 error；② 灯箱 `role="dialog" aria-modal="true" aria-label="照片查看器"`，`z-index` 1200，遮罩 `--overlay-scrim` + `blur(8px)`；③ `←`/`→` 切换、`Esc` 关闭、`I` 切信息栏、点遮罩关闭；④ 打开时焦点到关闭按钮，`Tab` 在灯箱内循环，关闭后焦点**回到触发的网格单元**；⑤ 主图用 `previewKey`（1600px），加载中显示 `thumbKey` `blur(12px)` 垫底 + 32px 进度环；失败显示「图片加载失败」+ 重试；⑥ 底部缩略条 88px、64×64、当前项 `0 0 0 2px #E89A2B`、自动滚动居中；⑦ 右上序号「12 / 48」；信息栏宽 320 展示标题/描述/拍摄时间/地点/上传者/相册/标签；⑧ 移动端（<768）全屏无圆角、箭头隐藏、支持左右滑动、信息栏变底部抽屉；⑨ 上传：预校验拦截超 20MB 与 heic，聚合为 1 条 warning Toast（如「3 个文件超过 20MB、2 个为 HEIC，已跳过」）；⑩ 队列 3 并发、单条 3px 进度条、汇总「已完成 32 / 100」、失败项单独重试 +「全部重试」；⑪ 上传成功后**原地插入**网格与时间轴（不整页刷新），Toast「已上传 98 张照片」；⑫ 取消上传弹确认「取消会中断正在上传的 8 张」；⑬ 可收起为右下角 40×40 迷你进度环；⑭ 100 张上传过程中浏览器内存不爆（`createObjectURL` 在用完后释放） |
| 关键约束 | ① 上传**逐张请求**、并发 3（ADR-05 实现细节），服务端仍支持多文件；② `BeeImage` 的 `src` 必须来自 `fileUrl(key)`，禁止拼接 `thumbKey` 后缀；③ 灯箱关闭时必须 `revokeObjectURL`（上传队列）与恢复焦点（灯箱）；④ `<img alt>`：有标题用标题，否则 `拍摄于 YYYY-MM-DD 的照片` |
| 涉及 P0 | P0-07, P0-08 |

#### T24 · 成长记录与宝宝档案

| 项 | 内容 |
| --- | --- |
| 依赖 | T16, T18, T19 |
| 优先级 | P0 |
| **产出文件** | `frontend/src/views/GrowthView.vue`<br/>`.../views/BabyView.vue`<br/>`frontend/src/components/business/GrowthChart.vue`<br/>`.../business/BabySwitcher.vue`<br/>`.../business/MilestoneCard.vue`<br/>`.../business/MilestoneModal.vue` |
| **验收标准** | ① `npm run type-check` 零 error；② ECharts **按需引入**（`echarts/core` + `LineChart` + `GridComponent/TooltipComponent/LegendComponent` + `CanvasRenderer`），`npm run build` 后 ECharts chunk ≤ 220KB gzip；③ 曲线三线双轴：身高 `#E89A2B` / 体重 `#D97757` / 头围 `#7D9471`；`smooth: 0.35`、`symbolSize: 6`、`lineWidth: 2.5`；X 轴 `type:'time'`、`YY/MM`；Y 左 cm、右 kg；`splitLine` 虚线 `#F0E8DC`；④ 图例可开关单条，激活单条时该条显示渐变 `areaStyle`；⑤ 分段控件 3月/6月/12月/全部，切换后重新请求并 300ms 动画重绘；⑥ 空态：空坐标系 +「还没有测量记录」+ 主按钮；⑦ `resize` 监听重绘；<640 隐藏右轴；⑧ 指标卡 3 等分（身高/体重/头围）+ 环比（上升 `--color-success`，下降 `--text-tertiary`），`tabular-nums`；⑨ 新增记录抽屉（400px，<768 全屏）：日期/身高/体重/头围/备注，前端拦截 30–150 / 1–60 / 20–70 与未来日期，保存后曲线 300ms 重绘 + 列表刷新；⑩ 记录列表倒序、hover 出编辑/删除、删除二次确认；⑪ 里程碑区按时间倒序，卡片含六边形徽章 + 日期 + `ageText` + 可点配图；⑫ 宝宝切换胶囊切换后成长记录与曲线联动刷新；⑬ 宝宝档案页：六边形头像 96×111、`已出生 443 天` 蜜金 14/500、出生身高/体重/里程碑数三列、编辑态同页切换表单（不跳转）；⑭ 无头像时六边形内首字占位（`--honey-100` 底 `--honey-700` 字） |
| 关键约束 | ① **禁止** `import * as echarts from 'echarts'`；② 曲线数据 `points` 已由后端升序，前端不再排序；③ 指标卡数值 `toFixed(1)` / `toFixed(2)`（体重 2 位）；④ `ageText` 用后端返回值，前端不重算 |
| 涉及 P0 | P0-04, P0-03, P0-05 |

#### T25 · 成员 / 搜索 / 设置 / 响应式收尾 / 前后端联调

| 项 | 内容 |
| --- | --- |
| 依赖 | T13, T20, T21, T22, T23, T24 |
| 优先级 | P0 |
| **产出文件** | `frontend/src/views/FamilyView.vue`<br/>`.../views/SearchView.vue`<br/>`.../views/SettingsView.vue`<br/>`.../views/NotFoundView.vue`<br/>`frontend/src/components/business/MemberCard.vue`<br/>`.../business/SearchGroup.vue`<br/>`README.md`（根，启动与配置说明）<br/>`docs/api-smoke.sh`（44 接口冒烟脚本） |
| **验收标准** | ① `npm run type-check && npm run lint && npm run build` 全部零 error；② 成员页：家庭名 + `4 / 6 人`、成员行（圆形头像 36 + 昵称 + 角色 Chip + 加入时间）、`owner`→`honey` Chip 文字「创建者」、`member`→`default` Chip 文字「成员」；③ 移除成员仅 owner 可见（且不能移除自己），确认文案「移除后 TA 上传的照片会保留在家庭中」；④ 邀请码 `--font-mono` 32/700 `letter-spacing:.2em` 蜜金居中、复制后 Toast、重置后立即刷新、`24 小时内有效` 提示；⑤ 满员 6/6 时邀请区置灰 + `--color-warning-bg` 提示条「已达 6 人上限」；⑥ 搜索页：搜索框自动聚焦、300ms 防抖、请求中显示旋转环、空关键词不请求、分「照片/相册/里程碑」三组（每组 ≤20 + 「查看全部」）、命中 `<mark>` 高亮（走安全转义）、无结果显示「没找到"xxx"」+ 建议、初始态显示最近标签与最近相册、`↑↓` + `Enter` 键盘操作；⑦ 设置页：左导航 200px（<1024 变顶部 Tab）、账号（昵称/邮箱/新密码 + 保存 Toast「已保存」）、数据区存储占用进度条 + 照片数、导出 P1 禁用 + tooltip、危险区需输入家庭名完全一致才可点「删除家庭」；⑧ 404 页可返回首页；⑨ **375px / 768px / 1024px / 1440px 四档无横向滚动、无元素重叠**（逐页用 DevTools 截图核对）；⑩ 全站键盘可达，`axe` 扫描无 critical；⑪ 100 张照片上传端到端 ≤ 3 分钟（PRD G1）；⑫ 首页 LCP ≤ 2s（Lighthouse，内网本地）；⑬ `docs/api-smoke.sh` 一次跑完 44 接口并输出 PASS/FAIL 汇总；⑭ 根 `README.md` 含：环境要求（JDK17 的 `JAVA_HOME` 命令）、`application-local.yml` 创建步骤、Flyway 自动建表说明、`npm run dev` 与 `mvn spring-boot:run` 启动顺序、测试账号 |
| 关键约束 | ① 删除家庭走 `DELETE /api/family` 且需 `confirmName` 完全匹配；② 搜索「查看全部」本期简化为在 `/search` 内切换 `type` 展示全部分页，不新建路由；③ `README.md` **必须**写明 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`，否则后续接手者必踩 JDK 25 的坑；④ 收尾阶段允许微调前面任务的样式细节，但**不得**修改 §5 的 API 契约与 §9.6 的类型定义 |
| 涉及 P0 | P0-02, P0-11, P0-01（账号设置）, P0-12（设置页概览）, 全量联调 |

### 10.4 任务依赖图

```mermaid
graph LR
    T01["T01 后端基础设施"] --> T02["T02 DDL+实体"]
    T02 --> T03["T03 仓储+原生查询"]
    T03 --> T04["T04 鉴权 JWT/Security"]
    T04 --> T05["T05 家庭与成员"]
    T04 --> T06["T06 宝宝档案"]
    T04 --> T09["T09 相册管理"]
    T04 --> T10["T10 存储与文件服务"]
    T06 --> T07["T07 成长记录与曲线"]
    T06 --> T08["T08 里程碑"]
    T09 --> T11["T11 照片上传与浏览"]
    T10 --> T11
    T06 --> T11
    T03 --> T12["T12 时间轴聚合"]
    T07 --> T12
    T08 --> T12
    T11 --> T12
    T12 --> T13["T13 搜索/标签/统计/收尾"]
    T09 --> T13
    T11 --> T13

    T14["T14 前端脚手架+设计系统"] --> T15["T15 基础组件 A"]
    T14 --> T16["T16 基础组件 B"]
    T14 --> T17["T17 类型+API+axios"]
    T17 --> T18["T18 store+utils+composables"]
    T14 --> T19["T19 布局与路由"]
    T16 --> T19
    T18 --> T19
    T15 --> T20["T20 登录页"]
    T17 --> T20
    T18 --> T20
    T19 --> T20
    T16 --> T21["T21 时间轴首页"]
    T18 --> T21
    T19 --> T21
    T15 --> T22["T22 相册列表+详情"]
    T16 --> T22
    T18 --> T22
    T19 --> T22
    T22 --> T23["T23 灯箱与上传"]
    T16 --> T24["T24 成长+宝宝档案"]
    T18 --> T24
    T19 --> T24

    T13 --> T25["T25 成员/搜索/设置/联调"]
    T20 --> T25
    T21 --> T25
    T22 --> T25
    T23 --> T25
    T24 --> T25

    subgraph W1["波次 1（可并行）"]
        T01
        T14
    end
    subgraph W4["波次 4（后端四路并行）"]
        T05
        T06
        T09
        T10
    end
    subgraph W5F["波次 5（前端四路并行）"]
        T20
        T21
        T22
        T24
    end
```

---

## 11. 共享约定（跨文件契约）

### 11.1 命名

| 类别 | 规则 | 示例 |
| --- | --- | --- |
| DB 列 | `snake_case` | `invite_code_expires_at`、`thumb_key` |
| Java 类 | `PascalCase`；实体无后缀，DTO 带 `Req`/`Resp`/`Query` 后缀，Mapper 带 `Mapper`，枚举无后缀 | `Photo`、`PhotoUpdateReq`、`PhotoResp`、`PhotoQuery`、`PhotoMapper`、`MediaType` |
| Java 方法 | `camelCase`，动词开头；repository 查询方法名由 Spring Data 派生 | `uploadPhotos`、`assertSameFamily`、`findByIdAndFamilyId` |
| Java 包 | 全小写，`com.beefamily.album.<layer>[.<module>]` | `com.beefamily.album.service`、`com.beefamily.album.dto.photo` |
| **DTO 后缀** | **请求体 `XxxReq`**、**响应体 `XxxResp`**、**查询参数 `XxxQuery`**、**批量结果 `XxxResult`** | `RegisterReq` / `TokenResp` / `PhotoQuery` / `BatchResultResp` |
| TS 类型 | `PascalCase` interface，**无 Dto/Resp 后缀**（与 JSON 直接同形） | `Photo`、`Album`、`GrowthCurve` |
| TS 变量/函数 | `camelCase` | `fileUrl`、`loadPhotos` |
| Vue 组件文件 | `PascalCase.vue`；基础组件 `Bee*`，业务组件无前缀；views 以 `View.vue` 结尾 | `BeeButton.vue`、`AlbumCard.vue`、`TimelineView.vue` |
| CSS class | `bee-<block>__<element>--<modifier>`（BEM 风格），全部 `scoped` | `.bee-album-card__cover`、`.bee-timeline__item--milestone` |
| **API 路径** | 全小写复数名词，`kebab-case` 多词，**无尾斜杠** | `/api/albums`、`/api/growth-records`、`/api/invite-code/reset` |
| **存储 key** | `{familyId}/{yyyy}/{MM}/{uuid}.{ext}`，`/` 分隔，**无前导 `/`** | `1/2026/11/c0a1...e7.jpg` |
| 环境变量 | 后端 `UPPER_SNAKE`；前端 `VITE_` 前缀 | `DB_URL`、`JWT_SECRET`、`VITE_API_BASE_URL` |

### 11.2 时间格式

| 场景 | 格式 | 实现位置 |
| --- | --- | --- |
| `LocalDateTime` JSON 序列化/反序列化 | `yyyy-MM-dd HH:mm:ss` | `config/JacksonConfig.java` 注册 `LocalDateTimeSerializer/Deserializer`（**不用** `spring.jackson.date-format`，那只对 `java.util.Date` 生效） |
| `LocalDate` JSON | `yyyy-MM-dd` | 同上 |
| 时区 | `Asia/Shanghai`（UTC+8，无夏令时） | ① `spring.jackson.time-zone: Asia/Shanghai`；② `hibernate.jdbc.time_zone: Asia/Shanghai`；③ JDBC URL 带 `serverTimezone=Asia/Shanghai`；④ `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))` 于 `@PostConstruct` |
| 「今天」 | `LocalDate.now(ZoneId.of("Asia/Shanghai"))` | `support/DateTimes#today()`，**禁止** `new Date()` / `LocalDate.now()` 无参 |
| 时间轴分组键 | `yyyy-MM` | 后端 `sortTime.format(FMT_MONTH)` |
| 月份展示文案 | `2026 年 10 月` | **后端生成** `TimelineGroupResp.label`，前端直接用 |
| 前端展示 | 列表用 `MM月 DD` + 星期；详情用 `yyyy-MM-dd HH:mm` | `utils/format.ts`（字符串切片，不引入 dayjs） |
| 数据库 | `DATETIME`（**不用** `TIMESTAMP`，避免 2038 与时区隐式转换） | DDL |

### 11.3 BigDecimal 序列化 —— 结论

**结论：序列化为 JSON **数字**（不字符串化），保留 DB 的 scale。**

| 项 | 决策与理由 |
| --- | --- |
| 为什么数字而非字符串 | ① 前端 `GrowthChart` 需要数值直接喂 ECharts `type:'time'` 折线，字符串需 `parseFloat` 多一层且易漏；② 精度需求仅 1–2 位小数（`DECIMAL(5,1)` / `DECIMAL(4,2)`），远小于 IEEE754 双精度 15–17 位有效数字的安全区间，JS `Number` 可精确表示，不存在「金额级」精度敏感；③ 本项目无金额、无累加运算，`0.1+0.2` 类问题无发生面 |
| 为什么保留 scale | `DECIMAL(4,2)` 的 `9.80` 序列化为 `9.80`（Jackson 默认 `BigDecimal.toString()`），前端 `toFixed(2)` 展示一致；**禁止**在 mapper 里调 `stripTrailingZeros()`（会把 `9.80` 变 `9.8`，再 `toFixed(2)` 又变 `9.80`，但若未来改成字符串契约就会漂移） |
| 配置 | ① 不注册自定义 `BigDecimal` 序列化器（默认即数字）；② `spring.jackson.generator.write-bigdecimal-as-plain: true` 防止科学计数法（如 `1E+2`）；③ `WRITE_NUMBERS_AS_STRINGS` **不开启** |
| 前端类型 | `heightCm: number \| null`；展示 `value.toFixed(1)`，体重 `toFixed(2)` |
| `fileSize`（`BIGINT`） | 同样序列化为数字。上限 20MB = 2×10⁷，远小于 `Number.MAX_SAFE_INTEGER`（9×10¹⁵），安全 |
| 反序列化 | `@DecimalMin` / `@DecimalMax` 校验，前端传 `78.5` 数字即可；**不接受**字符串 `"78.5"`（Jackson 默认可宽松转换，为契约清晰选择 `DeserializationFeature.ALLOW_COERCION_OF_SCALARS` 保持默认，不额外禁用） |
| 唯一例外 | 若未来引入金额（P2 商业化被明确排除），则改字符串 + 手写 `Money` 类型。当前不做 |

### 11.4 空值处理

| 项 | 规则 |
| --- | --- |
| JSON 输出 | **全量输出 `null`**（`spring.jackson.default-property-inclusion: always`）。**不启用 `NON_NULL`** —— 原因：前端 TS 用 `T \| null` 精确建模，字段缺失会让「未分类」(`albumId: null`) 与「字段不存在」无法区分；启用了反而不安全 |
| 空集合 | 输出 `[]`，**不输出 `null`**（repository 返回空 `List`，mapper 直接透传） |
| 数值 null | `BigDecimal`/`Long`/`Integer` 可为 null 的列，实体用包装类型（`BigDecimal`/`Long`/`Integer`），**不用基本类型** |
| 字符串 null | `VARCHAR NULL` 列映射为 `String`（可 null）；`NOT NULL` 列为 `String`（不可 null） |
| **PATCH 语义** | **统一为「全量提交」**：前端 PATCH 时提交该资源**全部可编辑字段**的当前值（含未改动的），服务端逐字段覆盖，`null` 明确表示「置空」。**不实现「缺失字段不更新」语义** —— 理由：Java `record` 无法区分「未传」与「传了 null」，若强行支持需引入 `JsonNullable`（额外依赖）或大量 `Map` 解析，收益为零（前端表单本来就有全部值） |
| 例外 | `PATCH /api/photos/{id}` 的 `tags` 字段是**完整集合语义**（传 `[]` 清空，传 null 表示不修改标签）。此例外必须在 controller 注释中写明 |
| 前端 | 所有可能为 null 的字段一律 `T \| null`（不用 `?`），模板中 `v-if` 或 `?? fallback` 显式处理；**禁止** `value!` 非空断言 |

### 11.5 错误码编号规则

```
code = HTTP状态码 × 100 + 两位序号（01..99）
  0         → 成功
  400xx     → 参数/请求格式问题
  401xx     → 未认证 / 凭证错误
  403xx     → 已认证但无权
  404xx     → 资源不存在
  409xx     → 状态冲突（唯一约束、数量上限、并发）
  413xx     → 请求体过大
  415xx     → 媒体类型不支持
  500xx     → 服务端错误
```

| 规则 | 说明 |
| --- | --- |
| 稳定性 | 已发布的编号**永不复用、永不改语义**；新增只能追加未使用序号 |
| 前端映射 | 前端 `types/api.ts` 维护 `ERROR_MESSAGE: Record<number, string>`，覆盖「可操作指引」类文案（如 41501 的 HEIC 指引）；未命中则用后端 `message` |
| 一次性落地 | T01 必须一次写全 25 个枚举（`OK` + 24 个错误码，见 §5.2），避免后续任务各自加码 |
| 日志 | 5xx 必须打 `traceId` + 完整堆栈；4xx 只打 WARN + 一行摘要（不含请求体全文） |

### 11.6 前端路径别名 `@`

| 配置处 | 内容 |
| --- | --- |
| `frontend/vite.config.ts` | `resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } }` |
| `frontend/tsconfig.json` | `"baseUrl": ".", "paths": { "@/*": ["./src/*"] }` |
| `frontend/eslint.config.js` | `settings['import/resolver'].typescript = { project: './tsconfig.json' }`（如启用 import 插件） |
| 使用约定 | **所有**跨目录 import 一律用 `@/`（`@/api`、`@/components/base/BeeButton.vue`、`@/stores/auth`、`@/types/models`）；**同目录**的相对 import 允许 `./`；**禁止** `../../../../` 三级以上相对路径 |
| `env.d.ts` | 需声明 `declare module '*.vue'` 与 `interface ImportMetaEnv { readonly VITE_API_BASE_URL: string }` |

### 11.7 其他跨文件约定

| 项 | 约定 |
| --- | --- |
| 分页 | 请求 `page` **0 基**、`size` 默认 24（时间轴）/60（照片网格）/20（列表）；前端 `BeePagination` 显示时 `page+1` |
| 图片尺寸 | 网格/缩略条/封面 → `thumbKey`（480）；灯箱 → `previewKey`（1600）；**永不**在网格加载 `storageKey` |
| 表单校验双写 | 前端 `utils/validate.ts` 与后端 `@Valid` 必须写同一套阈值（身高 30–150 等），改任一处必须同步另一处并在 PR 描述注明 |
| 日期输入 | 前端 `<input type="date">` → `yyyy-MM-dd` 字符串，直接对应后端 `LocalDate`，**不转 Date 对象** |
| 权限判断 | 前端只做 UI 隐藏（`role === 'owner'` / `createdBy === userId` / `uploadedBy === userId`），**不作为安全边界** |
| i18n | 本期仅中文；文案**集中**在 `utils/format.ts` 常量与组件内 `const TEXT = {...}`，不散落硬编码（PRD §10 可维护性） |
| 禁止 | ① `v-html`（除 `utils/format.ts#highlight` 的转义后输出）；② 硬编码色值；③ `any`；④ `!` 非空断言；⑤ 直接 `fetch`（一律走 `http`）；⑥ 组件内直接拼 `/api/files/...`（一律走 `fileUrl()`） |

---

## 12. 风险与待明确

| # | 风险 / 待明确 | 影响 | 缓解措施 / 决策 |
| --- | --- | --- | --- |
| **R-1** | **HEIC 不支持导致 iPhone 用户首次上传失败率高**（ADR-01 的直接后果）。目标用户「手机拍照为主」，默认格式就是 HEIC，若第一次上传就被拒，可能直接放弃产品 | 高（影响 G1「导入无痛」核心目标） | ① 提示文案必须给到「设置 → 相机 → 格式 → 兼容性最佳」与「相册中导出为 JPEG」两条路径，并在上传区常驻一行 12px 提示「iPhone 用户请使用 JPEG 格式」；② 失败文件**不进上传队列**（前端预拦截），避免「上传到 90% 才失败」的挫败感；③ 若上线后发现 >30% 用户踩坑，则 P1 优先接入 `sips`/`libheif`（macOS 自带 `sips` 可转码，风险低于跨平台 native 依赖），实现点已隔离在 `ImageValidator` 之后 |
| **R-2** | **全局搜索 `LIKE '%q%'` 无法走索引**，50k 照片时全表扫描 ≈ 20–60ms；若某家庭照片达 5 万张且并发多人搜索，可能逼近 300ms 预算 | 中 | ① 强制 `family_id` 前缀过滤，把扫描面限制在单家庭行数；② `q` 长度上限 50，`size` 上限 20（每次只回 20 条，且只 `SELECT` 必要列 + `LIMIT`）；③ 三组结果串行查询改**并行**（`CompletableFuture` 或直接三条独立查询取并集），实测确定；④ 若仍不达标，P1 加 MySQL 8 的 `FULLTEXT` + `WITH PARSER ngram`（中文 2-gram），或引入 `title` 前缀索引 + 标签精确匹配的组合策略。**本期不做全文索引**（额外 DDL 与调优成本，收益待验证） |
| **R-3** | **JWT 无服务端失效机制**：登出、被移除成员、被改密码后，旧 token 在 7 天内仍可用。PRD §10 要求「支持主动登出失效」 | 中（内网自用，但 PRD 有明示要求） | ① 登出仅前端清 token（**已知不满足 PRD 字面要求**，需产品确认可接受）；② 补偿措施：`user` 表加 `token_valid_after DATETIME` 列（改密码/登出时更新为 now），`JwtAuthenticationFilter` 比较 `iat >= token_valid_after` —— 仅增 1 列 + 1 次比较，**建议在 T04 内直接实现**，避免返工；③ 若产品接受，则文档明示取舍。**待产品确认（见 §12 待明确 U-1）** |
| **R-4** | **时间轴深翻页在极端数据下退化**：单家庭 5 万条记忆 → `OFFSET 47000` 需排序扫描 47k 行，P95 可能 150–400ms | 中低 | ① `hasMore` 用 `size+1` 探测，不依赖 `total`，保证功能正确；② offset > 3000（约 125 页）时若实测超 300ms，切换为 keyset（`cursor = sortTime|type|id`），接口保留 `cursor` 可选参数位，前端改造量仅 `useInfiniteScroll` 一处；③ 现实约束：家庭相册 5 万条记忆约为 10 年使用量，翻到第 125 页的场景概率极低 |
| **R-5** | **单机磁盘容量与备份意识缺失**：27GB 级别数据无 RAID、无异地备份，硬盘故障即全丢；PRD 明确「备份由运维脚本承担，本期不含」 | 中（数据不可恢复是不可接受的失败） | ① 在 `README.md` 与设置页「数据」区块显式提示「建议每周备份」，并提供可直接复制的 `mysqldump + rsync` 命令；② `docs/api-smoke.sh` 之外提供 `scripts/backup.sh`（T25 产出，不在 44 接口范围内）；③ 不做自动备份（需 launchd 配置，超出本期范围但必须文档化） |
| **R-6** | **ECharts 体积与首屏 LCP 预算冲突**：PRD G2 要求首屏 LCP ≤ 2s，若 ECharts 被打进主 chunk 会突破预算 | 中低 | ① `GrowthChart.vue` 必须是 `/growth` 路由的**懒加载子组件** + `defineAsyncComponent`，ECharts 只在进入成长页时下载；② 按需引入（`echarts/core`），目标 ≤ 220KB gzip；③ T14 验收要求「不含 ECharts 时产物 < 500KB gzip」，T24 验收要求「ECharts chunk ≤ 220KB gzip」，两道闸门 |
| **U-1** | **待产品确认**：PRD §10「JWT 支持主动登出失效」是否必须严格实现（见 R-3）。若必须，T04 增加 `user.token_valid_after` 列与比较逻辑（+1 DDL 列、+1 判断，成本低） | — | 架构建议：**实现**（成本低、满足 PRD 字面要求）；若产品同意放宽，则在 `docs/03-architecture.md` 与 PRD 中同步标注。默认按「实现」推进，T04 验收已含该点 |
| **U-2** | **待明确**：`GET /api/search` 的「查看全部」是否需要独立分页接口。当前设计为同接口 `type` 参数分页，`/search?q=&tab=photo` 复用 | 低 | 架构决策：复用同接口（少 3 个端点、少 3 个前端页面）。若产品要求「查看全部」进独立页，则新增 `GET /api/search/photos?q=&page=&size=`，改动集中在 T13 + T25 |
| **U-3** | **待明确**：相册「未分类」是否允许被设为某张照片的目标相册（即 `albumId: null` 的 PATCH 语义）。当前 `PhotoUpdateReq.albumId` 传 `null` = 移出相册（置为未分类），已在 §11.4 明确 | 低 | 已按「`null` = 未分类」实现，无歧义 |

---

## 附录 A · 文档与产出自检清单

| # | 检查项 | 状态 |
| --- | --- | --- |
| 1 | 12 条 P0 均有 API 覆盖（§5.3 每行末列） | ✅ |
| 2 | 10 张表 DDL 可直接在 MySQL 8.4 执行（§4.4） | ✅ |
| 3 | 三处唯一约束已落地：`(family_id,user_id)` / `(baby_id,recorded_at)` / `(family_id,name)` | ✅ |
| 4 | 循环外键方案已给出结论（§4.3 方案 B） | ✅ |
| 5 | 44 个接口全部列出方法/路径/权限/请求/响应/错误码 | ✅ |
| 6 | 3 个复杂接口有完整 JSON 示例（§5.4–5.6） | ✅ |
| 7 | 4 个关键时序图（§6.1–6.4） | ✅ |
| 8 | 「时间轴分页策略」有结论 + 论证（§5.7，ADR-05） | ✅ |
| 9 | 「BigDecimal 序列化」有结论 + 论证（§11.3，ADR-06） | ✅ |
| 10 | 25 个任务，每个含产出文件完整路径 + 依赖 + 可执行验收命令 | ✅ |
| 11 | 任务依赖图与并行波次（§10.4） | ✅ |
| 12 | 全程无 Java / Vue 业务代码（仅接口签名、字段、路径、SQL、JSON） | ✅ |
| 13 | JDK 17 的 `JAVA_HOME` 命令出现在 §10.1 与 T25 约束 | ✅ |
| 14 | 设计系统落地方式含 tokens + reset + base 三分工（§9.5） | ✅ |
| 15 | 前端类型定义与后端 DTO 一一对应（§9.6） | ✅ |
| 16 | 领域模型与服务类图（附录 B），含实体关系 + 服务职责边界 | ✅ |
| 17 | DDL 已在 MySQL 8.4.7 实测建表成功（10 表 / 18 外键 / 10 CHECK / 36 索引），并实测 `mysqldump` 免关外键检查恢复 | ✅ |
| 18 | 三处实测结论：`UNION ALL` 排序键须用整数 `type_ord`、cover 无 FK 时会悬挂、`DELETE FROM family` 级联不报错 | ✅ |


---

## 附录 B · 领域模型与服务类图

> ⚠️ **权威性说明**：列（字段）的**唯一权威是 §4.4 的 DDL**，本图只用来说明**类与类的关系、以及 service 层的职责边界**。若本图与 DDL 冲突，以 DDL 为准。
> 实体均不加 Jackson 注解；对外的 JSON 形状由 `dto/**` 的 record 决定（见 §5 与 §9.6）。

```mermaid
classDiagram
    direction LR

    %% ---------- 枚举 ----------
    class Role {
        <<enumeration>>
        owner
        member
    }
    class Gender {
        <<enumeration>>
        male
        female
        unknown
    }
    class MediaType {
        <<enumeration>>
        photo
        video
    }
    class PhotoStrategy {
        <<enumeration>>
        move
        delete
    }

    %% ---------- 通用契约 ----------
    class ApiResponse~T~ {
        +int code
        +String message
        +T data
        +String traceId
        +ok(T data)$
        +fail(ErrorCode e, String traceId)$
    }
    class PageResponse~T~ {
        +List~T~ items
        +int page
        +int size
        +long total
        +int totalPages
        +boolean hasMore
        +of(List~T~ items, PageQuery q, long total)$
    }
    class ErrorCode {
        <<enumeration>>
        OK
        INVALID_PARAM
        UNAUTHORIZED
        CROSS_FAMILY_ACCESS
        OWNER_ONLY
        NOT_RESOURCE_CREATOR
        RESOURCE_NOT_FOUND
        EMAIL_ALREADY_EXISTS
        INVITE_CODE_INVALID
        FAMILY_MEMBER_LIMIT
        BABY_LIMIT
        ALBUM_NAME_DUPLICATED
        FILE_TOO_LARGE
        UNSUPPORTED_MEDIA_TYPE
        INTERNAL_ERROR
        +int code
        +int httpStatus
        +String defaultMessage
    }
    class BusinessException {
        +ErrorCode errorCode
        +String message
    }
    class ApiResponse~T~ ..> ErrorCode : uses

    %% ---------- 领域实体 ----------
    class User {
        +Long id
        +String email
        +String passwordHash
        +String nickname
        +String avatarUrl
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }
    class Family {
        +Long id
        +String name
        +Long ownerId
        +String inviteCode
        +LocalDateTime inviteCodeExpiresAt
        +Long coverPhotoId
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }
    class FamilyMember {
        +Long id
        +Long familyId
        +Long userId
        +Role role
        +LocalDateTime joinedAt
    }
    class Baby {
        +Long id
        +Long familyId
        +String name
        +Gender gender
        +LocalDate birthday
        +String avatarUrl
        +BigDecimal birthHeightCm
        +BigDecimal birthWeightKg
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }
    class GrowthRecord {
        +Long id
        +Long babyId
        +LocalDate recordedAt
        +BigDecimal heightCm
        +BigDecimal weightKg
        +BigDecimal headCm
        +String note
        +Long createdBy
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }
    class Milestone {
        +Long id
        +Long babyId
        +Long familyId
        +String title
        +String description
        +LocalDate achievedAt
        +Long photoId
        +Long createdBy
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }
    class Album {
        +Long id
        +Long familyId
        +String name
        +String description
        +Long coverPhotoId
        +int photoCount
        +Long createdBy
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }
    class Photo {
        +Long id
        +Long familyId
        +Long albumId
        +String title
        +String description
        +String storageKey
        +String thumbKey
        +String previewKey
        +String mimeType
        +long fileSize
        +Integer width
        +Integer height
        +LocalDateTime takenAt
        +String locationName
        +BigDecimal latitude
        +BigDecimal longitude
        +String contentHash
        +MediaType mediaType
        +Long uploadedBy
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }
    class Tag {
        +Long id
        +Long familyId
        +String name
        +LocalDateTime createdAt
    }
    class PhotoTag {
        +Long photoId
        +Long tagId
        +LocalDateTime createdAt
    }

    User "1" --> "0..1" FamilyMember : userId
    Family "1" --> "1..6" FamilyMember : familyId
    Family "1" --> "0..3" Baby : familyId
    Family "1" --> "0..*" Album : familyId
    Family "1" --> "0..*" Photo : familyId
    Family "1" --> "0..*" Milestone : familyId
    Family "1" --> "0..*" Tag : familyId
    Baby "1" --> "0..*" GrowthRecord : babyId
    Baby "1" --> "0..*" Milestone : babyId
    Album "0..1" --> "0..*" Photo : albumId
    Photo "1" --> "0..*" PhotoTag : photoId
    Tag "1" --> "0..*" PhotoTag : tagId
    Photo "0..1" --> "0..1" Milestone : photoId
    FamilyMember ..> Role
    Baby ..> Gender
    Photo ..> MediaType

    %% ---------- 安全层 ----------
    class UserPrincipal {
        <<record>>
        +Long userId
        +String email
        +String nickname
        +Long familyId
        +Role role
        +boolean hasFamily()
        +boolean isOwner()
    }
    class JwtService {
        +String issue(Long userId, String email)
        +Long parseUserId(String token)
    }
    class FamilyAccessGuard {
        +UserPrincipal principal()
        +Long familyId()
        +void assertSameFamily(Long resourceFamilyId)
        +void assertOwner()
        +void assertCreatorOrOwner(Long createdBy)
        +void assertUploaderOrCreatorOrOwner(Long uploadedBy, Long createdBy)
    }
    class SecurityUtils {
        +UserPrincipal requirePrincipal()$
        +Long currentUserId()$
    }
    JwtService ..> UserPrincipal : builds
    FamilyAccessGuard ..> UserPrincipal : reads

    %% ---------- 服务层 ----------
    class AuthService {
        +TokenResp register(RegisterReq req)
        +TokenResp login(LoginReq req)
        +MeResp me()
        +MeResp updateProfile(UpdateProfileReq req)
        +void changePassword(ChangePasswordReq req)
    }
    class FamilyService {
        +FamilyResp get()
        +FamilyResp rename(UpdateFamilyReq req)
        +List~MemberResp~ members()
        +void removeMember(Long userId)
        +InviteCodeResp currentInviteCode()
        +InviteCodeResp resetInviteCode()
        +void deleteFamily(DeleteFamilyReq req)
    }
    class BabyService {
        +List~BabyResp~ list()
        +BabyResp create(BabyReq req)
        +BabyDetailResp detail(Long id)
        +BabyResp update(Long id, BabyUpdateReq req)
        +void delete(Long id)
    }
    class GrowthService {
        +PageResponse~GrowthRecordResp~ list(Long babyId, PageQuery q)
        +GrowthRecordResp upsert(Long babyId, GrowthRecordReq req)
        +GrowthRecordResp update(Long id, GrowthRecordReq req)
        +void delete(Long id)
        +GrowthCurveResp curve(Long babyId, String range)
    }
    class MilestoneService {
        +PageResponse~MilestoneResp~ list(Long babyId, PageQuery q)
        +MilestoneResp create(MilestoneReq req)
        +MilestoneResp update(Long id, MilestoneReq req)
        +void delete(Long id)
    }
    class AlbumService {
        +AlbumListResp list()
        +AlbumResp create(AlbumReq req)
        +AlbumDetailResp detail(Long id)
        +AlbumResp update(Long id, AlbumUpdateReq req)
        +DeleteAlbumResp delete(Long id, PhotoStrategy strategy)
    }
    class PhotoService {
        +PageResponse~PhotoResp~ list(PhotoQuery q)
        +PhotoDetailResp detail(Long id)
        +PhotoUploadResp upload(List~MultipartFile~ files, Long albumId)
        +PhotoResp update(Long id, PhotoUpdateReq req)
        +void delete(Long id)
        +int batchDelete(BatchDeleteReq req)
        +int batchMove(BatchMoveReq req)
    }
    %% PhotoIngestService 为独立 Bean，单张照片一个 REQUIRES_NEW 事务
    class PhotoIngestService {
        <<REQUIRES_NEW>>
        +PhotoResp ingestOne(PhotoDraft draft)
    }
    class TimelineService {
        +TimelinePageResp page(PageQuery q)
    }
    class SearchService {
        +SearchResp search(String q, String type, int limit)
    }
    class TagService {
        +List~TagResp~ suggest(String q)
        +void syncPhotoTags(Long photoId, List~String~ names)
    }
    class StatisticsService {
        +OverviewResp overview()
    }
    class StorageService {
        +String allocateKey(Long familyId, LocalDateTime at, String ext)
        +void writeOriginal(String key, InputStream in)
        +Path resolveReadable(String key)
        +void deleteQuietly(List~String~ keys)
        +void deleteDirectory(Long familyId)
    }
    class ThumbnailService {
        +ThumbResult generate(Path original, String keyBase)
    }
    class ExifService {
        +Optional~LocalDateTime~ readTakenAt(Path file)
    }
    class ImageValidator {
        +ImageType sniff(byte[] head, String filename)
    }

    AuthService ..> FamilyAccessGuard
    FamilyService ..> FamilyAccessGuard
    BabyService ..> FamilyAccessGuard
    GrowthService ..> FamilyAccessGuard
    MilestoneService ..> FamilyAccessGuard
    AlbumService ..> FamilyAccessGuard
    PhotoService ..> FamilyAccessGuard
    TimelineService ..> FamilyAccessGuard
    SearchService ..> FamilyAccessGuard
    StatisticsService ..> FamilyAccessGuard

    PhotoService --> PhotoIngestService : 逐张调用（独立事务）
    PhotoService --> StorageService
    PhotoService --> ThumbnailService
    PhotoService --> ExifService
    PhotoService --> ImageValidator
    PhotoService --> TagService
    AlbumService --> PhotoService : moveToUnclassified / deleteByAlbum
    TimelineService --> StorageService : 仅拼 key，无 IO
    FamilyService --> StorageService : deleteDirectory(afterCommit)
```

**服务层调用关系要点**

| 调用 | 方向 | 说明 |
| --- | --- | --- |
| `controller → service` | 单向 | controller 不含业务判断，只做参数绑定与 `ApiResponse` 包装 |
| `service → repository` | 单向 | `TimelineService` 是唯一跨 4 个 repository 的服务（UNION + 3 路 IN + 标签批查） |
| `PhotoService → PhotoIngestService` | 必须跨 Bean | 自调用会使 `@Transactional(REQUIRES_NEW)` 失效（§3.5） |
| `AlbumService → PhotoService` | 允许（单向，无环） | 删相册需要照片的「移至未分类 / 删除」能力；反向 `PhotoService → AlbumService` **禁止**（只需 `AlbumRepository` 维护 `photoCount`） |
| `service → FamilyAccessGuard` | 全部 service | 权限校验统一入口，controller 不参与 |
| `service → StorageService` | 仅 `PhotoService` 与 `FamilyService` | 其余 service 不得触碰文件系统 |


---

**文档结束。** 下游工程师请以本文 §10 任务清单为执行顺序，以 §5 API 契约与 §9.6 类型定义为接口唯一依据，以 `docs/02-ui-design.md` 为视觉唯一依据。
