package com.beefamily.album.common;

/**
 * 业务错误码（v1.1 共 35 项）。
 *
 * <p>编号规则：{@code code = HTTP状态码 × 100 + 两位序号}，前端可用 {@code Math.floor(code / 100)}
 * 得到 HTTP 语义分组。已发布编号**永不复用、永不改语义**，只能追加（docs/03-architecture.md §5.2、
 * §11.5；docs/07-architecture-incremental.md 附录 B）。</p>
 *
 * <p><b>40902 永久废弃</b>：曾为 {@code INVITE_CODE_INVALID}（旧邀请码）。
 * Q7 移除旧邀请码后该编号从枚举删除且**永久不再分配**，不保留别名、不提供兼容代码。
 * 409 组的空缺由从未发布的 40909 启用补齐，其余编号顺延不变。</p>
 *
 * <p><b>文案纪律</b>：50003 / 50301 共用同一个 {@link Messages#HEIC_FAILURE} 常量，保证用户侧
 * 文案逐字一致（docs/07 §9.9 裁定）；两者差异只体现在 code 与内部日志维度，避免泄露
 * 「解码能力不可用」这类实现信息（docs/06 §9.4 全站文案禁令）。</p>
 */
public enum ErrorCode {

    /** 成功。 */
    OK(0, 200, "OK"),

    /** 参数校验失败（字段级）。 */
    INVALID_PARAM(40001, 400, "参数校验失败"),
    /** 参数取值非法（枚举/类型不匹配/请求体不可解析）。 */
    INVALID_ARGUMENT(40002, 400, "参数取值非法"),

    /** 未登录或凭证无效。 */
    UNAUTHORIZED(40100, 401, "未登录或登录已失效"),
    /** 邮箱或密码错误。 */
    INVALID_CREDENTIALS(40101, 401, "邮箱或密码错误"),
    /** 原密码不正确。 */
    INVALID_OLD_PASSWORD(40102, 401, "原密码不正确"),
    /** 登录已过期（JWT exp 到期或已被 token_valid_after 置为失效）。 */
    TOKEN_EXPIRED(40103, 401, "登录已过期，请重新登录"),

    /** 已认证但无权执行该操作。 */
    FORBIDDEN(40300, 403, "无权执行该操作"),
    /** 跨家庭访问；差异化 message 由服务端透传，前端不按 code 本地映射。 */
    CROSS_FAMILY_ACCESS(40301, 403, "无权访问该资源"),
    /** 仅家庭创建者可执行（owner 独占操作）。 */
    OWNER_ONLY(40302, 403, "仅家庭创建者可执行此操作"),
    /** 仅资源创建者可执行。 */
    NOT_RESOURCE_CREATOR(40303, 403, "仅创建者可执行此操作"),
    /** 当前账号暂无可用家庭（无家庭态业务接口统一返回，不弹 danger Toast）。 */
    NO_ACTIVE_FAMILY(40304, 403, "当前账号暂无可用家庭"),
    /** 仅 owner 或 admin 可执行（申请列表 / 次数 / 审批对 member 返回此码）。 */
    ADMIN_ONLY(40305, 403, "仅家庭创建者或管理员可执行此操作"),

    /** 资源不存在。 */
    RESOURCE_NOT_FOUND(40401, 404, "资源不存在"),
    /** 加入申请不存在或已处理。 */
    JOIN_REQUEST_NOT_FOUND(40402, 404, "加入申请不存在或已处理"),

    /** 邮箱已注册。 */
    EMAIL_ALREADY_EXISTS(40901, 409, "该邮箱已注册"),
    // 40902 INVITE_CODE_INVALID —— 永久废弃，禁止复用、禁止别名、禁止兼容分支。
    /** 家庭成员已达上限（满 6 人禁止通过申请）。 */
    FAMILY_MEMBER_LIMIT(40903, 409, "家庭成员已达 6 人上限"),
    /** 宝宝档案已达上限。 */
    BABY_LIMIT(40904, 409, "宝宝档案已达 3 个上限"),
    /** 同名相册已存在。 */
    ALBUM_NAME_DUPLICATED(40905, 409, "同名相册已存在"),
    /** 成员不可移除 / 撤销 / 降级（创建者或自己）。 */
    MEMBER_NOT_REMOVABLE(40906, 409, "不能移除家庭创建者或自己"),
    /** 目标相册不存在或无权访问。 */
    CROSS_ALBUM_MOVE(40907, 409, "目标相册不存在或无权访问"),
    /** 删除家庭时输入的家庭名不匹配。 */
    CONFIRM_MISMATCH(40908, 409, "输入的家庭名不匹配，无法删除"),
    /** 家庭码无效（曾预分配未发布，现启用补齐 409 组）。 */
    FAMILY_CODE_INVALID(40909, 409, "家庭码无效"),
    /** 你已是该家庭成员，可直接切换。 */
    JOIN_REQUEST_ALREADY_MEMBER(40910, 409, "你已是该家庭成员，可直接切换"),
    /** 该申请当前状态不允许此操作。 */
    JOIN_REQUEST_STATUS_ILLEGAL(40911, 409, "该申请当前状态不允许此操作"),
    /** 该申请已过期，请重新申请。 */
    JOIN_REQUEST_EXPIRED(40912, 409, "该申请已过期，请重新申请"),
    /** 还有照片正在上传，请稍后再切换家庭。 */
    UPLOAD_IN_PROGRESS(40913, 409, "还有照片正在上传，请稍后再切换家庭"),

    /** 单文件超过 20MB。 */
    FILE_TOO_LARGE(41301, 413, "单张照片不能超过 20MB"),
    /** 单批超过 100 张。 */
    BATCH_TOO_LARGE(41302, 413, "单批最多 100 张"),

    /** 不支持的图片格式；GIF / SVG / 伪装文件统一命中，无分支。 */
    UNSUPPORTED_MEDIA_TYPE(41501, 415, "不支持的图片格式（支持 JPG / PNG / WebP / HEIC）"),

    /** 24 小时内申请次数已达上限；经 HttpStatus.resolve 映射 TOO_MANY_REQUESTS。 */
    RATE_LIMITED(42901, 429, "24 小时内申请次数已达上限，请稍后再试"),

    /** 兜底服务异常。 */
    INTERNAL_ERROR(50001, 500, "服务异常，请稍后重试"),
    /** 文件写入失败。 */
    STORAGE_WRITE_FAILED(50002, 500, "文件保存失败，请重试"),
    /** HEIC 单张解码失败；上传批量语义下为 failures[].code，HTTP 恒 200。 */
    HEIC_TRANSCODE_FAILED(50003, 500, Messages.HEIC_FAILURE),
    /** HEIC 解码能力不可用；映射 SERVICE_UNAVAILABLE，用户文案与 50003 逐字一致。 */
    HEIC_TRANSCODER_UNAVAILABLE(50301, 503, Messages.HEIC_FAILURE);

    /**
     * 集中存放需要跨多个错误码逐字复用的文案。
     *
     * <p>放在嵌套类中而非枚举的静态字段，是因为枚举常量必须先于静态字段初始化；
     * 嵌套类按需加载，可安全地被枚举常量的构造参数引用。</p>
     */
    private static final class Messages {

        /** docs/06 §9.2 ④ 权威降级文案，逐字照抄，不得改写。 */
        private static final String HEIC_FAILURE =
                "这张 HEIC 暂时无法解析，请在 iPhone 相册中打开照片 → 分享 →『导出为 JPEG』后重新上传。"
                        + "原文件不会丢失。";

        private Messages() {
            // 常量容器禁止实例化
        }
    }

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 业务码。
     *
     * @return 业务码
     */
    public int getCode() {
        return code;
    }

    /**
     * 对应 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * 默认文案。
     *
     * @return 默认中文提示
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
