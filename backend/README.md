# backend · 合规声明与 HEIC 依赖说明

> 本文只记录**第三方组件引入与合规事实**，不是启动/配置文档（见仓库根 `README.md`）。
> 对应设计：`docs/07-architecture-incremental.md` §9.2（L-1 / L-2 / L-3）、§11.6、§12.1（R-04 / R-06 / R-07）；
> 验证记录：`docs/qa/spike-s1-report.md`；可复现构建细节：`backend/lib/COMMIT.lock`。

## 1. 引入组件与锁定版本

本项目为支持 HEIC 上传，引入 **Openize.HEIC for Java 26.5.1**（纯 Java HEIF/HEIC 解码器，备选 A：源码自建 + 锁 commit + vendor 到项目内 `file://` 仓库）。

| 项 | 值 |
| --- | --- |
| 上游仓库 | `https://github.com/openize-com/openize-heic-java` |
| Maven 坐标 | `openize:openize-heic:26.5.1` |
| 锁定 tag | `v26.5.1`（lightweight tag） |
| **锁定 commit** | **`1118f3c45a47a60cde593a6c47848b2a3a961b9a`**（2026-05-12T05:00:40Z，与上游 `main` HEAD 一致） |
| 源码 tarball SHA-256 | `4fc31604b7afee5ee358d3c2234856f801fa27eb843cda3a44682ba6c657a9dc` |
| 自建环境 | JDK 17.0.15 + Maven 3.9.9，`-Dmaven.compiler.release=17`，字节码 major 61 |
| 产物 | `openize-heic-26.5.1.jar`，固定 `outputTimestamp` 后 SHA-256 `23e476205b89570c5eba4a62551b14a3a360570f3ca2db85578db44969425ddf` |

该组件**不在 Maven Central**（`openize:openize-heic` 与 `com.aspose:openize-heic` 均 404），故不按常规 `<dependency>` 直取；由源码锁 commit 自建并 vendor。备选 B（Aspose 仓 `com.aspose:openize-heic:25.9`）仅在 S-1 spike 中作对照，比对后即移除，**未进入、也不得进入交付 `pom.xml` 与依赖树**。

传递依赖口径：上游 POM 声明 `com.drewnoakes:metadata-extractor:2.20.0`，项目统一锁定 **2.19.0**，因此依赖声明必须用 `<exclusions>` 排除上游版本，保证 EXIF 解析全局只有一个版本。另外 `openize.isobmff` 在 26.5.1 是**同仓源码、与 `openize.heic` 编进同一个 jar**，不存在独立 artifact，故只需 vendor 1 个 jar。

## 2. 三份许可证（组合非标准 SPDX，逐条列明）

三份文本逐字归档于 `backend/lib/licenses/`，均取自锁定 commit / 锁定版本对应的上游原文；**不以单一 SPDX 标识概括**。

| # | 组件 | 许可证 | 归档路径 | 来源 URL |
| --- | --- | --- | --- | --- |
| 1 | Openize.HEIC（主） | **Openize License**（BSD-2-Clause + Third-party patents clause） | `backend/lib/licenses/Openize.txt` | `https://raw.githubusercontent.com/openize-com/openize-heic-java/v26.5.1/LICENSE` |
| 2 | Openize.IsoBmff | **MIT License** | `backend/lib/licenses/IsoBmff-MIT.txt` | `https://raw.githubusercontent.com/openize-com/openize-heic-java/v26.5.1/licenses/Openize.IsoBmff/LICENSE` |
| 3 | MetadataExtractor | **Apache License 2.0** | `backend/lib/licenses/MetadataExtractor-Apache-2.0.txt` | `https://raw.githubusercontent.com/drewnoakes/metadata-extractor/2.19.0/LICENSE` |

> 三份文本的 SHA-256 与逐字来源明细见 `backend/lib/COMMIT.lock` §6；上游 `publish/LICENSE.txt`（打进 jar 的 `META-INF/LICENSE.txt`）与 #1 内容一致，仅换行符差异。

## 3. HEVC/H.265 专利许可不在上游授权范围内（L-1 / R-04）

上游 `LICENSE` 的 **THIRD-PARTY PATENTS** 条款与 README 明确写明：

> "Openize does not and cannot grant You a patent license for the utilization of HEVC/H.265 image compression/decompression technologies."

即：**HEVC/H.265 图像压缩/解压缩技术所涉第三方专利许可，不在 Openize 的授权范围内**。若部署/分发使用这些技术的最终用户软件，由部署方自行取得所需第三方专利许可，并自行承担相应专利主张与赔偿义务。

这是**支持 HEIC 解码本身固有的风险**，不是选备选 A 或 B 可以规避的，已按设计登记为显式风险 **R-04**。**部署方须自行确认 HEVC/H.265 专利授权状态**；本项目不提供、也不代表任何专利许可或法律意见。

## 4. 使用定位（用户已拍板口径）

- **登记风险**：上述专利许可风险与 §9.8.1 门槛豁免风险（R-14 / R-15）均已在 `docs/07` §12.1 登记，不隐瞒、不淡化。
- **仅本地 / 内部自用**：本项目定位为家庭相册的本地 / 内部自部署使用，不对外提供 HEIC 转码服务、不开放公共 HEIC 转码 API、不将解码能力作为对外可售能力输出。
- 上线前须由部署方结合上述第 3 节自行完成合规确认；本声明不构成放行结论。

## 5. 测试夹具来源（L-2）

- HEIC 夹具规格与来源约束见 `docs/qa/heic-fixtures/README.md`；**全部来自本项目自行拍摄的 iPhone 照片**。
- **未包含任何第三方受限样本**，尤其**未打包上游 Openize.HEIC 仓库 `TestsData/samples/nokia/` 下的 Nokia 测试样本** —— 其 OSS Notice 仅授权非商业评估 / 测试 / 学术研究。
- 夹具**不得**用 `sips` / `ffmpeg` / `heif-convert` / `magick` 等系统命令生成或转换（Q2 口径）。
- 已到位的自拍原片含真实精确定位，仅本地留存、未加入版本控制（`.gitignore` 忽略 `*.heic` / `*.HEIC`）。

## 6. 纯 Java、无 native 依赖声明

| 验证项 | 结论 |
| --- | --- |
| `openize-heic-java 26.5.1` 全量源码（119 个 `.java`）+ 上游 POM 扫描 | `System.load` / `System.loadLibrary` / JNA / JNI / `native` 关键字 / `.so` `.dll` `.dylib` `libheif` 字符串：**0 命中** |
| 依赖树 | 唯一 `<classifier>` 是上游 **test scope** 的 JaCoCo agent runtime，**无 native classifier** |
| jar 内容 | 145 entries / 134 classes，**jar 内 native 二进制文件数 = 0** |
| JVM 运行期 | `-Xlog:library=info -verbose:class` 探针：加载的 native 库仅 JDK 自身 `libnio.dylib` / `libzip.dylib`；JNA 类加载 **0 次**，无第三方 native 库加载 |

即：HEIC 解码为**服务端纯 Java 链路**，不依赖任何操作系统专属命令、不依赖 `libheif` / JNI / JNA、不加载任何部署环境相关的 native 库；与 `docs/06` §9.4 的全站文案禁令一致（UI 不得暗示系统专属，也不得要求额外安装系统工具）。

## 7. 文件归属

| 文件 | 说明 |
| --- | --- |
| `backend/lib/COMMIT.lock` | 锁 commit + 完整构建命令 + JDK/Maven 版本 + 产物 SHA-256（S-1 spike 产出） |
| `backend/lib/licenses/*.txt` | 三份许可证原文（S-1 spike 产出） |
| `backend/README.md` | 本合规声明（S-1 spike 产出） |
| `backend/lib/openize-heic-26.5.1.jar`、`backend/lib/maven-repo/**`、`backend/pom.xml` 的依赖声明 | 由 **T01** 按 `docs/07` §9.2 / §11.1 落地；S-1 spike 仅在临时目录做等价验证，未提前写入实现产物 |
| `scripts/bootstrap-vendor.sh` | T01 交付的离线构建前置脚本（见第 8 节） |

## 8. 离线构建前置（全新环境必读）

`pom.xml` 通过 `<repositories>` 以 `file://${project.basedir}/lib/maven-repo` 引用 vendor 目录，使依赖保持普通 compile scope、`<exclusions>` 才能生效。但 Maven 的 `-o`（offline）会禁用**所有** remote repository —— `file://` 也不例外，只允许读「本地仓库里已带正确 `_remote.repositories` 标记」的 artifact。因此**全新 clone、本地仓库尚无 `openize` 时，首次直接跑 `mvn -o package` 会失败**：

```
Cannot access bee-vendor (file://.../backend/lib/maven-repo) in offline mode and the
artifact openize:openize-heic:jar:26.5.1 has not been downloaded from it before.
```

两条等价路径，任选其一（**都不需要外网**，`file://` 是本地文件系统）：

```bash
# A) 幂等脚本：把 vendored 产物装进 Maven 本地仓库（推荐，无需 Maven 插件解析）
bash scripts/bootstrap-vendor.sh

# B) 先跑一次不带 -o 的构建，让 Maven 自行从 file:// 仓库「下载」一次
cd backend && mvn -DskipTests package
```

之后即可执行验收命令：

```bash
cd backend
mvn -o package -DskipTests
mvn -o test
```

`bootstrap-vendor.sh` 会依次探测显式参数、`MAVEN_REPO_LOCAL` 环境变量、`~/.m2/settings.xml` 的 `<localRepository>`、`~/.m2/repository`，并打印实际写入路径；若本机 Maven 配置特殊，用第一个参数显式覆盖。脚本不删除任何文件、可重复执行。

