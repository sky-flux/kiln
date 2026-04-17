package com.skyflux.kiln.common.exception;

public class AppException extends RuntimeException {

    private final AppCode appCode;
    private final transient Object[] args;

    public AppException(AppCode appCode) {
        super(appCode.message());
        this.appCode = appCode;
        this.args = new Object[0];
    }

    public AppException(AppCode appCode, Object... args) {
        super(appCode.message());
        this.appCode = appCode;
        this.args = args;
    }

    public AppException(AppCode appCode, Throwable cause) {
        super(appCode.message(), cause);
        this.appCode = appCode;
        this.args = new Object[0];
    }

    public AppCode appCode() { return appCode; }
    public Object[] args() { return args; }
}
