# HEIC 夹具放置点

本目录用于存放 `docs/07-architecture-incremental.md` §9.6 / §9.8 定义的 S-1…S-5 实现前置门槛夹具。
**5/5 全通过才允许进入 T01 实现阶段。**

## 来源约束（强制）

- 必须使用本项目自行拍摄的 iPhone HEIC 原片。
- 禁止打包上游 Openize.HEIC 仓库内的 Nokia 测试样本 —— 其 OSS Notice 仅授权非商业评估 / 测试 / 学术研究。
- 禁止用 `sips` / `heif-convert` / `magick` / `ffmpeg` 等系统命令生成或转换夹具。
- 不得引入任何第三方受限样本。

## 待提供清单（7 张）

| 建议文件名 | 用途 | 判据 |
| --- | --- | --- |
| `F-0-baseline.heic` | S-1 解码 / S-3 尺寸 / S-4 EXIF 19 字段与 GPS 精确坐标 | 普通正立、带完整 EXIF 与定位 |
| `F-1-irot90.heic` | F-1 | `irot`=90°，正立，宽高互换 |
| `F-2-irot180.heic` | F-2 | `irot`=180°，正立，宽高不变 |
| `F-3-irot270.heic` | F-3 | `irot`=270°，正立，宽高互换 |
| `F-4-combined.heic` | F-4 | `irot` 与 Exif Orientation 同时非 1，只按 `irot` 校正一次，无双重旋转 |
| `F-5-livephoto.heic` | F-5 | 含 P/B slices 的 Live Photo 类 HEIC，验证 `50003` 降级不黑图、不静默成功 |
| `F-6-damaged.heic` | S-4 缺失态 / S-5 降级 | 人为损坏或无 EXIF；`parse_status` 正确，不阻断上传、不产生 5xx |

方向不必精确对应文件名：iPhone 正持、横持、倒持竖拍即可覆盖不同拍摄朝向，QA 会用 metadata-extractor 读出实际 `irot` 值后归类。

## 隐私

本目录会随仓库入库。请避开人脸与家庭住址画面；GPS 请用公开地点拍摄。

## 当前状态

原片待提供。S-1…S-5 尚未执行；本文件仅为放置点与夹具规格说明，不代表任何验证结论。
