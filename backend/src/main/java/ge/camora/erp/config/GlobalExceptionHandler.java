package ge.camora.erp.config;

import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.module.ingestion.FileParsingException;
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
            .body(ApiResponse.error(properties.getMessages().getInternalServerErrorPrefix() + ex.getMessage()));
    }
}
