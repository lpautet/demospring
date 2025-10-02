package net.pautet.softs.demospring.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

@Component
public class ConnectorSchemaProvider {

    private final String schemaName;

    public ConnectorSchemaProvider() {
        this.schemaName = loadSchemaName("NetatmoWeatherConnector.yaml");
    }

    public String schemaName() {
        return schemaName;
    }

    @SuppressWarnings("unchecked")
    private String loadSchemaName(String resourceName) {
        try (InputStream in = new ClassPathResource(resourceName).getInputStream()) {
            Map<String, Object> yaml = new Yaml().load(in);
            Map<String, Object> components = (Map<String, Object>) yaml.get("components");
            if (components == null) return null;
            Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
            if (schemas == null || schemas.isEmpty()) return null;
            return schemas.keySet().iterator().next();
        } catch (Exception e) {
            return null;
        }
    }
}
