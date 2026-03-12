package ge.camora.erp.model.dto;

public class SalesProductExclusionRequest {
    private String displayName;
    private Boolean excluded;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean getExcluded() {
        return excluded;
    }

    public void setExcluded(Boolean excluded) {
        this.excluded = excluded;
    }
}
