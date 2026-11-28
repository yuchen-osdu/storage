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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.common.http.HttpConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.databind.DeserializationFeature;

class JsonHttpMessageConvertersTest {

  private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
          HttpMessageConvertersAutoConfiguration.class, WebMvcAutoConfiguration.class))
      .withUserConfiguration(HttpConfiguration.class, BeanConfig.class);

  @Test
  void stringBodiesShouldBeWrittenAsIsAndJsonShouldFailOnUnknownProperties() {
    runner.run(context -> {
      List<HttpMessageConverter<?>> converters = context.getBean(RequestMappingHandlerAdapter.class)
          .getMessageConverters();

      HttpMessageConverter<?> stringWriter = converters.stream()
          .filter(c -> c.canWrite(String.class, MediaType.APPLICATION_JSON))
          .findFirst().orElseThrow();
      assertInstanceOf(StringHttpMessageConverter.class, stringWriter,
          "Pre-serialized String responses must not be JSON-quoted");

      JacksonJsonHttpMessageConverter json = (JacksonJsonHttpMessageConverter) converters.stream()
          .filter(c -> c instanceof JacksonJsonHttpMessageConverter j
              && j.canRead(ResolvableType.forClass(Object.class), MediaType.APPLICATION_JSON))
          .findFirst().orElseThrow();
      assertTrue(json.getMapper().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
      assertTrue(json.getMapper().isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
    });
  }
}
