# S-1 P0 Spike 报告 · HEIC 纯 Java 依赖前置验证

| 项 | 值 |
| --- | --- |
| 执行 | QA（heic-spike-qa）· 2026-11-05 ~ 2026-11-06 |
| 任务依据 | `team-lead` 派单「§9.8 P0 spike 中不依赖真实 HEIC 夹具的前置部分」 |
| 规格基线 | `docs/05-prd-incremental.md` / `docs/06-ui-incremental.md` / `docs/07-architecture-incremental.md`，任务下发时 HEAD `001c984` |
| 实际执行时 HEAD | **`f988bac`**（`docs: record user-approved S-1..S-5 gate waiver and risks R-14/R-15`） |
| 验证范围 | S-1 全部子判据（含原本标注「暂未执行」的单张解码）、合规交付、夹具骨架状态、S-3/S-4 独立准备方案 |
| 硬性边界 | 未写任何 T01 实现产物（vendored jar / `maven-repo/**` / `pom.xml` 依赖声明）；未改 `docs/05`/`06`/`07`；未动 `bee_family` 演示库；未打包任何第三方受限样本；未用系统命令生成夹具；未与其他成员直连 |
| 路由决策 | **NoOne**（上游 26.5.1 源码无缺陷，全部构建/依赖判据通过；本任务脚手架自身的 1 处问题已由 QA 自行修正） |

---

## 0. 结论摘要

### 0.1 S-1 判据逐项结果（`docs/07` §9.8 S-1）

| # | S-1 子判据 | 结果 | 证据 |
| --- | --- | --- | --- |
| 1 | 锁定 commit 源码在 JDK 17 + Maven 3.9.9 自建成功 | ✅ 通过 | §2 / §3；`BUILD SUCCESS`，117 源文件 `[debug release 17]`，字节码 major 61 |
| 2 | vendor jar 装入项目 `file://` 仓库后 `mvn -o package` 通过 | ✅ 通过 | §4；临时消费者工程 `mvn -o clean dependency:tree package` EXIT=0，离线日志 0 次在线下载 |
| 3 | `<exclusions>` 后 EXIF 仅 2.19.0 一个版本 | ✅ 通过 | §4；依赖树 `metadata-extractor:jar:2.19.0` 唯一，2.20.0 出现 0 次 |
| 4 | 至少 1 张自拍 HEIC 解码为正立 `BufferedImage` | ✅ 通过（判据层面） | §6；`IMG_8974.HEIC` → 3024×4032、非全黑（7013 色）、`irot=3` 已由上游应用。**视觉正立性的人工判读仍归 S-2**，见 §11 |
| 5 | JVM 启动无 native 库加载 | ✅ 通过 | §5；仅 JDK 自身 `libnio.dylib` / `libzip.dylib`，JNA 类加载 0 次 |
| 6 | 备选 B 仅作对照后移除 | ✅ 通过 | §7；A/B 对照完成，制品已从临时目录删除，未进入任何 pom / 仓库 |

**S-1 判定：通过（6/6）**。这是判据层面的通过；**不等于 5 项 P0 门槛 5/5**，S-2 / S-5 仍因缺夹具只能部分覆盖（§12）。

### 0.2 本次最有价值的两项实证

1. **`openize-heic-java 26.5.1` 会自动应用容器 `irot`，Java 侧绝不可二次旋转**（§6）。对用户自拍片（`irot=3` / 270°、Exif `Orientation=6`）解码，`HeicImage.getWidth()/getHeight()` 直接返回 **3024×4032**（原 `ispe` 为 4032×3024），`HeicImageFrame.toString()` 明确打印 `4032x3024 270°`，像素数组恰为 `3024*4032`。即 §9.6 要求的「输出即正立 `BufferedImage`」由上游库在 `decode()` 内部一次性完成；T03 实现若在 `OpenizeHeicTranscoder#decode()` 里再读 `irot` 或 Exif `Orientation` 补转一次，必然产生双重旋转。
2. **设计文档与 26.5.1 实况的两处偏差**（§3.4 / §7.3）：① `openize.isobmff` 不是独立 artifact，与 `openize.heic` 同仓同 jar，只需 vendor **1 个** jar；② 上游 POM 的 `metadata-extractor` 是 **2.20.0**（非设计默认的 2.19.0），`<exclusions>` 确有必要。二者已写入 `backend/lib/COMMIT.lock`，需转达 T01。

---

## 1. 基线变更与口径说明（必读）

任务下发时基线是 `001c984`，且要求「原片待用户提供，S-2 / S-5 及 S-1 的解码判据暂未执行」「不得开始 T01 实现」。实际执行时仓库已推进 4 个 commit，状态变化如下：

| 变化 | 内容 | 对本任务的处理 |
| --- | --- | --- |
| 真实原片已到位 | `docs/qa/heic-fixtures/IMG_8974.HEIC`（用户自拍 iPhone 12 Pro Max，1,001,143 B，SHA-256 `4454368d…`），仅本地留存、未被 git 跟踪 | 原「S-1 解码判据暂未执行」的前提消失 → **已补做**（§6），且原片合规可用（自拍、非 Nokia、非系统命令生成） |
| 夹具 README 已存在 | `docs/qa/heic-fixtures/README.md` 由其他 QA 落地，含 F-0…F-6 规格、实测、来源约束 | **不覆盖重写**，仅做 4 处最小事实修正（§9） |
| 用户批准门槛豁免 | `f988bac` 新增 `07` §9.8.1：仅豁免「5/5 全通过才允许进入 T01」，判据本身 / 6 条 `irot` 约束 / `50003` 不黑图不静默成功 **均不豁免**；R-14 / R-15 已登记，上线前 T05 补验 | 本任务仍**只做门槛本身**，不启动 T01、不写实现产物；把 README 中与豁免冲突的滞后表述同步 |
| 文件归属冲突 | `software-engineer` 直连称 T01 也负责 `COMMIT.lock` / `licenses/` / `backend/README.md`，并请我勿写 `backend/lib/` | 按任务书「不得与其他成员直连」**未回复**，已单独报 `team-lead` 裁决；本任务按派单落盘 `COMMIT.lock` + `licenses/` + `backend/README.md`，**不写** jar / `maven-repo/**` / `pom.xml` |

> 因此下文所有「已通过」只针对 S-1 的构建、依赖、纯 Java 与单张解码判据；**不构成 T01 放行结论**，放行依据以 `07` §9.8.1 用户豁免为准。

---

## 2. S-1.1 · commit 锁定

| 项 | 值 |
| --- | --- |
| 仓库 | `https://github.com/openize-com/openize-heic-java` |
| tag | `v26.5.1`（lightweight tag，`git/ref/tags` 直接指向 commit） |
| **commit** | **`1118f3c45a47a60cde593a6c47848b2a3a961b9a`** |
| 时间 / 父 commit | `2026-05-12T05:00:40Z` / `9dbbbef4c1b145635cc58b476d5cf4702dfeb76a`（= tag `v.26.5`） |
| message | `Fixed ArrayIndexOutOfBoundsException in transform_tree.java.` |
| main HEAD | 与上述 commit **相同**（已用 `commits/main` 复核） |
| tarball SHA-256 | `4fc31604b7afee5ee358d3c2234856f801fa27eb843cda3a44682ba6c657a9dc` |

复核路径：`GET /repos/openize-com/openize-heic-java/tags`、`/git/ref/tags/v26.5.1`、`/commits/v26.5.1`、`/commits/main`，四者一致。已写入 `backend/lib/COMMIT.lock` §1。

---

## 3. S-1.2 · JDK 17 + Maven 3.9.9 源码离线自建

### 3.1 环境与命令

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
# 复核：mvn -version → Java version: 17.0.15, vendor: Oracle Corporation
#       （不设 JAVA_HOME 时 mvn 跑在 Homebrew OpenJDK 25 上，必须显式指定）
cd <临时目录>/src        # 由 v26.5.1 tarball 展开，--strip-components=1
mvn -B -Dmaven.repo.local=<临时构建仓库> -DskipTests -Dmaven.compiler.release=17 package
```

实测 `Apache Maven 3.9.9`、`maven-compiler-plugin 3.13.0` 编译 `117 source files with javac [debug release 17]`，`BUILD SUCCESS`，用时 34.7s。上游 POM 写的是 `source/target=8`，由命令行 `-Dmaven.compiler.release=17` 覆盖；jar 内 `HeicImage.class` / `ImageRotation.class` / `ImageMirror.class` 的 **major version = 61（Java 17）**。

离线重复构建同样通过：

```bash
mvn -o -B -Dmaven.repo.local=<临时构建仓库> -DskipTests -Dmaven.compiler.release=17 clean package
# EXIT=0, BUILD SUCCESS, 3.483s
```

### 3.2 产物与 SHA-256

| 产物 | 大小 | SHA-256 |
| --- | --- | --- |
| `openize-heic-26.5.1.jar`（首次在线构建） | 233,430 B | `a5372e3075f837961e7fc8ec914551f4993d3a809196cd21ba8ce6e78508f748` |
| 同命令再跑一次（未固定时间戳） | 233,430 B | `6c57631edcd00aed4b99a6d3d68086eb75fe68c156e8f389a900b283087455e4` |
| **固定 `outputTimestamp` 后连续两次**（推荐） | 233,430 B | **`23e476205b89570c5eba4a62551b14a3a360570f3ca2db85578db44969425ddf`**（两次字节级一致） |

jar 内容：145 entries / 134 classes，含 `META-INF/LICENSE.txt`、`openize/heic/**`、`openize/io/**`、`openize/isobmff/**`，**native 二进制文件 0 个**。

### 3.3 可复现性（重要，需转达 T01）

上游 POM 未设 `project.build.outputTimestamp`，`maven-jar-plugin 3.4.2` 把构建时间写进 zip 条目时间戳，**同 commit + 同 JDK 的两次构建 SHA-256 会不同**（见 3.2 前两行）。已验证的解法：

```bash
mvn -B -DskipTests -Dmaven.compiler.release=17 \
    -Dproject.build.outputTimestamp=2026-05-12T05:00:40Z package
```

取上游 commit 时间作为固定值，连续两次构建 `cmp` 完全一致（SHA `23e47620…`）。**`COMMIT.lock` 以该固定值为准**，建议 T01 采用同一命令，否则「锁 commit 保证可复现」只在源码层成立、字节层不成立。

### 3.4 上游 POM 事实（与设计文档的两处偏差）

| 事实 | 26.5.1 实况 | 影响 |
| --- | --- | --- |
| `com.drewnoakes:metadata-extractor` | **2.20.0**（compile） | 项目锁 2.19.0 → `<exclusions>` 确有必要，详见 §4 |
| `org.testng:testng` / `org.jacoco:org.jacoco.agent` | 6.8.8 / 0.8.12，**均 test scope** | 唯一 `<classifier>runtime</classifier>` 属 JaCoCo agent，**不是 native classifier** |
| `openize.isobmff` | **无独立 artifact**；源码 `src/main/java/openize/isobmff/**` 与 `openize.heic` 一起编进同一 jar | 设计 §9.2「`isobmff-*.jar` 一并 vendor」/ §11.6「`openize:openize-isobmff` 传递依赖」与实况不符 → **只需 vendor 1 个 jar**；MIT 许可证文本仍照常归档 |

### 3.5 环境坑：本次未复现（如实标注）

任务书记录的「沙箱禁止 Maven 把 `*.pom.<digits>.tmp...` / `*.jar.<digits>.tmp...` 重命名为正式文件，导致 POM/JAR absent」**在本次环境未复现**：使用独立 `-Dmaven.repo.local`、Maven 3.9.9、阿里云镜像（`maven.aliyun.com/repository/public`）时首次在线构建即 EXIT=0；构建仓库内 `*.tmp` 残留 **0 个**，仅 2 个 `.lastUpdated`（`metadata-extractor:2.19.0` 先查 `bee-vendor` file:// 仓库 miss 的标记，不影响离线构建）。

备用收敛脚本（**本次未触发，故未验证其效果，不得写为「已验证」**），供 T01 遇到该坑时使用：

```bash
# 1) 在线 mvn 触发下载；2) 把 tmp 复制为正式 .pom / .jar；3) mvn -o 迭代收敛
python3 - <<'PY'
import glob, os, re, shutil
root = "<本地构建仓库>"
for p in glob.iglob(root + "/**/*", recursive=True):
    if not os.path.isfile(p):
        continue
    m = re.search(r"\.(pom|jar)\.\d+\.tmp", os.path.basename(p))
    if m:
        target = p[: p.rfind("." + m.group(1) + ".")] + "." + m.group(1)
        shutil.copyfile(p, target)
PY
mvn -o -B package   # 重复至 BUILD SUCCESS
```

另：`mvn install:install-file ... -DlocalRepositoryPath=...` 在本次沙箱中命令被中断（EXIT 137，无输出），故临时 `file://` 仓库改用**等价手工布局**（`group/artifact/version/` 下放 `.jar` + 上游 `.pom`）验证；`COMMIT.lock` 仍按设计记录 `install-file` 为 T01 的落地方式，T01 实际执行时应自行确认该命令在本机可用。手工布局因缺 `.sha1` 会产生 `Checksum validation failed, no checksums available from bee-vendor` 的 WARNING，`install-file` 会自动生成校验和，不属缺陷。

---

## 4. S-1.3 · `<exclusions>` 与 `mvn -o package`

在临时目录搭建**与 T01 计划等价**的消费者工程（不写入仓库）：`file://${project.basedir}/vendor-repo` + `openize:openize-heic:26.5.1`（带 `<exclusions>` 排除 `com.drewnoakes:metadata-extractor`）+ 显式 `com.drewnoakes:metadata-extractor:2.19.0`，并有一个真实引用 `HeicImage` / `ImageRotation` / `com.drew.metadata.Metadata` 的 `Probe.java` 参与编译。

```bash
mvn -o -B -Dmaven.repo.local=<临时构建仓库> clean dependency:tree package
# EXIT=0, BUILD SUCCESS, 1.875s
```

离线依赖树：

```
com.beefamily:spike-s1-consumer:jar:0.0.1
+- openize:openize-heic:jar:26.5.1:compile
\- com.drewnoakes:metadata-extractor:jar:2.19.0:compile
   \- com.adobe.xmp:xmpcore:jar:6.1.11:compile
```

| 核对项 | 结果 |
| --- | --- |
| `metadata-extractor` 版本数 | **1 个（2.19.0）**；日志中 `2.20.0` 出现 **0 次** |
| `xmpcore:6.1.11` | 2.19.0 的正常传递依赖，非版本冲突 |
| `file://${project.basedir}/vendor-repo` 插值 | **成功**，日志展开为 `file:///private/tmp/.../consumer/vendor-repo/...`（设计 §9.2 写法可用） |
| 离线纯度 | 离线日志中 `Downloading from Central` 出现 **0 次** |
| 编译链接 | `Compiling 1 source file with javac [debug release 17]`，三类引用全部解析成功 |

> 首轮离线曾失败：`Failed to execute goal maven-clean-plugin ... maven-shared-utils / commons-io has not been downloaded`。判定为**测试脚手架自身问题**（我在脚手架里多加了 `clean` 目标，与 HEIC 依赖无关）→ 按 QA 路由自行修正：在线 `mvn clean` 预热插件后重跑即 EXIT=0。**非源码缺陷，未向工程师报 bug**。

---

## 5. S-1.4 · 纯 Java / 无 native 依赖

| 层面 | 方法 | 结果 |
| --- | --- | --- |
| 源码静态 | 全仓 119 个 `.java` + 上游 `pom.xml`，词边界扫描 | `System.load` / `System.loadLibrary` / `com.sun.jna` / `net.java.dev.jna` / `JNI` / `native` 关键字 / `.dll` `.dylib` `.so` `libheif` `libde265` `x265`：**0 命中** |
| POM | `<classifier>` 全量检查 | 仅 `org.jacoco.agent:0.8.12:runtime`，**test scope**，非 native classifier |
| jar 二进制 | zip 条目按扩展名扫描 | **native 二进制 0 个** |
| 依赖树 | `dependency:tree` | 无 `jna` / `jna-platform` / 任何 `*natives*` classifier |
| JVM 运行期 | `java -Xlog:library=info -verbose:class spike.Probe` | 加载的 native 库只有 JDK 自身 `<JAVA_HOME>/lib/libnio.dylib` 与 `libzip.dylib`；`com.sun.jna` / `net.java.dev.jna` 类加载 **0 次**；`openize.*` 与 `com.drew.metadata.*` 均来自 jar 内的 `.class` |

探针同时打印 `com.drew.metadata.Metadata` 的 `CodeSource` = `.../metadata-extractor/2.19.0/metadata-extractor-2.19.0.jar`，再次佐证 EXIF 版本唯一。结论与 `06` §9.4「UI 不得出现『仅 macOS 可用』『需安装系统工具』」一致。

---

## 6. S-1.5 · 单张自拍 HEIC 解码（原标注「暂未执行」，本次补做）

原片：`docs/qa/heic-fixtures/IMG_8974.HEIC`（用户自拍，iPhone 12 Pro Max / iOS 18.7.8，4032×3024，`irot=3` → 270° CW，Exif `Orientation=6`，无 `imir`；SHA-256 `4454368df773c7692e6965200a5f1333f11e4eb740c049fe7878b5b94bee48b1`）。**只读该原片，不写任何图片文件、不调用系统命令、不生成夹具。**

探针（临时目录，JDK 17，classpath = vendored jar + 2.19.0 + xmpcore）：

```java
try (IOFileStream fs = new IOFileStream(path, IOMode.READ)) {
    HeicImage image = HeicImage.load(fs);
    long w = image.getWidth(), h = image.getHeight();
    int[] px = image.getInt32Array(PixelFormat.Argb32);
    BufferedImage bi = new BufferedImage((int) w, (int) h, TYPE_INT_ARGB);
    bi.setRGB(0, 0, (int) w, (int) h, px, 0, (int) w);
}
```

实测输出（`DECODE_EXIT=0`，无任何异常）：

| 指标 | 实测值 | 判读 |
| --- | --- | --- |
| `container.width × height` | **3024 × 4032** | 原 `ispe` 为 4032×3024 → **宽高已按 `irot=3` 互换** |
| `frame.toString()` | `[49,grid] prof ([8,8,8]) 4032x3024 **270°** Derived: [...]` | 上游明确记录并应用了 270° 旋转 |
| `frames` | 1 | 单帧 |
| `pixels.length` | 12,192,768 = 3024 × 4032 | 与正立后尺寸完全吻合，无部分解码 |
| `nonBlackRatio` | **1.000000**（12,192,768 / 12,192,768） | 非全黑 |
| `distinctColors`（抽样上限 20000） | 7,013 | 非空白 / 非纯色 |
| `avgRGB` | 167, 163, 153 | 符合自然光照片量级 |
| `BufferedImage` | 3024 × 4032 | 构造成功 |

### 关键结论（必须转达 T03）

**26.5.1 在 `HeicImage.load()` / `getInt32Array()` 内部已把容器 `irot`（本片 270°）一次性应用到输出像素与 `getWidth/getHeight`；且它只按 `irot` 校正，没有再按 Exif `Orientation=6` 补转（否则尺寸/内容会不一致）。** 因此：

- `OpenizeHeicTranscoder#decode()` **不得**再执行任何旋转（既不要 `irot` 也不要 Exif `Orientation`），只负责把上游结果包装成 `HeicDecodeResult`；`rotationApplied` 仅作审计，语义为「由上游完成」。
- `width/height` 取正立 `BufferedImage` 实测值（3024×4032）即满足 §9.6 约束 5，**不得**取 `ispe` 盒的 4032×3024。
- `photo_exif.orientation` 仍只入库 / 审计，不参与旋转（本片 `Orientation=6` 与 `irot=3` 同时非 1，正是 §9.6 F-4 的双重旋转陷阱样本）。
- 两档派生图一律从这张正立 `BufferedImage` 生成，`ThumbnailService` 不做二次方向校正（§9.6 约束 6）。

> **边界**：本判据只证明「能解码、尺寸与 `irot` 语义一致、非黑非空白」。**视觉上是否正立**需人工/图像比对，属 **S-2**；`irot` 90° / 180° 方向仍缺 F-1 / F-2 夹具。本报告不把 S-2 判据说成已通过。

---

## 7. S-1.6 · 备选 B 对照（对照后移除）

| 项 | 备选 A（采用） | 备选 B（仅对照） |
| --- | --- | --- |
| 坐标 / 来源 | `openize:openize-heic:26.5.1`，GitHub 锁 commit 自建 | `com.aspose:openize-heic:25.9`，`https://releases.aspose.com/java/repo/` |
| 可获取性 | 源码可锁 commit，产物入库离线可构建 | POM HTTP 200、jar HTTP 200（242,033 B，SHA `55c4bb8a…`）；依赖外部私有 repo 可达性 |
| Maven Central | 404 | 404 |
| 字节码 major | **61（Java 17，release=17 自建）** | 52（Java 8） |
| native 二进制 | 0 | 0 |
| 含 `openize/isobmff` | 是（同 jar） | 是（同 jar） |
| POM `<dependencies>` | 有（含 `metadata-extractor:2.20.0`） | **无**，且不提及 `metadata-extractor` → **无法用 `<exclusions>` 统一 EXIF 版本**，也无法保证 EXIF 链路可用（26.5.0 才加入 EXIF 支持） |
| 处置 | ✅ 采用 | **仅 spike 对照，制品已从临时目录删除；未写入、也不得写入交付 `pom.xml` / 依赖树 / 仓库** |

另外核对 `openize:openize-heic:25.4`（官方产品页坐标）POM 亦 200；`25.9` / `25.4` / `groupId` 两种写法并存，印证设计 §9.2「坐标不一致，不作为唯一来源」的终裁。清理确认：临时目录内已无 `aspose*` 文件。

---

## 8. 合规交付（用户拍板口径：登记风险、仅本地/内部自用、不对外提供服务）

| 交付 | 路径 | 内容 |
| --- | --- | --- |
| 构建锁定 | `backend/lib/COMMIT.lock` | commit / 完整构建命令 / JDK 17 + Maven 3.9.9 / 产物 SHA-256 / 可复现方案 / 上游 POM 偏差 / 许可证来源 / 环境说明 |
| 许可文本 1 | `backend/lib/licenses/Openize.txt` | Openize License（BSD-2-Clause + Third-party patents clause），2,534 B，SHA `c1db8e65…` |
| 许可文本 2 | `backend/lib/licenses/IsoBmff-MIT.txt` | MIT License，1,098 B，SHA `fd8559cb…` |
| 许可文本 3 | `backend/lib/licenses/MetadataExtractor-Apache-2.0.txt` | Apache License 2.0，11,364 B，SHA `8bbe16f5…`（取自 tag `2.19.0`，对应项目锁定版本） |
| 合规声明 | `backend/README.md` | ①组件与锁定 commit；②三份许可证名称/路径/来源 URL；③**HEVC/H.265 专利许可不在上游授权范围内、部署方需自行确认**（L-1 / R-04）；④夹具为自拍 iPhone 照片、未含任何第三方受限样本（L-2）；⑤纯 Java 无 native；⑥仅本地/内部自用、不对外提供 HEIC 转码服务 |

三份许可证均为**逐字原文**（保留上游 CRLF），来源 URL 与锁定 commit 记录在 `COMMIT.lock` §6 与 `backend/README.md` §2，不改动原文、不以单一 SPDX 概括（L-3）。上游 `TestsData/samples/nokia/`（含 12 个 `.heic`/`.heif` 等共 31 个文件、约 320 MB）仅存在于临时 tarball 展开目录，**从未复制进仓库**。

---

## 9. 夹具目录骨架状态

`docs/qa/heic-fixtures/README.md` **在本次执行前已由其他 QA 落地**（含 F-0…F-6 清单、`irot`/Exif 实测、来源约束、隐私与入库状态、`sips` 边界），内容已覆盖任务书要求的 F-1…F-5 规格、命名/方向要求、来源约束（自拍 iPhone 原片、禁 Nokia 样本、禁 `sips`/`ffmpeg` 等系统命令生成）。为**避免覆盖他人成果**，本次只做 4 处最小事实修正，均为已被本 spike 或已提交豁免推翻的表述：

| # | 原表述 | 修正为 |
| --- | --- | --- |
| 1 | 「`openize-heic-java 26.5.1` 尚未锁定 commit、未离线构建（QA 前置任务因网络流超时失败，零交付物）」 | 补齐锁定 commit、离线构建通过、`<exclusions>` / 无 native / 单张解码结果，并强调只满足 S-1 判据 |
| 2 | 「因此 **S-1…S-5 尚未执行**」 | 「S-1 的构建 / 单张解码判据已执行并通过，S-2…S-5 仍未执行」 |
| 3 | 「放行口径待用户明确；在此之前不启动 T01」 | 同步 `07` §9.8.1 已批准豁免（仅豁免 5/5 阻断约束，判据与 6 条约束不豁免，R-14/R-15 上线前 T05 补验） |
| 4 | 「S-1…S-5 尚未执行，T01 未启动」 | 「S-1 构建与单张解码判据已通过；S-2…S-5 尚未执行，按 §9.8.1 豁免上线前由 T05 补验」 |

夹具到位情况（沿用现有 README）：**1 / 7 张**，`IMG_8974.HEIC` 覆盖 F-0 / F-3（`irot=3`）/ F-4（组合）；F-1 / F-2 / F-5 / F-6 由用户 2026-11-06 决定跳过。原片未被 git 跟踪（`.gitignore` 忽略 `*.heic` / `*.HEIC`）。

---

## 10. S-3 / S-4 可独立准备（**仅方案，未写业务代码、未执行**）

### 10.1 Thumbnailator 0.4.20 · 从正立 `BufferedImage` 生成 1600px / 480px 两档 JPG

输入固定为 §6 得到的**正立** `BufferedImage`（`irot` 已由上游应用），不是 HEIC 原文件、不是 `ispe` 盒尺寸。

验证方案（关键 API 与步骤，非实现代码）：

| 步 | 动作 | 要点 |
| --- | --- | --- |
| 1 | `Thumbnails.of(BufferedImage)` 构建 builder | **禁止** `Thumbnails.of(heicFile)` —— Java ImageIO 读不了 HEIC |
| 2 | `.size(1600, 1600)` / `.size(480, 480)` | `size()` 是「不超过」语义：长边精确等于阈值；本片 3024×4032 → 1200×1600 / 360×480；横图 4032×3024 → 1600×1200 / 480×360 |
| 3 | `.imageType(BufferedImage.TYPE_INT_RGB)` | 避免 `TYPE_INT_ARGB` 写 JPG 时抛 `Bogus input colorspace` / 产生异常文件 |
| 4 | `.outputFormat("jpg").outputQuality(<架构统一值>)` | 质量参数由 `07` §9.4 统一为一个值，UI 不展示、不提供调节 |
| 5 | `.toOutputStream(...)` → 写 `display` / `thumb` 两档 | 由 `BufferedImage` 重编码**天然 strip 全部元数据（含 GPS）**，无需额外剥离代码（§9.7） |
| 6 | 用 `ImageIO.read()` 读回两档 | 验证 JPG 可被浏览器直接打开 |

**禁止项**：不调用 Thumbnailator 的 `.rotate()` / `.scale()` 做任何方向校正；不读 Exif `Orientation` 或 `irot`；不从 `ispe` 原始宽高推算输出尺寸；不引入第 4 档文件。

判据清单（对应 S-3）：

| # | 判据 | 核对方法 |
| --- | --- | --- |
| 1 | 两档长边精确 1600 / 480 | `ImageIO.read()` 后 `max(w,h)`；竖图 1200×1600 / 360×480 |
| 2 | JPG 可被浏览器直接打开 | 魔数 `FF D8 FF`、`Content-Type: image/jpeg`、`ImageIO.read()` 非 null |
| 3 | **原 HEIC 字节 hash 前后一致** | 上传前后 `shasum -a 256` 比对 `IMG_8974.HEIC`（`4454368d…`），原图不做有损覆盖 |
| 4 | `display_key == preview_key` 指向同一物理文件 | 见 10.2 |
| 5 | 三档文件数不出现第 4 个 | `storage_key` / `preview_key(=display_key)` / `thumb_key` 共 3 个文件 |
| 6 | 两档从正立 `BufferedImage` 生成 | 与 §6 的 3024×4032 输出比对，宽高比一致、无二次旋转 |
| 7 | 派生 JPG 已被 strip | 用 `metadata-extractor` 读派生 JPG 应无 GPS / Exif 目录（与 S-4 联动） |

### 10.2 `display_key == preview_key` 字段核对清单

| 字段 | HEIC 期望值 | 核对位置 / 依据 |
| --- | --- | --- |
| `source_format` | `'heic'` | `07` §9.4；V2 迁移回填历史照片为 `'jpeg'` |
| `storage_key` | `{uuid}.heic`，**原始字节完整保留** | 「下载原始文件（HEIC）」入口；EXIF 唯一读取源（H-7） |
| `preview_key` | `{uuid}_preview.jpg`（1600px JPG） | 与 `display_key` 在**同一次写入赋同值** |
| `display_key` | **== `preview_key`**（同值语义句柄） | `07` §9.4「不产生第四档」；前端 `displayKey !== null` 即判定 HEIC |
| `thumb_key` | `{uuid}_thumb.jpg`（480px JPG） | 网格 / 时间轴 / 封面 |
| 物理文件数 | **恰好 3** | 原图 + 1600px JPG + 480px JPG；不出现第 4 个 |
| 非 HEIC 对照 | `display_*` 全为 `NULL` | 前端单按钮「下载原图」→ `storageKey`（`06` §8.6） |
| 下载语义 | 默认 `displayKey`（1600px JPG）；「下载原始文件（HEIC）」→ `storageKey` | `06` §8.6 双按钮，无「查看原图」 |
| 灯箱 / 默认下载 | 走 `displayKey` | `06` §8.6；浏览器无法渲染 HEIC |
| EXIF 数据源 | **恒为 `storageKey` 原始字节**，派生 JPG 不参与 | `07` §9.3 / §9.7 / C-09 |
| 媒体类型 | `.heic → image/heic` | `resolveMediaType`（K-9） |
| 目录 | `{familyId}/{yyyy}/{MM}/{uuid}.{ext}` 不变 | K-9 |
| 失败补偿 | `writtenKeys` 三档全部 `deleteQuietly`，不落库、不进 `successes[]` | `07` §9.5 / S-5 |

### 10.3 EXIF 19 字段与 `parse_status` 四态映射表

**19 个核心字段**（`07` §9.3 `photo_exif` 全列；`software` / `modify_time` 为辅助展示不计入 19；`width` / `height` / `fileSize` 由 `ExifResp` 从 `photo` 投影，不存 `photo_exif`）：

| # | 字段 | 来源 / 注意 |
| --- | --- | --- |
| 1 | `make` | Exif IFD0 |
| 2 | `model` | Exif IFD0 |
| 3 | `lens_model` | Exif SubIFD |
| 4 | `f_number` | Exif SubIFD |
| 5 | `exposure_time` | Exif SubIFD |
| 6 | `iso` | Exif SubIFD |
| 7 | `focal_length` | Exif SubIFD |
| 8 | `focal_length_35mm` | Exif SubIFD |
| 9 | `exposure_bias` | Exif SubIFD |
| 10 | `exposure_mode` | Exif SubIFD |
| 11 | `metering_mode` | Exif SubIFD |
| 12 | `white_balance` | Exif SubIFD |
| 13 | `flash` | Exif SubIFD |
| 14 | `orientation` | **仅入库 / 审计，不参与旋转**（§9.6 约束 3）；本片为 6 |
| 15 | `offset_time_original` | Exif SubIFD |
| 16 | `gps_latitude_raw` | **十进制度原文**，UI 展示唯一数据源；不四舍五入 / 不补零 / 不换算度分秒 / 不改符号 |
| 17 | `gps_longitude_raw` | 同上 |
| 18 | `gps_altitude` | GPS 原文 |
| 19 | `original_file_name` | **来自 multipart 原始文件名，不是 EXIF 字段**（如 `IMG_4822.HEIC`）；四态下都应有值，是核对「解析失败仍不丢文件名」的关键 |

**`parse_status` 四态**（`07` §9.9）：

| 状态 | 判定条件 | 灯箱文案（逐字） |
| --- | --- | --- |
| `ok` | 成功解析到至少 1 个 EXIF 目录且字段可读 | 正常展示 19 字段 + GPS 精确原文 |
| `absent` | 无 EXIF 目录（截图 / 二次导出 / 社交平台转存） | 「该照片没有拍摄参数信息」 |
| `stripped` | 检测到元数据被移除标志 | 「拍摄参数已被移除」 |
| `failed` | 解析抛异常 / 不支持的容器 | 「暂时无法读取拍摄参数」 |

**19 字段 × 四态映射**（`✓` 正常取值，`—` 为空 / 不展示，`△` 见备注）：

| 字段组 | `ok` | `absent` | `stripped` | `failed` | 备注 |
| --- | --- | --- | --- | --- | --- |
| #1–13, #15（拍摄参数 14 项） | ✓ | — | — | — | 四态文案由 `parse_status` 驱动，不逐字段报错、不空白异常 |
| #14 `orientation` | ✓（落库值不变） | — | — | — | **任何状态下都不得参与旋转计算**；`HeifDirectory.TAG_IMAGE_ROTATION` 同 |
| #16–18 `gps_*_raw` / `gps_altitude` | ✓ 精确原文 | — | — | — | 展示真源为 DB 原文；派生 JPG 被 strip **不影响**展示；不接地理编码、不提供用户开关（Q3 / ADR-09） |
| #19 `original_file_name` | ✓ | **△** | **△** | **△** | 取自 multipart 文件名而非 EXIF → 四态下均应保留；若为空说明入库链路把「解析失败」误扩散到了业务字段，属缺陷 |
| 状态标志 `has_exif` | `true` | `false` | `false` | `false` | 驱动 §9.9 文案矩阵 |
| 状态标志 `exif_stripped` | `false` | `false` | `true` | `false` | — |
| 状态标志 `taken_at_from_exif` | ✓ / `false`（回落 `DateTimes.now()`） | `false` | `false` | `false` | `photo.taken_at` best-effort 派生，失败不得阻断上传 |
| `photo_exif` 行 | **写入** | **写入**（状态行） | **写入**（状态行） | **写入**（`parse_status='failed'` 一行） | 任何状态都必须有且仅有一行，绝不外抛 |

**S-4 待验判据（本次只整理方案，未执行）**：① 19 字段可读；② `gps_*_raw` 原文精确无改写；③ 派生 JPG 已被 strip；④ 损坏 / 无 EXIF 样本解析失败时 `parse_status` 正确且**不阻断上传、不产生 5xx、不导致入库失败**；⑤ 历史 56 张不回填（灯箱按「无 EXIF」文案展示）；⑥ `ExifService#readFromOriginal` 捕获 `Throwable` 返回 `failed`，绝不外抛；⑦ 读取时机在写盘成功后、转码前，源为原始 `byte[]`。

---

## 11. 环境受限项与如实标注

| # | 项 | 状态 |
| --- | --- | --- |
| 1 | 任务书所述 `*.pom.<digits>.tmp...` / `*.jar.<digits>.tmp...` rename 限制 | **本次未复现**（`*.tmp` 残留 0 个，仅 2 个 `.lastUpdated`）。备用收敛脚本已在 §3.5 给出但**未触发、未验证效果**，不得写为「已验证」 |
| 2 | `mvn install:install-file` 落地 `file://` 仓库 | 本次命令在沙箱被中断（EXIT 137），**未验证该命令**；改用等价手工布局完成 `mvn -o package` 验证。T01 采用 `install-file` 时需自行确认 |
| 3 | GitHub 直连 | `https://github.com/...` 首次 `CONNECT tunnel 502`；经环境代理 `api.github.com` / `raw.githubusercontent.com` / `releases.aspose.com` 均 200，源码与许可证均取自这些可达路径 |
| 4 | F-1 / F-2 / F-5 / F-6 夹具 | **缺**（用户 2026-11-06 决定跳过）→ S-2 的 90°/180° 方向、S-5 的 P/B slices Live Photo 与人为损坏样本**未验证** |
| 5 | 「解码为正立 `BufferedImage`」中的**视觉正立性** | 只证明尺寸与 `irot` 语义一致、非黑非空白、上游 `toString()` 打印 270°；**未做人工视觉比对**，归 S-2 |
| 6 | S-3 / S-4 | **只写方案（§10），未执行**；两档 JPG、`display_key == preview_key`、EXIF 19 字段实测均待 T05 / 后续门槛 |
| 7 | 端到端上传链路 | 未执行；项目 `ImageValidator` 仍按 ADR-01 拒绝 HEIC（后端源码本次未改动），本报告的解码结论来自**独立 JVM 探针**，不是服务端接口结果 |
| 8 | 文件归属冲突 | `software-engineer` 声称 T01 也负责 `COMMIT.lock` / `licenses/` / `backend/README.md`；已报 `team-lead` 裁决，本任务按派单落盘（见 §1） |
| 9 | `docs/qa/heic-fixtures/README.md` 与 §9.8.1 豁免 | 本次已同步 4 处滞后表述；其余内容（F-1/F-2/F-5/F-6 跳过表、技术备注）保持原样 |
| 10 | T01 实现产物 | **未写入** `backend/lib/openize-heic-26.5.1.jar`、`backend/lib/maven-repo/**`、`backend/pom.xml` 依赖声明；全部验证留在 `/tmp/heic-spike-s1-20261105-190123/` |

> 按 QA 路由：本次无源码缺陷需回退工程师（Send To: NoOne）；首轮离线失败属测试脚手架自身问题，已由 QA 自行修正（Send To: QA self）。

---

## 12. 剩余阻塞清单

| # | 阻塞 | 影响 | 建议动作 |
| --- | --- | --- | --- |
| B-1 | F-1 / F-2 夹具缺（90° / 180° `irot`） | S-2 只能覆盖 `irot=3`；R-14 | 按 §9.8.1 上线前 T05 补验；现有 README 已给出「复制本片仅改容器 `irot` 字节」的技术备注（仍属自拍原片派生，不引入第三方样本） |
| B-2 | F-5 / F-6 夹具缺（P/B slices Live Photo、人为损坏） | S-5 的 `50003` 降级不黑图 / 不落库 / 无盘残留未验证；R-15 | 上线前 T05 补验；F-6 可由 `IMG_8974.HEIC` 截断派生，F-5 需含 P/B slices 的编码数据 |
| B-3 | `irot` 是否需 Java 侧补旋转的**实现决策** | 若 T03 在 `decode()` 内二次旋转 → 双重旋转（本片已实证上游自动应用） | **转达 T03/T01**：`OpenizeHeicTranscoder#decode()` 不再旋转，`rotationApplied` 标记为「由上游完成」，宽高取正立 `BufferedImage` 实测 |
| B-4 | `openize.isobmff` 无独立 artifact；上游 `metadata-extractor` 为 2.20.0 | 设计 §9.2 / §11.6 描述偏差；`<exclusions>` 必填 | 转达 T01：只 vendor 1 个 jar；按 `COMMIT.lock` §5 落地 |
| B-5 | 产物字节级可复现 | 不设 `outputTimestamp` 时两次构建 SHA 不同 | T01 采用 `COMMIT.lock` §3.2 的固定时间戳命令，以 SHA `23e47620…` 为准 |
| B-6 | `file://` 仓库 `install-file` 命令在本机被沙箱中断 | T01 落地方式待确认 | 现场验证；备用手工布局 + 校验和生成方案见 §3.5 |
| B-7 | S-3 / S-4 未执行 | 两档 JPG、字段核对、EXIF 19 字段与四态仅方案 | 按 §10 方案在 T05 执行 |
| B-8 | 文件归属冲突（§1 / §11-8） | 可能与 T01 重复落盘同一批文件 | `team-lead` 裁决；我已明确只写 `COMMIT.lock` / `licenses/` / `backend/README.md` / `docs/qa/**` |
| B-9 | 合规放行 | HEVC/H.265 专利许可不在上游授权范围（R-04） | 部署方上线前自行确认；本项目仅本地/内部自用、不对外提供转码服务（`backend/README.md` §3 §4） |

---

## 13. 交付文件清单

| 路径 | 类型 | 说明 |
| --- | --- | --- |
| `backend/lib/COMMIT.lock` | 新增 | 锁 commit + 完整构建命令 + 环境 + SHA-256 + 合规来源 |
| `backend/lib/licenses/Openize.txt` | 新增 | Openize License 逐字原文 |
| `backend/lib/licenses/IsoBmff-MIT.txt` | 新增 | MIT 逐字原文 |
| `backend/lib/licenses/MetadataExtractor-Apache-2.0.txt` | 新增 | Apache-2.0 逐字原文（tag 2.19.0） |
| `backend/README.md` | 新增 | 合规声明 |
| `docs/qa/heic-fixtures/README.md` | **修改（4 处最小事实修正）** | 原文件由其他 QA 落地，不覆盖重写 |
| `docs/qa/spike-s1-report.md` | 新增 | 本报告 |

**未写入**：`backend/lib/openize-heic-26.5.1.jar`、`backend/lib/maven-repo/**`、`backend/pom.xml`（T01 实现产物）；任何 `.heic` / `.heif` 样本；`docs/05`、`docs/06`、`docs/07`。

---

## 14. 门槛状态（供 §12.3 自检清单回填）

| 门槛 | 状态 |
| --- | --- |
| **S-1** | ✅ **通过（6/6 子判据）**，本报告 |
| S-2 | ⚠️ 部分覆盖（仅 `irot=3` / F-4 组合；缺 F-1/F-2；视觉正立性未人工判读） |
| S-3 | ⏳ 未执行（方案见 §10.1 / §10.2） |
| S-4 | ⏳ 未执行（方案见 §10.3） |
| S-5 | ⚠️ 缺 F-5 / F-6，未执行 |

**5 项 P0 门槛整体仍非 5/5**。按 `07` §9.8.1 用户豁免，可直接进入 T01–T05 全量实现；但 **S-1…S-5 判据本身、§9.6 六条约束、`50003` 降级不黑图不静默成功均不豁免**，R-14 / R-15 须在**上线前 T05 补验，不通过不得放行生产**。
