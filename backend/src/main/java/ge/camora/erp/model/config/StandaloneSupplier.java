package ge.camora.erp.model.config;

import java.time.LocalDateTime;

public class StandaloneSupplier {
    private String platform;
    private String name;
    private boolean isExcluded;
    private LocalDateTime firstSeen;

    public StandaloneSupplier() {}

    public StandaloneSupplier(String platform, String name, boolean isExcluded, LocalDateTime firstSeen) {
        this.platform = platform;
        this.name = name;
        this.isExcluded = isExcluded;
        this.firstSeen = firstSeen;
    }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isExcluded() { return isExcluded; }
    public void setExcluded(boolean excluded) { isExcluded = excluded; }
    public LocalDateTime getFirstSeen() { return firstSeen; }
    public void setFirstSeen(LocalDateTime firstSeen) { this.firstSeen = firstSeen; }
}
