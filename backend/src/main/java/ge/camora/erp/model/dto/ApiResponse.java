package ge.camora.erp.model.dto;

import java.time.Instant;

public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final String error;
    private final String code;
    private final String technicalDetails;
    private final Instant timestamp;

    private ApiResponse(boolean success, T data, String error, String code, String technicalDetails) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.code = code;
        this.technicalDetails = technicalDetails;
        this.timestamp = Instant.now();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, null, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, message, code, null);
    }

    public static <T> ApiResponse<T> error(String code, String message, String technicalDetails) {
        return new ApiResponse<>(false, null, message, code, technicalDetails);
    }

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getError() { return error; }
    public String getCode() { return code; }
    public String getTechnicalDetails() { return technicalDetails; }
    public Instant getTimestamp() { return timestamp; }
}
