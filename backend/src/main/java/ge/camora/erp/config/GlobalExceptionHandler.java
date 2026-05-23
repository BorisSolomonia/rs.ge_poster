package ge.camora.erp.config;

import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.module.bankanalysis.BogApiException;
import ge.camora.erp.module.ingestion.FileParsingException;
import ge.camora.erp.module.bankanalysis.TbcDbiException;
import ge.camora.erp.module.rsge.RsgeIntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final CamoraProperties properties;

    public GlobalExceptionHandler(CamoraProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(FileParsingException.class)
    public ResponseEntity<ApiResponse<Void>> handleParsingError(FileParsingException ex) {
        log.error("File parsing error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.error("Application state error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(TbcDbiException.class)
    public ResponseEntity<ApiResponse<Void>> handleTbcDbi(TbcDbiException ex) {
        log.error("TBC DBI error [{}]: {}", ex.getCode(), ex.getMessage(), ex);
        HttpStatus status = switch (ex.getCode()) {
            case TbcDbiException.PASSWORD_CHANGE_REQUIRED -> HttpStatus.CONFLICT;
            case TbcDbiException.INCORRECT_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case TbcDbiException.USER_IS_BLOCKED -> HttpStatus.LOCKED;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(BogApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleBogApi(BogApiException ex) {
        log.error("BOG API error [{}]: {}", ex.getCode(), ex.getMessage(), ex);
        HttpStatus status = switch (ex.getCode()) {
            case BogApiException.DISABLED, BogApiException.CONFIG_MISSING -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .findFirst().orElse(properties.getMessages().getValidationError());
        return ResponseEntity.badRequest().body(ApiResponse.error(msg));
    }

    @ExceptionHandler(RsgeIntegrationException.class)
    public ResponseEntity<ApiResponse<Void>> handleRsgeIntegration(RsgeIntegrationException ex) {
        log.error("RS.ge integration error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex, HttpServletRequest request) {
        log.error(
            "Multipart error: message='{}', method={}, uri={}, contentType={}, contentLength={}, userAgent={}",
            ex.getMessage(),
            request.getMethod(),
            request.getRequestURI(),
            request.getContentType(),
            request.getContentLengthLong(),
            request.getHeader("User-Agent")
        );
        return ResponseEntity.badRequest().body(ApiResponse.error("Request must be multipart/form-data"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(properties.getMessages().getInternalServerErrorPrefix() + "See backend logs for details."));
    }
}
