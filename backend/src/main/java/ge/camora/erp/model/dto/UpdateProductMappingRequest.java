package ge.camora.erp.model.dto;

public class UpdateProductMappingRequest {
    private String rsgeProductPattern;
    private String posterProductPattern;
    private Boolean isRegex;
    private Boolean isExcluded;
    private Integer priority;

    public String getRsgeProductPattern() { return rsgeProductPattern; }
    public void setRsgeProductPattern(String rsgeProductPattern) { this.rsgeProductPattern = rsgeProductPattern; }
    public String getPosterProductPattern() { return posterProductPattern; }
    public void setPosterProductPattern(String posterProductPattern) { this.posterProductPattern = posterProductPattern; }
    public Boolean getIsRegex() { return isRegex; }
    public void setIsRegex(Boolean isRegex) { this.isRegex = isRegex; }
    public Boolean getIsExcluded() { return isExcluded; }
    public void setIsExcluded(Boolean isExcluded) { this.isExcluded = isExcluded; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
}
