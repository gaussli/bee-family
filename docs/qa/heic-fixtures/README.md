# HEIC 夹具放置点

本目录用于存放 `docs/07-architecture-incremental.md` §9.6 / §9.8 定义的 S-1…S-5 实现前置门槛夹具。
**5/5 全通过才允许进入 T01 实现阶段。**

## 来源约束（强制）

- 必须使用本项目自行拍摄的 iPhone HEIC 原片。
- 禁止打包上游 Openize.HEIC 仓库内的 Nokia 测试样本 —— 其 OSS Notice 仅授权非商业评估 / 测试 / 学术研究。
- 禁止用 `sips` / `heif-convert` / `magick` / `ffmpeg` 等系统命令生成或转换夹具。
- 不得引入任何第三方受限样本。

## 清单（7 张）与当前覆盖

| 夹具 | 判据 | 状态 |
| --- | --- | --- |
| F-0 基线 | 普通正立、完整 EXIF 与 GPS、用于 S-1 解码 / S-3 尺寸 / S-4 19 字段 | 已覆盖（`IMG_8974.HEIC`） |
| F-1 | `irot`=90°，正立，宽高互换 | 缺 |
| F-2 | `irot`=180°，正立，宽高不变 | 缺 |
| F-3 | `irot`=270°，正立，宽高互换 | 已覆盖（`IMG_8974.HEIC`，`irot`=3） |
| F-4 | `irot` 与 Exif Orientation 同时非 1，只按 `irot` 校正一次 | 已覆盖（同上，`irot`=3 + `Orientation`=6） |
| F-5 | 含 P/B slices 的 Live Photo 类 HEIC，验证 `50003` 降级不黑图 | 缺 |
| F-6 | 人为损坏或无 EXIF，验证 `parse_status` 与 S-5 降级 | 缺（可由 `IMG_8974.HEIC` 截断派生） |

方向不必精确对应编号：iPhone 正持、横持、倒持竖拍即可覆盖不同拍摄朝向，QA 会读出实际 `irot` 值后归类。

## 已到位样本实测：`IMG_8974.HEIC`（2026-11-06）

| 项 | 实测值 |
| --- | --- |
| 大小 / SHA-256 | 1,001,143 bytes / `4454368df773c7692e6965200a5f1333f11e4eb740c049fe7878b5b94bee48b1` |
| `file` | ISO Media, HEIF Image HEVC Main or Main Still Picture Profile |
| `ftyp` | major=`heic`，minor=0，compatible=`mif1 MiHE MiPr miaf MiHB heic` |
| 顶层 box | `ftyp`(40) / `meta`(4241) / `mdat`(996862)；`hvcC` ×3；含 Exif item |
| **`irot`** | **3 → 270° CW**；无 `imir` |
| **Exif `Orientation`** | **6 → Rotate 90 CW**；QuickTime `Rotation` 同为 90 CW |
| 像素尺寸 | 4032×3024 |
| 设备 / 系统 | Apple iPhone 12 Pro Max / iOS 18.7.8 |
| 拍摄时间 | 2026-11-05 09:33:14 |
| EXIF | 208 个字段；Display P3；`ColorSpace`=Uncalibrated |
| GPS | 存在，含高度与时间戳（真实定位，精确数值不记入本文档） |
| 宿主识别 | `sips -g` 返回 `format: heic, 4032×3024` |

**关键实证**：`irot=3` 与 Exif `Orientation=6` 同时非 1，正是 `07` §9.6 的 F-4 组合样本，也是「双重旋转」陷阱的典型 —— 若解码器已按容器 `irot` 摆正，Java 侧再按 Exif Orientation 补转一次就会歪。这为「唯一责任方 = `HeicTranscoder#decode()` 内一次性正立化，`photo_exif.orientation` 只入库诊断、不参与旋转」提供了真实依据，而不是设计假设。

## 验证边界（不得混淆）

- `sips` 能识别只证明**宿主系统**可解码该文件，**不等于项目纯 Java 链路可解码**。`openize-heic-java 26.5.1` 尚未锁定 commit、未离线构建（QA 前置任务因网络流超时失败，零交付物）。
- 项目当前 `ImageValidator.java:75-76` 仍按 ADR-01 抛 `41501` + `HEIC_HINT`，`Limits.java:75` 把 `heic/heif` 列在黑名单 —— 源码未改、T01 未启动，现在端到端上传 HEIC 必然被拒。
- 因此 **S-1…S-5 尚未执行**，本文件不代表任何项目链路验证结论。

## 隐私与入库状态

- `IMG_8974.HEIC` 含真实精确定位（含高度、拍摄时间、设备型号）。
- 现有 `.gitignore:38-42` 的 `*.heic` / `*.HEIC` 会忽略该原片（该段注释写「不支持的图片格式样张」，属 ADR-01 遗留措辞，v1.1 后已过时，但规则仍有效）。
- **原片仅本地留存、未加入版本控制**，QA 直接按本地路径读取即可。若后续确需纳入仓库，须显式调整忽略规则并取得照片提供者的同意。
- 复制来源 `/Users/lijinhai/Downloads/IMG_8974.HEIC`，已用 SHA-256 校验与源文件一致；源文件未被改动。

## 当前状态

**2026-11-06 用户决定：剩余 4 张夹具（F-1 / F-2 / F-5 / F-6）先跳过**，因用户手头没有对应图片；不再向用户索取原片。

| 夹具 | 处置 | 对门槛的影响 |
| --- | --- | --- |
| F-1 / F-2 | 跳过 | S-2 只剩 F-3（`irot`=3）与 F-4（组合）可验证，90° / 180° 两个方向未覆盖 |
| F-5 | 跳过 | S-5 的 P/B slices / Live Photo 专项未覆盖 |
| F-6 | 跳过 | S-5 的损坏样本未覆盖（注：本可由 `IMG_8974.HEIC` 截断派生，无需新照片） |

**后果**：`07` §9.8 把 S-1…S-5「5/5 全通过」定为阻断级门槛，任一失败不得进入 T01。按当前夹具，S-1 / S-3 / S-4 可执行，S-2 / S-5 只能部分覆盖，**门槛无法自动满足**。放行口径待用户明确；在此之前不启动 T01、不改源码、不改已冻结的 `07`。

**技术备注（不改变用户决定）**：F-1 / F-2 可由本片复制后仅修改容器 `irot` 属性字节（3→1 / 3→2）派生，F-6 可由本片截断派生；三者都源自用户自拍原片，不引入第三方受限样本，也不需要用户再提供照片。F-5 需要含 P/B slices 的 Live Photo 编码数据，无法从静态照片派生。

1 / 7 张到位，覆盖 F-0 / F-3 / F-4。S-1…S-5 尚未执行，T01 未启动。
