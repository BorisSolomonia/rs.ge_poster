package ge.camora.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "camora")
public class CamoraProperties {

    private String apiPrefix;
    private String configDir;
    private final ConfigFiles configFiles = new ConfigFiles();
    private final Cors cors = new Cors();
    private final ResultCache resultCache = new ResultCache();
    private final Seed seed = new Seed();
    private final Reconciliation reconciliation = new Reconciliation();
    private final SalesAnalysis salesAnalysis = new SalesAnalysis();
    private final CashFlow cashFlow = new CashFlow();
    private final RsgeApi rsgeApi = new RsgeApi();
    private final Parsers parsers = new Parsers();
    private final Platforms platforms = new Platforms();
    private final Messages messages = new Messages();
    private BigDecimal matchThreshold;

    public String getApiPrefix() {
        return apiPrefix;
    }

    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    public String getConfigDir() {
        return configDir;
    }

    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }

    public ConfigFiles getConfigFiles() {
        return configFiles;
    }

    public Cors getCors() {
        return cors;
    }

    public ResultCache getResultCache() {
        return resultCache;
    }

    public Seed getSeed() {
        return seed;
    }

    public Reconciliation getReconciliation() {
        return reconciliation;
    }

    public SalesAnalysis getSalesAnalysis() {
        return salesAnalysis;
    }

    public CashFlow getCashFlow() {
        return cashFlow;
    }

    public RsgeApi getRsgeApi() {
        return rsgeApi;
    }

    public Parsers getParsers() {
        return parsers;
    }

    public Platforms getPlatforms() {
        return platforms;
    }

    public Messages getMessages() {
        return messages;
    }

    public BigDecimal getMatchThreshold() {
        return matchThreshold;
    }

    public void setMatchThreshold(BigDecimal matchThreshold) {
        this.matchThreshold = matchThreshold;
    }

    public static class ConfigFiles {
        private String supplierMappings;
        private String productMappings;
        private String standaloneSuppliers;
        private String salesProductExclusions;
        private String salesEvents;

        public String getSupplierMappings() {
            return supplierMappings;
        }

        public void setSupplierMappings(String supplierMappings) {
            this.supplierMappings = supplierMappings;
        }

        public String getProductMappings() {
            return productMappings;
        }

        public void setProductMappings(String productMappings) {
            this.productMappings = productMappings;
        }

        public String getStandaloneSuppliers() {
            return standaloneSuppliers;
        }

        public void setStandaloneSuppliers(String standaloneSuppliers) {
            this.standaloneSuppliers = standaloneSuppliers;
        }

        public String getSalesProductExclusions() {
            return salesProductExclusions;
        }

        public void setSalesProductExclusions(String salesProductExclusions) {
            this.salesProductExclusions = salesProductExclusions;
        }

        public String getSalesEvents() {
            return salesEvents;
        }

        public void setSalesEvents(String salesEvents) {
            this.salesEvents = salesEvents;
        }
    }

    public static class Cors {
        private List<String> allowedOriginPatterns = new ArrayList<>();
        private List<String> allowedMethods = new ArrayList<>();
        private List<String> allowedHeaders = new ArrayList<>();
        private boolean allowCredentials;
        private long maxAgeSeconds;

        public List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public long getMaxAgeSeconds() {
            return maxAgeSeconds;
        }

        public void setMaxAgeSeconds(long maxAgeSeconds) {
            this.maxAgeSeconds = maxAgeSeconds;
        }
    }

    public static class ResultCache {
        private long maximumSize;
        private long expireAfterHours;

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        public long getExpireAfterHours() {
            return expireAfterHours;
        }

        public void setExpireAfterHours(long expireAfterHours) {
            this.expireAfterHours = expireAfterHours;
        }
    }

    public static class Seed {
        private boolean enabled;
        private String resource;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }
    }

    public static class Reconciliation {
        private long resultExpireAfterHours;

        public long getResultExpireAfterHours() {
            return resultExpireAfterHours;
        }

        public void setResultExpireAfterHours(long resultExpireAfterHours) {
            this.resultExpireAfterHours = resultExpireAfterHours;
        }
    }

    public static class SalesAnalysis {
        private BigDecimal matchThreshold;
        private int weekStartsOnIso;
        private String exclusionsSeedResource;
        private int suggestionLimit;
        private int maxSuggestionDistance;

        public BigDecimal getMatchThreshold() {
            return matchThreshold;
        }

        public void setMatchThreshold(BigDecimal matchThreshold) {
            this.matchThreshold = matchThreshold;
        }

        public int getWeekStartsOnIso() {
            return weekStartsOnIso;
        }

        public void setWeekStartsOnIso(int weekStartsOnIso) {
            this.weekStartsOnIso = weekStartsOnIso;
        }

        public String getExclusionsSeedResource() {
            return exclusionsSeedResource;
        }

        public void setExclusionsSeedResource(String exclusionsSeedResource) {
            this.exclusionsSeedResource = exclusionsSeedResource;
        }

        public int getSuggestionLimit() {
            return suggestionLimit;
        }

        public void setSuggestionLimit(int suggestionLimit) {
            this.suggestionLimit = suggestionLimit;
        }

        public int getMaxSuggestionDistance() {
            return maxSuggestionDistance;
        }

        public void setMaxSuggestionDistance(int maxSuggestionDistance) {
            this.maxSuggestionDistance = maxSuggestionDistance;
        }
    }

    public static class CashFlow {
        private boolean enabled;
        private String sheetId;
        private String sheetName;
        private String range;
        private int sourceStartRow;
        private String syncFixedDelay;
        private String serviceAccountJson;
        private String serviceAccountPath;
        private BigDecimal warningNegativeBalanceThreshold;
        private final LedgerColumns columns = new LedgerColumns();
        private List<String> incomeKeywords = new ArrayList<>();
        private List<String> expenseKeywords = new ArrayList<>();
        private List<String> safeKeywords = new ArrayList<>();
        private List<String> dividendKeywords = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSheetId() {
            return sheetId;
        }

        public void setSheetId(String sheetId) {
            this.sheetId = sheetId;
        }

        public String getSheetName() {
            return sheetName;
        }

        public void setSheetName(String sheetName) {
            this.sheetName = sheetName;
        }

        public String getRange() {
            return range;
        }

        public void setRange(String range) {
            this.range = range;
        }

        public int getSourceStartRow() {
            return sourceStartRow;
        }

        public void setSourceStartRow(int sourceStartRow) {
            this.sourceStartRow = sourceStartRow;
        }

        public String getSyncFixedDelay() {
            return syncFixedDelay;
        }

        public void setSyncFixedDelay(String syncFixedDelay) {
            this.syncFixedDelay = syncFixedDelay;
        }

        public String getServiceAccountJson() {
            return serviceAccountJson;
        }

        public void setServiceAccountJson(String serviceAccountJson) {
            this.serviceAccountJson = serviceAccountJson;
        }

        public String getServiceAccountPath() {
            return serviceAccountPath;
        }

        public void setServiceAccountPath(String serviceAccountPath) {
            this.serviceAccountPath = serviceAccountPath;
        }

        public BigDecimal getWarningNegativeBalanceThreshold() {
            return warningNegativeBalanceThreshold;
        }

        public void setWarningNegativeBalanceThreshold(BigDecimal warningNegativeBalanceThreshold) {
            this.warningNegativeBalanceThreshold = warningNegativeBalanceThreshold;
        }

        public LedgerColumns getColumns() {
            return columns;
        }

        public List<String> getIncomeKeywords() {
            return incomeKeywords;
        }

        public void setIncomeKeywords(List<String> incomeKeywords) {
            this.incomeKeywords = incomeKeywords;
        }

        public List<String> getExpenseKeywords() {
            return expenseKeywords;
        }

        public void setExpenseKeywords(List<String> expenseKeywords) {
            this.expenseKeywords = expenseKeywords;
        }

        public List<String> getSafeKeywords() {
            return safeKeywords;
        }

        public void setSafeKeywords(List<String> safeKeywords) {
            this.safeKeywords = safeKeywords;
        }

        public List<String> getDividendKeywords() {
            return dividendKeywords;
        }

        public void setDividendKeywords(List<String> dividendKeywords) {
            this.dividendKeywords = dividendKeywords;
        }
    }

    public static class LedgerColumns {
        private int category;
        private int day;
        private int month;
        private int year;
        private int counterparty;
        private int materialValue;
        private int serviceValue;
        private int cashInflow;
        private int cashOutflow;
        private int cashBalance;
        private int bogInflow;
        private int bogOutflow;
        private int bogBalance;
        private int tbcInflow;
        private int tbcOutflow;
        private int tbcBalance;
        private int comment;
        private int fullDate;
        private int validationFlag;

        public int getCategory() { return category; }
        public void setCategory(int category) { this.category = category; }
        public int getDay() { return day; }
        public void setDay(int day) { this.day = day; }
        public int getMonth() { return month; }
        public void setMonth(int month) { this.month = month; }
        public int getYear() { return year; }
        public void setYear(int year) { this.year = year; }
        public int getCounterparty() { return counterparty; }
        public void setCounterparty(int counterparty) { this.counterparty = counterparty; }
        public int getMaterialValue() { return materialValue; }
        public void setMaterialValue(int materialValue) { this.materialValue = materialValue; }
        public int getServiceValue() { return serviceValue; }
        public void setServiceValue(int serviceValue) { this.serviceValue = serviceValue; }
        public int getCashInflow() { return cashInflow; }
        public void setCashInflow(int cashInflow) { this.cashInflow = cashInflow; }
        public int getCashOutflow() { return cashOutflow; }
        public void setCashOutflow(int cashOutflow) { this.cashOutflow = cashOutflow; }
        public int getCashBalance() { return cashBalance; }
        public void setCashBalance(int cashBalance) { this.cashBalance = cashBalance; }
        public int getBogInflow() { return bogInflow; }
        public void setBogInflow(int bogInflow) { this.bogInflow = bogInflow; }
        public int getBogOutflow() { return bogOutflow; }
        public void setBogOutflow(int bogOutflow) { this.bogOutflow = bogOutflow; }
        public int getBogBalance() { return bogBalance; }
        public void setBogBalance(int bogBalance) { this.bogBalance = bogBalance; }
        public int getTbcInflow() { return tbcInflow; }
        public void setTbcInflow(int tbcInflow) { this.tbcInflow = tbcInflow; }
        public int getTbcOutflow() { return tbcOutflow; }
        public void setTbcOutflow(int tbcOutflow) { this.tbcOutflow = tbcOutflow; }
        public int getTbcBalance() { return tbcBalance; }
        public void setTbcBalance(int tbcBalance) { this.tbcBalance = tbcBalance; }
        public int getComment() { return comment; }
        public void setComment(int comment) { this.comment = comment; }
        public int getFullDate() { return fullDate; }
        public void setFullDate(int fullDate) { this.fullDate = fullDate; }
        public int getValidationFlag() { return validationFlag; }
        public void setValidationFlag(int validationFlag) { this.validationFlag = validationFlag; }
    }

    public static class RsgeApi {
        private String endpoint;
        private String username;
        private String password;
        private int timeoutSeconds;
        private boolean debug;
        private int debugSampleCount;
        private int debugResponseSnippetLength;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public boolean isDebug() {
            return debug;
        }

        public void setDebug(boolean debug) {
            this.debug = debug;
        }

        public int getDebugSampleCount() {
            return debugSampleCount;
        }

        public void setDebugSampleCount(int debugSampleCount) {
            this.debugSampleCount = debugSampleCount;
        }

        public int getDebugResponseSnippetLength() {
            return debugResponseSnippetLength;
        }

        public void setDebugResponseSnippetLength(int debugResponseSnippetLength) {
            this.debugResponseSnippetLength = debugResponseSnippetLength;
        }
    }

    public static class Platforms {
        private String rsge;
        private String poster;

        public String getRsge() {
            return rsge;
        }

        public void setRsge(String rsge) {
            this.rsge = rsge;
        }

        public String getPoster() {
            return poster;
        }

        public void setPoster(String poster) {
            this.poster = poster;
        }
    }

    public static class Messages {
        private String validationError;
        private String internalServerErrorPrefix;
        private String supplierMappingNotFoundPrefix;
        private String productMappingNotFoundPrefix;
        private String genericNotFoundPrefix;
        private String missingInPosterTemplate;
        private String missingInRsgeTemplate;
        private String discrepancyCorrectionTemplate;

        public String getValidationError() {
            return validationError;
        }

        public void setValidationError(String validationError) {
            this.validationError = validationError;
        }

        public String getInternalServerErrorPrefix() {
            return internalServerErrorPrefix;
        }

        public void setInternalServerErrorPrefix(String internalServerErrorPrefix) {
            this.internalServerErrorPrefix = internalServerErrorPrefix;
        }

        public String getSupplierMappingNotFoundPrefix() {
            return supplierMappingNotFoundPrefix;
        }

        public void setSupplierMappingNotFoundPrefix(String supplierMappingNotFoundPrefix) {
            this.supplierMappingNotFoundPrefix = supplierMappingNotFoundPrefix;
        }

        public String getProductMappingNotFoundPrefix() {
            return productMappingNotFoundPrefix;
        }

        public void setProductMappingNotFoundPrefix(String productMappingNotFoundPrefix) {
            this.productMappingNotFoundPrefix = productMappingNotFoundPrefix;
        }

        public String getGenericNotFoundPrefix() {
            return genericNotFoundPrefix;
        }

        public void setGenericNotFoundPrefix(String genericNotFoundPrefix) {
            this.genericNotFoundPrefix = genericNotFoundPrefix;
        }

        public String getMissingInPosterTemplate() {
            return missingInPosterTemplate;
        }

        public void setMissingInPosterTemplate(String missingInPosterTemplate) {
            this.missingInPosterTemplate = missingInPosterTemplate;
        }

        public String getMissingInRsgeTemplate() {
            return missingInRsgeTemplate;
        }

        public void setMissingInRsgeTemplate(String missingInRsgeTemplate) {
            this.missingInRsgeTemplate = missingInRsgeTemplate;
        }

        public String getDiscrepancyCorrectionTemplate() {
            return discrepancyCorrectionTemplate;
        }

        public void setDiscrepancyCorrectionTemplate(String discrepancyCorrectionTemplate) {
            this.discrepancyCorrectionTemplate = discrepancyCorrectionTemplate;
        }
    }

    public static class Parsers {
        private final Rsge rsge = new Rsge();
        private final Poster poster = new Poster();
        private final AmountSheet sales = new AmountSheet();
        private final AmountSheet tbc = new AmountSheet();
        private final AmountSheet bog = new AmountSheet();

        public Rsge getRsge() {
            return rsge;
        }

        public Poster getPoster() {
            return poster;
        }

        public AmountSheet getSales() {
            return sales;
        }

        public AmountSheet getTbc() {
            return tbc;
        }

        public AmountSheet getBog() {
            return bog;
        }
    }

    public static class Rsge {
        private int skipHeaderRows;
        private int minColumns;
        private String dateFormat;
        private final RsgeColumns columns = new RsgeColumns();

        public int getSkipHeaderRows() {
            return skipHeaderRows;
        }

        public void setSkipHeaderRows(int skipHeaderRows) {
            this.skipHeaderRows = skipHeaderRows;
        }

        public int getMinColumns() {
            return minColumns;
        }

        public void setMinColumns(int minColumns) {
            this.minColumns = minColumns;
        }

        public String getDateFormat() {
            return dateFormat;
        }

        public void setDateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
        }

        public RsgeColumns getColumns() {
            return columns;
        }
    }

    public static class RsgeColumns {
        private int productName;
        private int unit;
        private int quantity;
        private int unitPrice;
        private int totalPrice;
        private int waybill;
        private int supplier;
        private int date;

        public int getProductName() {
            return productName;
        }

        public void setProductName(int productName) {
            this.productName = productName;
        }

        public int getUnit() {
            return unit;
        }

        public void setUnit(int unit) {
            this.unit = unit;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public int getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(int unitPrice) {
            this.unitPrice = unitPrice;
        }

        public int getTotalPrice() {
            return totalPrice;
        }

        public void setTotalPrice(int totalPrice) {
            this.totalPrice = totalPrice;
        }

        public int getWaybill() {
            return waybill;
        }

        public void setWaybill(int waybill) {
            this.waybill = waybill;
        }

        public int getSupplier() {
            return supplier;
        }

        public void setSupplier(int supplier) {
            this.supplier = supplier;
        }

        public int getDate() {
            return date;
        }

        public void setDate(int date) {
            this.date = date;
        }
    }

    public static class Poster {
        private int sheetIndex;
        private int skipHeaderRows;
        private String dateFormat;
        private final PosterColumns columns = new PosterColumns();

        public int getSheetIndex() {
            return sheetIndex;
        }

        public void setSheetIndex(int sheetIndex) {
            this.sheetIndex = sheetIndex;
        }

        public int getSkipHeaderRows() {
            return skipHeaderRows;
        }

        public void setSkipHeaderRows(int skipHeaderRows) {
            this.skipHeaderRows = skipHeaderRows;
        }

        public String getDateFormat() {
            return dateFormat;
        }

        public void setDateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
        }

        public PosterColumns getColumns() {
            return columns;
        }
    }

    public static class PosterColumns {
        private int documentNumber;
        private int date;
        private int supplier;
        private int productsRaw;
        private int totalPrice;

        public int getDocumentNumber() {
            return documentNumber;
        }

        public void setDocumentNumber(int documentNumber) {
            this.documentNumber = documentNumber;
        }

        public int getDate() {
            return date;
        }

        public void setDate(int date) {
            this.date = date;
        }

        public int getSupplier() {
            return supplier;
        }

        public void setSupplier(int supplier) {
            this.supplier = supplier;
        }

        public int getProductsRaw() {
            return productsRaw;
        }

        public void setProductsRaw(int productsRaw) {
            this.productsRaw = productsRaw;
        }

        public int getTotalPrice() {
            return totalPrice;
        }

        public void setTotalPrice(int totalPrice) {
            this.totalPrice = totalPrice;
        }
    }

    public static class AmountSheet {
        private int sheetIndex;
        private int skipHeaderRows;
        private int minColumns;
        private String dateFormat;
        private final AmountColumns columns = new AmountColumns();

        public int getSheetIndex() {
            return sheetIndex;
        }

        public void setSheetIndex(int sheetIndex) {
            this.sheetIndex = sheetIndex;
        }

        public int getSkipHeaderRows() {
            return skipHeaderRows;
        }

        public void setSkipHeaderRows(int skipHeaderRows) {
            this.skipHeaderRows = skipHeaderRows;
        }

        public int getMinColumns() {
            return minColumns;
        }

        public void setMinColumns(int minColumns) {
            this.minColumns = minColumns;
        }

        public String getDateFormat() {
            return dateFormat;
        }

        public void setDateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
        }

        public AmountColumns getColumns() {
            return columns;
        }
    }

    public static class AmountColumns {
        private int date;
        private int amount;
        private int product = -1;

        public int getDate() {
            return date;
        }

        public void setDate(int date) {
            this.date = date;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public int getProduct() {
            return product;
        }

        public void setProduct(int product) {
            this.product = product;
        }
    }
}
