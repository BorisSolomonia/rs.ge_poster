package ge.camora.erp.model.dto;

import jakarta.validation.constraints.NotBlank;

public class StandaloneExcludeRequest {
    @NotBlank
    private String platform;
    @NotBlank
    private String name;

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
