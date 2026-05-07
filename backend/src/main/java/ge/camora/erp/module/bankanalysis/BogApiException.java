package ge.camora.erp.module.bankanalysis;

public class BogApiException extends RuntimeException {

    public static final String DISABLED = "BOG_API_DISABLED";
    public static final String CONFIG_MISSING = "BOG_CONFIG_MISSING";
    public static final String TOKEN_FAILED = "BOG_TOKEN_FAILED";
    public static final String STATEMENT_FAILED = "BOG_STATEMENT_FAILED";
    public static final String HTTP_ERROR = "BOG_API_HTTP_ERROR";

    private final String code;

    public BogApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BogApiException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
