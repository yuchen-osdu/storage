package org.opengroup.osdu.storage.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaPropertyOrderOpenApiCustomizerTest {

    @Test
    void should_sortPairSchemaPropertiesAlphabetically() {
        Schema<?> pair = new Schema<>();
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("value", new StringSchema());
        properties.put("key", new StringSchema());
        properties.put("left", new StringSchema());
        properties.put("right", new StringSchema());
        pair.setProperties(properties);

        Schema<?> record = new Schema<>();
        Map<String, Schema> recordProps = new LinkedHashMap<>();
        recordProps.put("id", new StringSchema());
        recordProps.put("kind", new StringSchema());
        record.setProperties(recordProps);

        OpenAPI openApi = new OpenAPI().components(new Components()
                .addSchemas("PairStringString", pair)
                .addSchemas("Record", record));

        SchemaPropertyOrderOpenApiCustomizer.apply(openApi);

        assertEquals(List.of("key", "left", "right", "value"),
                new ArrayList<>(openApi.getComponents().getSchemas().get("PairStringString").getProperties().keySet()));
        // Non-Pair schemas keep declaration order.
        assertEquals(List.of("id", "kind"),
                new ArrayList<>(openApi.getComponents().getSchemas().get("Record").getProperties().keySet()));
    }
}
