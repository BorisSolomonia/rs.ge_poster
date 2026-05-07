package ge.camora.erp.model.dto;

import java.time.Instant;

public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final String error;
    private final String code;
    private final Instant timestamp;

    private ApiResponse(boolean success, T data, String error, String code) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.code = code;
        this.timestamp = Instant.now();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, message, code);
    }

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getError() { return error; }
    public String getCode() { return code; }
    public Instant getTimestamp() { return timestamp; }
}
