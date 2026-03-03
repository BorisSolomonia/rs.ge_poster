package ge.camora.erp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CamoraProperties properties;

    public WebConfig(CamoraProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CamoraProperties.Cors cors = properties.getCors();
        registry.addMapping(properties.getApiPrefix() + "/**")
            .allowedOriginPatterns(cors.getAllowedOriginPatterns().toArray(String[]::new))
            .allowedMethods(cors.getAllowedMethods().toArray(String[]::new))
            .allowedHeaders(cors.getAllowedHeaders().toArray(String[]::new))
            .allowCredentials(cors.isAllowCredentials())
            .maxAge(cors.getMaxAgeSeconds());
    }
}
