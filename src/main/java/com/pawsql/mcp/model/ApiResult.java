package com.pawsql.mcp.model;

import java.io.Serializable;

public record ApiResult(int code, String message, Object data) implements Serializable {
    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public Object data() {
        return data;
    }

    public static ApiResult succ(Object data) {
        return succ(200, "success", data);
    }

    public static ApiResult succ(int code, String msg, Object data) {
        return new ApiResult(code, msg, data);
    }

    public static ApiResult fail(String msg) {
        return fail(400, msg, null);
    }

    public static ApiResult fail(String msg, String data) {
        return fail(400, msg, data);
    }

    public static ApiResult fail(int code, String msg, Object data) {
        return new ApiResult(code, msg, data);
    }
}