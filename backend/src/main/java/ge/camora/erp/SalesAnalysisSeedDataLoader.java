package ge.camora.erp;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.store.ConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class SalesAnalysisSeedDataLoader {

    private static final Logger log = LoggerFactory.getLogger(SalesAnalysisSeedDataLoader.class);

    private final ConfigStore configStore;
    private final CamoraProperties properties;
    private final ResourceLoader resourceLoader;

    public SalesAnalysisSeedDataLoader(ConfigStore configStore, CamoraProperties properties, ResourceLoader resourceLoader) {
        this.configStore = configStore;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedExcludedProducts() {
        String resourceLocation = properties.getSalesAnalysis().getExclusionsSeedResource();
        if (resourceLocation == null || resourceLocation.isBlank()) {
            return;
        }

        Resource resource = resourceLoader.getResource(resourceLocation);
        if (!resource.exists()) {
            log.warn("SalesAnalysisSeedDataLoader: resource not found {}", resourceLocation);
            return;
        }

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                if (value.isBlank() || value.startsWith("#")) {
                    continue;
                }
                configStore.upsertSalesProduct(value, true, "seed");
                count++;
            }
            log.info("SalesAnalysisSeedDataLoader: upserted {} excluded sales products from seed", count);
        } catch (Exception e) {
            log.error("SalesAnalysisSeedDataLoader failed: {}", e.getMessage(), e);
        }
    }
}
