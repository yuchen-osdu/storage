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

import static java.time.Clock.systemDefaultZone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageConverters.ClientBuilder;
import org.springframework.http.converter.HttpMessageConverters.ServerBuilder;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.StdDateFormat;

@Configuration
public class BeanConfig {

  @Bean
  public Clock clock() {
    return systemDefaultZone();
  }

  @Bean
  public Gson gson() {
    return new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.BIG_DECIMAL).create();
  }

  /**
   * Keeps the Jackson 2 era wire format where Jackson 3 changed a default: decimals are read and written exactly
   * as sent, {@code null} for a primitive stays the default value instead of failing the request, UTC dates are
   * written as {@code +00:00} rather than {@code Z}, and setterless collection getters are still filled on read.
   */
  @Bean
  public JsonMapperBuilderCustomizer recordNumbersCustomizer() {
    return builder -> builder
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .defaultDateFormat(new StdDateFormat().withZeroOffsetAsZ(false))
        .enable(MapperFeature.USE_GETTERS_AS_SETTERS);
  }

  // Restores Boot's customizable mapper, which os-core-common's jsonObjectMapper bean otherwise suppresses.
  // Declared as JsonMapper so it is the one Boot's HTTP message converters pick up.
  @Bean
  @Primary
  public JsonMapper objectMapper(ObjectProvider<JsonMapper.Builder> builderProvider) {
    return builderProvider.getIfAvailable(() -> {
      JsonMapper.Builder builder = JsonMapper.builder();
      recordNumbersCustomizer().customize(builder);
      return builder;
    }).build();
  }

  /**
   * Makes the JSON converter fail on unknown properties. Boot 4 adds {@code HttpMessageConverter} beans as custom
   * converters ahead of the String converter (Boot 3 replaced the default one), which would JSON-quote the
   * pre-serialized String bodies storage returns, so the JSON converter is swapped in through a customizer instead.
   * Ordered after Boot's own Jackson customizer ({@code @Order(0)}) so this converter wins.
   */
  @Bean
  @Order(1)
  public StrictJsonConvertersCustomizer strictJsonConvertersCustomizer(JsonMapper objectMapper) {
    return new StrictJsonConvertersCustomizer(
        objectMapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
  }

  public static class StrictJsonConvertersCustomizer
      implements ServerHttpMessageConvertersCustomizer, ClientHttpMessageConvertersCustomizer {

    private final JsonMapper jsonMapper;

    StrictJsonConvertersCustomizer(JsonMapper jsonMapper) {
      this.jsonMapper = jsonMapper;
    }

    public JsonMapper getJsonMapper() {
      return jsonMapper;
    }

    @Override
    public void customize(ServerBuilder builder) {
      builder.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper));
    }

    @Override
    public void customize(ClientBuilder builder) {
      builder.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper));
    }
  }

}
