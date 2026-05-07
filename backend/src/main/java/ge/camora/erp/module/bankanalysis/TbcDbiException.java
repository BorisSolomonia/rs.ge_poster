package ge.camora.erp.module.bankanalysis;

public class TbcDbiException extends RuntimeException {

    private final String code;

    public TbcDbiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public TbcDbiException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
