package ge.camora.erp.model.dto;

import jakarta.validation.constraints.NotBlank;

public class PatternTestRequest {
    @NotBlank
    private String pattern;
    @NotBlank
    private String testValue;
    private boolean isRegex;

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public String getTestValue() { return testValue; }
    public void setTestValue(String testValue) { this.testValue = testValue; }
    public boolean isRegex() { return isRegex; }
    public void setRegex(boolean regex) { isRegex = regex; }
}
