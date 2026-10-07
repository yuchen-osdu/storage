package org.opengroup.osdu.storage.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;

/**
 * Stabilizes OpenAPI property order for schemas whose Java source does not yield a
 * deterministic property map (notably Apache Commons {@code Pair} → {@code PairStringString}).
 *
 * <p>Without this, springdoc can emit {@code PairStringString} properties in varying order
 * across JVMs/builds, which makes committed-vs-live OpenAPI checks
 * ({@code cimpl-dev-check-openapi-spec}) flaky. Other schemas keep springdoc's natural
 * (declaration) order.
 */
@UtilityClass
class SchemaPropertyOrderOpenApiCustomizer {

    void apply(OpenAPI openApi) {
        if (openApi == null || openApi.getComponents() == null) {
            return;
        }
        Components components = openApi.getComponents();
        if (components.getSchemas() == null) {
            return;
        }
        for (Map.Entry<String, Schema> entry : components.getSchemas().entrySet()) {
            if (isUnstableSchemaName(entry.getKey())) {
                sortPropertiesRecursively(entry.getValue());
            }
        }
    }

    private boolean isUnstableSchemaName(String name) {
        // springdoc names Pair<String,String> as PairStringString; similarly PairXyz for other args.
        return name != null && name.startsWith("Pair");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void sortPropertiesRecursively(Schema schema) {
        if (schema == null || schema.getProperties() == null || schema.getProperties().isEmpty()) {
            return;
        }
        Map<String, Schema> sorted = new TreeMap<>();
        for (Object entryObj : schema.getProperties().entrySet()) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObj;
            Object value = entry.getValue();
            if (value instanceof Schema nested) {
                sortPropertiesRecursively(nested);
                sorted.put(String.valueOf(entry.getKey()), nested);
            }
        }
        schema.setProperties(new LinkedHashMap<>(sorted));
    }
}
