package com.beefamily.album.common;

/**
 * 业务错误码。
 *
 * <p>编号规则：{@code code = HTTP状态码 × 100 + 两位序号}，前端可用 {@code Math.floor(code / 100)}
 * 得到 HTTP 语义分组。已发布编号**永不复用、永不改语义**，只能追加（docs/03-architecture.md §5.2、§11.5）。</p>
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
    /** 跨家庭访问。 */
    CROSS_FAMILY_ACCESS(40301, 403, "无权访问该资源"),
    /** 仅家庭创建者可执行。 */
    OWNER_ONLY(40302, 403, "仅家庭创建者可执行此操作"),
    /** 仅资源创建者可执行。 */
    NOT_RESOURCE_CREATOR(40303, 403, "仅创建者可执行此操作"),

    /** 资源不存在。 */
    RESOURCE_NOT_FOUND(40401, 404, "资源不存在"),

    /** 邮箱已注册。 */
    EMAIL_ALREADY_EXISTS(40901, 409, "该邮箱已注册"),
    /** 邀请码无效或已过期。 */
    INVITE_CODE_INVALID(40902, 409, "邀请码无效或已过期"),
    /** 家庭成员已达上限。 */
    FAMILY_MEMBER_LIMIT(40903, 409, "家庭成员已达 6 人上限"),
    /** 宝宝档案已达上限。 */
    BABY_LIMIT(40904, 409, "宝宝档案已达 3 个上限"),
    /** 同名相册已存在。 */
    ALBUM_NAME_DUPLICATED(40905, 409, "同名相册已存在"),
    /** 成员不可移除（创建者或自己）。 */
    MEMBER_NOT_REMOVABLE(40906, 409, "不能移除家庭创建者或自己"),
    /** 目标相册不存在或无权访问。 */
    CROSS_ALBUM_MOVE(40907, 409, "目标相册不存在或无权访问"),
    /** 删除家庭时输入的家庭名不匹配。 */
    CONFIRM_MISMATCH(40908, 409, "输入的家庭名不匹配，无法删除"),

    /** 单文件超过 20MB。 */
    FILE_TOO_LARGE(41301, 413, "单张照片不能超过 20MB"),
    /** 单批超过 100 张。 */
    BATCH_TOO_LARGE(41302, 413, "单批最多 100 张"),

    /** 不支持的图片格式。 */
    UNSUPPORTED_MEDIA_TYPE(41501, 415, "不支持的图片格式（支持 jpg/jpeg/png/webp）"),

    /** 兜底服务异常。 */
    INTERNAL_ERROR(50001, 500, "服务异常，请稍后重试"),
    /** 文件写入失败。 */
    STORAGE_WRITE_FAILED(50002, 500, "文件保存失败，请重试");

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
