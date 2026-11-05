# 蜜记 · Bee Family

> 把一家人的时光，收成一册暖光相册。

家庭相册 + 宝宝成长记录。照片按时间自动成轴，可以分相册整理，宝宝的第一次走路、
第一次叫妈妈这些瞬间能以「里程碑」定格，身高体重头围有曲线可看。

- 前端：Vue 3 + TypeScript + Vite（自建设计系统，不依赖任何 UI 组件库）
- 后端：Spring Boot 3.3 + Spring Security + Spring Data JPA + Flyway
- 数据库：MySQL 8
- 对象存储：本地文件系统（`backend/data/uploads`，按家庭隔离）

---

## 一、目录结构

```
bee-family/
├── backend/                    Spring Boot 服务（端口 8080）
│   ├── src/main/java/com/beefamily/album/
│   │   ├── common/             统一响应信封、分页、错误码、业务异常、全局异常处理、TraceId
│   │   ├── config/             Security / Jackson / 存储 / Web 配置
│   │   ├── security/           JWT 签发校验、认证过滤器、当前用户上下文、家庭访问守卫
│   │   ├── domain/             10 张表的 JPA 实体与枚举
│   │   ├── repository/         仓储接口（含时间轴 UNION ALL 原生查询）
│   │   ├── dto/                各领域请求/响应 record，按领域分子包
│   │   ├── mapper/             实体 ↔ DTO 转换（静态方法，无状态）
│   │   ├── service/            业务逻辑与事务边界（鉴权/家庭/宝宝/成长/里程碑/相册/照片/标签/时间轴/搜索/统计/存储）
│   │   ├── controller/         12 个 REST 控制器，共 44 个端点
│   │   └── support/            日期、限额、邀请码、图片类型与魔数校验等横切工具
│   └── src/main/resources/
│       ├── application.yml    公共配置（不含任何口令）
│       ├── application-local.yml.example   本地配置模板
│       └── db/migration/       Flyway 迁移脚本（表结构唯一权威）
├── frontend/                   前端应用（端口 5173）
│   └── src/
│       ├── api/                axios 封装与各领域接口
│       ├── components/base/    19 个基础组件（BeeButton / BeeImage / BeeIcon…）
│       ├── components/business/17 个业务组件（相册卡 / 时间轴 / 成长曲线 / 灯箱…）
│       ├── composables/        焦点陷阱、无限滚动、上传队列、Toast、断点
│       ├── layouts/            AppShell / SideNav / TopBar / TabBar
│       ├── router/             路由表与鉴权守卫
│       ├── stores/             Pinia（auth / family / album / photo / toast）
│       ├── styles/             tokens.css（设计令牌）/ reset.css / base.css
│       ├── types/              与后端 DTO 逐字段对齐的类型
│       ├── utils/              格式化、校验、图片、防抖、剪贴板
│       └── views/              10 个页面
└── docs/
    ├── 01-prd.md               产品需求（范围唯一依据）
    ├── 02-ui-design.md         视觉与交互规范（含逐页线框）
    ├── 03-architecture.md      技术架构、API 契约与任务拆分
    ├── 04-test-report.md       测试报告（用例、并发压测、性能实测）
    ├── api-smoke.sh            接口冒烟测试脚本（44 项断言）
    ├── e2e-verify.mjs          端到端 / 越权 / 并发验证脚本
    └── screenshots/            各页面实拍截图（1440 / 375）
```

---

## 二、环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | **17** | 编译目标是 `release 17`，低版本无法编译 |
| Maven | 3.9+ | |
| Node.js | 18+（建议 20/22） | 前端构建 |
| MySQL | 8.0+ | 需要 `utf8mb4` |

---

## 三、启动后端

### 1. 准备 JDK 17

macOS 默认 `java` 可能不是 17，先显式指定：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # 应输出 17.x
```

> Linux / Windows 请替换为各自的 JDK 17 安装路径。

### 2. 准备数据库

```bash
mysql -uroot -p -e "CREATE DATABASE bee_family CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

**表结构由 Flyway 自动创建**（`db/migration/V1__init.sql`，共 10 张表），
不需要手工建表，也不要在数据库里手改结构：`spring.jpa.hibernate.ddl-auto=validate`
会在启动时校验实体与表结构是否一致，不一致直接启动失败。

### 3. 生成本地配置

```bash
cd backend
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

编辑 `application-local.yml`，至少改三处：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/bee_family?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: 你的数据库口令
app:
  jwt:
    secret: 至少 32 字节的随机字符串
  storage:
    root: ./data/uploads
```

> `application-local.yml` 已在 `.gitignore` 中，**不会被提交**。
> 仓库里只保留 `application-local.yml.example` 模板。

也可以完全用环境变量启动（不落盘任何口令）：

```bash
export DB_URL='jdbc:mysql://localhost:3306/bee_family?...'
export DB_USERNAME=root
export DB_PASSWORD=你的口令
export APP_JWT_SECRET=$(openssl rand -hex 32)
```

### 4. 启动

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

看到 `Started BeeFamilyApplication` 即启动成功，服务在 `http://localhost:8080`。

> 端口被环境变量覆盖时（例如 shell 里有 `SERVER__PORT`），
> 用 `unset SERVER__PORT` 或显式指定 `-Dspring-boot.run.jvmArguments="-Dserver.port=8080"`。

---

## 四、启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`。开发服务器已把 `/api` 代理到 `http://localhost:8080`，
**不需要**任何 CORS 配置。

常用命令：

| 命令 | 作用 |
| --- | --- |
| `npm run dev` | 开发服务器（5173） |
| `npm run type-check` | `vue-tsc --noEmit` 类型检查 |
| `npm run lint` | ESLint（0 error / 0 warning） |
| `npm run build` | 类型检查 + 生产构建，产物在 `dist/` |
| `npm run preview` | 预览构建产物 |

---

## 五、首次使用：自己注册一个账号

1. 打开 `http://localhost:5173/login`，切到「注册」。
2. 填自己的邮箱 + 口令（6–32 位）即可。**第一个注册的用户会自动创建家庭，并成为家庭创建者（owner）**，
   不需要邀请码 —— 也就是说空库直接注册就能用，仓库不预置任何账号。
3. 第二位及之后的家人注册时，需要 owner 在「家庭成员 → 邀请家人加入」里提供的
   8 位邀请码（24 小时有效，可随时重置）。

> 口令只存在于你自己的数据库里。仓库、脚本、文档中都**没有任何默认账号或默认口令**，
> 冒烟脚本也要求通过环境变量传入（见第六节）。

### 截图里的演示数据不在仓库内

`docs/screenshots/` 下的截图用的是**本地 seed 出来的演示数据**，仅用于视觉验收，
仓库里没有种子脚本、也没有 SQL 备份，具体包括：

- 家庭「许家小院」（owner 昵称「妈妈」，邮箱 `mom@home.local`）
- 宝宝「团团」（2025-08-18 出生，3 条里程碑）
- 3 本相册（青岛海边 / 日常碎片 / 宝宝满月）与约 56 张照片、12 条成长记录

所以克隆仓库后启动看到的是**空状态**，这是预期行为；截图里那些数字复现不出来是正常的。

---

## 六、测试

### 1. 后端单元 / 集成测试（不依赖数据库）

```bash
cd backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home   # JDK 17
mvn test
```

一条命令即可跑完 **190 个用例**，不需要 MySQL、不需要任何环境变量 / 密钥：
Service 层用 Mockito 打桩仓储，Controller 层用 `@WebMvcTest` 只加载 Web 切片。

需要真实 MySQL 的用例（原生 `UNION ALL` 时间轴排序与分页、唯一约束、外键 `ON DELETE`
行为）打了 `@Tag("mysql")`，默认被 `pom.xml` 里的 `<excludedGroups>${qa.excludedGroups}</excludedGroups>`
（默认值 `mysql`）排除。显式运行：

```bash
cd backend
DB_USERNAME=root DB_PASSWORD='<你的口令>' \
mvn test -Dgroups=mysql -Dqa.excludedGroups=none
```

> 这组用例会在库里新建一套 `QA家族-<时间戳>` 的隔离数据，跑完自动删除，不会触碰既有数据。

### 2. 前端纯函数验证（不引入测试框架）

```bash
cd frontend
npm run verify
```

`scripts/verify-utils.mjs` 对 `src/utils/format.ts` 与 `src/utils/validate.ts` 做 32 项断言
（含 `highlight()` 的 XSS 转义用例），用 Node 22 原生的 TypeScript 类型擦除
（`--experimental-strip-types`）直接跑，**不新增任何 devDependency**。

> 为什么不用 vitest：本项目前端刻意保持零测试框架的依赖面，引入 vitest 会带进
> jsdom / tinypool 等一串传递依赖，与「只断言纯函数」的目标不成比例。

### 3. 端到端 / 越权 / 鉴权 / 并发 / 分页（需要真实后端）

```bash
# 必须连一个空库：user 表非空时注册需要邀请码，只有首个用户才会成为 owner 并自动建家庭
mysql -uroot -p -e "create database bee_family_e2e default character set utf8mb4"
cd backend && DB_URL='jdbc:mysql://localhost:3306/bee_family_e2e?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false' \
  DB_USERNAME=root DB_PASSWORD='<你的口令>' mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18081

# 另开一个终端
BASE_URL=http://127.0.0.1:18081 DB_NAME=bee_family_e2e UPLOAD_ROOT=backend/data/uploads-e2e \
  node docs/e2e-verify.mjs
```

49 条用例覆盖：主流程 16 条、跨家庭越权 10 条、鉴权 6 条、上传安全 6 条、并发 4 条、分页 4 条、性能 1 条、
清理 2 条。脚本自建账号与数据，结束走一遍 `DELETE /api/family` 九步删除，不留残留。

完整测试结论（用例清单、并发压测数据、性能实测、遗留风险）见 `docs/04-test-report.md`。

---

## 七、接口冒烟测试

后端 + 数据库就绪后：

```bash
# 口令必须由环境变量传入（脚本里没有任何默认口令，缺了会直接报用法）
EMAIL=<你的邮箱> PASSWORD=<你的密码> docs/api-smoke.sh

# 顺带验证 Vite 代理链路
BASE_URL=http://localhost:5173/api EMAIL=<你的邮箱> PASSWORD=<你的密码> docs/api-smoke.sh
```

脚本会覆盖 44 项接口断言，逐项打印 `PASS / FAIL`，最后给出汇总与失败清单，
并在结尾自动清理自己创建的数据（里程碑、成长记录、相册、宝宝）。

> EMAIL 指向的账号不存在时，脚本会自动注册一个 `smoke-<时间戳>@home.local` 的临时账号
> （同时自动建家庭），不会污染你自己的演示数据。
> JSON 解析优先用 `python3`，没有则用 `jq`，两者都没有时退化为 grep/sed 并打印提示；
> 脚本不写任何临时文件，在受限环境（TMPDIR 不可写）里也能一次跑完。

---

## 八、约定与注意事项

- **统一响应信封**：`{ code, message, data, traceId }`，`code=0` 表示成功；
  axios 响应拦截器已把 `data` 拆出来直接返回，业务代码不用再解一层。
- **错误码**：`4xxxx` 业务语义错误（如 `40905` 相册重名、`41301` 超过 20MB、
  `41501` 不支持的图片格式），`40103` 表示 token 已失效（登出 / 改密码 / 移除成员后旧 token 全部失效）。
- **上传接口恒返回 HTTP 200**：逐张失败信息在响应体 `failures[]` 里，
  前端不能按 HTTP 状态判错。
- **图片 URL 只能走 `?token=`**：`<img>` 无法设置请求头，统一由 `utils/fileUrl.ts` 拼接。
- **禁止硬编码颜色**：所有颜色必须来自 `src/styles/tokens.css` 的 CSS 变量，
  `grep -rn "#[0-9A-Fa-f]\{6\}" src/components src/views` 必须为空。
- **不使用 UI 组件库**：组件全部自建，样式用原生 CSS + scoped，不引入 Tailwind / Sass。

---

## 九、设计文档

| 文档 | 内容 |
| --- | --- |
| `docs/01-prd.md` | 产品需求、角色与权限、验收标准 |
| `docs/02-ui-design.md` | 设计令牌、组件规格、逐页线框、交互细节、自检清单 |
| `docs/03-architecture.md` | 技术选型、分层、数据库设计、接口清单、任务拆分 |
