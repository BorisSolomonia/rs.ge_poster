package ge.camora.erp;

import ge.camora.erp.config.CamoraProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final CamoraProperties properties;

    public HealthController(CamoraProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/health/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean ready = true;

        Path configDir = Path.of(properties.getConfigDir());
        boolean configDirExists = Files.isDirectory(configDir);
        boolean configDirWritable = configDirExists && Files.isWritable(configDir);
        checks.put("configDir", Map.of(
            "path", configDir.toAbsolutePath().toString(),
            "exists", configDirExists,
            "writable", configDirWritable
        ));
        ready = configDirExists && configDirWritable;

        checks.put("tbcDbi", Map.of(
            "enabled", properties.getTbcDbi().isEnabled(),
            "hasUsername", present(properties.getTbcDbi().getUsername()),
            "hasPassword", present(properties.getTbcDbi().getPassword()),
            "hasCertificate", present(properties.getTbcDbi().getCertificatePath()) || present(properties.getTbcDbi().getCertificateBase64()),
            "hasAccountNumber", present(properties.getTbcDbi().getAccountNumber())
        ));
        checks.put("bogApi", Map.of(
            "enabled", properties.getBogApi().isEnabled(),
            "hasClientId", present(properties.getBogApi().getClientId()),
            "hasClientSecret", present(properties.getBogApi().getClientSecret()),
            "hasAccountNumber", present(properties.getBogApi().getAccountNumber())
        ));
        checks.put("cashFlow", Map.of(
            "enabled", properties.getCashFlow().isEnabled(),
            "hasSheetId", present(properties.getCashFlow().getSheetId()),
            "hasServiceAccount", present(properties.getCashFlow().getServiceAccountPath()) || present(properties.getCashFlow().getServiceAccountJson())
        ));
        checks.put("rsge", Map.of(
            "hasUsername", present(properties.getRsgeApi().getUsername()),
            "hasPassword", present(properties.getRsgeApi().getPassword())
        ));

        body.put("status", ready ? "UP" : "DOWN");
        body.put("checks", checks);
        return ResponseEntity.status(ready ? 200 : 503).body(body);
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
