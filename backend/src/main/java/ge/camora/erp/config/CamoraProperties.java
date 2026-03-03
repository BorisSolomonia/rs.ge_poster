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

        public Rsge getRsge() {
            return rsge;
        }

        public Poster getPoster() {
            return poster;
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
}
