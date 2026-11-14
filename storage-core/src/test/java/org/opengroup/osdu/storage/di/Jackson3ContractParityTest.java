// Copyright 2017-2026, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.common.http.HttpConfiguration;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.storage.model.PatchRecordsRequestModel;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Compares the JSON wire format of the storage API models, and of the core-common storage/CRS models storage
 * returns, between the Jackson 2 mappers storage used before (Boot 3 {@code Jackson2ObjectMapperBuilder} plus
 * {@link BeanConfig}'s settings) and the Jackson 3 beans now in the Spring context. Property order is not
 * compared: Jackson 3 sorts alphabetically and JSON objects are unordered.
 */
class Jackson3ContractParityTest {

  private static final Pattern MODEL_PACKAGES = Pattern.compile(
      "org\\.opengroup\\.osdu\\.(storage\\.(model|request|response|dto|enums|conversion)"
          + "|core\\.common\\.model\\.(storage|crs|entitlements|legal|indexer))\\..*");

  // Jackson 3 names a getXValue-style property "xValue" where Jackson 2 wrote "xvalue". These are in-memory
  // helpers that storage never serializes.
  private static final Set<String> NOT_JSON_MODELS = Set.of(
      "org.opengroup.osdu.core.common.model.crs.PointConversionInfo",
      "org.opengroup.osdu.core.common.model.legal.ServiceConfig");

  @SuppressWarnings("removal")
  private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON2_PRIMARY = Jackson2ObjectMapperBuilder.json()
      .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS,
          com.fasterxml.jackson.databind.cfg.JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
      .featuresToEnable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
      .build();

  private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON2_HTTP = JACKSON2_PRIMARY.copy()
      .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private static JsonMapper primary;
  private static JsonMapper http;

  @BeforeAll
  static void loadJackson3Beans() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(HttpConfiguration.class, BeanConfig.class)
        .run(context -> {
          primary = context.getBean(JsonMapper.class);
          http = context.getBean(BeanConfig.StrictJsonConvertersCustomizer.class).getJsonMapper();
        });
  }

  @Test
  void everyModelShouldHaveTheSameWireFormatOnBothJacksonVersions() throws Exception {
    List<String> differences = new ArrayList<>();
    int compared = 0;
    for (Class<?> model : modelClasses()) {
      Object instance = populatedInstance(model);
      if (instance == null) {
        continue;
      }
      String jackson2Json;
      try {
        jackson2Json = JACKSON2_PRIMARY.writeValueAsString(instance);
      } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException e) {
        continue; // Not a JSON model under Jackson 2 either.
      }
      compared++;
      String jackson3Json;
      try {
        jackson3Json = primary.writeValueAsString(instance);
      } catch (RuntimeException e) {
        differences.add(model.getName() + " does not serialize: " + e.getMessage());
        continue;
      }
      if (!sameJson(jackson2Json, jackson3Json)) {
        differences.add(model.getName() + "\n  jackson2: " + jackson2Json + "\n  jackson3: " + jackson3Json);
        continue;
      }
      String readBack = readBackDifference(model, jackson2Json);
      if (readBack != null) {
        differences.add(readBack);
      }
    }
    assertTrue(compared > 90, "Expected to compare the model classes, but only compared " + compared);
    assertEquals(List.of(), differences, String.join("\n", differences));
  }

  @Test
  void recordShouldKeepExactDecimalsNullsAndNestedValues() throws Exception {
    String json = "{\"id\":\"opendes:wellbore:1\",\"kind\":\"opendes:wks:wellbore:1.0.0\",\"version\":1,"
        + "\"data\":{\"big\":12345678901234567890.1234567890,\"price\":1.50,\"exp\":1E+3,\"int\":7,"
        + "\"none\":null,\"nested\":{\"list\":[1,2.0,\"x\",null,true],\"empty\":{}}},"
        + "\"meta\":[{\"kind\":\"Unit\",\"propertyNames\":[\"a\"]}]}";

    Record jackson2 = JACKSON2_HTTP.readValue(json, Record.class);
    Record jackson3 = http.readValue(json, Record.class);

    assertEquals(JACKSON2_PRIMARY.writeValueAsString(jackson2), JACKSON2_PRIMARY.writeValueAsString(jackson3));
    String expected = JACKSON2_PRIMARY.writeValueAsString(jackson2);
    assertEquals(JACKSON2_PRIMARY.readTree(expected), JACKSON2_PRIMARY.readTree(primary.writeValueAsString(jackson3)));
    assertTrue(primary.writeValueAsString(jackson3).contains("12345678901234567890.1234567890"));
    assertTrue(primary.writeValueAsString(jackson3).contains("\"price\":1.50"));
  }

  @Test
  void recordBlobsWrittenWithAPlainMapperShouldNotChange() throws Exception {
    // ObmStorage writes record blobs with a plain mapper; reading them back must give the same JSON.
    String json = "{\"data\":{\"big\":12345678901234567890.1234567890,\"none\":null,\"list\":[1,2.5,null]},"
        + "\"meta\":[{\"kind\":\"Unit\"}],\"modifyUser\":\"user\",\"modifyTime\":7}";
    RecordData recordData = http.readValue(json, RecordData.class);

    String jackson2 = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(recordData);
    String jackson3 = new tools.jackson.databind.ObjectMapper().writeValueAsString(recordData);

    assertEquals(JACKSON2_PRIMARY.readTree(jackson2), JACKSON2_PRIMARY.readTree(jackson3));
  }

  @Test
  void httpMapperShouldStillRejectUnknownPropertiesAndAcceptNullForPrimitives() throws Exception {
    assertThrows(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class,
        () -> JACKSON2_HTTP.readValue("{\"unknown\":1}", PatchRecordsRequestModel.class));
    assertThrows(tools.jackson.databind.exc.UnrecognizedPropertyException.class,
        () -> http.readValue("{\"unknown\":1}", PatchRecordsRequestModel.class));

    assertEquals(0L, JACKSON2_HTTP.readValue("{\"modifyTime\":null}", RecordData.class).getModifyTime());
    assertEquals(0L, http.readValue("{\"modifyTime\":null}", RecordData.class).getModifyTime());
  }

  @Test
  void jsonPatchRequestShouldBindThroughTheJackson2Library() throws Exception {
    String json = "{\"query\":{\"ids\":[\"opendes:wellbore:1\"]},\"ops\":["
        + "{\"op\":\"replace\",\"path\":\"/data/price\",\"value\":1.50},"
        + "{\"op\":\"add\",\"path\":\"/tags/a\",\"value\":\"b\"},"
        + "{\"op\":\"remove\",\"path\":\"/acl/viewers/0\"}]}";

    PatchRecordsRequestModel jackson2 = JACKSON2_HTTP.readValue(json, PatchRecordsRequestModel.class);
    PatchRecordsRequestModel jackson3 = http.readValue(json, PatchRecordsRequestModel.class);

    assertEquals(JACKSON2_PRIMARY.writeValueAsString(jackson2.getOps()), JACKSON2_PRIMARY.writeValueAsString(jackson3.getOps()));
    assertEquals(JACKSON2_PRIMARY.readTree(JACKSON2_PRIMARY.writeValueAsString(jackson2)),
        JACKSON2_PRIMARY.readTree(primary.writeValueAsString(jackson3)));
    assertTrue(primary.writeValueAsString(jackson3).contains("\"value\":1.50"));
    assertThrows(tools.jackson.databind.DatabindException.class,
        () -> http.readValue("{\"ops\":[{\"op\":\"unknown\",\"path\":\"/kind\"}]}", PatchRecordsRequestModel.class));
  }

  private static boolean sameJson(String expected, String actual) throws com.fasterxml.jackson.core.JsonProcessingException {
    return JACKSON2_PRIMARY.readTree(quoteRawSamples(expected)).equals(JACKSON2_PRIMARY.readTree(quoteRawSamples(actual)));
  }

  // @JsonRawValue fields write the "value" sample unquoted; quote it so the output can be parsed.
  private static String quoteRawSamples(String json) {
    return json.replaceAll("(?<=[\\[:,])value(?=[\\],}])", "\"value\"");
  }

  private String readBackDifference(Class<?> model, String json) {
    Object jackson2Value;
    try {
      jackson2Value = JACKSON2_HTTP.readValue(json, model);
    } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException e) {
      return null; // Write-only under Jackson 2, so there is no read behaviour to preserve.
    }
    try {
      Object jackson3Value = http.readValue(json, model);
      String expected = JACKSON2_PRIMARY.writeValueAsString(jackson2Value);
      String actual = JACKSON2_PRIMARY.writeValueAsString(jackson3Value);
      return sameJson(expected, actual) ? null
          : model.getName() + " reads differently\n  jackson2: " + expected + "\n  jackson3: " + actual;
    } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException e) {
      return model.getName() + " does not deserialize: " + e.getMessage();
    }
  }

  private static Set<Class<?>> modelClasses() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new RegexPatternTypeFilter(MODEL_PACKAGES));
    Set<Class<?>> models = new HashSet<>();
    for (String basePackage : List.of("org.opengroup.osdu.storage", "org.opengroup.osdu.core.common.model")) {
      for (BeanDefinition definition : scanner.findCandidateComponents(basePackage)) {
        String name = definition.getBeanClassName();
        if (!name.contains("Test") && !NOT_JSON_MODELS.contains(name)) {
          models.add(Class.forName(name));
        }
      }
    }
    return models;
  }

  private static Object populatedInstance(Class<?> model) {
    try {
      Object instance = model.getDeclaredConstructor().newInstance();
      for (Class<?> type = model; type != null && type != Object.class; type = type.getSuperclass()) {
        for (Field field : type.getDeclaredFields()) {
          populate(instance, field);
        }
      }
      return instance;
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  private static void populate(Object instance, Field field) throws IllegalAccessException {
    if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
      return;
    }
    Object value = sample(field.getType(), field.getGenericType());
    if (value != null) {
      field.setAccessible(true);
      field.set(instance, value);
    }
  }

  private static Object sample(Class<?> type, Type genericType) {
    if (type == String.class) {
      return "value";
    }
    if (type == int.class || type == Integer.class) {
      return 7;
    }
    if (type == long.class || type == Long.class) {
      return 7L;
    }
    if (type == double.class || type == Double.class) {
      return 1.5d;
    }
    if (type == float.class || type == Float.class) {
      return 1.5f;
    }
    if (type == boolean.class || type == Boolean.class) {
      return true;
    }
    if (type == BigDecimal.class) {
      return new BigDecimal("1.50");
    }
    if (type == java.util.Date.class) {
      return new java.util.Date(1893456000000L);
    }
    if (type == java.time.Instant.class) {
      return java.time.Instant.ofEpochSecond(1893456000L);
    }
    if (type == java.time.LocalDate.class) {
      return java.time.LocalDate.of(2030, 1, 2);
    }
    if (type == java.time.LocalDateTime.class) {
      return java.time.LocalDateTime.of(2030, 1, 2, 3, 4, 5);
    }
    if (type.isEnum()) {
      Object[] constants = type.getEnumConstants();
      return constants.length == 0 ? null : constants[0];
    }
    if (type == String[].class) {
      return new String[] {"value"};
    }
    if (genericType instanceof ParameterizedType parameterized) {
      Type[] arguments = parameterized.getActualTypeArguments();
      if (List.class.isAssignableFrom(type) && arguments[0] == String.class) {
        return new ArrayList<>(List.of("value"));
      }
      if (Set.class.isAssignableFrom(type) && arguments[0] == String.class) {
        return new TreeSet<>(Set.of("value"));
      }
      if (Map.class.isAssignableFrom(type) && arguments[0] == String.class) {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        map.put("nullValue", null);
        if (arguments[1] == Object.class) {
          map.put("decimal", new BigDecimal("1.50"));
          map.put("nested", new LinkedHashMap<>(Map.of("list", List.of(1, "x"))));
        }
        return map;
      }
    }
    return null;
  }
}
