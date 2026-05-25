package ge.camora.erp.model.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class SupplierCashPayment {
    private String id;
    private String supplierKey;
    private String supplierTin;
    private String supplierName;
    private LocalDate date;
    private BigDecimal amount;
    private String note;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SupplierCashPayment() {
    }

    public SupplierCashPayment(
        String id,
        String supplierKey,
        String supplierTin,
        String supplierName,
        LocalDate date,
        BigDecimal amount,
        String note,
        String source,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        this.id = id;
        this.supplierKey = supplierKey;
        this.supplierTin = supplierTin;
        this.supplierName = supplierName;
        this.date = date;
        this.amount = amount;
        this.note = note;
        this.source = source;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSupplierKey() { return supplierKey; }
    public void setSupplierKey(String supplierKey) { this.supplierKey = supplierKey; }
    public String getSupplierTin() { return supplierTin; }
    public void setSupplierTin(String supplierTin) { this.supplierTin = supplierTin; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
