package com.skyflux.kiln.common.exception;

/**
 * Application-level error codes.
 *
 * <p>Each value carries three properties:
 * <ul>
 *   <li>{@link #code()} — internal numeric code (stable, safe to log and return to clients).</li>
 *   <li>{@link #httpStatus()} — the HTTP status number this code maps to when surfaced via REST.</li>
 *   <li>{@link #message()} — default human-readable message (zh-CN).</li>
 * </ul>
 *
 * <p>The HTTP status is kept as a plain {@code int} to preserve the
 * "{@code common/} has no Spring dependency" invariant (see CLAUDE.md /
 * {@code docs/design.md} Ch 19.6.3 Dependency Rule).
 */
public enum AppCode {

    // 1xxx: generic client / framework errors
    BAD_REQUEST(1000, 400, "请求参数错误"),
    UNAUTHORIZED(1001, 401, "未登录或登录已过期"),
    FORBIDDEN(1002, 403, "无权限访问"),
    NOT_FOUND(1003, 404, "资源不存在"),
    CONFLICT(1004, 409, "资源冲突"),
    TOO_MANY_REQUESTS(1005, 429, "请求过于频繁"),
    VALIDATION_FAILED(1006, 400, "参数校验失败"),
    METHOD_NOT_ALLOWED(1007, 405, "方法不被允许"),
    MEDIA_TYPE_NOT_SUPPORTED(1008, 415, "媒体类型不受支持"),
    RATE_LIMITED(1009, 429, "请求过于频繁,请稍后重试"),
    INTERNAL_ERROR(1999, 500, "系统内部错误"),

    // 2xxx: authentication / session
    LOGIN_FAILED(2001, 401, "用户名或密码错误"),
    TOKEN_EXPIRED(2002, 401, "Token 已过期"),
    ACCOUNT_LOCKED(2003, 423, "账户已临时锁定,请稍后重试"),

    // 3xxx: business rule violations (client-visible, not server fault)
    BUSINESS_ERROR(3000, 400, "业务处理失败");

    private final int code;
    private final int httpStatus;
    private final String message;

    AppCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int code() { return code; }
    public int httpStatus() { return httpStatus; }
    public String message() { return message; }
}
