package ge.camora.erp.model.config;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class SalesEvent {
    private LocalDate date;
    private String name;
    private String normalizedName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SalesEvent() {
    }

    public SalesEvent(LocalDate date, String name, String normalizedName, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.date = date;
        this.name = name;
        this.normalizedName = normalizedName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
