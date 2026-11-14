package org.opengroup.osdu.storage.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

import static io.swagger.v3.oas.annotations.media.Schema.DEFAULT_SENTINEL;

/**
 * Removes swagger-annotations {@code Schema.DEFAULT_SENTINEL} ({@code "##default"}) values that
 * springdoc can leak into the served OpenAPI when {@code @Schema} is used with constraints but
 * without an explicit {@code type}/{@code implementation} (it falls through an arraySchema path
 * that calls {@code resolveDefaultValue} without filtering the sentinel).
 *
 * <p>Contract fuzzers treat {@code default: "##default"} as a real sample value, which fails
 * pattern/minLength checks (e.g. {@code x-collaboration}).
 *
 * <p>Coverage is limited to paths, operations, request/response bodies, parameters, and
 * {@code components.schemas|parameters|requestBodies|responses}, plus nested schema
 * properties/items/additionalProperties/composites. It does <em>not</em> walk
 * {@code components.headers}, response headers, callbacks, or OpenAPI 3.1
 * {@code prefixItems}/{@code patternProperties}. That is enough for the current Storage spec;
 * extend the walker if those locations start carrying {@code @Schema} defaults.
 */
@UtilityClass
class DefaultSentinelOpenApiCustomizer {

    void apply(OpenAPI openApi) {
        if (openApi == null) {
            return;
        }
        Set<Schema<?>> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

        if (openApi.getPaths() != null) {
            for (PathItem pathItem : openApi.getPaths().values()) {
                stripPathItem(pathItem, visited);
            }
        }

        Components components = openApi.getComponents();
        if (components == null) {
            return;
        }
        if (components.getSchemas() != null) {
            components.getSchemas().values().forEach(schema -> stripSchema(schema, visited));
        }
        if (components.getParameters() != null) {
            components.getParameters().values().forEach(parameter -> stripParameter(parameter, visited));
        }
        if (components.getRequestBodies() != null) {
            components.getRequestBodies().values().forEach(body -> stripRequestBody(body, visited));
        }
        if (components.getResponses() != null) {
            components.getResponses().values().forEach(response -> stripResponse(response, visited));
        }
    }

    private void stripPathItem(PathItem pathItem, Set<Schema<?>> visited) {
        if (pathItem == null) {
            return;
        }
        if (pathItem.getParameters() != null) {
            pathItem.getParameters().forEach(parameter -> stripParameter(parameter, visited));
        }
        for (Operation operation : pathItem.readOperations()) {
            stripOperation(operation, visited);
        }
    }

    private void stripOperation(Operation operation, Set<Schema<?>> visited) {
        if (operation == null) {
            return;
        }
        if (operation.getParameters() != null) {
            operation.getParameters().forEach(parameter -> stripParameter(parameter, visited));
        }
        stripRequestBody(operation.getRequestBody(), visited);
        if (operation.getResponses() != null) {
            operation.getResponses().values().forEach(response -> stripResponse(response, visited));
        }
    }

    private void stripParameter(Parameter parameter, Set<Schema<?>> visited) {
        if (parameter == null) {
            return;
        }
        stripSchema(parameter.getSchema(), visited);
        stripContent(parameter.getContent(), visited);
        if (isDefaultSentinel(parameter.getExample())) {
            parameter.setExample(null);
        }
    }

    private void stripRequestBody(RequestBody requestBody, Set<Schema<?>> visited) {
        if (requestBody == null) {
            return;
        }
        stripContent(requestBody.getContent(), visited);
    }

    private void stripResponse(ApiResponse response, Set<Schema<?>> visited) {
        if (response == null) {
            return;
        }
        stripContent(response.getContent(), visited);
    }

    private void stripContent(Content content, Set<Schema<?>> visited) {
        if (content == null) {
            return;
        }
        for (MediaType mediaType : content.values()) {
            if (mediaType == null) {
                continue;
            }
            stripSchema(mediaType.getSchema(), visited);
            if (isDefaultSentinel(mediaType.getExample())) {
                mediaType.setExample(null);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private void stripSchema(Schema schema, Set<Schema<?>> visited) {
        if (schema == null || !visited.add(schema)) {
            return;
        }

        if (isDefaultSentinel(schema.getDefault())) {
            // setDefault(null) alone marks defaultSetFlag and serializes as `default: null`.
            // Clear the flag so the property is omitted from the served OpenAPI.
            schema.setDefault(null);
            schema.setDefaultSetFlag(false);
        }
        if (isDefaultSentinel(schema.getExample())) {
            schema.setExample(null);
            schema.setExampleSetFlag(false);
        }

        if (schema.getProperties() != null) {
            for (Object property : schema.getProperties().values()) {
                if (property instanceof Schema<?> propertySchema) {
                    stripSchema(propertySchema, visited);
                }
            }
        }
        stripSchema(schema.getItems(), visited);
        if (schema.getAdditionalProperties() instanceof Schema<?> additional) {
            stripSchema(additional, visited);
        }
        stripSchemaList(schema.getAllOf(), visited);
        stripSchemaList(schema.getAnyOf(), visited);
        stripSchemaList(schema.getOneOf(), visited);
        stripSchema(schema.getNot(), visited);
    }

    private void stripSchemaList(List<Schema> schemas, Set<Schema<?>> visited) {
        if (schemas == null) {
            return;
        }
        for (Schema<?> schema : schemas) {
            stripSchema(schema, visited);
        }
    }

    boolean isDefaultSentinel(Object value) {
        return value != null && DEFAULT_SENTINEL.equals(String.valueOf(value));
    }
}
