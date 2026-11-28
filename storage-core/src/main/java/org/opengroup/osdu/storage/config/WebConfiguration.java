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

package org.opengroup.osdu.storage.config;

import org.opengroup.osdu.storage.request.ReplayRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.filter.UrlHandlerFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

  /**
   * Spring Framework 7 removed trailing slash path matching; this filter keeps "/path/" and "/path"
   * mapped to the same handler, ahead of security and request logging filters.
   */
  @Bean
  public FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
    FilterRegistrationBean<UrlHandlerFilter> registration = new FilterRegistrationBean<>(
        UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
    return registration;
  }

  @Override
  public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
    builder.addCustomConverter(strictConverter());
  }

  /**
   * Strict (fail on unknown properties) converter for ReplayRequest only. Not a bean, so it is only registered here
   * and not also collected into Boot's converter list. Jackson 3 defaults are adjusted to the plain Jackson 2
   * {@code ObjectMapper} this converter used before.
   */
  static JacksonJsonHttpMessageConverter strictConverter() {
    JsonMapper jsonMapper = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .build();
    return new JacksonJsonHttpMessageConverter(jsonMapper) {

      @Override
      public boolean canRead(ResolvableType type, MediaType mediaType) {
        return isReplayRequest(type.toClass()) && MediaType.APPLICATION_JSON.includes(mediaType);
      }

      @Override
      public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) {
        return isReplayRequest(valueClass) && MediaType.APPLICATION_JSON.includes(mediaType);
      }

      private boolean isReplayRequest(Class<?> clazz) {
        return ReplayRequest.class.isAssignableFrom(clazz);
      }
    };
  }

}
