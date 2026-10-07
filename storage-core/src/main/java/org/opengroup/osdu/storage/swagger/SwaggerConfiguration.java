package org.opengroup.osdu.storage.swagger;


import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.api.HealthCheckApi;
import org.opengroup.osdu.storage.api.InfoApi;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Configuration
@Profile("!noswagger")
public class SwaggerConfiguration {

    // Public endpoints that do not require data-partition-id. Keys are
    // declaringType#methodName — not OpenAPI operationIds or bare method names —
    // so a future controller method named info()/livenessCheck() is not matched.
    private static final Set<String> PUBLIC_HANDLER_METHODS = Set.of(
            handlerKey(InfoApi.class, "info"),
            handlerKey(HealthCheckApi.class, "livenessCheck"));

    private static String handlerKey(Class<?> controller, String methodName) {
        return controller.getName() + "#" + methodName;
    }

    private static boolean isPublicHandler(HandlerMethod handlerMethod) {
        // Prefer declaring class over beanType so CGLIB/JDK proxies do not miss the match.
        return PUBLIC_HANDLER_METHODS.contains(
                handlerKey(handlerMethod.getMethod().getDeclaringClass(),
                        handlerMethod.getMethod().getName()));
    }
    @Autowired
    private SwaggerConfigurationProperties configurationProperties;

    @Bean
    public OpenAPI customOpenAPI() {

        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("Authorization")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");
        final String securitySchemeName = "Authorization";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(securitySchemeName);
        Components components = new Components().addSecuritySchemes(securitySchemeName, securityScheme);

        OpenAPI openAPI = new OpenAPI()
                .addSecurityItem(securityRequirement)
                .components(components)
                .info(apiInfo())
                .tags(tags());

        if(configurationProperties.isApiServerFullUrlEnabled())
            return openAPI;
        return openAPI
                .servers(Arrays.asList(new Server().url(configurationProperties.getApiServerUrl())));
    }

    private List<Tag> tags() {
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag().name("records").description("Records management operations"));
        tags.add(new Tag().name("query").description("Querying Records operations"));
        tags.add(new Tag().name("info").description("Version info endpoint"));
        return tags;
    }

    private Info apiInfo() {
        return new Info()
                .title(configurationProperties.getApiTitle())
                .description(configurationProperties.getApiDescription())
                .version(configurationProperties.getApiVersion())
                .license(new License().name(configurationProperties.getApiLicenseName()).url(configurationProperties.getApiLicenseUrl()))
                .contact(new Contact().name(configurationProperties.getApiContactName()).email(configurationProperties.getApiContactEmail()));
    }

    @Bean
    public OperationCustomizer operationCustomizer() {
        return (operation, handlerMethod) -> {
                Parameter dataPartitionId = new Parameter()
                        .name(DpsHeaders.DATA_PARTITION_ID)
                        .description("Tenant Id")
                        .in("header")
                        .required(true)
                        // minLength(1): empty data-partition-id is rejected (400), so it is not
                        // a schema-compliant value for contract fuzzers.
                        .schema(new StringSchema().minLength(1));
                Parameter frameOfReference = new Parameter()
                        .name(DpsHeaders.FRAME_OF_REFERENCE)
                        .description("This value indicates whether normalization applies, should be either " +
                                "`none` or `units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;`")
                        .in("header")
                        .required(true)
                        .example("units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;")
                        .schema(new StringSchema());

                // /info and /liveness_check are intentionally public, unauthenticated endpoints
                // that do not require a data-partition-id header.
                if (isPublicHandler(handlerMethod)) {
                    dataPartitionId.setRequired(false);
                }

                // springdoc merges same-path/method handlers (e.g. two PATCH /records consumes)
                // into one Operation and invokes this customizer once per HandlerMethod.
                // Skip if the header was already added on a previous pass.
                addParameterIfAbsent(operation, dataPartitionId);
                if ("fetchRecords".equals(operation.getOperationId())) {
                    addParameterIfAbsent(operation, frameOfReference);
                }
                return operation;
              };
    }

    private static void addParameterIfAbsent(Operation operation, Parameter parameter) {
        if (hasParameter(operation, parameter.getName(), parameter.getIn())) {
            return;
        }
        operation.addParametersItem(parameter);
    }

    private static boolean hasParameter(Operation operation, String name, String in) {
        if (operation.getParameters() == null) {
            return false;
        }
        return operation.getParameters().stream()
                .anyMatch(existing -> name.equals(existing.getName()) && in.equals(existing.getIn()));
    }

    /**
     * springdoc can emit {@code default: "##default"} (swagger-annotations sentinel) for
     * {@code @Schema}-constrained parameters; strip it so contract fuzzers do not treat it as a
     * real sample value.
     */
    @Bean
    public OpenApiCustomizer stripDefaultSentinel() {
        return DefaultSentinelOpenApiCustomizer::apply;
    }

    /**
     * Apache Commons {@code Pair} property order from springdoc is not stable across JVMs;
     * sort those schemas so committed-vs-live OpenAPI checks do not flake.
     */
    @Bean
    public OpenApiCustomizer sortUnstableSchemaProperties() {
        return SchemaPropertyOrderOpenApiCustomizer::apply;
    }

}