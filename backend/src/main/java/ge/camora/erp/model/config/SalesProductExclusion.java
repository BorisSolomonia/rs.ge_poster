package ge.camora.erp.model.config;

import java.time.LocalDateTime;

public class SalesProductExclusion {
    private String normalizedName;
    private String displayName;
    private boolean excluded;
    private String source;
    private LocalDateTime firstSeen;

    public SalesProductExclusion() {
    }

    public SalesProductExclusion(String normalizedName, String displayName, boolean excluded, String source, LocalDateTime firstSeen) {
        this.normalizedName = normalizedName;
        this.displayName = displayName;
        this.excluded = excluded;
        this.source = source;
        this.firstSeen = firstSeen;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isExcluded() {
        return excluded;
    }

    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(LocalDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }
}
