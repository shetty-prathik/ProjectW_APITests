package com.projectw.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Generic wrapper matching the Project W standard response envelope:
 * { "code": 200, "message": "...", "data": { ... } }
 *
 * @param <T> The type of the data payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public ApiResponse() {}

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    @Override
    public String toString() {
        return "ApiResponse{code=" + code + ", message='" + message + "', data=" + data + "}";
    }
}
