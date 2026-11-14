package org.opengroup.osdu.storage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;

@ExtendWith(MockitoExtension.class)
class CollaborationContextHelperTest {

  private static final String DIRECTIVES =
      "id=9e1c4e74-3b9b-4b17-a0d5-67766558ec65,application=TestApp";

  @Mock
  private CollaborationContextFactory factory;

  @Test
  void should_returnFactoryResult_when_createSucceeds() {
    Optional<CollaborationContext> expected = Optional.of(
        CollaborationContext.builder()
            .id(UUID.fromString("9e1c4e74-3b9b-4b17-a0d5-67766558ec65"))
            .application("TestApp")
            .build());
    when(factory.create(eq(DIRECTIVES))).thenReturn(expected);

    Optional<CollaborationContext> actual = CollaborationContextHelper.create(factory, DIRECTIVES);

    assertSame(expected, actual);
  }

  @Test
  void should_mapIllegalArgumentException_toAppException400() {
    when(factory.create(eq(DIRECTIVES)))
        .thenThrow(new IllegalArgumentException("Invalid collaboration directives"));

    AppException e = assertThrows(AppException.class,
        () -> CollaborationContextHelper.create(factory, DIRECTIVES));

    assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
    assertEquals("Validation error.", e.getError().getReason());
    assertEquals("Invalid collaboration directives", e.getError().getMessage());
    assertTrue(e.getOriginalException() instanceof IllegalArgumentException);
  }
}
