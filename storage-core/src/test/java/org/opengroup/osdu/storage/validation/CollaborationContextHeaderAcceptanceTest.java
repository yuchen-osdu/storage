package org.opengroup.osdu.storage.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengroup.osdu.core.common.model.collaboration.validation.CollaborationContextValidationDoc.X_COLLABORATION_DIRECTIVES_PATTERN;

import jakarta.validation.ConstraintValidatorContext;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.opengroup.osdu.core.common.model.validation.CollaborationContextValidator;

/**
 * Guards against regressing to a runtime {@code @Pattern(X_COLLABORATION_DIRECTIVES_PATTERN)}
 * that only accepts the OpenAPI canonical form. Runtime enforcement must stay on
 * {@code @ValidateCollaborationContext}, which is order-independent, case-insensitive on keys,
 * tolerates spaces around {@code =}, and allows forward-compatible extra directives.
 */
class CollaborationContextHeaderAcceptanceTest {

  private static final String UUID = "a99cef48-2ed6-4beb-8a43-002373431f13";

  private CollaborationContextValidator validator;
  private ConstraintValidatorContext context;

  @BeforeEach
  void setUp() {
    validator = new CollaborationContextValidator();
    context = Mockito.mock(ConstraintValidatorContext.class);
    ConstraintValidatorContext.ConstraintViolationBuilder builder =
        Mockito.mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
    Mockito.when(context.buildConstraintViolationWithTemplate(Mockito.anyString()))
        .thenReturn(builder);
    Mockito.when(builder.addConstraintViolation()).thenReturn(context);
  }

  static Stream<String> acceptedCollaborationHeaders() {
    return Stream.of(
        "id=" + UUID + ",application=pws",
        "application=pws,id=" + UUID,
        "id=" + UUID + ", application=pws",
        "ID=" + UUID + ",Application=pws",
        "id=" + UUID + ",application=pws,OtherFutureDirective=x");
  }

  static Stream<String> nonCanonicalButAcceptedHeaders() {
    return Stream.of(
        "application=pws,id=" + UUID,
        "id=" + UUID + ", application=pws",
        "ID=" + UUID + ",Application=pws",
        "id=" + UUID + ",application=pws,OtherFutureDirective=x");
  }

  @ParameterizedTest
  @MethodSource("acceptedCollaborationHeaders")
  void should_acceptFlexibleCollaborationHeaders_viaValidateCollaborationContext(String header) {
    assertTrue(validator.isValid(header, context), () -> "expected valid: " + header);
  }

  @ParameterizedTest
  @MethodSource("nonCanonicalButAcceptedHeaders")
  void should_rejectNonCanonicalHeaders_againstOpenApiCanonicalPatternOnly(String header) {
    assertFalse(
        header.matches(X_COLLABORATION_DIRECTIVES_PATTERN),
        () -> "OpenAPI canonical pattern should not match: " + header);
  }

  @Test
  void should_acceptCanonicalHeader_againstOpenApiCanonicalPattern() {
    String canonical = "id=" + UUID + ",application=pws";
    assertTrue(canonical.matches(X_COLLABORATION_DIRECTIVES_PATTERN));
    assertTrue(validator.isValid(canonical, context));
  }
}
