package org.opengroup.osdu.storage.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.swagger.v3.oas.annotations.media.Schema.DEFAULT_SENTINEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultSentinelOpenApiCustomizerTest {

    @Test
    void should_clearDefaultSentinel_onOperationParameterSchema() {
        Schema<?> collaborationSchema = new StringSchema()
                .minLength(1)
                .pattern("^(id=[^,]+,)?application=[^,]+$");
        collaborationSchema.setDefault(DEFAULT_SENTINEL);

        Parameter collaboration = new Parameter()
                .name("x-collaboration")
                .in("header")
                .required(true)
                .schema(collaborationSchema);

        OpenAPI openApi = new OpenAPI()
                .path("/api/storage/v2/copy", new PathItem()
                        .put(new Operation()
                                .operationId("copyRecordReferencesBetweenNamespaces")
                                .parameters(List.of(collaboration))));

        DefaultSentinelOpenApiCustomizer.apply(openApi);

        Schema<?> cleared = openApi.getPaths()
                .get("/api/storage/v2/copy")
                .getPut()
                .getParameters()
                .get(0)
                .getSchema();
        assertNull(cleared.getDefault());
        // Must be unset (omitted), not an explicit JSON/YAML `default: null`.
        assertFalse(cleared.getDefaultSetFlag());
        assertEquals(1, cleared.getMinLength());
    }

    @Test
    void should_preserveRealDefaults_andClearNestedSentinel() {
        Schema<?> nested = new StringSchema();
        nested.setDefault(DEFAULT_SENTINEL);

        Schema<?> parent = new Schema<>();
        parent.setProperties(Map.of("name", nested));
        parent.setDefault("keep-me");

        OpenAPI openApi = new OpenAPI()
                .components(new Components().addSchemas("Record", parent));

        DefaultSentinelOpenApiCustomizer.apply(openApi);

        assertEquals("keep-me", openApi.getComponents().getSchemas().get("Record").getDefault());
        assertNull(((Schema<?>) openApi.getComponents()
                .getSchemas()
                .get("Record")
                .getProperties()
                .get("name")).getDefault());
    }
}
