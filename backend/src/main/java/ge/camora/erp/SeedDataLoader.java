package ge.camora.erp;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.SupplierMapping;
import ge.camora.erp.store.ConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class SeedDataLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final ConfigStore configStore;
    private final CamoraProperties properties;
    private final ResourceLoader resourceLoader;

    public SeedDataLoader(ConfigStore configStore, CamoraProperties properties, ResourceLoader resourceLoader) {
        this.configStore = configStore;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!properties.getSeed().isEnabled()) {
            log.info("SeedDataLoader: disabled via configuration");
            return;
        }
        if (!configStore.isEmpty()) {
            log.info("SeedDataLoader: skipping, {} supplier mappings already exist",
                     configStore.getAllSupplierMappings().size());
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(properties.getSeed().getResource());
            if (!resource.exists()) {
                log.warn("SeedDataLoader: resource not found: {}", properties.getSeed().getResource());
                return;
            }
            int count = 0;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("\uFEFF")) line = line.substring(1);
                    if (line.isBlank()) continue;

                    int commaIdx = line.indexOf(',');
                    if (commaIdx < 0) continue;

                    String posterAlias  = line.substring(0, commaIdx).trim();
                    String rsgeRawValue = line.substring(commaIdx + 1).trim();

                    if (posterAlias.isEmpty() || rsgeRawValue.isEmpty()) continue;

                    SupplierMapping mapping = new SupplierMapping();
                    mapping.setPosterAlias(posterAlias);
                    mapping.setRsgeRawValue(rsgeRawValue);
                    configStore.addSupplierMapping(mapping);
                    count++;
                }
            }

            log.info("SeedDataLoader: imported {} supplier mappings from seed CSV", count);
        } catch (Exception e) {
            log.error("SeedDataLoader failed: {}", e.getMessage(), e);
        }
    }
}
