package ge.camora.erp.model.dto;

public record PatternTestResult(
    boolean matches,
    String pattern,
    String testValue,
    boolean isRegex,
    String error
) {}
