# Q7 验收基线同步简报

| 项 | 值 |
| --- | --- |
| 执行 | QA（heic-spike-qa）· 2026-11-06 |
| 派单 | `team-lead`「同步 Q7 之后的验收基线（只改 `docs/` 下的验证脚本）」 |
| 执行时基线 | `c4b7944 test(qa): round-2 regression report and Q7 contract alignment`（另一 QA 已提交基础 Q7 对齐）；此前 `758da71`（T01）、`f7753db`（S-1 spike） |
| 契约依据 | `docs/07-architecture-incremental.md` §5.2 第 6 项、§5.3.1（行 369–406）、§12.2（行 1579）、附录 B；`docs/05-prd-incremental.md` §3 INC-P0-01 / §2 作废项 |
| 本次改动 | `docs/e2e-verify.mjs`（B1/B2/B3 严格化 + A 段过时提示 + H 清理补账号）、`docs/qa/q7-baseline-sync.md`（本简报） |
| 未跑 | 依赖 v1.1 后端接口的端到端（后端仍 v1.0 / T02 进行中），如实标注 |
| 边界 | 未改 `backend/**`、`frontend/**`（只复核）、`docs/05`/`06`/`07`；未动 `bee_family` 演示库；未重启 8080/5173；未与其他成员直连 |

---

## 1. 背景：Q7 删了什么，旧断言为什么必然 FAIL

T01（`758da71`）按 Q7 终裁**永久删除**（无别名、无弃用接口、无双份测试基线）：

- `GET /api/family/invite-code`、`POST /api/family/invite-code/reset` 端点；
- `40902 INVITE_CODE_INVALID` 错误码（附录 B 中「废弃，永久不再分配」）；
- `RegisterReq.inviteCode` 字段 → 注册体恒为 `{email, password, nickname}`；
- `InviteCodeResp` / `InviteCodes` 8 位 + 24h TTL 常量等。

替换为 §5.3.1 的新契约：

```jsonc
// POST /api/auth/register 请求（Q1：无任何码）
{ "email": "...", "password": "...", "nickname": "..." }

// TokenResp（注册成功后 0 家庭，前端跳 /onboarding 状态 A）
{
  "token": "...", "user": { "id": 1, "...": "..." },
  "activeFamilyId": null, "family": null, "role": null,
  "families": [], "babies": [], "onboardingState": "NEW", "pendingRequestCount": 0
}
```

`docs/05` §3 INC-P0-01：注册**不创建、不加入任何家庭**，`user` 表已有 N 人时注册仍成功，响应 `active_family_id = null`。§12.2 行 1579 明确把旧 B2 断言作废为「注册不传任何码 → 200，`activeFamilyId=null`、0 家庭；`40902` 不再存在」。

因此旧脚本里「邀请码返回 200 / 8 位 / `expired=false`」「缺码注册 → `409/40902`」「凭邀请码注册加入同一家庭」三类断言全部失效。

---

## 2. 已由 `c4b7944` 落地、本次确认无需重复修改的部分

另一 QA 的 `c4b7944` 已提交基础 Q7 对齐（5 文件 / +111 −23）。本次只读复核确认：

| 文件 | 状态 |
| --- | --- |
| `docs/probe-count.mjs` | 注册体 `inviteCode: null` 已清除（HEAD 版无残留） |
| `docs/probe-d6.mjs` | 同上 |
| `docs/seed-visual.mjs` | 同上（派单未列，属同方向清理） |
| `docs/api-smoke.sh` | 第 246–262 行已有 Q7 正向断言 `GET/POST /family/invite-code*` → `req_any ... "404 403 401 405"` |
| `docs/04-test-report.md` | 回归报告已更新（本次未改） |
| `docs/e2e-verify.mjs` | 第 302 行注册体已去 `inviteCode`；B1/B2/B3 已做**初步**改写（见 §3，B2 过弱由本次加强） |

派单第 2 项「清 `probe-count.mjs` / `probe-d6.mjs` 注册体里的 `inviteCode: null`」因此**已完成**，本简报记录确认，未做无意义重写。

---

## 3. 本次修改的断言（逐条）

### 3.1 `docs/e2e-verify.mjs` B1 — 旧端点不可达（严格度：↑）

| | 内容 |
| --- | --- |
| 改前 | `record('B1', '旧邀请码端点已移除（404/403/401/405…）', [404,403,401,405].includes(status), ...)` |
| 改后 | 在状态码断言之外**追加** `inviteResp.body?.code !== 0 && !inviteResp.body?.data?.code`，并在 detail 中打印 `data.code` |
| 为什么 | 防止「端点被误恢复成返回 200 成功信封、但恰好状态码异常」的漏判；Q7 要求端点**不存在**，不是「存在但不给码」 |

### 3.2 `docs/e2e-verify.mjs` B2 — 注册不传任何码成功并进入无家庭引导（严格度：↑↑，本次核心）

| | 内容 |
| --- | --- |
| 改前 | `record('B2', '注册不再校验邀请码：不会出现已删除的 40902', noInvite.body?.code !== 40902, ...)` |
| 问题 | **几乎恒真**：注册返回 500 / 400 / 401 / 网络错误（`body=null`）时 `code !== 40902` 都成立，等于把 B2 变成空断言，**降低了严格度**，且完全没有验证「进入无家庭引导」 |
| 改后 | 同时断言：`status === 200`、`body.code === 0`、`token` 存在、`user.id` 为整数、`activeFamilyId == null`、`family == null`、`families` 为**空数组**、`onboardingState === 'NEW'`、`body.code !== 40902`；detail 打印全部诊断字段 |
| 为什么 | 逐字对齐 §5.3.1 TokenResp 契约与 §12.2 行 1579；且 B2 执行时 `user` 表已有 A 等账号，正好覆盖 INC-P0-01「不再判首用户」 |
| 附带 | 把邮箱提取为 `noCodeEmail` 常量，供 §3.4 清理复用 |

### 3.3 `docs/e2e-verify.mjs` B3 — 第二账号同样 0 家庭（严格度：↑）

| | 内容 |
| --- | --- |
| 改前 | `record('B3', '注册第二个账号成功…', status === 200 && Number.isInteger(userC), ...)` |
| 改后 | 追加 `body.code === 0`、`Boolean(tokenC)`、`activeFamilyId == null`、`families` 空、`onboardingState === 'NEW'` |
| 为什么 | Q7 下「凭邀请码加入同一家庭」的旧断言（原 `familyId === familyA`）已作废，但不能退化成只看 200；新契约要求 C 也是 0 家庭，才能由后续 SQL 夹具构造跨家庭场景（第 506 行 `delete from family_member where user_id = C` 依赖 C 无既有归属） |

### 3.4 `docs/e2e-verify.mjs` H 段清理 — 补 B2 账号（严格度：—，保持幂等）

| | 内容 |
| --- | --- |
| 改前 | `delete from \`user\` where email in ('${emailA}', '${emailC}')` |
| 改后 | 追加 `'${noCodeEmail}'` + 注释 |
| 为什么 | 旧 B2 期望注册失败（不产生用户）；Q7 下注册**成功创建账号**，若不清会残留 `qa-nocode-*` 用户，破坏脚本「空库」前提并污染下一轮 |

### 3.5 A 段前置失败提示（严格度：—，只改文案）

第 314–315 行原文「user 表非空时注册必须带邀请码，只有首个用户才会成为 owner 并自动建家庭」是 v1.0 口径、Q7 后已作废，改为「Q7 后注册不创建、不加入任何家庭（`activeFamilyId=null` / `onboardingState=NEW`），A 组主流程仍依赖 v1.0 语义，需后端 v1.1 就绪后统一改造」，并指向本简报。**未改 A1 断言与前置检查逻辑**（见 §6 剩余项）。

> 未降低任何其它断言：A1–A15、B4–B10、C、D、E、F、G、H1/H2 的判定表达式全部原样保留。

---

## 4. 前端 `ApiError.isAuthError` 只读复核（**未改任何前端文件**）

| 检查点 | 位置 / 结论 |
| --- | --- |
| 区间定义 | `frontend/src/api/http.ts:59-61`：`this.code >= 40100 && this.code < 40200` → **仅 `40100..40199`** |
| `40304` / `40305` | 均 `>= 40300`，**不在区间**，`isAuthError === false` |
| 响应拦截分支 | `http.ts:152-161`：`if (apiError.isAuthError && !redirecting) { unauthorizedHandler?.() } else if (apiError.code === 40304) { dispatchContextError(40304) }`。`40304` 只走 `noActiveFamilyHandler`；`40305` **两个分支都不进**，仅透传服务端 `message` |
| token 清理路径 | `frontend/src/main.ts:24-30`：`setUnauthorizedHandler` → `useAuthStore().clearLocal()`（`stores/auth.ts:74` 会 `localStorage.removeItem(TOKEN_KEY)`）+ 跳 `/login`。**该路径只在 `isAuthError` 为真时触发** |
| `40304` 处理 | `main.ts:37-41`：`setNoActiveFamilyHandler` 仅 `router.replace({name:'onboarding'})`；`main.ts:35` 注释明确「不清 token、不弹 danger Toast」 |
| `dispatchContextError` | `http.ts:118-122` 只在 `code === 40304 && noActiveFamilyHandler` 时触发，不做任何本地文案映射 |

**结论：符合 `docs/07` 附录 B「`ApiError.isAuthError` 判定区间 `40100..40199` 不变；`40304/40305` 不触发误清 token」的要求。`40304`（无家庭态）与 `40305`（ADMIN_ONLY）都不会被当作认证失效清 token，只有 `40100..40199` 中的认证失效码才会。** 未修改前端（T04 已交付，避免写冲突）。

---

## 5. 语法检查（未跑端到端）

```
node --check docs/e2e-verify.mjs   OK
node --check docs/probe-count.mjs  OK
node --check docs/probe-d6.mjs     OK
node --check docs/seed-visual.mjs  OK
node --check docs/visual-check.mjs OK
bash -n      docs/api-smoke.sh     OK
```

**未执行** `node docs/e2e-verify.mjs` / `docs/api-smoke.sh` / 任何依赖新后端接口的端到端：后端仍是 v1.0（`ImageValidator` 按 ADR-01 拒 HEIC、无 `/api/families`、无 `MeResp.onboardingState`），T02 正在实现；按派单要求如实标注「跑了必然失败」，不制造假绿/假红结论。`node --check` 只做语法解析，不发起任何请求、不连库、不触碰 `bee_family` 演示库。

---

## 6. 剩余未同步项（需后续处理，本次未擅自扩大范围）

| # | 项 | 影响 | 建议改造 |
| --- | --- | --- | --- |
| R-1 | **A 组主流程仍依赖 v1.0「首注册自动建家庭」**：`A1` 断言 `role === 'owner'`；`familyA` 取 `registerA.body?.data?.familyId`；第 311 行前置 `Number.isInteger(familyA)` 否则整轮 `exit(1)` | Q7 下注册返回 `role=null` / `familyId` 不存在 → **A1 FAIL 且整轮前置失败**，B–H 全部不执行 | 注册后显式 `POST /api/families {name}`（`07` §5.3.1 行 416，返回 `FamilyResp` 含 `familyCode`）取 `familyId`；`A1` 改为「注册成功 + 0 家庭 + `NEW`」；新增 `A1b` 验证创建家庭后 `role=owner` + 10 位家庭码；后续 SQL 夹具改用新 `familyId`。**需后端 v1.1 就绪后一并验证** |
| R-2 | B2 现会成功创建账号，头部注释（第 13–18 行）仍写「脚本自建独立家庭」 | 文档描述与实际略有偏差 | A 组改造时同步措辞 |
| R-3 | v1.1 新契约的 e2e 用例尚未新增 | 家庭码（10 位 / 永久 / 重置）、申请审批 5 态、多家庭切换 13 项重置、`40304` 无家庭白名单、`40913` 在途上传阻断、HEIC 上传态与 EXIF 面板、35 错误码新增项，均未覆盖 | T05 集成回归统一补；`docs/04-test-report.md` 已由 `c4b7944` 更新回归报告，本简报不重复 |
| R-4 | `docs/api-smoke.sh` 的 Q7 断言（第 246–262 行）由他人落地 | 本次只做 `bash -n`，未逐条核对语义与 44 项计数 | 由该脚本维护者在后端 v1.1 就绪后跑一次冒烟 |
| R-5 | 并行 WIP | 执行期间 `c4b7944` 由另一 QA 提交基础 Q7 对齐；`docs/07-architecture-incremental.md` 仍有 `team-lead` 未提交的 §9.8.2 勘误 WIP | **本 commit 未 add `docs/07`**（避免把主理人未定稿内容带入）；本简报基于 `c4b7944` 的增量改动，未回滚/覆盖任何他人成果 |
| R-6 | 后端仍 v1.0 | 所有依赖新接口的断言**均未执行验证** | T02/T03 完成后由 T05 统一跑 e2e + 冒烟；当前结论仅为静态契约对齐 |

---

## 7. 交付文件

| 路径 | 动作 |
| --- | --- |
| `docs/e2e-verify.mjs` | B1/B2/B3 严格化、A 段过时提示修正、H 段清理补 `noCodeEmail` |
| `docs/qa/q7-baseline-sync.md` | 新增（本简报） |

`docs/probe-count.mjs` / `docs/probe-d6.mjs` 的 `inviteCode: null` 清理已由 `c4b7944` 完成，本 commit 不含这两个文件；`frontend/**`、`backend/**`、`docs/05|06|07` 均未修改。
